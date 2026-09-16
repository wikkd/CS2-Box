#!/usr/bin/env python3
"""check-ids.py — compat-packs 箱子 JSON 物品 id 核查工具

扫描目录下所有 box JSON（config/csbox/*.json schema，文件名即箱子 id），
对其中 id / tag / loot_table 字段做三类检查：

1. 语法检查
   - id         必须匹配 namespace:path（namespace=[a-z0-9_.-]+，
                path=[a-z0-9_./-]+），例如 minecraft:iron_ingot
   - tag        必须以 # 开头，且 # 后为合法 namespace:path，
                例如 #minecraft:swords
   - loot_table 必须是合法 namespace:path，
                例如 minecraft:chests/simple_dungeon
   - 顶层 key 也按 id 语法检查

2. 模组存在性（离线本地比对）
   - 离线无法查询注册表，因此用 --registry <json> 提供本地注册表快照：
         {"minecraft": ["diamond", "iron_ingot"], "apotheosis": ["gem"]}
     即 namespace → item path 列表。
   - 有 registry 时：id 的 namespace 必须在表中，且 path 必须在该
     namespace 的物品列表里；tag / loot_table 只能核对 namespace
     （离线无法核对 tag 内容 / 战利品表路径），并输出提示。
   - 无 registry 时只做语法 + 结构检查，并提示需要注册表文件才能深度核查。

3. 结构粗查
   - 坏 JSON 报文件名 + 行号（json.JSONDecodeError 的 lineno）
   - random / drop / enabled / requires / icon / discount / stock /
     restock_minutes / max_per_player / cooldown_seconds / permission
     类型与范围粗查
   - 物品条目：id / tag / loot_table 三选一（互斥）、weight >= 0、
     count 为 >=1 整数或 [min,max] 区间、enchant 为 true 或对象；
     残留 price 字段报错（v2.0.1 已移除，价格统一入 _prices.json）
   - 价格表 _prices.json：键 = ns:path（可选 #variant 子键），值 = 非负整数

输出汇总：每个文件「有效/无效」、无效条目原因、按 namespace 统计的 id
数量；--json 输出机器可读 JSON。退出码：存在 error 时返回 1。

用法
----
python check-ids.py                          # 默认扫 compat-packs，仅语法/结构
python check-ids.py --dir compat-packs       # 指定目录
python check-ids.py --registry registry.json # 附带本地物品注册表深度核查
python check-ids.py --json                   # 机器可读 JSON 输出
"""
import argparse
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

ID_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
TAG_RE = re.compile(r"^#[a-z0-9_.-]+:[a-z0-9_./-]+$")
GRADE_RE = re.compile(r"grade[1-5]")
# _prices.json 键：ns:path，可选 #variant（TACZ 枪/弹等 NBT 变体）
PRICE_KEY_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$")
PRICE_TABLE_NAME = "_prices.json"

# (字段, 最小值) — 顶层整数类字段
TOP_INT_FIELDS = (("stock", -1), ("restock_minutes", 0),
                  ("max_per_player", -1), ("cooldown_seconds", 0))


def is_number(x):
    return isinstance(x, (int, float)) and not isinstance(x, bool)


# ---------------------------------------------------------------------------
# registry
# ---------------------------------------------------------------------------

def load_registry(path):
    """加载 {namespace: [item_path, ...]} 注册表快照。"""
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise ValueError("registry 必须是对象 {namespace: [item path, ...]}")
    for ns, paths in data.items():
        if not isinstance(ns, str) or not isinstance(paths, list) \
                or not all(isinstance(p, str) for p in paths):
            raise ValueError(f"registry 条目无效: {ns!r}（需要 string → [string, ...]）")
    return data


# ---------------------------------------------------------------------------
# 单项检查
# ---------------------------------------------------------------------------

def check_id_value(value, where, errors, ns_counts, registry):
    """id：语法 + （有 registry 时）namespace/物品存在性。"""
    if not isinstance(value, str) or not ID_RE.fullmatch(value):
        errors.append(f"{where}: 非法 id {value!r}（需 namespace:path）")
        return
    ns, _, path = value.partition(":")
    ns_counts[ns] = ns_counts.get(ns, 0) + 1
    if registry is not None:
        if ns not in registry:
            errors.append(f"{where}: 命名空间/模组不存在: {ns}")
        elif path not in registry[ns]:
            errors.append(f"{where}: 物品不存在（注册表离线比对）: {value}")


