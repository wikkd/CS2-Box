#!/usr/bin/env python3
"""boxgen.py — CS2-Box 宝箱 JSON 生成器（config/csbox/*.json）

生成符合 CS2-Box v2.0.1 box schema 的宝箱配置。文件名（不含 .json）即为
箱子 id（加载后为 csgobox:<文件名>）。

用法示例
--------
# 命令行模式（每个 --gradeN 可重复，xN=count，xMIN-MAX=count 区间，wN=weight）
python boxgen.py \\
    --name "#FF5555 神化宝石箱" \\
    --key csgobox:csgo_key1 \\
    --drop 0.25 \\
    --random 625,125,25,6,4 \\
    --grade1 "minecraft:iron_ingot x4 w2" \\
    --grade1 "minecraft:bread x8" \\
    --grade2 "minecraft:iron_sword x1 w3" \\
    --grade2 "minecraft:golden_apple x2" \\
    --grade3 "minecraft:diamond_pickaxe x1" \\
    --grade3 "#minecraft:swords x1 w2" \\
    --grade4 "minecraft:diamond_sword x1 enchant" \\
    --grade5 "minecraft:netherite_sword x1-3 w5" \\
    --enchant \\
    --tag #minecraft:swords \\
    --loot-table minecraft:chests/simple_dungeon \\
    --requires apotheosis --requires tacz \\
    --enabled false --discount 0.2 --stock 10 --restock-minutes 60 \\
    --max-per-player 5 --cooldown-seconds 300 --permission csbox.open \\
    --icon 1001 --id my_sword_box --dry-run

# 终端售价统一入 _prices.json（v2.0.1：箱子 JSON 里不再写 price）
python boxgen.py \
    --name 限购箱 --grade1 "minecraft:diamond x4" \
    --price 150 --dry-run
# 多 id / 需要变体键时显式指定：
python boxgen.py \
    --name TACZ 军火终端 --grade3 "tacz:modern_kinetic_gun x1" \
    --price 2000 --price-key "tacz:modern_kinetic_gun#tacz:ak47" --dry-run

# 交互模式：不带任何参数直接运行，逐项提问（带默认值）。
python boxgen.py

# 只打印不写文件
python boxgen.py --name 测试箱 --grade1 "minecraft:bread x8" --dry-run

物品条目（--gradeN SPEC）语法
-----------------------------
    SPEC = <来源> [xN | xMIN-MAX] [wN] [enchant]
    <来源> = namespace:path           → id 字段
           | #namespace:path          → tag 字段（item tag 引用）
    缺省来源时使用 --tag / --loot-table 提供的默认来源（此时 SPEC 只写
    修饰符，例如 "x4 w2"）。
    xN         count = N（N >= 1）
    xMIN-MAX   count = [min, max] 区间（min >= 1 且 max >= min）
    wN         weight = N（N >= 0；>10000 按模组行为钳制为 10000）
    enchant    给该条目加 "enchant": true（随机附魔）

顶层层级字段
------------
    name / key / drop / random / enabled / requires / icon / discount /
    stock / restock_minutes / max_per_player / cooldown_seconds / permission
    grade1 ~ grade5（v2.0.1 schema，详见 docs/box-schema/box.schema.json）

校验规则
--------
    * id 必须 namespace:path；tag 必须 #namespace:path
      （namespace=[a-z0-9_.-]+，path=[a-z0-9_./-]+）
    * count 区间 min >= 1 且 max >= min；weight >= 0
    * 文件名/id 只允许 [a-z0-9_./-]+（拒绝 .. 路径段）
    * 价格表键 ns:path（可选 #variant 子键），值非负整数（--price 合并写
      _prices.json，保留已有条目）

输出前打印摘要：5 档物品数、总权重、每档估算概率百分比
（公式与模组一致：gradeChance = weight / sum(positive weights)，保留 1 位小数）。
"""
import argparse
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")
sys.stdin.reconfigure(encoding="utf-8")

DEFAULT_RANDOM = [625, 125, 25, 6, 4]
ID_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
TAG_RE = re.compile(r"^#[a-z0-9_.-]+:[a-z0-9_./-]+$")
FILE_RE = re.compile(r"^[a-z0-9_./-]+$")
# _prices.json 键：ns:path，可选 #variant（NBT 变体，如 TACZ 枪/弹）
PRICE_KEY_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$")


# ---------------------------------------------------------------------------
# 解析 / 校验助手
# ---------------------------------------------------------------------------

