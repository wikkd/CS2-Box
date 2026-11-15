#!/usr/bin/env python3
"""merge-prices.py — 把 compat-pack 的 _prices.json 片段合并进全局价格表

用法
----
python scripts/merge-prices.py <source.json> [more.json ...] <target.json>

最后一个参数为目标文件（通常 config/csbox/_prices.json，不存在的则创建），
其余为来源片段（compat-packs/<mod>-pack/_prices.json）。合并规则与
superb-warfare-pack 的既有部署惯例一致：

- 幂等：重复执行不改变结果；
- 既有键优先：目标里已有的 id 定价不被来源覆盖（多包部署先到先得）；
- 键语法按 check-ids.py 的 PRICE_KEY_RE 校验（ns:path，可选 #variant），
  值必须是非负整数或 [min, max] 闭区间（与 PriceTable/PriceRange schema 一致）；
  来源中的非法条目报错退出，不写入；
- 合并后按键排序输出（2 空格缩进 + 尾换行）。

退出码：0 = 合并成功（含无变化），1 = 参数/解析/校验失败。
"""
import json
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

PRICE_KEY_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$")


def is_valid_price_value(value):
    """固定非负整数或 [min, max] 闭区间（PriceRange，v2.0.1 schema）。"""
    if isinstance(value, bool):
        return False
    if isinstance(value, int):
        return value >= 0
    if isinstance(value, list) and len(value) == 2 and all(
            isinstance(v, int) and not isinstance(v, bool) and v >= 0
            for v in value):
        return value[0] <= value[1]
    return False


def load_table(path):
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except FileNotFoundError:
        return {}
    except json.JSONDecodeError as e:
        print(f"ERROR: {path}: bad JSON (line {e.lineno})", file=sys.stderr)
        sys.exit(1)
    if not isinstance(data, dict):
        print(f"ERROR: {path}: price table must be a JSON object", file=sys.stderr)
        sys.exit(1)
    for key, value in data.items():
        if not PRICE_KEY_RE.match(key):
            print(f"ERROR: {path}: bad key {key!r}", file=sys.stderr)
            sys.exit(1)
        if not is_valid_price_value(value):
            print(f"ERROR: {path}: {key!r} = {value!r} is not a non-negative int "
                  f"or [min, max] range", file=sys.stderr)
            sys.exit(1)
    return data


def main():
    args = sys.argv[1:]
    if len(args) < 2:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
    sources, target = args[:-1], args[-1]
    table = load_table(target)
    before = len(table)
    for source in sources:
        fragment = load_table(source)
        for key, value in fragment.items():
            if key in table:
                if table[key] != value:
                    print(f"keep {key} = {table[key]} (target already priced; "
                          f"source {source} wanted {value})")
            else:
                table[key] = value
        print(f"merged {source}: +{len(fragment)} keys")
    # 目标既有键已在 update 前占位，天然「既有键优先」；此处只统计净增。
    added = len(table) - before
    with open(target, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dict(sorted(table.items())), f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"{target}: {before} -> {len(table)} keys (+{added} new)")


if __name__ == "__main__":
    main()
