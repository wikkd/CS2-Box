#!/usr/bin/env python3
"""为武库商小屋模板添加村庄 jigsaw 连接块，生成各生物群系副本。

背景：村庄房屋池的元素必须包含 jigsaw 块（name=minecraft:building_entrance，
与街道 jigsaw 的 target 匹配）才能被 JigsawPlacement 放置；用户手建的
arms_dealer_hut.nbt 没有 jigsaw 块，导致村庄生成时小屋永远被跳过。

本脚本：
- 读取基础模板 common/src/main/resources/data/csgobox/structure/arms_dealer_hut.nbt
- 在东墙开口 (7,1,6) 添加 jigsaw 块（orientation=east_up，朝向街道）
  —— 原版平原街道的房屋接口朝向为 west/east（垂直于道路），south_up 只能匹配
    稀有的 north_up 接口；east_up 可对接道路东侧常见的 west_up 房屋接口
- 按生物群系生成副本 arms_dealer_hut_<biome>.nbt（jigsaw pool 指向对应群系街道池）
- 更新 5 个村庄房屋池 houses.json 引用对应副本

基础模板（独立结构集/手动 /place 使用）保持不含 jigsaw 不变。
用法：python3 scripts/add-village-jigsaw.py [--weight N]
"""
import argparse
import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STRUCT = ROOT / "common/src/main/resources/data/csgobox/structure"
POOLS = ROOT / "common/src/main/resources/data/minecraft/worldgen/template_pool/village"

BIOMES = {
    "plains": "minecraft:village/plains/streets",
    "desert": "minecraft:village/desert/streets",
    "savanna": "minecraft:village/savanna/streets",
    "snowy": "minecraft:village/snowy/streets",
    "taiga": "minecraft:village/taiga/streets",
}

JIGSAW_POS = (7, 1, 6)  # 东墙开口处（L 形开口的东侧，地面层），接口面朝东
JIGSAW_ORIENTATION = "east_up"


def load_import_helpers():
    spec = importlib.util.spec_from_file_location(
        "import_arms_dealer_hut", ROOT / "scripts/import-arms-dealer-hut.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--weight", type=int, default=6)
    args = ap.parse_args()

    h = load_import_helpers()
    base = h.read_nbt(STRUCT / "arms_dealer_hut.nbt")
    base_payload = base.v
    blocks = base_payload["blocks"].v[1]
    palette = base_payload["palette"].v[1]
    if any("jigsaw" in p.v.get("Name", h.Tag(h.STRING, "")).v for p in palette):
        print("base template already has jigsaw; aborting")
        return 1
    jigsaw_state = len(palette)

    for biome, street_pool in BIOMES.items():
        root = h.read_nbt(STRUCT / "arms_dealer_hut.nbt")
        payload = root.v
        pal = payload["palette"].v[1]
        blks = payload["blocks"].v[1]

        pal.append(h.Tag(h.COMPOUND, {
            "Name": h.tag_str("minecraft:jigsaw"),
            "Properties": h.Tag(h.COMPOUND, {"orientation": h.tag_str(JIGSAW_ORIENTATION)}),
        }))

        nbt = {
            "id": h.tag_str("minecraft:jigsaw"),
            "name": h.tag_str("minecraft:building_entrance"),
            "pool": h.tag_str(street_pool),
            "target": h.tag_str("minecraft:building_entrance"),
            "joint": h.tag_str("aligned"),
            "final_state": h.tag_str("minecraft:air"),
        }
        blks.append(h.Tag(h.COMPOUND, {
            "pos": h.Tag(h.INT_ARRAY, list(JIGSAW_POS)),
            "state": h.Tag(h.INT, jigsaw_state),
            "nbt": h.Tag(h.COMPOUND, nbt),
        }))

        out = STRUCT / f"arms_dealer_hut_{biome}.nbt"
        h.write_nbt(out, root)
        print(f"wrote {out.relative_to(ROOT)} (jigsaw pool={street_pool})")

    for biome in BIOMES:
        pool_file = POOLS / biome / "houses.json"
        data = json.loads(pool_file.read_text(encoding="utf-8"))
        changed = False
        for e in data["elements"]:
            loc = e.get("element", {}).get("location", "")
            if "arms_dealer_hut" in loc:
                if loc != f"csgobox:arms_dealer_hut_{biome}":
                    e["element"]["location"] = f"csgobox:arms_dealer_hut_{biome}"
                e["weight"] = args.weight
                changed = True
        if not changed:
            print(f"WARN: no arms_dealer entry in {pool_file.relative_to(ROOT)}")
        pool_file.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"updated {pool_file.relative_to(ROOT)} -> weight {args.weight}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