def parse_bool(value):
    """解析 true/false 风格的布尔字符串。"""
    s = str(value).strip().lower()
    if s in ("1", "true", "yes", "y", "on"):
        return True
    if s in ("0", "false", "no", "n", "off"):
        return False
    raise argparse.ArgumentTypeError(f"无法解析布尔值: {value!r}")


def parse_random(value):
    """解析 5 个逗号分隔的等级权重（grade1..grade5）。"""
    parts = [p.strip() for p in value.split(",") if p.strip() != ""]
    if len(parts) != 5:
        raise argparse.ArgumentTypeError(
            f"random 需要恰好 5 个权重（grade1..grade5），得到 {len(parts)} 个: {value!r}")
    out = []
    for p in parts:
        try:
            v = int(p)
        except ValueError:
            raise argparse.ArgumentTypeError(f"权重不是整数: {p!r}")
        if v < 0:
            raise argparse.ArgumentTypeError(f"权重不能为负: {v}")
        out.append(v)
    return out


def validate_id(value, what="id"):
    """id 必须匹配 namespace:path；返回原值。"""
    if not ID_RE.fullmatch(value):
        raise ValueError(
            f"{what} 必须是 namespace:path（namespace=[a-z0-9_.-]+、path=[a-z0-9_./-]+），"
            f"得到 {value!r}")
    return value


def validate_tag(value, what="tag"):
    """tag 必须以 # 开头且 # 后为合法 namespace:path；返回原值。"""
    if not TAG_RE.fullmatch(value):
        raise ValueError(
            f"{what} 必须以 # 开头并跟 namespace:path（例如 #minecraft:swords），"
            f"得到 {value!r}")
    return value


def validate_box_id(value, what="箱子 id"):
    """箱子 id（= 文件名字符串，可含嵌套 '/' 路径段）逐段只允许 [a-z0-9_./-]+。

    只有最终文件名会成为箱子 id，因此**目录部分不参与校验**（Windows 盘符
    大小写、用户目录里的非 id 字符都不应被误判）。每段禁止为空、'.'、'..'。
    """
    if not value:
        raise ValueError(f"{what} 不能为空")
    for segment in value.split("/"):
        if not segment:
            raise ValueError(f"{what} 不能包含空路径段: {value!r}")
        if segment in (".", ".."):
            raise ValueError(f"{what} 不能包含 . / .. 路径段: {value!r}")
        if not FILE_RE.fullmatch(segment):
            raise ValueError(f"{what} 每段只允许字符 [a-z0-9_./-]+，得到 {segment!r}")
    return value


def validate_file_name(value, what="文件名"):
    """兼容旧调用：等同于校验箱子 id（逐段字符集）。"""
    return validate_box_id(value, what)


def to_snake(name):
    """把显示名转为小写蛇形；无字母数字时回退 csgo_box。"""
    s = re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")
    return s or "csgo_box"


def parse_icon(value):
    """icon：纯数字 → int（CustomModelData），否则按字符串 item-model id 保留。"""
    if re.fullmatch(r"\d+", value):
        return int(value)
    return value


# ---------------------------------------------------------------------------
# 物品条目解析
# ---------------------------------------------------------------------------