def check_tag_value(value, where, errors, infos, registry):
    """tag：'#xxx' = 物品标签引用；否则 = legacy NBT 字符串（SNBT，如 1.20.1 的
    TACZ GunId 顶层 NBT），只做轻量括号/引号配对检查。"""
    if not isinstance(value, str):
        errors.append(f"{where}: tag 应为字符串: {value!r}")
        return
    if not value.startswith("#"):
        if not _nbt_balanced(value):
            errors.append(f"{where}: legacy NBT tag 括号/引号不配对: {value!r}")
        else:
            infos.append(f"{where}: legacy NBT tag（非 # 标签引用），跳过物品标签检查")
        return
    rest = value[1:]
    if not ID_RE.fullmatch(rest):
        errors.append(f"{where}: 非法 tag {value!r}（# 后需 namespace:path）")
        return
    ns = rest.partition(":")[0]
    if registry is not None:
        if ns not in registry:
            errors.append(f"{where}: 命名空间/模组不存在: {ns}（tag）")
        else:
            infos.append(f"{where}: tag 离线只能核对命名空间，无法核对 # 后路径 {rest!r}")


def _nbt_balanced(s):
    """SNBT 轻量配对检查：{} / [] 深度与引号闭合（含 \\ 转义）。"""
    depth = 0
    quote = None
    i = 0
    n = len(s)
    while i < n:
        c = s[i]
        if quote is not None:
            if c == "\\":
                i += 2
                continue
            if c == quote:
                quote = None
        elif c in "\"'":
            quote = c
        elif c in "{[":
            depth += 1
        elif c in "}]":
            depth -= 1
            if depth < 0:
                return False
        i += 1
    return depth == 0 and quote is None


def check_loot_value(value, where, errors, infos, registry):
    """loot_table：语法 + （有 registry 时）namespace 存在性。"""
    if not isinstance(value, str) or not ID_RE.fullmatch(value):
        errors.append(f"{where}: 非法 loot_table id {value!r}（需 namespace:path）")
        return
    ns = value.partition(":")[0]
    if registry is not None:
        if ns not in registry:
            errors.append(f"{where}: 命名空间/模组不存在: {ns}（loot_table）")
        else:
            infos.append(f"{where}: loot_table 离线只能核对命名空间，无法核对路径 {value!r}")


# ---------------------------------------------------------------------------
# 结构检查
# ---------------------------------------------------------------------------

def check_top_level(data, errors):
    if not isinstance(data, dict):
        errors.append("顶层必须是 JSON 对象")
        return

    if "name" in data and not isinstance(data["name"], str):
        errors.append("name: 应为字符串")

    if "type" in data:
        t = data["type"]
        if t not in ("csbox", "terminal"):
            errors.append(f"type: 应为 csbox 或 terminal，得到 {t!r}")
        elif t == "terminal" and "key" in data:
            errors.append("key: terminal 禁止声明 key 字段（严格分离）")

    if "random" in data:
        r = data["random"]
        if not isinstance(r, list) or len(r) != 5 \
                or not all(is_number(x) for x in r):
            errors.append("random: 需要恰好 5 个数字（grade1..grade5）")

    if "drop" in data:
        d = data["drop"]
        if not is_number(d) or not (0.0 <= d <= 1.0):
            errors.append("drop: 需要 0.0~1.0 的数字")

    if "enabled" in data and not isinstance(data["enabled"], bool):
        errors.append("enabled: 应为布尔值")

    if "requires" in data:
        req = data["requires"]
        if not isinstance(req, list) or not all(isinstance(x, str) for x in req):
            errors.append("requires: 应为字符串数组（模组 id 列表）")

    if "icon" in data:
        ic = data["icon"]
        if not ((isinstance(ic, int) and not isinstance(ic, bool) and ic >= 0)
                or isinstance(ic, str)):
            errors.append("icon: 应为非负整数或字符串")

    if "discount" in data:
        d = data["discount"]
        if not is_number(d) or not (0.0 <= d <= 1.0):
            errors.append("discount: 需要 0.0~1.0 的数字")

    for field, lo in TOP_INT_FIELDS:
        if field in data:
            v = data[field]
            if not isinstance(v, int) or isinstance(v, bool) or v < lo:
                errors.append(f"{field}: 需要整数 >= {lo}")

    if "permission" in data and not isinstance(data["permission"], str):
        errors.append("permission: 应为字符串")


