package com.reclizer.csgobox.v1_21_1.box;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reclizer.csgobox.v1_21_1.CsgoBox;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the default box configuration when the config/csbox directory
 * contains no JSON files. Also encapsulates the human-readable _tutorial
 * block that documents the JSON schema.
 */
final class BoxDefaults {

    private static final String DEFAULT_BOX_FILE = "weapon_supply_box.json";

    private static final String[] DEFAULT_ENTITIES = {
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
    };

    private BoxDefaults() {
    }

    static void writeDefaultIfEmpty(Path boxesDir, Gson gson) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(boxesDir, "*.json")) {
            if (stream.iterator().hasNext()) return;
        } catch (IOException ignored) {
            return;
        }

        Path defaultFile = boxesDir.resolve(DEFAULT_BOX_FILE);
        CsgoBox.LOGGER.info("No box JSON files found, creating default: {}", defaultFile);

        JsonObject json = new JsonObject();
        addTutorial(json);
        json.addProperty("name", "武器供应箱");
        json.addProperty("key", "csgobox:csgo_key0");
        json.addProperty("drop", 1.0);

        JsonArray random = new JsonArray();
        random.add(625);
        random.add(125);
        random.add(25);
        random.add(5);
        random.add(2);
        json.add("random", random);

        JsonArray entity = new JsonArray();
        for (String e : DEFAULT_ENTITIES) {
            entity.add(e);
            entity.add(1);
        }
        json.add("entity", entity);

        addDefaultItems(json, "grade5",
                "minecraft:netherite_sword", "minecraft:netherite_axe", "minecraft:netherite_pickaxe",
                "minecraft:netherite_shovel", "minecraft:netherite_hoe", "minecraft:diamond_helmet",
                "minecraft:diamond_chestplate", "minecraft:diamond_leggings", "minecraft:diamond_boots",
                "minecraft:netherite_helmet", "minecraft:netherite_chestplate", "minecraft:netherite_leggings",
                "minecraft:netherite_boots");
        addDefaultItems(json, "grade4",
                "minecraft:diamond_sword", "minecraft:diamond_axe", "minecraft:diamond_pickaxe",
                "minecraft:diamond_shovel", "minecraft:diamond_hoe", "minecraft:golden_helmet",
                "minecraft:golden_chestplate", "minecraft:golden_leggings", "minecraft:golden_boots");
        addDefaultItems(json, "grade3",
                "minecraft:golden_sword", "minecraft:golden_axe", "minecraft:golden_pickaxe",
                "minecraft:golden_shovel", "minecraft:golden_hoe", "minecraft:iron_helmet",
                "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots",
                "minecraft:shield");
        addDefaultItems(json, "grade2",
                "minecraft:iron_sword", "minecraft:iron_axe", "minecraft:iron_pickaxe",
                "minecraft:iron_shovel", "minecraft:iron_hoe", "minecraft:chainmail_helmet",
                "minecraft:chainmail_chestplate", "minecraft:chainmail_leggings", "minecraft:chainmail_boots",
                "minecraft:bow", "minecraft:crossbow");
        addDefaultItems(json, "grade1",
                "minecraft:wooden_sword", "minecraft:wooden_axe", "minecraft:wooden_pickaxe",
                "minecraft:wooden_shovel", "minecraft:wooden_hoe", "minecraft:stone_sword",
                "minecraft:stone_axe", "minecraft:stone_pickaxe", "minecraft:stone_shovel",
                "minecraft:stone_hoe", "minecraft:leather_helmet", "minecraft:leather_chestplate",
                "minecraft:leather_leggings", "minecraft:leather_boots");

        try (Writer writer = Files.newBufferedWriter(defaultFile)) {
            gson.toJson(json, writer);
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to write default box JSON: {}", defaultFile, e);
        }
    }

    private static void addTutorial(JsonObject json) {
        JsonObject tutorial = new JsonObject();
        tutorial.addProperty("note", "JSON does not support real comments, so this _tutorial object is used as documentation and is ignored by the mod loader.");
        tutorial.addProperty("file_name", "The JSON file name becomes the box id. Example: weapon_supply_box.json becomes csgobox:weapon_supply_box.");
        tutorial.addProperty("name", "Display name shown on the box item and GUI.");
        tutorial.addProperty("key", "Required key item id. Use minecraft:air for a box that does not need a key.");
        tutorial.addProperty("drop", "Default entity drop chance from 0.0 to 1.0. Entity-specific rates below override this value.");
        tutorial.addProperty("random", "Five weights ordered from grade1 to grade5. Higher weight means more likely. Non-positive values use defaults; values above 10000 are clamped.");
        tutorial.addProperty("entity", "Either a plain list of entity ids, or alternating entity id and drop rate pairs. Example: [\"minecraft:zombie\", 0.25, \"minecraft:skeleton\", 0.10].");
        tutorial.addProperty("grades", "grade1 is the lowest rarity and grade5 is the highest rarity. Empty or invalid item entries are skipped.");
        tutorial.addProperty("item_id", "Each item object must include an id such as minecraft:diamond_sword.");
        tutorial.addProperty("item_count", "count is optional and defaults to 1.");
        tutorial.addProperty("components", "For Minecraft 1.21.1, prefer the components object for custom names, lore, enchantments, and other data components.");
        tutorial.addProperty("legacy_tag", "Legacy tag strings are still accepted for older configs, but components should be used for new configs.");

        JsonArray itemExample = new JsonArray();
        itemExample.add("{\"id\":\"minecraft:diamond_sword\",\"count\":1}");
        itemExample.add("{\"id\":\"minecraft:diamond_sword\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"{\\\"text\\\":\\\"Example Sword\\\",\\\"italic\\\":false}\"}}");
        tutorial.add("item_examples", itemExample);

        JsonArray workflow = new JsonArray();
        workflow.add("Copy this file and rename it to create another box.");
        workflow.add("Change name, key, drop, random, entity, and grade item lists.");
        workflow.add("Restart the game or server so the mod reloads config/csbox/*.json.");
        workflow.add("Give yourself a configured box item whose box_id component points to csgobox:<file_name_without_json>.");
        tutorial.add("workflow", workflow);

        json.add("_tutorial", tutorial);
    }

    private static JsonObject itemJson(String id) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("count", 1);
        return obj;
    }

    private static void addDefaultItems(JsonObject json, String gradeKey, String... itemIds) {
        JsonArray arr = new JsonArray();
        for (String id : itemIds) {
            arr.add(itemJson(id));
        }
        json.add(gradeKey, arr);
    }
}