def parse_grade_spec(spec, default_tag=None, default_loot=None):
    """解析一条 --gradeN 物品条目。

    返回 {"id"|"tag"|"loot_table": ..., "count"?, "weight"?, "enchant"?}。
    """
    tokens = spec.split()
    if not tokens:
        raise ValueError("空物品条目")
    source = None
    count = None
    weight = None
    enchant = False

    for tok in tokens:
        # 来源：以 # 开头（tag），或包含 :（namespace:path / loot table id）。
        # 修饰符（xN / xMIN-MAX / wN / enchant）从不含 ':'。
        if tok.startswith("#") or ":" in tok:
            if source is not None:
                raise ValueError(f"一个条目只能有一个来源，出现 {source!r} 与 {tok!r}")
            source = tok
            continue

        m = re.fullmatch(r"[xX](\d+)(?:-(\d+))?", tok)
        if m:
            lo = int(m.group(1))
            hi = int(m.group(2)) if m.group(2) is not None else None
            if lo < 1:
                raise ValueError(f"count 下限必须 >= 1: {tok}")
            if hi is not None:
                if hi < lo:
                    raise ValueError(f"count 区间 [min,max] 需要 max >= min: {tok}")
                count = [lo, hi]
            else:
                count = lo
            continue

        m = re.fullmatch(r"[wW](\d+)", tok)
        if m:
            weight = int(m.group(1))
            if weight < 0:
                raise ValueError(f"weight 不能为负: {tok}")
            continue

        if tok.lower() in ("enchant", "e"):
            enchant = True
            continue

        if re.fullmatch(r"[wW]-?\d+", tok):
            raise ValueError(f"weight 需要 wN（N >= 0），得到 {tok!r}")
        raise ValueError(
            f"无法识别的修饰符: {tok!r}（支持 xN / xMIN-MAX / wN / enchant；"
            f"物品来源需写为 namespace:path 或 #namespace:path）")

    if source is None:
        if default_tag is not None:
            source = default_tag
            used_default = "tag"
        elif default_loot is not None:
            source = default_loot
            used_default = "loot_table"
        else:
            raise ValueError(
                "条目缺少来源（需要 namespace:path 或 #tag；"
                "或用 --tag / --loot-table 提供默认来源）")
    else:
        used_default = None

    item = {}
    if used_default == "loot_table":
        validate_id(source, "loot_table")
        item["loot_table"] = source
    elif source.startswith("#"):
        validate_tag(source)
        item["tag"] = source
    else:
        validate_id(source)
        item["id"] = source
    if count is not None:
        item["count"] = count
    if weight is not None:
        item["weight"] = weight
    if enchant:
        item["enchant"] = True
    return item


# ---------------------------------------------------------------------------
# 箱子构建
# ---------------------------------------------------------------------------

def build_box(args):
    box = {}
    box["name"] = args.name

    if args.key is not None:
        validate_id(args.key, "key")
        box["key"] = args.key
    if args.drop is not None:
        if not (0.0 <= args.drop <= 1.0):
            raise ValueError(f"drop 必须在 0.0~1.0 之间: {args.drop}")
        box["drop"] = args.drop

    weights = list(args.random)
    for i, w in enumerate(weights):
        if w > 10000:
            print(f"  警告: random[{i}] = {w} 超过 10000，按模组行为钳制为 10000",
                  file=sys.stderr)
            weights[i] = 10000
    box["random"] = weights

    if args.enabled is not None:
        box["enabled"] = args.enabled
    if args.requires:
        box["requires"] = list(args.requires)
    if args.icon is not None:
        box["icon"] = parse_icon(args.icon)
    if args.discount is not None:
        if not (0.0 <= args.discount <= 1.0):
            raise ValueError(f"discount 必须在 0.0~1.0 之间: {args.discount}")
        box["discount"] = args.discount
    if args.stock is not None:
        if args.stock < -1:
            raise ValueError(f"stock 必须 >= -1（-1 = 无限）: {args.stock}")
        box["stock"] = args.stock
    if args.restock_minutes is not None:
        if args.restock_minutes < 0:
            raise ValueError(f"restock_minutes 必须 >= 0: {args.restock_minutes}")
        box["restock_minutes"] = args.restock_minutes
    if args.max_per_player is not None:
        if args.max_per_player < -1:
            raise ValueError(f"max_per_player 必须 >= -1: {args.max_per_player}")
        box["max_per_player"] = args.max_per_player
    if args.cooldown_seconds is not None:
        if args.cooldown_seconds < 0:
            raise ValueError(f"cooldown_seconds 必须 >= 0: {args.cooldown_seconds}")
        box["cooldown_seconds"] = args.cooldown_seconds
    if args.permission is not None:
        box["permission"] = args.permission

    # 与仓库示例一致：grade5 → grade1 降序输出
    for g in (5, 4, 3, 2, 1):
        items = []
        for spec in getattr(args, f"grade{g}"):
            item = parse_grade_spec(spec, default_tag=args.tag,
                                    default_loot=args.loot_table)
            if args.enchant and "enchant" not in item:
                item["enchant"] = True
            if "enchant" in item and "loot_table" in item:
                print("  提示: enchant 对 loot_table 条目在运行时会被忽略",
                      file=sys.stderr)
            items.append(item)
        if items:
            box[f"grade{g}"] = items
    return box


