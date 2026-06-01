package com.reclizer.csgobox.api.box;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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

public class BoxJsonLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path BOXES_DIR = FMLPaths.CONFIGDIR.get().resolve("csbox");

    private static final String[] GRADE_IDS = {"classified", "restricted", "mil_spec", "industrial", "consumer"};
    private static final String[] GRADE_NAMES = {"\u4fdd\u5bc6", "\u53d7\u9650", "\u519b\u89c4\u7ea7", "\u5de5\u4e1a\u7ea7", "\u6d88\u8d39\u7ea7"};
    private static final int[] GRADE_COLORS = {0xFFD32CE6, 0xFF8847FF, 0xFF4B69FF, 0xFF4B69FF, 0xFF4B69FF};

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

        int loaded = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(BOXES_DIR, "*.json")) {
            for (Path file : stream) {
                try {
                    BoxDefinition def = loadFromFile(file);
                    if (def != null) {
                        BoxRegistry.register(def);
                        loaded++;
                        CsgoBox.LOGGER.info("Loaded box from JSON: {} -> {}", file.getFileName(), def.id());
                    }
                } catch (Exception e) {
                    CsgoBox.LOGGER.error("Failed to load box JSON file: {}", file, e);
                }
            }
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to list box JSON files in {}", BOXES_DIR, e);
        }

        CsgoBox.LOGGER.info("Loaded {} box(es) from {}", loaded, BOXES_DIR);
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
        json.addProperty("name", "\u6b66\u5668\u4f9b\u5e94\u7bb1");
        json.addProperty("key", "csgobox:csgo_key0");
        json.addProperty("drop", 1.0);

        JsonArray random = new JsonArray();
        random.add(2);
        random.add(5);
        random.add(25);
        random.add(125);
        random.add(625);
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

        json.add("grade5", createGrade5Items());
        json.add("grade4", createGrade4Items());
        json.add("grade3", createGrade3Items());
        json.add("grade2", createGrade2Items());
        json.add("grade1", createGrade1Items());

        try (Writer writer = Files.newBufferedWriter(defaultFile)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to write default box JSON: {}", defaultFile, e);
        }
    }

    private static JsonArray createGrade5Items() {
        JsonArray arr = new JsonArray();
        arr.add(itemJson("minecraft:netherite_sword"));
        arr.add(itemJson("minecraft:netherite_axe"));
        arr.add(itemJson("minecraft:netherite_pickaxe"));
        arr.add(itemJson("minecraft:netherite_shovel"));
        arr.add(itemJson("minecraft:netherite_hoe"));
        arr.add(itemJson("minecraft:diamond_helmet"));
        arr.add(itemJson("minecraft:diamond_chestplate"));
        arr.add(itemJson("minecraft:diamond_leggings"));
        arr.add(itemJson("minecraft:diamond_boots"));
        arr.add(itemJson("minecraft:netherite_helmet"));
        arr.add(itemJson("minecraft:netherite_chestplate"));
        arr.add(itemJson("minecraft:netherite_leggings"));
        arr.add(itemJson("minecraft:netherite_boots"));
        return arr;
    }

    private static JsonArray createGrade4Items() {
        JsonArray arr = new JsonArray();
        arr.add(itemJson("minecraft:diamond_sword"));
        arr.add(itemJson("minecraft:diamond_axe"));
        arr.add(itemJson("minecraft:diamond_pickaxe"));
        arr.add(itemJson("minecraft:diamond_shovel"));
        arr.add(itemJson("minecraft:diamond_hoe"));
        arr.add(itemJson("minecraft:golden_helmet"));
        arr.add(itemJson("minecraft:golden_chestplate"));
        arr.add(itemJson("minecraft:golden_leggings"));
        arr.add(itemJson("minecraft:golden_boots"));
        return arr;
    }

    private static JsonArray createGrade3Items() {
        JsonArray arr = new JsonArray();
        arr.add(itemJson("minecraft:golden_sword"));
        arr.add(itemJson("minecraft:golden_axe"));
        arr.add(itemJson("minecraft:golden_pickaxe"));
        arr.add(itemJson("minecraft:golden_shovel"));
        arr.add(itemJson("minecraft:golden_hoe"));
        arr.add(itemJson("minecraft:iron_helmet"));
        arr.add(itemJson("minecraft:iron_chestplate"));
        arr.add(itemJson("minecraft:iron_leggings"));
        arr.add(itemJson("minecraft:iron_boots"));
        arr.add(itemJson("minecraft:shield"));
        return arr;
    }

    private static JsonArray createGrade2Items() {
        JsonArray arr = new JsonArray();
        arr.add(itemJson("minecraft:iron_sword"));
        arr.add(itemJson("minecraft:iron_axe"));
        arr.add(itemJson("minecraft:iron_pickaxe"));
        arr.add(itemJson("minecraft:iron_shovel"));
        arr.add(itemJson("minecraft:iron_hoe"));
        arr.add(itemJson("minecraft:chainmail_helmet"));
        arr.add(itemJson("minecraft:chainmail_chestplate"));
        arr.add(itemJson("minecraft:chainmail_leggings"));
        arr.add(itemJson("minecraft:chainmail_boots"));
        arr.add(itemJson("minecraft:bow"));
        arr.add(itemJson("minecraft:crossbow"));
        return arr;
    }

    private static JsonArray createGrade1Items() {
        JsonArray arr = new JsonArray();
        arr.add(itemJson("minecraft:wooden_sword"));
        arr.add(itemJson("minecraft:wooden_axe"));
        arr.add(itemJson("minecraft:wooden_pickaxe"));
        arr.add(itemJson("minecraft:wooden_shovel"));
        arr.add(itemJson("minecraft:wooden_hoe"));
        arr.add(itemJson("minecraft:stone_sword"));
        arr.add(itemJson("minecraft:stone_axe"));
        arr.add(itemJson("minecraft:stone_pickaxe"));
        arr.add(itemJson("minecraft:stone_shovel"));
        arr.add(itemJson("minecraft:stone_hoe"));
        arr.add(itemJson("minecraft:leather_helmet"));
        arr.add(itemJson("minecraft:leather_chestplate"));
        arr.add(itemJson("minecraft:leather_leggings"));
        arr.add(itemJson("minecraft:leather_boots"));
        return arr;
    }

    private static String itemJson(String id) {
        return "{\"id\":\"" + id + "\",\"count\":1}";
    }

    private static BoxDefinition loadFromFile(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String boxIdStr = fileName.substring(0, fileName.length() - 5);

        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file)) {
            json = GSON.fromJson(reader, JsonObject.class);
        }
        if (json == null) return null;

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
                    ItemStack stack = parseItem(elem.getAsString());
                    if (stack != null && !stack.isEmpty()) {
                        items.add(stack);
                    }
                }
                if (!items.isEmpty()) {
                    grades.add(new GradeGroup(GRADE_IDS[i], GRADE_NAMES[i], GRADE_COLORS[i], weights[i], items));
                }
            }
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

        return builder.build();
    }

    private static int[] parseWeights(JsonObject json) {
        int[] weights = new int[5];
        if (json.has("random")) {
            JsonArray randomArr = json.getAsJsonArray("random");
            for (int i = 0; i < Math.min(randomArr.size(), 5); i++) {
                weights[i] = randomArr.get(i).getAsInt();
            }
        }
        for (int i = 0; i < 5; i++) {
            if (weights[i] <= 0) weights[i] = 20;
        }
        return weights;
    }

    private static void parseEntities(JsonObject json, List<ResourceLocation> dropEntityIds,
                                       Map<ResourceLocation, Float> entityDropRates) {
        if (!json.has("entity")) return;
        JsonArray entityArr = json.getAsJsonArray("entity");
        if (entityArr.size() == 0) return;

        if (entityArr.size() >= 2 && entityArr.get(1).isJsonPrimitive()
                && entityArr.get(1).getAsJsonPrimitive().isString()) {
            for (JsonElement elem : entityArr) {
                ResourceLocation entityId = ResourceLocation.parse(elem.getAsString());
                dropEntityIds.add(entityId);
            }
            return;
        }

        for (int i = 0; i + 1 < entityArr.size(); i += 2) {
            String entityIdStr = entityArr.get(i).getAsString();
            float rate = entityArr.get(i + 1).getAsFloat();
            ResourceLocation entityId = ResourceLocation.parse(entityIdStr);
            dropEntityIds.add(entityId);
            entityDropRates.put(entityId, rate);
        }
    }

    private static ItemStack parseItem(String itemJson) {
        try {
            JsonObject obj = GSON.fromJson(itemJson, JsonObject.class);
            if (obj == null) return ItemStack.EMPTY;

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
            CsgoBox.LOGGER.warn("Failed to parse item JSON: {}", itemJson, e.getMessage());
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
        for (GradeGroup g : def.grades()) {
            random.add(g.weight());
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

        for (int i = 0; i < def.grades().size(); i++) {
            String gradeKey = "grade" + (def.grades().size() - i);
            GradeGroup g = def.grades().get(i);
            JsonArray itemsArr = new JsonArray();
            for (ItemStack item : g.items()) {
                itemsArr.add(serializeItemStack(item));
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
        Path file = BOXES_DIR.resolve(boxId.getPath() + ".json");
        try {
            if (Files.exists(file)) {
                Files.delete(file);
                CsgoBox.LOGGER.info("Deleted box JSON: {}", file);
            }
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to delete box JSON: {}", file, e);
        }
    }

    private static String serializeItemStack(ItemStack stack) {
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

        return GSON.toJson(obj);
    }
}
