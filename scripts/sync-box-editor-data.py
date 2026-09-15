#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sync box-editor embedded data from the authoritative docs.

Reads docs/box-schema/*.schema.json, docs/examples/*.json and the TACZ
compat pack, then regenerates box-editor/js/data.js (embedded, so the
editor works from file:// with no fetch) and copies the raw schemas into
box-editor/data/schemas/shared/ for future per-version overrides.

Run from the repository root:
    python scripts/sync-box-editor-data.py
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT_JS = ROOT / "box-editor" / "js" / "data.js"
SCHEMA_COPY_DIR = ROOT / "box-editor" / "data" / "schemas" / "shared"

# Version metadata. Extend per version when the runtime schema starts
# diverging: add a "schemaFile" pointing at box-editor/data/schemas/<v>/...
VERSIONS = [
    {
        "key": "1.21.1",
        "label": {"en": "1.21.1 (NeoForge)", "zh": "1.21.1 (NeoForge)"},
        "short": {"en": "1.21.1", "zh": "1.21.1"},
        "taczVariants": True,
        "components": True,
        "itemModel": False,  # MC 1.21.1 has no minecraft:item_model component
        "note": {
            "en": "Data Components; TACZ GunId/AmmoId variants supported.",
            "zh": "Data Components；支持 TACZ GunId/AmmoId 变体键。",
        },
    },
    {
        "key": "26.1.2",
        "label": {"en": "26.1.2", "zh": "26.1.2"},
        "short": {"en": "26.1.2", "zh": "26.1.2"},
        "taczVariants": False,
        "components": True,
        "itemModel": True,
        "note": {
            "en": "Data Components; no id#variant price keys.",
            "zh": "Data Components；价格表不使用 id#变体 子键。",
        },
    },
    {
        "key": "26.2",
        "label": {"en": "26.2", "zh": "26.2"},
        "short": {"en": "26.2", "zh": "26.2"},
        "taczVariants": False,
        "components": True,
        "itemModel": True,
        "note": {
            "en": "Data Components; no id#variant price keys.",
            "zh": "Data Components；价格表不使用 id#变体 子键。",
        },
    },
    {
        "key": "forge-26.1.2",
        "label": {"en": "Forge 26.1.2", "zh": "Forge 26.1.2"},
        "short": {"en": "Forge 26.1.2", "zh": "Forge 26.1.2"},
        "taczVariants": False,
        "components": True,
        "itemModel": True,
        "note": {
            "en": "Data Components; no id#variant price keys.",
            "zh": "Data Components；价格表不使用 id#变体 子键。",
        },
    },
    {
        "key": "forge-26.2",
        "label": {"en": "Forge 26.2", "zh": "Forge 26.2"},
        "short": {"en": "Forge 26.2", "zh": "Forge 26.2"},
        "taczVariants": False,
        "components": True,
        "itemModel": True,
        "note": {
            "en": "Data Components; no id#variant price keys.",
            "zh": "Data Components；价格表不使用 id#变体 子键。",
        },
    },
    {
        "key": "forge-1.20.1",
        "label": {"en": "Forge 1.20.1", "zh": "Forge 1.20.1"},
        "short": {"en": "Forge 1.20.1", "zh": "Forge 1.20.1"},
        "taczVariants": True,
        "components": False,
        "itemModel": False,
        "note": {
            "en": "Legacy NBT tag (no Data Components); TACZ variants supported.",
            "zh": "旧版 NBT tag（无 Data Components）；支持 TACZ 变体键。",
        },
    },
]

# The five grade bands (grade1..grade5).
GRADE_NAMES = [
    {"id": "consumer", "zh": "消费级（灰）", "en": "Consumer (Gray)"},
    {"id": "industrial", "zh": "工业级（浅蓝）", "en": "Industrial (Light Blue)"},
    {"id": "mil_spec", "zh": "军规级（蓝）", "en": "Mil-Spec (Blue)"},
    {"id": "restricted", "zh": "受限级（紫）", "en": "Restricted (Purple)"},
    {"id": "classified", "zh": "保密级（粉/红）", "en": "Classified (Pink/Red)"},
]

GRADE_DEFAULT_PRICES = [6, 10, 16, 22, 30]
GRADE_RECYCLE_YIELDS = [3, 5, 7, 8, 8]

# Every vanilla Minecraft mob (1.21.x). Used for the entity id dropdown.
ENTITY_SUGGESTIONS = [
    {"id": "allay", "en": "Allay", "zh": "悦灵"},
    {"id": "armadillo", "en": "Armadillo", "zh": "犰狳"},
    {"id": "axolotl", "en": "Axolotl", "zh": "美西螈"},
    {"id": "bat", "en": "Bat", "zh": "蝙蝠"},
    {"id": "bee", "en": "Bee", "zh": "蜜蜂"},
    {"id": "blaze", "en": "Blaze", "zh": "烈焰人"},
    {"id": "bogged", "en": "Bogged", "zh": "沼泽骷髅"},
    {"id": "breeze", "en": "Breeze", "zh": "旋风人"},
    {"id": "camel", "en": "Camel", "zh": "骆驼"},
    {"id": "cat", "en": "Cat", "zh": "猫"},
    {"id": "cave_spider", "en": "Cave Spider", "zh": "洞穴蜘蛛"},
    {"id": "chicken", "en": "Chicken", "zh": "鸡"},
    {"id": "cod", "en": "Cod", "zh": "鳕鱼"},
    {"id": "cow", "en": "Cow", "zh": "牛"},
    {"id": "creeper", "en": "Creeper", "zh": "苦力怕"},
    {"id": "dolphin", "en": "Dolphin", "zh": "海豚"},
    {"id": "donkey", "en": "Donkey", "zh": "驴"},
    {"id": "drowned", "en": "Drowned", "zh": "溺尸"},
    {"id": "elder_guardian", "en": "Elder Guardian", "zh": "远古守卫者"},
    {"id": "ender_dragon", "en": "Ender Dragon", "zh": "末影龙"},
    {"id": "enderman", "en": "Enderman", "zh": "末影人"},
    {"id": "endermite", "en": "Endermite", "zh": "末影螨"},
    {"id": "evoker", "en": "Evoker", "zh": "唤魔者"},
    {"id": "fox", "en": "Fox", "zh": "狐狸"},
    {"id": "frog", "en": "Frog", "zh": "青蛙"},
    {"id": "ghast", "en": "Ghast", "zh": "恶魂"},
    {"id": "glow_squid", "en": "Glow Squid", "zh": "发光鱿鱼"},
    {"id": "goat", "en": "Goat", "zh": "山羊"},
    {"id": "guardian", "en": "Guardian", "zh": "守卫者"},
    {"id": "hoglin", "en": "Hoglin", "zh": "疣猪兽"},
    {"id": "horse", "en": "Horse", "zh": "马"},
    {"id": "husk", "en": "Husk", "zh": "尸壳"},
    {"id": "iron_golem", "en": "Iron Golem", "zh": "铁傀儡"},
    {"id": "llama", "en": "Llama", "zh": "羊驼"},
    {"id": "magma_cube", "en": "Magma Cube", "zh": "岩浆怪"},
    {"id": "mooshroom", "en": "Mooshroom", "zh": "哞菇"},
    {"id": "mule", "en": "Mule", "zh": "骡"},
    {"id": "ocelot", "en": "Ocelot", "zh": "豹猫"},
    {"id": "panda", "en": "Panda", "zh": "熊猫"},
    {"id": "parrot", "en": "Parrot", "zh": "鹦鹉"},
    {"id": "phantom", "en": "Phantom", "zh": "幻翼"},
    {"id": "pig", "en": "Pig", "zh": "猪"},
    {"id": "piglin", "en": "Piglin", "zh": "猪灵"},
    {"id": "piglin_brute", "en": "Piglin Brute", "zh": "猪灵蛮兵"},
    {"id": "pillager", "en": "Pillager", "zh": "掠夺者"},
    {"id": "polar_bear", "en": "Polar Bear", "zh": "北极熊"},
    {"id": "pufferfish", "en": "Pufferfish", "zh": "河豚"},
    {"id": "rabbit", "en": "Rabbit", "zh": "兔子"},
    {"id": "ravager", "en": "Ravager", "zh": "劫掠兽"},
    {"id": "salmon", "en": "Salmon", "zh": "鲑鱼"},
    {"id": "sheep", "en": "Sheep", "zh": "绵羊"},
    {"id": "shulker", "en": "Shulker", "zh": "潜影贝"},
    {"id": "silverfish", "en": "Silverfish", "zh": "蠹虫"},
    {"id": "skeleton", "en": "Skeleton", "zh": "骷髅"},
    {"id": "skeleton_horse", "en": "Skeleton Horse", "zh": "骷髅马"},
    {"id": "slime", "en": "Slime", "zh": "史莱姆"},
    {"id": "sniffer", "en": "Sniffer", "zh": "嗅探兽"},
    {"id": "snow_golem", "en": "Snow Golem", "zh": "雪傀儡"},
    {"id": "spider", "en": "Spider", "zh": "蜘蛛"},
    {"id": "squid", "en": "Squid", "zh": "鱿鱼"},
    {"id": "stray", "en": "Stray", "zh": "流浪者"},
    {"id": "strider", "en": "Strider", "zh": "炽足兽"},
    {"id": "tadpole", "en": "Tadpole", "zh": "蝌蚪"},
    {"id": "trader_llama", "en": "Trader Llama", "zh": "行商羊驼"},
    {"id": "tropical_fish", "en": "Tropical Fish", "zh": "热带鱼"},
    {"id": "turtle", "en": "Turtle", "zh": "海龟"},
    {"id": "vex", "en": "Vex", "zh": "恼鬼"},
    {"id": "villager", "en": "Villager", "zh": "村民"},
    {"id": "vindicator", "en": "Vindicator", "zh": "卫道士"},
    {"id": "wandering_trader", "en": "Wandering Trader", "zh": "流浪商人"},
    {"id": "warden", "en": "Warden", "zh": "循声守卫"},
    {"id": "witch", "en": "Witch", "zh": "女巫"},
    {"id": "wither", "en": "Wither", "zh": "凋灵"},
    {"id": "wither_skeleton", "en": "Wither Skeleton", "zh": "凋灵骷髅"},
    {"id": "wolf", "en": "Wolf", "zh": "狼"},
    {"id": "zoglin", "en": "Zoglin", "zh": "僵尸疣猪兽"},
    {"id": "zombie", "en": "Zombie", "zh": "僵尸"},
    {"id": "zombie_horse", "en": "Zombie Horse", "zh": "僵尸马"},
    {"id": "zombie_villager", "en": "Zombie Villager", "zh": "僵尸村民"},
]

# Built-in key items registered by CS2-Box (lang keys: item.csgobox.csgo_key*).
KEY_SUGGESTIONS = [
    {"id": "minecraft:air", "en": "Keyless (minecraft:air)", "zh": "免钥匙 (minecraft:air)"},
    {"id": "csgobox:csgo_key_copper", "en": "Copper Key", "zh": "铜钥匙"},
    {"id": "csgobox:csgo_key0", "en": "Iron Key", "zh": "铁钥匙"},
    {"id": "csgobox:csgo_key1", "en": "Gold Key", "zh": "金钥匙"},
    {"id": "csgobox:csgo_key2", "en": "Diamond Key", "zh": "钻石钥匙"},
    {"id": "csgobox:csgo_key3", "en": "Netherite Key", "zh": "下界合金钥匙"},
]


def load_json(path: pathlib.Path):
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def build_examples():
    examples = []
    doc_examples = [
        ("weapon_dealer", "weapon_dealer.json", "weapon_dealer.json", "_prices.json",
         {"en": "Weapon vendor terminal", "zh": "武器供应商终端"}),
        ("enchant_vendor", "enchant_vendor.json", "enchant_vendor.json", "_prices.json",
         {"en": "Enchanted book vendor terminal", "zh": "附魔书终端"}),
        ("supply_outpost", "supply_outpost.json", "supply_outpost.json", "_prices.json",
         {"en": "Survival supply terminal", "zh": "生存补给终端"}),
        ("tacz_gun_crate", "gun_crate.json", "gun_crate.json", "_prices.json",
         {"en": "TACZ gun crate (compat pack)", "zh": "TACZ 军火枪械箱（联动包）"}),
    ]
    for key, box_file, _, prices_file, desc in doc_examples:
        if key == "tacz_gun_crate":
            box_path = ROOT / "compat-packs" / "tacz-pack" / box_file
            prices_path = ROOT / "compat-packs" / "tacz-pack" / prices_file
        else:
            box_path = ROOT / "docs" / "examples" / box_file
            prices_path = ROOT / "docs" / "examples" / prices_file
        examples.append({
            "key": key,
            "file": box_file,
            "desc": desc,
            "box": load_json(box_path),
            "prices": load_json(prices_path),
        })
    return examples


def main():
    if sys.version_info < (3, 9):
        print("python 3.9+ required", file=sys.stderr)
        return 1
    box_schema = load_json(ROOT / "docs" / "box-schema" / "box.schema.json")
    prices_schema = load_json(ROOT / "docs" / "box-schema" / "prices.schema.json")
    examples = build_examples()

    payload = {
        "schemas": {"box": box_schema, "prices": prices_schema},
        "versions": VERSIONS,
        "gradeNames": GRADE_NAMES,
        "gradeDefaultPrices": GRADE_DEFAULT_PRICES,
        "gradeRecycleYields": GRADE_RECYCLE_YIELDS,
        "keySuggestions": KEY_SUGGESTIONS,
        "entitySuggestions": ENTITY_SUGGESTIONS,
        "examples": examples,
    }
    js = (
        "// AUTO-GENERATED by scripts/sync-box-editor-data.py - do not edit by hand.\n"
        "// Run `python scripts/sync-box-editor-data.py` after changing\n"
        "// docs/box-schema/*.schema.json or the bundled examples.\n"
        "window.CSBDATA = "
        + json.dumps(payload, ensure_ascii=False, indent=2)
        + ";\n"
    )

    SCHEMA_COPY_DIR.mkdir(parents=True, exist_ok=True)
    (SCHEMA_COPY_DIR / "box.schema.json").write_text(
        json.dumps(box_schema, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (SCHEMA_COPY_DIR / "prices.schema.json").write_text(
        json.dumps(prices_schema, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    OUT_JS.parent.mkdir(parents=True, exist_ok=True)
    OUT_JS.write_text(js, encoding="utf-8")
    print(f"wrote {OUT_JS.relative_to(ROOT)} ({len(js)} bytes)")
    print(f"wrote {SCHEMA_COPY_DIR.relative_to(ROOT)}/box.schema.json, prices.schema.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())