def print_summary(box):
    """打印 5 档物品数、总权重、每档估算概率百分比。"""
    weights = box.get("random", DEFAULT_RANDOM)
    positive = [w for w in weights if w > 0]
    total = sum(positive)
    print(f"总权重（positive 之和）: {total}")
    for g in range(1, 6):
        items = box.get(f"grade{g}", [])
        w = weights[g - 1] if g - 1 < len(weights) else 0
        chance = (w / total * 100.0) if total > 0 and w > 0 else 0.0
        item_weight_sum = sum(
            it.get("weight", 1) for it in items if it.get("weight", 1) > 0)
        print(f"  grade{g}: {len(items)} 件物品 | 档位权重 {w} | "
              f"档内正权重和 {item_weight_sum} | 概率 {chance:.1f}%")


def dump(box):
    return json.dumps(box, ensure_ascii=False, indent=2) + "\n"


def resolve_out(args):
    """决定输出路径；默认 config/csbox/<id>.json（id = 文件名 = 小写蛇形）。

    只校验**文件名 basename**（它才是箱子 id）——显式 --out 的目录部分
    （如 ``C:/Users/.../csbox/``）不参与 id 字符集校验。
    """
    if args.out:
        out = args.out
        base = out.replace("\\", "/")
        if base.lower().endswith(".json"):
            base = base[:-5]
        base = base.rsplit("/", 1)[-1]  # basename only
        validate_box_id(base, what="箱子 id（--out 文件名）")
        return out if out.lower().endswith(".json") else out + ".json"
    box_id = args.id if args.id else to_snake(args.name)
    validate_box_id(box_id)
    return "config/csbox/" + box_id + ".json"


# ---------------------------------------------------------------------------
# 价格表（_prices.json）
# ---------------------------------------------------------------------------

def upsert_price_table(table_path, key, price, dry_run=False):
    """把 {key: price} 合并进 _prices.json（缺失则新建，保留已有条目）。

    key 可为 ns:path 或 ns:path#variant；值与运行期 PriceTable 同口径：
    非负整数。dry_run 只打印将要写入的内容。
    """
    if not PRICE_KEY_RE.fullmatch(key):
        raise ValueError(f"价格表键 {key!r} 非法（需 ns:path，可选 #variant）")
    if not isinstance(price, int) or isinstance(price, bool) or price < 0:
        raise ValueError(f"价格必须是非负整数: {price!r}")
    table = {}
    if os.path.exists(table_path):
        try:
            with open(table_path, encoding="utf-8") as f:
                loaded = json.load(f)
        except json.JSONDecodeError as e:
            raise ValueError(f"{table_path} JSON 解析失败，无法合并价格: {e}") from e
        if not isinstance(loaded, dict):
            raise ValueError(f"{table_path} 不是 JSON 对象（价格表应为 {{id: 价格}} 扁平对象）")
        table = loaded
    table[key] = price
    text = json.dumps(dict(sorted(table.items())), ensure_ascii=False, indent=2) + "\n"
    if dry_run:
        print("--- dry-run: 价格表将写为 ---")
        print(text)
    else:
        os.makedirs(os.path.dirname(table_path) or ".", exist_ok=True)
        with open(table_path, "w", encoding="utf-8") as f:
            f.write(text)
        print(f"已合并价格 {key} = {price} 到 {table_path}")


def merge_box_price(args, box, out):
    """--price 时把价格合并进箱子同目录的 _prices.json。

    v2.0.1 起箱子 JSON 里不再写 price；价格统一由 _prices.json 管理。
    键缺省推导：全部物品条目只有一个纯 id 来源时用它，否则需 --price-key。
    """
    if args.price is None:
        return
    if args.price_key:
        key = args.price_key
    else:
        ids = set()
        for g in range(1, 6):
            for item in box.get(f"grade{g}", []):
                if "id" in item:
                    ids.add(item["id"])
        if not ids:
            raise ValueError("--price 需要一个来源物品 id，当前没有任何 id 条目")
        if len(ids) > 1:
            raise ValueError("--price 推导出多个物品 id（" + "、".join(sorted(ids))
                             + "）——请用 --price-key ns:path[#variant] 显式指定价格表键")
        key = next(iter(ids))
    table_path = args.prices
    if table_path is None:
        table_path = os.path.join(os.path.dirname(out) or ".", "_prices.json")
    upsert_price_table(table_path, key, args.price, dry_run=args.dry_run)


# ---------------------------------------------------------------------------
# 交互模式
# ---------------------------------------------------------------------------

