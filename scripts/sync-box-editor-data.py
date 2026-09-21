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

# Curated item-id autocomplete (common crate loot in vanilla 1.21.x) plus the
# TACZ item ids. TACZ entries carry tacz=True and are filtered client-side by
# the effective TACZ field visibility. Plain ids — the client prefixes
# minecraft:/tacz: when building the datalist.
ITEM_SUGGESTIONS = [
    # melee / ranged weapons
    {"id": "wooden_sword", "en": "Wooden Sword", "zh": "木剑"},
    {"id": "stone_sword", "en": "Stone Sword", "zh": "石剑"},
    {"id": "iron_sword", "en": "Iron Sword", "zh": "铁剑"},
    {"id": "golden_sword", "en": "Golden Sword", "zh": "金剑"},
    {"id": "diamond_sword", "en": "Diamond Sword", "zh": "钻石剑"},
    {"id": "netherite_sword", "en": "Netherite Sword", "zh": "下界合金剑"},
    {"id": "wooden_axe", "en": "Wooden Axe", "zh": "木斧"},
    {"id": "stone_axe", "en": "Stone Axe", "zh": "石斧"},
    {"id": "iron_axe", "en": "Iron Axe", "zh": "铁斧"},
    {"id": "golden_axe", "en": "Golden Axe", "zh": "金斧"},
    {"id": "diamond_axe", "en": "Diamond Axe", "zh": "钻石斧"},
    {"id": "netherite_axe", "en": "Netherite Axe", "zh": "下界合金斧"},
    {"id": "trident", "en": "Trident", "zh": "三叉戟"},
    {"id": "mace", "en": "Mace", "zh": "重锤"},
    {"id": "bow", "en": "Bow", "zh": "弓"},
    {"id": "crossbow", "en": "Crossbow", "zh": "弩"},
    {"id": "arrow", "en": "Arrow", "zh": "箭"},
    {"id": "tipped_arrow", "en": "Tipped Arrow", "zh": "药水箭"},
    {"id": "spectral_arrow", "en": "Spectral Arrow", "zh": "光灵箭"},
    {"id": "shield", "en": "Shield", "zh": "盾牌"},
    # tools
    {"id": "wooden_pickaxe", "en": "Wooden Pickaxe", "zh": "木镐"},
    {"id": "stone_pickaxe", "en": "Stone Pickaxe", "zh": "石镐"},
    {"id": "iron_pickaxe", "en": "Iron Pickaxe", "zh": "铁镐"},
    {"id": "golden_pickaxe", "en": "Golden Pickaxe", "zh": "金镐"},
    {"id": "diamond_pickaxe", "en": "Diamond Pickaxe", "zh": "钻石镐"},
    {"id": "netherite_pickaxe", "en": "Netherite Pickaxe", "zh": "下界合金镐"},
    {"id": "iron_shovel", "en": "Iron Shovel", "zh": "铁锹"},
    {"id": "diamond_shovel", "en": "Diamond Shovel", "zh": "钻石锹"},
    {"id": "netherite_shovel", "en": "Netherite Shovel", "zh": "下界合金锹"},
    {"id": "iron_hoe", "en": "Iron Hoe", "zh": "铁锄"},
    {"id": "diamond_hoe", "en": "Diamond Hoe", "zh": "钻石锄"},
    {"id": "shears", "en": "Shears", "zh": "剪刀"},
    {"id": "flint_and_steel", "en": "Flint and Steel", "zh": "打火石"},
    {"id": "fishing_rod", "en": "Fishing Rod", "zh": "钓鱼竿"},
    {"id": "compass", "en": "Compass", "zh": "指南针"},
    {"id": "clock", "en": "Clock", "zh": "时钟"},
    {"id": "spyglass", "en": "Spyglass", "zh": "望远镜"},
    {"id": "brush", "en": "Brush", "zh": "刷子"},
    {"id": "lead", "en": "Lead", "zh": "拴绳"},
    {"id": "name_tag", "en": "Name Tag", "zh": "命名牌"},
    {"id": "saddle", "en": "Saddle", "zh": "鞍"},
    # armor
    {"id": "leather_helmet", "en": "Leather Helmet", "zh": "皮革头盔"},
    {"id": "leather_chestplate", "en": "Leather Chestplate", "zh": "皮革胸甲"},
    {"id": "leather_leggings", "en": "Leather Leggings", "zh": "皮革裤子"},
    {"id": "leather_boots", "en": "Leather Boots", "zh": "皮革靴子"},
    {"id": "chainmail_helmet", "en": "Chainmail Helmet", "zh": "锁链头盔"},
    {"id": "chainmail_chestplate", "en": "Chainmail Chestplate", "zh": "锁链胸甲"},
    {"id": "chainmail_leggings", "en": "Chainmail Leggings", "zh": "锁链裤子"},
    {"id": "chainmail_boots", "en": "Chainmail Boots", "zh": "锁链靴子"},
    {"id": "iron_helmet", "en": "Iron Helmet", "zh": "铁头盔"},
    {"id": "iron_chestplate", "en": "Iron Chestplate", "zh": "铁胸甲"},
    {"id": "iron_leggings", "en": "Iron Leggings", "zh": "铁裤子"},
    {"id": "iron_boots", "en": "Iron Boots", "zh": "铁靴子"},
    {"id": "golden_helmet", "en": "Golden Helmet", "zh": "金头盔"},
    {"id": "golden_chestplate", "en": "Golden Chestplate", "zh": "金胸甲"},
    {"id": "golden_leggings", "en": "Golden Leggings", "zh": "金裤子"},
    {"id": "golden_boots", "en": "Golden Boots", "zh": "金靴子"},
    {"id": "diamond_helmet", "en": "Diamond Helmet", "zh": "钻石头盔"},
    {"id": "diamond_chestplate", "en": "Diamond Chestplate", "zh": "钻石胸甲"},
    {"id": "diamond_leggings", "en": "Diamond Leggings", "zh": "钻石裤子"},
    {"id": "diamond_boots", "en": "Diamond Boots", "zh": "钻石靴子"},
    {"id": "netherite_helmet", "en": "Netherite Helmet", "zh": "下界合金头盔"},
    {"id": "netherite_chestplate", "en": "Netherite Chestplate", "zh": "下界合金胸甲"},
    {"id": "netherite_leggings", "en": "Netherite Leggings", "zh": "下界合金裤子"},
    {"id": "netherite_boots", "en": "Netherite Boots", "zh": "下界合金靴子"},
    {"id": "turtle_helmet", "en": "Turtle Shell", "zh": "海龟壳"},
    {"id": "elytra", "en": "Elytra", "zh": "鞘翅"},
    # ores / ingots / gems
    {"id": "coal", "en": "Coal", "zh": "煤炭"},
    {"id": "charcoal", "en": "Charcoal", "zh": "木炭"},
    {"id": "raw_iron", "en": "Raw Iron", "zh": "粗铁"},
    {"id": "raw_gold", "en": "Raw Gold", "zh": "粗金"},
    {"id": "raw_copper", "en": "Raw Copper", "zh": "粗铜"},
    {"id": "iron_ingot", "en": "Iron Ingot", "zh": "铁锭"},
    {"id": "gold_ingot", "en": "Gold Ingot", "zh": "金锭"},
    {"id": "copper_ingot", "en": "Copper Ingot", "zh": "铜锭"},
    {"id": "netherite_ingot", "en": "Netherite Ingot", "zh": "下界合金锭"},
    {"id": "netherite_scrap", "en": "Netherite Scrap", "zh": "下界合金碎片"},
    {"id": "diamond", "en": "Diamond", "zh": "钻石"},
    {"id": "emerald", "en": "Emerald", "zh": "绿宝石"},
    {"id": "lapis_lazuli", "en": "Lapis Lazuli", "zh": "青金石"},
    {"id": "redstone", "en": "Redstone Dust", "zh": "红石粉"},
    {"id": "amethyst_shard", "en": "Amethyst Shard", "zh": "紫水晶碎片"},
    {"id": "quartz", "en": "Nether Quartz", "zh": "下界石英"},
    {"id": "gold_nugget", "en": "Gold Nugget", "zh": "金粒"},
    {"id": "iron_nugget", "en": "Iron Nugget", "zh": "铁粒"},
    {"id": "nether_star", "en": "Nether Star", "zh": "下界之星"},
    {"id": "echo_shard", "en": "Echo Shard", "zh": "回响碎片"},
    {"id": "netherite_upgrade_smithing_template", "en": "Netherite Upgrade", "zh": "锻造模板：下界合金升级"},
    # blocks (common loot / building)
    {"id": "stone", "en": "Stone", "zh": "石头"},
    {"id": "cobblestone", "en": "Cobblestone", "zh": "圆石"},
    {"id": "deepslate", "en": "Deepslate", "zh": "深板岩"},
    {"id": "cobbled_deepslate", "en": "Cobbled Deepslate", "zh": "深板岩圆石"},
    {"id": "granite", "en": "Granite", "zh": "花岗岩"},
    {"id": "andesite", "en": "Andesite", "zh": "安山岩"},
    {"id": "diorite", "en": "Diorite", "zh": "闪长岩"},
    {"id": "dirt", "en": "Dirt", "zh": "泥土"},
    {"id": "grass_block", "en": "Grass Block", "zh": "草方块"},
    {"id": "sand", "en": "Sand", "zh": "沙子"},
    {"id": "red_sand", "en": "Red Sand", "zh": "红沙"},
    {"id": "gravel", "en": "Gravel", "zh": "沙砾"},
    {"id": "clay_ball", "en": "Clay Ball", "zh": "黏土球"},
    {"id": "sandstone", "en": "Sandstone", "zh": "砂岩"},
    {"id": "obsidian", "en": "Obsidian", "zh": "黑曜石"},
    {"id": "crying_obsidian", "en": "Crying Obsidian", "zh": "哭泣的黑曜石"},
    {"id": "netherrack", "en": "Netherrack", "zh": "下界岩"},
    {"id": "end_stone", "en": "End Stone", "zh": "末地石"},
    {"id": "prismarine_shard", "en": "Prismarine Shard", "zh": "海晶碎片"},
    {"id": "prismarine_crystals", "en": "Prismarine Crystals", "zh": "海晶砂粒"},
    {"id": "sea_lantern", "en": "Sea Lantern", "zh": "海晶灯"},
    {"id": "glowstone_dust", "en": "Glowstone Dust", "zh": "荧石粉"},
    {"id": "glowstone", "en": "Glowstone", "zh": "荧石"},
    {"id": "shroomlight", "en": "Shroomlight", "zh": "菌光体"},
    {"id": "torch", "en": "Torch", "zh": "火把"},
    {"id": "lantern", "en": "Lantern", "zh": "灯笼"},
    {"id": "soul_lantern", "en": "Soul Lantern", "zh": "灵魂灯笼"},
    {"id": "chain", "en": "Chain", "zh": "锁链"},
    {"id": "iron_bars", "en": "Iron Bars", "zh": "铁栏杆"},
    {"id": "scaffolding", "en": "Scaffolding", "zh": "脚手架"},
    {"id": "glass", "en": "Glass", "zh": "玻璃"},
    {"id": "tnt", "en": "TNT", "zh": "TNT"},
    {"id": "bookshelf", "en": "Bookshelf", "zh": "书架"},
    {"id": "crafting_table", "en": "Crafting Table", "zh": "工作台"},
    {"id": "furnace", "en": "Furnace", "zh": "熔炉"},
    {"id": "chest", "en": "Chest", "zh": "箱子"},
    {"id": "barrel", "en": "Barrel", "zh": "木桶"},
    {"id": "ender_chest", "en": "Ender Chest", "zh": "末影箱"},
    {"id": "shulker_box", "en": "Shulker Box", "zh": "潜影盒"},
    {"id": "white_wool", "en": "White Wool", "zh": "白色羊毛"},
    {"id": "black_wool", "en": "Black Wool", "zh": "黑色羊毛"},
    {"id": "blue_wool", "en": "Blue Wool", "zh": "蓝色羊毛"},
    {"id": "red_wool", "en": "Red Wool", "zh": "红色羊毛"},
    {"id": "yellow_wool", "en": "Yellow Wool", "zh": "黄色羊毛"},
    {"id": "lime_wool", "en": "Lime Wool", "zh": "黄绿色羊毛"},
    # wood
    {"id": "oak_log", "en": "Oak Log", "zh": "橡木原木"},
    {"id": "spruce_log", "en": "Spruce Log", "zh": "云杉原木"},
    {"id": "birch_log", "en": "Birch Log", "zh": "白桦原木"},
    {"id": "jungle_log", "en": "Jungle Log", "zh": "丛林原木"},
    {"id": "acacia_log", "en": "Acacia Log", "zh": "金合欢原木"},
    {"id": "dark_oak_log", "en": "Dark Oak Log", "zh": "深色橡木原木"},
    {"id": "mangrove_log", "en": "Mangrove Log", "zh": "红树原木"},
    {"id": "cherry_log", "en": "Cherry Log", "zh": "樱花原木"},
    {"id": "bamboo_block", "en": "Bamboo Block", "zh": "竹块"},
    {"id": "crimson_stem", "en": "Crimson Stem", "zh": "绯红菌柄"},
    {"id": "warped_stem", "en": "Warped Stem", "zh": "诡异菌柄"},
    {"id": "oak_planks", "en": "Oak Planks", "zh": "橡木木板"},
    {"id": "stick", "en": "Stick", "zh": "木棍"},
    # food
    {"id": "bread", "en": "Bread", "zh": "面包"},
    {"id": "apple", "en": "Apple", "zh": "苹果"},
    {"id": "golden_apple", "en": "Golden Apple", "zh": "金苹果"},
    {"id": "enchanted_golden_apple", "en": "Enchanted Golden Apple", "zh": "附魔金苹果"},
    {"id": "golden_carrot", "en": "Golden Carrot", "zh": "金胡萝卜"},
    {"id": "carrot", "en": "Carrot", "zh": "胡萝卜"},
    {"id": "potato", "en": "Potato", "zh": "马铃薯"},
    {"id": "baked_potato", "en": "Baked Potato", "zh": "烤马铃薯"},
    {"id": "beetroot", "en": "Beetroot", "zh": "甜菜根"},
    {"id": "sweet_berries", "en": "Sweet Berries", "zh": "甜浆果"},
    {"id": "glow_berries", "en": "Glow Berries", "zh": "发光浆果"},
    {"id": "pumpkin_pie", "en": "Pumpkin Pie", "zh": "南瓜派"},
    {"id": "cookie", "en": "Cookie", "zh": "曲奇"},
    {"id": "honey_bottle", "en": "Honey Bottle", "zh": "蜂蜜瓶"},
    {"id": "cooked_beef", "en": "Cooked Beef", "zh": "熟牛排"},
    {"id": "cooked_porkchop", "en": "Cooked Porkchop", "zh": "熟猪排"},
    {"id": "cooked_chicken", "en": "Cooked Chicken", "zh": "熟鸡肉"},
    {"id": "cooked_cod", "en": "Cooked Cod", "zh": "熟鳕鱼"},
    {"id": "cooked_salmon", "en": "Cooked Salmon", "zh": "熟鲑鱼"},
    {"id": "dried_kelp", "en": "Dried Kelp", "zh": "干海带"},
    # potions / combat consumables
    {"id": "potion", "en": "Potion", "zh": "药水"},
    {"id": "splash_potion", "en": "Splash Potion", "zh": "喷溅药水"},
    {"id": "lingering_potion", "en": "Lingering Potion", "zh": "滞留药水"},
    {"id": "experience_bottle", "en": "Bottle o' Enchanting", "zh": "附魔之瓶"},
    {"id": "fire_charge", "en": "Fire Charge", "zh": "火焰弹"},
    {"id": "ender_pearl", "en": "Ender Pearl", "zh": "末影珍珠"},
    {"id": "ender_eye", "en": "Eye of Ender", "zh": "末影之眼"},
    {"id": "snowball", "en": "Snowball", "zh": "雪球"},
    {"id": "egg", "en": "Egg", "zh": "鸡蛋"},
    # enchanting / misc valuables
    {"id": "book", "en": "Book", "zh": "书"},
    {"id": "enchanted_book", "en": "Enchanted Book", "zh": "附魔书"},
    {"id": "paper", "en": "Paper", "zh": "纸"},
    {"id": "bone", "en": "Bone", "zh": "骨头"},
    {"id": "blaze_rod", "en": "Blaze Rod", "zh": "烈焰棒"},
    {"id": "blaze_powder", "en": "Blaze Powder", "zh": "烈焰粉"},
    {"id": "ghast_tear", "en": "Ghast Tear", "zh": "恶魂之泪"},
    {"id": "magma_cream", "en": "Magma Cream", "zh": "岩浆膏"},
    {"id": "phantom_membrane", "en": "Phantom Membrane", "zh": "幻翼膜"},
    {"id": "slime_ball", "en": "Slimeball", "zh": "黏液球"},
    {"id": "totem_of_undying", "en": "Totem of Undying", "zh": "不死图腾"},
    {"id": "shulker_shell", "en": "Shulker Shell", "zh": "潜影壳"},
    {"id": "heart_of_the_sea", "en": "Heart of the Sea", "zh": "海洋之心"},
    {"id": "nautilus_shell", "en": "Nautilus Shell", "zh": "鹦鹉螺壳"},
    {"id": "wind_charge", "en": "Wind Charge", "zh": "风弹"},
    {"id": "goat_horn", "en": "Goat Horn", "zh": "山羊角"},
    {"id": "disc_fragment_5", "en": "Disc Fragment (5)", "zh": "唱片残片（5）"},
    {"id": "music_disc_cat", "en": "Music Disc — Cat", "zh": "音乐唱片 — Cat"},
    {"id": "music_disc_pigstep", "en": "Music Disc — Pigstep", "zh": "音乐唱片 — Pigstep"},
    {"id": "music_disc_otherside", "en": "Music Disc — Otherside", "zh": "音乐唱片 — Otherside"},
    {"id": "leather", "en": "Leather", "zh": "皮革"},
    {"id": "rabbit_hide", "en": "Rabbit Hide", "zh": "兔子皮"},
    {"id": "ink_sac", "en": "Ink Sac", "zh": "墨囊"},
    {"id": "glow_ink_sac", "en": "Glow Ink Sac", "zh": "发光墨囊"},
    {"id": "honeycomb", "en": "Honeycomb", "zh": "蜜脾"},
    {"id": "scute", "en": "Scute", "zh": "鳞甲"},
    {"id": "spider_eye", "en": "Spider Eye", "zh": "蜘蛛眼"},
    {"id": "gunpowder", "en": "Gunpowder", "zh": "火药"},
    {"id": "bone_meal", "en": "Bone Meal", "zh": "骨粉"},
    {"id": "sugar", "en": "Sugar", "zh": "糖"},
    {"id": "breeze_rod", "en": "Breeze Rod", "zh": "旋风棒"},
    {"id": "heavy_core", "en": "Heavy Core", "zh": "重型核心"},
    # TACZ (Timeless and Classics Zero) — gated by TACZ field visibility
    {"id": "modern_kinetic_gun", "en": "TACZ Gun", "zh": "TACZ 枪械", "tacz": True},
    {"id": "ammo", "en": "TACZ Ammo", "zh": "TACZ 弹药", "tacz": True},
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
        "itemSuggestions": ITEM_SUGGESTIONS,
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