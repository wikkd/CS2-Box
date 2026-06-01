package com.reclizer.csgobox.kubejs;

import com.reclizer.csgobox.api.box.BoxDefinition;
import com.reclizer.csgobox.api.box.BoxRegistry;
import com.reclizer.csgobox.api.box.GradeGroup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class DefaultBoxes {

    public static void registerAll() {
        registerWeaponSupplyBox();
    }

    private static void registerWeaponSupplyBox() {
        BoxDefinition box = BoxDefinition.builder(
                        ResourceLocation.parse("csgobox:weapon_supply_box"),
                        "武器供应箱"
                )
                .key(ResourceLocation.parse("csgobox:csgo_key0"))
                .dropRate(1.0F)
                .dropFrom(
                        "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper",
                        "minecraft:spider", "minecraft:cave_spider", "minecraft:enderman",
                        "minecraft:witch", "minecraft:slime", "minecraft:silverfish",
                        "minecraft:blaze", "minecraft:ghast", "minecraft:magma_cube",
                        "minecraft:zombified_piglin", "minecraft:wither_skeleton",
                        "minecraft:stray", "minecraft:husk", "minecraft:drowned",
                        "minecraft:guardian", "minecraft:elder_guardian", "minecraft:shulker",
                        "minecraft:endermite", "minecraft:evoker", "minecraft:vindicator",
                        "minecraft:pillager", "minecraft:ravager", "minecraft:vex",
                        "minecraft:phantom", "minecraft:piglin", "minecraft:piglin_brute",
                        "minecraft:hoglin", "minecraft:zoglin", "minecraft:zombie_villager"
                )
                .addGrade(new GradeGroup("consumer", "消费级", 0xFF4B69FF, 625)
                        .withItem(new ItemStack(Items.WOODEN_SWORD))
                        .withItem(new ItemStack(Items.WOODEN_AXE))
                        .withItem(new ItemStack(Items.WOODEN_PICKAXE))
                        .withItem(new ItemStack(Items.WOODEN_SHOVEL))
                        .withItem(new ItemStack(Items.WOODEN_HOE))
                        .withItem(new ItemStack(Items.STONE_SWORD))
                        .withItem(new ItemStack(Items.STONE_AXE))
                        .withItem(new ItemStack(Items.STONE_PICKAXE))
                        .withItem(new ItemStack(Items.STONE_SHOVEL))
                        .withItem(new ItemStack(Items.STONE_HOE))
                        .withItem(new ItemStack(Items.LEATHER_HELMET))
                        .withItem(new ItemStack(Items.LEATHER_CHESTPLATE))
                        .withItem(new ItemStack(Items.LEATHER_LEGGINGS))
                        .withItem(new ItemStack(Items.LEATHER_BOOTS))
                )
                .addGrade(new GradeGroup("industrial", "工业级", 0xFF4B69FF, 125)
                        .withItem(new ItemStack(Items.IRON_SWORD))
                        .withItem(new ItemStack(Items.IRON_AXE))
                        .withItem(new ItemStack(Items.IRON_PICKAXE))
                        .withItem(new ItemStack(Items.IRON_SHOVEL))
                        .withItem(new ItemStack(Items.IRON_HOE))
                        .withItem(new ItemStack(Items.CHAINMAIL_HELMET))
                        .withItem(new ItemStack(Items.CHAINMAIL_CHESTPLATE))
                        .withItem(new ItemStack(Items.CHAINMAIL_LEGGINGS))
                        .withItem(new ItemStack(Items.CHAINMAIL_BOOTS))
                        .withItem(new ItemStack(Items.BOW))
                        .withItem(new ItemStack(Items.CROSSBOW))
                )
                .addGrade(new GradeGroup("mil_spec", "军规级", 0xFF4B69FF, 25)
                        .withItem(new ItemStack(Items.GOLDEN_SWORD))
                        .withItem(new ItemStack(Items.GOLDEN_AXE))
                        .withItem(new ItemStack(Items.GOLDEN_PICKAXE))
                        .withItem(new ItemStack(Items.GOLDEN_SHOVEL))
                        .withItem(new ItemStack(Items.GOLDEN_HOE))
                        .withItem(new ItemStack(Items.IRON_HELMET))
                        .withItem(new ItemStack(Items.IRON_CHESTPLATE))
                        .withItem(new ItemStack(Items.IRON_LEGGINGS))
                        .withItem(new ItemStack(Items.IRON_BOOTS))
                        .withItem(new ItemStack(Items.SHIELD))
                )
                .addGrade(new GradeGroup("restricted", "受限", 0xFF8847FF, 5)
                        .withItem(new ItemStack(Items.DIAMOND_SWORD))
                        .withItem(new ItemStack(Items.DIAMOND_AXE))
                        .withItem(new ItemStack(Items.DIAMOND_PICKAXE))
                        .withItem(new ItemStack(Items.DIAMOND_SHOVEL))
                        .withItem(new ItemStack(Items.DIAMOND_HOE))
                        .withItem(new ItemStack(Items.GOLDEN_HELMET))
                        .withItem(new ItemStack(Items.GOLDEN_CHESTPLATE))
                        .withItem(new ItemStack(Items.GOLDEN_LEGGINGS))
                        .withItem(new ItemStack(Items.GOLDEN_BOOTS))
                )
                .addGrade(new GradeGroup("classified", "保密", 0xFFD32CE6, 2)
                        .withItem(new ItemStack(Items.NETHERITE_SWORD))
                        .withItem(new ItemStack(Items.NETHERITE_AXE))
                        .withItem(new ItemStack(Items.NETHERITE_PICKAXE))
                        .withItem(new ItemStack(Items.NETHERITE_SHOVEL))
                        .withItem(new ItemStack(Items.NETHERITE_HOE))
                        .withItem(new ItemStack(Items.DIAMOND_HELMET))
                        .withItem(new ItemStack(Items.DIAMOND_CHESTPLATE))
                        .withItem(new ItemStack(Items.DIAMOND_LEGGINGS))
                        .withItem(new ItemStack(Items.DIAMOND_BOOTS))
                        .withItem(new ItemStack(Items.NETHERITE_HELMET))
                        .withItem(new ItemStack(Items.NETHERITE_CHESTPLATE))
                        .withItem(new ItemStack(Items.NETHERITE_LEGGINGS))
                        .withItem(new ItemStack(Items.NETHERITE_BOOTS))
                )
                .build();

        BoxRegistry.register(box);
    }
}
