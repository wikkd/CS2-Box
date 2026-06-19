package com.reclizer.csgobox.utils;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public final class EntityChineseMap {

    private static final Map<ResourceLocation, String> ZH_MAP = new HashMap<>();

    private EntityChineseMap() {
    }

    static {
        putAllPassive();
        putAllHostile();
        putAllNeutral();
        putAllBoss();
        putAllAquatic();
        putAllVehicle();
        putAllMisc();
    }

    public static String getDisplayName(ResourceLocation entityId) {
        String zh = ZH_MAP.get(entityId);
        if (zh != null) return zh;
        return entityId.getPath();
    }

    public static String getDisplayNameFull(ResourceLocation entityId) {
        String zh = ZH_MAP.get(entityId);
        if (zh != null) return zh + " (" + entityId + ")";
        return entityId.toString();
    }

    private static void putAll(String[][] entries) {
        for (String[] entry : entries) {
            ZH_MAP.put(ResourceLocation.parse(entry[0]), entry[1]);
        }
    }

    private static void putAllPassive() {
        putAll(new String[][] {
                {"minecraft:bat", "蝙蝠"},
                {"minecraft:chicken", "鸡"},
                {"minecraft:cow", "牛"},
                {"minecraft:mooshroom", "哞菇"},
                {"minecraft:pig", "猪"},
                {"minecraft:sheep", "羊"},
                {"minecraft:rabbit", "兔子"},
                {"minecraft:wolf", "狼"},
                {"minecraft:cat", "猫"},
                {"minecraft:ocelot", "豹猫"},
                {"minecraft:fox", "狐狸"},
                {"minecraft:horse", "马"},
                {"minecraft:donkey", "驴"},
                {"minecraft:mule", "骡"},
                {"minecraft:skeleton_horse", "骷髅马"},
                {"minecraft:zombie_horse", "僵尸马"},
                {"minecraft:llama", "羊驼"},
                {"minecraft:trader_llama", "行商羊驼"},
                {"minecraft:parrot", "鹦鹉"},
                {"minecraft:panda", "熊猫"},
                {"minecraft:polar_bear", "北极熊"},
                {"minecraft:turtle", "海龟"},
                {"minecraft:bee", "蜜蜂"},
                {"minecraft:goat", "山羊"},
                {"minecraft:axolotl", "美西螈"},
                {"minecraft:frog", "青蛙"},
                {"minecraft:tadpole", "蝌蚪"},
                {"minecraft:sniffer", "嗅探兽"},
                {"minecraft:armadillo", "犰狳"},
                {"minecraft:camel", "骆驼"},
                {"minecraft:allay", "悦灵"},
                {"minecraft:glow_squid", "发光鱿鱼"},
                {"minecraft:squid", "鱿鱼"},
                {"minecraft:villager", "村民"},
                {"minecraft:wandering_trader", "流浪商人"},
                {"minecraft:iron_golem", "铁傀儡"},
                {"minecraft:snow_golem", "雪傀儡"},
        });
    }

    private static void putAllHostile() {
        putAll(new String[][] {
                {"minecraft:zombie", "僵尸"},
                {"minecraft:husk", "尸壳"},
                {"minecraft:drowned", "溺尸"},
                {"minecraft:zombie_villager", "僵尸村民"},
                {"minecraft:skeleton", "骷髅"},
                {"minecraft:stray", "流髑"},
                {"minecraft:bogged", "沼骸"},
                {"minecraft:wither_skeleton", "凋零骷髅"},
                {"minecraft:creeper", "苦力怕"},
                {"minecraft:spider", "蜘蛛"},
                {"minecraft:cave_spider", "洞穴蜘蛛"},
                {"minecraft:enderman", "末影人"},
                {"minecraft:witch", "女巫"},
                {"minecraft:slime", "史莱姆"},
                {"minecraft:magma_cube", "岩浆怪"},
                {"minecraft:ghast", "恶魂"},
                {"minecraft:blaze", "烈焰人"},
                {"minecraft:guardian", "守卫者"},
                {"minecraft:elder_guardian", "远古守卫者"},
                {"minecraft:silverfish", "蠹虫"},
                {"minecraft:endermite", "末影螨"},
                {"minecraft:shulker", "潜影贝"},
                {"minecraft:phantom", "幻翼"},
                {"minecraft:pillager", "掠夺者"},
                {"minecraft:vindicator", "卫道士"},
                {"minecraft:evoker", "唤魔者"},
                {"minecraft:ravager", "劫掠兽"},
                {"minecraft:vex", "恼鬼"},
                {"minecraft:warden", "监守者"},
                {"minecraft:breeze", "旋风人"},
                {"minecraft:zombified_piglin", "僵尸猪灵"},
                {"minecraft:zombie_piglin", "僵尸猪灵"},
                {"minecraft:zoglin", "僵尸疣猪兽"},
        });
    }

    private static void putAllNeutral() {
        ZH_MAP.put(ResourceLocation.parse("minecraft:piglin"), "猪灵");
        ZH_MAP.put(ResourceLocation.parse("minecraft:piglin_brute"), "猪灵蛮兵");
        ZH_MAP.put(ResourceLocation.parse("minecraft:hoglin"), "疣猪兽");
        ZH_MAP.put(ResourceLocation.parse("minecraft:dolphin"), "海豚");
        ZH_MAP.put(ResourceLocation.parse("minecraft:llama"), "羊驼");
    }

    private static void putAllBoss() {
        putAll(new String[][] {
                {"minecraft:ender_dragon", "末影龙"},
                {"minecraft:wither", "凋灵"},
        });
    }

    private static void putAllAquatic() {
        ZH_MAP.put(ResourceLocation.parse("minecraft:cod"), "鳕鱼");
        ZH_MAP.put(ResourceLocation.parse("minecraft:salmon"), "鲑鱼");
        ZH_MAP.put(ResourceLocation.parse("minecraft:pufferfish"), "河豚");
        ZH_MAP.put(ResourceLocation.parse("minecraft:tropical_fish"), "热带鱼");
    }

    private static void putAllVehicle() {
        ZH_MAP.put(ResourceLocation.parse("minecraft:boat"), "船");
        ZH_MAP.put(ResourceLocation.parse("minecraft:chest_boat"), "运输船");
        ZH_MAP.put(ResourceLocation.parse("minecraft:minecart"), "矿车");
        ZH_MAP.put(ResourceLocation.parse("minecraft:chest_minecart"), "运输矿车");
        ZH_MAP.put(ResourceLocation.parse("minecraft:furnace_minecart"), "动力矿车");
        ZH_MAP.put(ResourceLocation.parse("minecraft:hopper_minecart"), "漏斗矿车");
        ZH_MAP.put(ResourceLocation.parse("minecraft:tnt_minecart"), "TNT矿车");
    }

    private static void putAllMisc() {
        ZH_MAP.put(ResourceLocation.parse("minecraft:item"), "物品");
        ZH_MAP.put(ResourceLocation.parse("minecraft:item_frame"), "物品展示框");
        ZH_MAP.put(ResourceLocation.parse("minecraft:glow_item_frame"), "荧光物品展示框");
        ZH_MAP.put(ResourceLocation.parse("minecraft:painting"), "画");
        ZH_MAP.put(ResourceLocation.parse("minecraft:armor_stand"), "盔甲架");
        ZH_MAP.put(ResourceLocation.parse("minecraft:leash_knot"), "拴绳结");
        ZH_MAP.put(ResourceLocation.parse("minecraft:experience_orb"), "经验球");
        ZH_MAP.put(ResourceLocation.parse("minecraft:experience_bottle"), "附魔之瓶");
        ZH_MAP.put(ResourceLocation.parse("minecraft:egg"), "鸡蛋");
        ZH_MAP.put(ResourceLocation.parse("minecraft:snowball"), "雪球");
        ZH_MAP.put(ResourceLocation.parse("minecraft:ender_pearl"), "末影珍珠");
        ZH_MAP.put(ResourceLocation.parse("minecraft:eye_of_ender"), "末影之眼");
        ZH_MAP.put(ResourceLocation.parse("minecraft:potion"), "药水");
        ZH_MAP.put(ResourceLocation.parse("minecraft:arrow"), "箭");
        ZH_MAP.put(ResourceLocation.parse("minecraft:spectral_arrow"), "光灵箭");
        ZH_MAP.put(ResourceLocation.parse("minecraft:trident"), "三叉戟");
        ZH_MAP.put(ResourceLocation.parse("minecraft:fireball"), "火球");
        ZH_MAP.put(ResourceLocation.parse("minecraft:small_fireball"), "小火球");
        ZH_MAP.put(ResourceLocation.parse("minecraft:wither_skull"), "凋灵之首");
        ZH_MAP.put(ResourceLocation.parse("minecraft:dragon_fireball"), "末影龙火球");
        ZH_MAP.put(ResourceLocation.parse("minecraft:shulker_bullet"), "潜影贝导弹");
        ZH_MAP.put(ResourceLocation.parse("minecraft:llama_spit"), "羊驼唾沫");
        ZH_MAP.put(ResourceLocation.parse("minecraft:falling_block"), "掉落方块");
        ZH_MAP.put(ResourceLocation.parse("minecraft:tnt"), "TNT");
        ZH_MAP.put(ResourceLocation.parse("minecraft:lightning_bolt"), "闪电");
        ZH_MAP.put(ResourceLocation.parse("minecraft:fishing_bobber"), "浮漂");
        ZH_MAP.put(ResourceLocation.parse("minecraft:evoker_fangs"), "唤魔者尖牙");
        ZH_MAP.put(ResourceLocation.parse("minecraft:player"), "玩家");
        ZH_MAP.put(ResourceLocation.parse("minecraft:area_effect_cloud"), "区域效果云");
        ZH_MAP.put(ResourceLocation.parse("minecraft:marker"), "标记");
        ZH_MAP.put(ResourceLocation.parse("minecraft:interaction"), "交互实体");
        ZH_MAP.put(ResourceLocation.parse("minecraft:text_display"), "文本展示");
        ZH_MAP.put(ResourceLocation.parse("minecraft:block_display"), "方块展示");
        ZH_MAP.put(ResourceLocation.parse("minecraft:item_display"), "物品展示");
    }
}
