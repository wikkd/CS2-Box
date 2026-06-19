package com.reclizer.csgobox.box;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.reclizer.csgobox.CsgoBox;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes box definitions under config/csbox.
 *
 * <p>Supported item formats are Minecraft 1.21.1 data components and legacy
 * NBT tags. The legacy path is kept so older config files can still load.</p>
 */
public final class BoxJsonLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path BOXES_DIR = FMLPaths.CONFIGDIR.get().resolve("csbox");

    private static final String[] GRADE_IDS = {"classified", "restricted", "mil_spec", "industrial", "consumer"};
    private static final String[] GRADE_NAMES = {"\u4fdd\u5bc6", "\u53d7\u9650", "\u519b\u89c4\u7ea7", "\u5de5\u4e1a\u7ea7", "\u6d88\u8d39\u7ea7"};
    private static final int[] GRADE_COLORS = {0xFFD32CE6, 0xFF8847FF, 0xFF4B69FF, 0xFF4B69FF, 0xFF4B69FF};

    private BoxJsonLoader() {
    }

    public static void loadAll() {
        if (!Files.exists(BOXES_DIR)) {
            try {
                Files.createDirectories(BOXES_DIR);
            } catch (IOException e) {
                CsgoBox.LOGGER.error("Failed to create boxes config directory: {}", BOXES_DIR, e);
                return;
            }
            CsgoBox.LOGGER.info("Created boxes config directory: {}", BOXES_DIR);
        }

        writeDefaultIfEmpty();

        int[] loaded = {0};
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(BOXES_DIR, "*.json")) {
            for (Path file : stream) {
                try {
                    loadFromFile(file).ifPresent(def -> {
                        BoxRegistry.register(def);
                        loaded[0]++;
                        CsgoBox.LOGGER.info("Loaded box from JSON: {} -> {}", file.getFileName(), def.id());
                    });
                } catch (Exception e) {
                    CsgoBox.LOGGER.error("Failed to load box JSON file: {}", file, e);
                }
            }
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to list box JSON files in {}", BOXES_DIR, e);
        }

        CsgoBox.LOGGER.info("Loaded {} box(es) from {}", loaded[0], BOXES_DIR);
    }

    private static void writeDefaultIfEmpty() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(BOXES_DIR, "*.json")) {
            if (stream.iterator().hasNext()) return;
        } catch (IOException ignored) {
            return;
        }

        Path defaultFile = BOXES_DIR.resolve("weapon_supply_box.json");
        CsgoBox.LOGGER.info("No box JSON files found, creating default: {}", defaultFile);

        JsonObject json = new JsonObject();
        addTutorial(json);
        json.addProperty("name", "\u6b66\u5668\u4f9b\u5e94\u7bb1");
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
        String[] entities = {
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
        for (String e : entities) {
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
            GSON.toJson(json, writer);
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

    private static Optional<BoxDefinition> loadFromFile(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String boxIdStr = fileName.substring(0, fileName.length() - 5);

        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file)) {
            json = GSON.fromJson(reader, JsonObject.class);
        }
        if (json == null) return Optional.empty();

        String name = getString(json, "name", boxIdStr);
        ResourceLocation keyItem = ResourceLocation.parse(getString(json, "key", "csgobox:csgo_key0"));
        float dropRate = getFloat(json, "drop", 0.12F);

        int[] weights = parseWeights(json);

        List<ResourceLocation> dropEntityIds = new ArrayList<>();
        Map<ResourceLocation, Float> entityDropRates = new HashMap<>();
        parseEntities(json, dropEntityIds, entityDropRates);

        List<GradeGroup> grades = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String gradeKey = "grade" + (5 - i);
            if (json.has(gradeKey)) {
                JsonArray itemsArr = json.getAsJsonArray(gradeKey);
                List<ItemStack> items = new ArrayList<>();
                for (JsonElement elem : itemsArr) {
                    ItemStack stack = parseItem(elem);
                    if (stack != null && !stack.isEmpty()) {
                        items.add(stack);
                    }
                }
                if (!items.isEmpty()) {
                    grades.add(new GradeGroup(GRADE_IDS[i], GRADE_NAMES[i], GRADE_COLORS[i], weights[4 - i], items));
                }
            }
        }

        if (grades.isEmpty()) {
            CsgoBox.LOGGER.warn("Skipping box '{}': all items failed to parse (missing mods?)", boxIdStr);
            return Optional.empty();
        }

        BoxDefinition.Builder builder = BoxDefinition.builder(
                ResourceLocation.parse("csgobox:" + boxIdStr), name);
        builder.key(keyItem);
        builder.dropRate(dropRate);
        for (ResourceLocation entityId : dropEntityIds) {
            Float rate = entityDropRates.get(entityId);
            if (rate != null) {
                builder.entityDropRate(entityId.toString(), rate);
            }
            builder.dropFrom(entityId.toString());
        }
        for (GradeGroup grade : grades) {
            builder.addGrade(grade);
        }

        return Optional.of(builder.build());
    }

    /**
     * JSON "random" is ordered grade1 -> grade5.
     */
    private static int[] parseWeights(JsonObject json) {
        int[] weights = BoxDefinition.DEFAULT_WEIGHTS.clone();
        if (json.has("random")) {
            JsonArray randomArr = json.getAsJsonArray("random");
            for (int i = 0; i < Math.min(randomArr.size(), 5); i++) {
                weights[i] = randomArr.get(i).getAsInt();
            }
        }
        for (int i = 0; i < 5; i++) {
            String gradeKey = "grade" + (i + 1);
            if (weights[i] <= 0) {
                if (weights[i] < 0) {
                    CsgoBox.LOGGER.warn("Negative weight {} for {} in box config, using default: {}",
                            weights[i], gradeKey, BoxDefinition.DEFAULT_WEIGHTS[i]);
                }
                weights[i] = BoxDefinition.DEFAULT_WEIGHTS[i];
            } else if (weights[i] > 10000) {
                CsgoBox.LOGGER.warn("Weight {} for {} exceeds maximum, clamping to 10000", weights[i], gradeKey);
                weights[i] = 10000;
            }
        }
        return weights;
    }

    /**
     * Parses either a plain entity id list or alternating entity id/drop-rate pairs.
     */
    private static void parseEntities(JsonObject json, List<ResourceLocation> dropEntityIds,
                                       Map<ResourceLocation, Float> entityDropRates) {
        if (!json.has("entity")) return;
        JsonArray entityArr = json.getAsJsonArray("entity");
        if (entityArr.size() == 0) return;

        if (entityArr.size() == 1 || (entityArr.get(1).isJsonPrimitive()
                && entityArr.get(1).getAsJsonPrimitive().isString())) {
            for (JsonElement elem : entityArr) {
                ResourceLocation entityId = ResourceLocation.parse(elem.getAsString());
                dropEntityIds.add(entityId);
            }
            return;
        }

        if ((entityArr.size() & 1) != 0) {
            CsgoBox.LOGGER.warn("Ignoring trailing entity entry without drop rate: {}",
                    entityArr.get(entityArr.size() - 1));
        }
        for (int i = 0; i + 1 < entityArr.size(); i += 2) {
            String entityIdStr = entityArr.get(i).getAsString();
            float rate = entityArr.get(i + 1).getAsFloat();
            ResourceLocation entityId = ResourceLocation.parse(entityIdStr);
            dropEntityIds.add(entityId);
            entityDropRates.put(entityId, rate);
        }
    }

    /**
     * Parses an item object, or a legacy JSON string containing that object.
     */
    private static ItemStack parseItem(JsonElement elem) {
        try {
            JsonObject obj;
            if (elem.isJsonPrimitive()) {
                // Legacy configs stored the item object as a JSON string.
                obj = GSON.fromJson(elem.getAsString(), JsonObject.class);
            } else {
                obj = elem.getAsJsonObject();
            }
            if (obj == null) return ItemStack.EMPTY;
            if (!obj.has("id")) {
                CsgoBox.LOGGER.warn("Skipping item JSON without id: {}", elem);
                return ItemStack.EMPTY;
            }

            String id = obj.get("id").getAsString();
            int count = obj.has("count") ? obj.get("count").getAsInt() : 1;

            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item == null) {
                CsgoBox.LOGGER.warn("Unknown item in box JSON: {}", id);
                return ItemStack.EMPTY;
            }

            ItemStack stack = new ItemStack(item, count);

            if (obj.has("components")) {
                try {
                    JsonElement componentsJson = obj.get("components");
                    DataComponentPatch patch = DataComponentPatch.CODEC.parse(JsonOps.INSTANCE, componentsJson)
                            .result().orElse(DataComponentPatch.EMPTY);
                    stack.applyComponents(patch);
                } catch (Exception e) {
                    CsgoBox.LOGGER.warn("Failed to parse components for item {}: {}", id, e.getMessage());
                }
            } else if (obj.has("tag")) {
                try {
                    String tagStr = obj.get("tag").getAsString();
                    var tag = TagParser.parseTag(tagStr);
                    DataComponentPatch patch = DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, tag)
                            .result().orElse(DataComponentPatch.EMPTY);
                    stack.applyComponents(patch);
                } catch (Exception e) {
                    CsgoBox.LOGGER.warn("Failed to parse NBT tag for item {}: {}", id, e.getMessage());
                }
            }

            return stack;
        } catch (Exception e) {
            CsgoBox.LOGGER.warn("Failed to parse item JSON: {}", elem, e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    private static String getString(JsonObject json, String key, String defaultValue) {
        return json.has(key) ? json.get(key).getAsString() : defaultValue;
    }

    private static float getFloat(JsonObject json, String key, float defaultValue) {
        return json.has(key) ? json.get(key).getAsFloat() : defaultValue;
    }

    public static void saveToFile(BoxDefinition def) {
        try {
            Files.createDirectories(BOXES_DIR);
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to create boxes directory for save", e);
            return;
        }

        Path file = BOXES_DIR.resolve(def.id().getPath() + ".json");
        Path tempFile = BOXES_DIR.resolve(def.id().getPath() + ".json.tmp");

        JsonObject json = new JsonObject();
        json.addProperty("name", def.name().getString());
        json.addProperty("key", def.keyItem().toString());
        json.addProperty("drop", def.dropRate());

        JsonArray random = new JsonArray();
        for (int i = 4; i >= 0; i--) {
            GradeGroup g = def.findGrade(GRADE_IDS[i]).orElse(null);
            random.add(g != null ? g.weight() : 0);
        }
        json.add("random", random);

        JsonArray entity = new JsonArray();
        if (!def.entityDropRates().isEmpty()) {
            for (Map.Entry<ResourceLocation, Float> entry : def.entityDropRates().entrySet()) {
                entity.add(entry.getKey().toString());
                entity.add(entry.getValue());
            }
        } else {
            for (ResourceLocation e : def.dropEntities()) {
                entity.add(e.toString());
                entity.add(1);
            }
        }
        json.add("entity", entity);

        for (int i = 0; i < 5; i++) {
            String gradeKey = "grade" + (5 - i);
            GradeGroup g = def.findGrade(GRADE_IDS[i]).orElse(null);
            JsonArray itemsArr = new JsonArray();
            if (g != null) {
                for (ItemStack item : g.items()) {
                    itemsArr.add(serializeItemStack(item));
                }
            }
            json.add(gradeKey, itemsArr);
        }

        try (Writer writer = Files.newBufferedWriter(tempFile)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to save box JSON: {}", file, e);
            return;
        }

        try {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            CsgoBox.LOGGER.info("Saved box to JSON: {} -> {}", def.id(), file);
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to finalize box JSON: {}", file, e);
        }
    }

    public static void deleteFile(ResourceLocation boxId) {
        Path file = BOXES_DIR.resolve(boxId.getPath() + ".json").normalize();
        if (!file.startsWith(BOXES_DIR.normalize())) {
            CsgoBox.LOGGER.warn("Rejected path traversal attempt: {}", boxId.getPath());
            return;
        }
        try {
            if (Files.exists(file)) {
                Files.delete(file);
                CsgoBox.LOGGER.info("Deleted box JSON: {}", file);
            }
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to delete box JSON: {}", file, e);
        }
    }

    private static JsonObject serializeItemStack(ItemStack stack) {
        JsonObject obj = new JsonObject();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        obj.addProperty("id", itemId.toString());
        obj.addProperty("count", stack.getCount());

        DataComponentPatch patch = stack.getComponentsPatch();
        if (!patch.isEmpty()) {
            try {
                var result = DataComponentPatch.CODEC.encodeStart(JsonOps.INSTANCE, patch);
                result.result().ifPresent(elem -> obj.add("components", elem));
            } catch (Exception e) {
                CsgoBox.LOGGER.warn("Failed to serialize components for item: {}", itemId, e.getMessage());
            }
        }

        return obj;
    }
}