def ask(prompt, default, parse=None, blank_ok=False):
    """交互提问：显示默认值，空白回车用默认值；blank_ok 时空输入返回 None。"""
    suffix = f"（默认: {default}）" if default is not None else ""
    while True:
        try:
            raw = input(f"{prompt}{suffix}: ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            sys.exit(130)
        if raw == "":
            if blank_ok and default is None:
                return None
            if default is not None:
                raw = str(default)
            else:
                continue
        if parse is None:
            return raw
        try:
            return parse(raw)
        except ValueError as e:
            print(f"  无效输入: {e}")


def ask_bool(prompt, default):
    def _p(s):
        return parse_bool(s)
    return ask(prompt, default, parse=_p)


def run_interactive():
    """无参数时的逐项提问模式，返回与 CLI 同构的 Namespace。"""
    import argparse as _argparse
    ns = _argparse.Namespace()

    ns.name = ask("显示名 name", "我的 CS2 宝箱")
    ns.key = ask("钥匙物品 id key", "minecraft:air", parse=validate_id)
    ns.drop = ask("掉落概率 drop (0.0~1.0)", 0.25,
                  parse=lambda s: _range_float(s, "drop", 0.0, 1.0))
    ns.random = ask("等级权重 random（5 个，逗号分隔）", "625,125,25,6,4",
                    parse=parse_random)

    tag = ask("默认 item tag（留空=无，如 #minecraft:swords）", None,
              blank_ok=True)
    if tag:
        if not tag.startswith("#"):
            tag = "#" + tag
        validate_tag(tag)
    loot = ask("默认 loot_table id（留空=无）", None, blank_ok=True)
    if loot:
        validate_id(loot, "loot_table")
    if tag and loot:
        print("  错误: --tag 与 --loot-table 互斥", file=sys.stderr)
        sys.exit(2)
    ns.tag = tag
    ns.loot_table = loot
    ns.enchant = ask_bool("给所有条目加随机附魔 enchant", False)

    for g in range(1, 6):
        n = ask(f"grade{g} 物品数量", 0,
                parse=lambda s: _int_min(s, "物品数量", 0))
        specs = []
        for i in range(n):
            spec = ask(f"  grade{g} 第{i + 1}条（来源 [xN] [wN] [enchant]）",
                       None)
            specs.append(spec)
        setattr(ns, f"grade{g}", specs)

    ns.enabled = ask("enabled（true/false，留空=省略）", None,
                     parse=parse_bool, blank_ok=True)
    req = ask("requires 依赖模组 id（逗号分隔，留空=无）", None, blank_ok=True)
    ns.requires = [r.strip() for r in req.split(",") if r.strip()] if req else []
    ns.icon = ask("icon（数字 CustomModelData 或 item-model id，留空=无）",
                  None, blank_ok=True)
    ns.discount = ask("discount (0.0~1.0，留空=无)", None,
                      parse=lambda s: _range_float(s, "discount", 0.0, 1.0),
                      blank_ok=True)
    ns.stock = ask("stock（-1=无限，留空=无）", None,
                   parse=lambda s: _int_min(s, "stock", -1), blank_ok=True)
    ns.restock_minutes = ask("restock_minutes（留空=无）", None,
                             parse=lambda s: _int_min(s, "restock_minutes", 0),
                             blank_ok=True)
    ns.max_per_player = ask("max_per_player（-1=无限，留空=无）", None,
                            parse=lambda s: _int_min(s, "max_per_player", -1),
                            blank_ok=True)
    ns.cooldown_seconds = ask("cooldown_seconds（留空=无）", None,
                              parse=lambda s: _int_min(s, "cooldown_seconds", 0),
                              blank_ok=True)
    ns.permission = ask("permission 权限节点（留空=无）", None, blank_ok=True)
    box_id = to_snake(ns.name)
    ns.id = ask("箱子 id（= 文件名，小写蛇形；默认由 name 转换）", box_id,
                parse=validate_file_name)
    ns.out = None
    ns.dry_run = False
    return ns


def _int_min(s, what, lo):
    v = int(s)
    if v < lo:
        raise ValueError(f"{what} 必须 >= {lo}")
    return v


def _range_float(s, what, lo, hi):
    v = float(s)
    if not (lo <= v <= hi):
        raise ValueError(f"{what} 必须在 {lo}~{hi} 之间")
    return v


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser():
    parser = argparse.ArgumentParser(
        prog="boxgen.py",
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--name", help="显示名（命令行模式必填）")
    parser.add_argument("--key", default="minecraft:air",
                        help="钥匙物品 id（默认 minecraft:air = 免钥匙）")
    parser.add_argument("--drop", type=float, default=0.25,
                        help="实体掉落概率 0.0~1.0（默认 0.25）")
    parser.add_argument("--random", type=parse_random, default=DEFAULT_RANDOM,
                        help="5 个等级权重 grade1..grade5，逗号分隔（默认 625,125,25,6,4）")
    for g in range(1, 6):
        parser.add_argument(f"--grade{g}", action="append", default=[],
                            metavar="SPEC",
                            help=f"grade{g} 物品条目，可重复；SPEC: 来源 [xN|xMIN-MAX] [wN] [enchant]")
    parser.add_argument("--enchant", action="store_true",
                        help="给所有生成的物品条目加 \"enchant\": true（随机附魔）")
    parser.add_argument("--tag", default=None, metavar="TAG",
                        help="默认物品来源 item tag（#namespace:path），作用于未内联来源的条目")
    parser.add_argument("--loot-table", dest="loot_table", default=None,
                        metavar="ID",
                        help="默认物品来源 loot table id（namespace:path），作用于未内联来源的条目；与 --tag 互斥")
    parser.add_argument("--requires", action="append", default=[],
                        metavar="MODID", help="依赖模组 id，可重复（requires 数组）")
    parser.add_argument("--enabled", type=parse_bool, default=None,
                        metavar="BOOL", help="enabled（true/false；省略时不写字段）")
    parser.add_argument("--discount", type=float, default=None,
                        help="终端价格折扣 0.0~1.0")
    parser.add_argument("--stock", type=int, default=None,
                        help="终端库存限制，-1 = 无限")
    parser.add_argument("--restock-minutes", dest="restock_minutes", type=int,
                        default=None, help="补货间隔分钟，0 = 不自动补货")
    parser.add_argument("--max-per-player", dest="max_per_player", type=int,
                        default=None, help="每人开箱上限，-1 = 无限")
    parser.add_argument("--cooldown-seconds", dest="cooldown_seconds", type=int,
                        default=None, help="开箱冷却秒数，0 = 无冷却")
    parser.add_argument("--permission", default=None,
                        help="权限节点（permission 字段）")
    parser.add_argument("--icon", default=None,
                        help="icon：数字 CustomModelData 或字符串 item-model id")
    parser.add_argument("--id", default=None,
                        help="箱子 id = 文件名（小写蛇形，[a-z0-9_./-]+）；默认由 name 转换")
    parser.add_argument("--price", type=int, default=None,
                        help="物品终端售价（武库点数，非负整数）。v2.0.1 起价格不写进箱子 "
                             "JSON，会合并进箱子同目录的 _prices.json（配合 --price-key / --prices）")
    parser.add_argument("--price-key", default=None, metavar="KEY",
                        help="价格表键（ns:path 或 ns:path#variant）。缺省时若全部物品只有一个 "
                             "纯 id 来源则自动推导，否则必须显式给出")
    parser.add_argument("--prices", default=None, metavar="PATH",
                        help="价格表路径（默认：输出箱子同目录的 _prices.json）")
    parser.add_argument("--out", default=None,
                        help="输出路径（默认 config/csbox/<id>.json）")
    parser.add_argument("--dry-run", action="store_true",
                        help="只打印生成的 JSON，不写文件")
    return parser


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)

    if argv is None and len(sys.argv) == 1:
        args = run_interactive()
    elif args.name is None:
        parser.error("--name 必填（或直接运行进入交互模式）")

    if args.tag is not None and args.loot_table is not None:
        parser.error("--tag 与 --loot-table 互斥，只能选一个")
    try:
        if args.tag is not None:
            if not args.tag.startswith("#"):
                args.tag = "#" + args.tag
            validate_tag(args.tag)
        if args.loot_table is not None:
            validate_id(args.loot_table, "loot_table")
        if args.id is not None:
            validate_file_name(args.id, "箱子 id")
        box = build_box(args)
    except ValueError as e:
        parser.error(str(e))

    print("=== 生成摘要 ===")
    print_summary(box)

    text = dump(box)
    if args.dry_run:
        print("--- dry-run: 不写文件，生成的 JSON 如下 ---")
        print(text)
        merge_box_price(args, box, resolve_out(args))
        return 0

    out = resolve_out(args)
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    merge_box_price(args, box, out)
    with open(out, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"已写入 {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