def check_item(item, where, errors, infos, ns_counts, registry):
    """单个物品条目：来源互斥 + weight/count/enchant 粗查。"""
    if not isinstance(item, dict):
        errors.append(f"{where}: 物品条目应为对象")
        return

    sources = 0
    if "id" in item:
        sources += 1
        check_id_value(item["id"], where + ".id", errors, ns_counts, registry)
    if "tag" in item:
        v = item["tag"]
        if isinstance(v, str) and v.startswith("#"):
            sources += 1
        check_tag_value(v, where + ".tag", errors, infos, registry)
    if "loot_table" in item:
        sources += 1
        check_loot_value(item["loot_table"], where + ".loot_table",
                         errors, infos, registry)

    if sources == 0:
        errors.append(f"{where}: 必须声明 id / tag / loot_table 之一")
    elif sources > 1:
        errors.append(f"{where}: id / tag / loot_table 只能声明一个（发现 {sources} 个）")

    if "price" in item:
        errors.append(
            f"{where}.price: price 已移除（v2.0.1）——终端价格统一写入目录下的 "
            f"{PRICE_TABLE_NAME}（按物品 id，可选 #variant 子键）")

    if "weight" in item:
        w = item["weight"]
        if not isinstance(w, int) or isinstance(w, bool) or w < 0:
            errors.append(f"{where}.weight: 需要非负整数")
        elif w > 10000:
            infos.append(f"{where}.weight: {w} 超过 10000，模组将钳制为 10000")

    if "count" in item:
        c = item["count"]
        if isinstance(c, bool):
            errors.append(f"{where}.count: 需要整数或 [min,max] 数组，得到 {c!r}")
        elif isinstance(c, list):
            if len(c) == 2 and all(is_number(x) for x in c):
                lo, hi = c
                if lo < 1 or hi < lo:
                    errors.append(
                        f"{where}.count: 区间需 min>=1 且 max>=min，得到 {c!r}")
            else:
                errors.append(f"{where}.count: 需要 [min,max] 两个数字，得到 {c!r}")
        elif not is_number(c) or c < 1:
            errors.append(f"{where}.count: 需要 >= 1，得到 {c!r}")

    if "enchant" in item:
        e = item["enchant"]
        ok = isinstance(e, bool) or (
            isinstance(e, dict)
            and ("id" in e or "level" in e)
            and isinstance(e.get("id", ""), str))
        if not ok:
            errors.append(f"{where}.enchant: 需要 true 或对象 {{id?, level?}}，得到 {e!r}")


# ---------------------------------------------------------------------------
# 文件分析
# ---------------------------------------------------------------------------

def walk(data, path, errors, infos, ns_counts, registry):
    """递归遍历，检查 id/tag/loot_table 字段与 grade 物品数组。"""
    if isinstance(data, dict):
        for k, v in data.items():
            if k.startswith("_"):
                continue  # 跳过 _tutorial 等文档对象
            wp = f"{path}.{k}" if path else k
            if k == "id" and isinstance(v, str):
                check_id_value(v, wp, errors, ns_counts, registry)
            elif k == "tag" and isinstance(v, str):
                check_tag_value(v, wp, errors, infos, registry)
            elif k == "loot_table" and isinstance(v, str):
                check_loot_value(v, wp, errors, infos, registry)
            elif GRADE_RE.fullmatch(k):
                if isinstance(v, list):
                    for i, item in enumerate(v):
                        check_item(item, f"{wp}[{i}]", errors, infos,
                                   ns_counts, registry)
                else:
                    errors.append(f"{wp}: 应为物品数组")
                continue  # 物品条目已整体检查，避免重复计数/重复报错
            elif k == "key" and path == "":
                check_id_value(v, wp, errors, ns_counts, registry)
            walk(v, wp, errors, infos, ns_counts, registry)
    elif isinstance(data, list):
        for i, v in enumerate(data):
            walk(v, f"{path}[{i}]", errors, infos, ns_counts, registry)


def analyze_file(path, registry):
    """分析单个 JSON 文件，返回结果 dict。"""
    errors, infos, ns_counts = [], [], {}
    try:
        with open(path, encoding="utf-8") as f:
            text = f.read()
        data = json.loads(text)
    except json.JSONDecodeError as e:
        errors.append(f"{path}:{e.lineno}:{e.colno}: JSON 解析失败: {e.msg}")
        return {"path": path, "valid": False, "errors": errors,
                "infos": infos, "id_counts": ns_counts}
    except OSError as e:
        errors.append(f"{path}: 无法读取: {e}")
        return {"path": path, "valid": False, "errors": errors,
                "infos": infos, "id_counts": ns_counts}

    check_top_level(data, errors)
    walk(data, "", errors, infos, ns_counts, registry)

    # 文件内错误统一加路径前缀，便于定位
    prefixed = [f"{path}: {e}" if not e.startswith(path) else e for e in errors]
    return {"path": path, "valid": not prefixed, "errors": prefixed,
            "infos": infos, "id_counts": ns_counts}


def _is_valid_price_value(value):
    """_prices.json 值：非负整数（固定价）或两元素 [min, max] 范围（闭区间随机价）。
    与运行时 PriceTable.parse 同口径：bool 不算整数；范围要求两元素、均非负、min<=max。"""
    if isinstance(value, bool):
        return False
    if isinstance(value, int):
        return value >= 0
    if isinstance(value, list) and len(value) == 2:
        lo, hi = value
        if isinstance(lo, bool) or isinstance(hi, bool):
            return False
        return (isinstance(lo, int) and lo >= 0
                and isinstance(hi, int) and hi >= 0
                and lo <= hi)
    return False


