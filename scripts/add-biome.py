#!/usr/bin/env python3
"""向 CS2-Box 的 `has_structure/*` 群系标签追加生物群系（供武库商小屋在模组群系生成）。

背景：武库商小屋（arms_dealer_hut）的 structure JSON 通过
`"biomes": "#csgobox:has_structure/arms_dealer_hut"` 决定生成群系。原版 biome
tag 跨数据包**默认合并追加**（`"replace": true` 才整体覆盖），所以让小屋进模组
群系的最干净做法就是向该 tag 追加 biome id / 嵌套 tag —— structure 与
structure_set 都不用动。同样地，把模组群系加进
`#minecraft:has_structure/village_<biome>` 后，原版该类型村庄（含 CS2-Box 已
挂入 houses 池的小屋副本，weight 6）会自动在模组群系生成。

本脚本：
- 读取目标群系标签 JSON（默认 `data/csgobox/tags/worldgen/biome/has_structure/arms_dealer_hut.json`）
- 去重追加 `--biome` 指定的 biome id 或 `#嵌套 tag`（可多次传参 / 逗号分隔）
- `--replace` 整体覆盖（谨慎：会丢弃其它数据包的追加）
- `--dry-run` 只打印将要发生的改动，不写盘（幂等验证用）
- `--tag <path>` 指定其它目标标签（如 `data/minecraft/tags/worldgen/biome/has_structure/village_plains.json`），
  便于给村庄版小屋开模组群系

用法：
    python3 scripts/add-biome.py --biome mymod:my_biome --biome '#mymod:is_my' --dry-run
    python3 scripts/add-biome.py --biome 'mymod:a,mymod:b' --tag data/minecraft/tags/worldgen/biome/has_structure/village_plains.json
    python3 scripts/add-biome.py --biome mymod:my_biome --replace   # 整体覆盖（慎用）
退出码 0 = 成功（含幂等无改动）。
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_TAG = ROOT / "common/src/main/resources/data/csgobox/tags/worldgen/biome/has_structure/arms_dealer_hut.json"

# Windows 控制台默认 GBK，强制 UTF-8 输出避免中文乱码（stderr 同理）。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def display(path: Path) -> str:
    """仓库内路径显示相对路径，仓库外（如 /tmp 测试副本）显示绝对路径。"""
    try:
        return str(path.resolve().relative_to(ROOT.resolve()))
    except ValueError:
        return str(path)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--tag", type=Path, default=DEFAULT_TAG,
                    help="目标 biome tag JSON（默认 csgobox 的 arms_dealer_hut）")
    ap.add_argument("--biome", action="append", default=[], metavar="ID",
                    help="要追加的 biome id 或 #嵌套 tag（可多次，或用逗号分隔多个）")
    ap.add_argument("--replace", action="store_true",
                    help="整体覆盖 values（默认追加；覆盖会丢弃其它数据包的追加）")
    ap.add_argument("--dry-run", action="store_true",
                    help="只打印改动，不写盘")
    args = ap.parse_args()

    if not args.biome:
        ap.error("至少需要一个 --biome（biome id 或 '#tag'）")

    tag_path = args.tag
    if not tag_path.is_absolute():
        tag_path = ROOT / tag_path
    if not tag_path.exists():
        print(f"ERROR: tag 文件不存在: {display(tag_path)}")
        return 2

    additions = []
    for chunk in args.biome:
        for item in chunk.split(","):
            item = item.strip()
            if item:
                additions.append(item)
    if not additions:
        ap.error("--biome 解析后为空")

    data = load(tag_path)
    values = data.get("values", [])
    new_values = list(additions) if args.replace else list(values)

    added = []
    for item in additions:
        if item not in new_values:
            new_values.append(item)
            added.append(item)

    if not added and not args.replace:
        print(f"OK 幂等无改动: {display(tag_path)}（{len(new_values)} 项）")
        return 0

    print(f"目标: {display(tag_path)}（原 {len(values)} 项）")
    print(f"新增 {len(added)} 项: {', '.join(added)}")
    if args.replace:
        print("模式: --replace 整体覆盖")
    if args.dry_run:
        print(f"DRY-RUN 不写盘（覆盖后共 {len(new_values)} 项）")
        return 0

    data["values"] = new_values
    tag_path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"已写入: {display(tag_path)}（共 {len(new_values)} 项）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