def analyze_price_table(path):
    """_prices.json 全局价格表核查：键 = ns:path（可选 #variant），值 = 非负整数
    或 [min, max] 范围（v2.0.1+）。
    与运行时 PriceTable.parse 同口径：非法条目报错并跳过，其余条目照常生效。"""
    errors, infos = [], []
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except json.JSONDecodeError as e:
        errors.append(f"{path}:{e.lineno}:{e.colno}: JSON 解析失败: {e.msg}")
        return {"path": path, "valid": False, "errors": errors,
                "infos": infos, "id_counts": {}}
    except OSError as e:
        errors.append(f"{path}: 无法读取: {e}")
        return {"path": path, "valid": False, "errors": errors,
                "infos": infos, "id_counts": {}}

    if not isinstance(data, dict):
        errors.append(f"{path}: 顶层必须是 JSON 对象")
        return {"path": path, "valid": False, "errors": errors,
                "infos": infos, "id_counts": {}}

    ns_counts = {}
    for key, value in data.items():
        if not PRICE_KEY_RE.fullmatch(key):
            errors.append(f"{path}: 非法价格表键 {key!r}"
                          "（需 ns:path，可选 #variant）")
            continue
        ns = key.partition(":")[0]
        ns_counts[ns] = ns_counts.get(ns, 0) + 1
        if not _is_valid_price_value(value):
            errors.append(f"{path}: {key} 的价格应为非负整数或 [min, max] 范围，得到 {value!r}")

    prefixed = [f"{path}: {e}" if not e.startswith(path) else e for e in errors]
    return {"path": path, "valid": not prefixed, "errors": prefixed,
            "infos": infos, "id_counts": ns_counts}


def collect_json_files(root):
    files = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames.sort()
        for fn in sorted(filenames):
            if fn.lower().endswith(".json"):
                files.append(os.path.join(dirpath, fn))
    return files


# ---------------------------------------------------------------------------
# 输出
# ---------------------------------------------------------------------------

def format_file(res):
    lines = ["[有效]  " + res["path"]] if res["valid"] \
        else ["[无效]  " + res["path"]]
    for e in res["errors"]:
        lines.append("    - " + e)
    for i in res["infos"]:
        lines.append("    ~ " + i)
    return lines


def merge_counts(results):
    agg = {}
    for r in results:
        for ns, c in r["id_counts"].items():
            agg[ns] = agg.get(ns, 0) + c
    return agg


def build_parser():
    parser = argparse.ArgumentParser(
        prog="check-ids.py",
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--dir", default="compat-packs",
                        help="要扫描的目录（递归找 *.json），默认 compat-packs")
    parser.add_argument("--registry", default=None, metavar="JSON",
                        help="本地物品注册表快照 {namespace: [path, ...]}，"
                             "用于离线深度核查；缺省只做语法/结构检查")
    parser.add_argument("--json", action="store_true",
                        help="输出机器可读 JSON")
    return parser


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)

    if not os.path.isdir(args.dir):
        parser.error(f"目录不存在: {args.dir}")

    registry = None
    if args.registry:
        try:
            registry = load_registry(args.registry)
        except (OSError, ValueError) as e:
            parser.error(f"无法加载 registry {args.registry}: {e}")

    files = collect_json_files(args.dir)
    results = []
    for f in files:
        if os.path.basename(f) == PRICE_TABLE_NAME:
            results.append(analyze_price_table(f))
        else:
            results.append(analyze_file(f, registry))
    invalid = [r for r in results if not r["valid"]]
    agg = merge_counts(results)
    notice = None
    if registry is None:
        notice = "未提供 --registry：仅做语法/结构检查，无法深度核查物品存在性"

    summary = {
        "dir": args.dir,
        "registry": args.registry,
        "total": len(results),
        "valid": len(results) - len(invalid),
        "invalid": len(invalid),
        "id_counts": agg,
        "notice": notice,
    }

    if args.json:
        out = {
            "dir": args.dir,
            "registry": args.registry,
            "notice": notice,
            "files": results,
            "summary": summary,
        }
        print(json.dumps(out, ensure_ascii=False, indent=2))
    else:
        for r in results:
            print("\n".join(format_file(r)))
        print()
        print(f"扫描目录: {args.dir}")
        print(f"文件总数: {summary['total']} | "
              f"有效: {summary['valid']} | 无效: {summary['invalid']}")
        if agg:
            pairs = ", ".join(f"{ns}: {c}" for ns, c in sorted(agg.items()))
            print(f"按 namespace 统计的 id 数量: {pairs}")
        if notice:
            print(f"提示: {notice}")

    return 1 if invalid else 0


if __name__ == "__main__":
    sys.exit(main())
