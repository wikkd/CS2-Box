package com.reclizer.csgobox.forge_26_2.box;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.reclizer.csgobox.forge_26_2.CsgoBox;
import com.reclizer.csgobox.forge_26_2.item.ItemCsgoBox;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses and serializes individual ItemStack entries within box JSON.
 *
 * <p>Both directions tolerate the legacy "tag" NBT string format and the
 * modern "components" data-component patch format. Components are decoded
 * entry-by-entry so a single malformed component no longer drops the whole
 * patch (MC's {@code DataComponentPatch.CODEC} is all-or-nothing); failed
 * entries are reported as warnings, as are partially dropped legacy NBT
 * patches.</p>
 *
 * <p>v2.1.0 additions:</p>
 * <ul>
 *   <li>{@code weight} — intra-grade item weight (parallel to the item list).</li>
 *   <li>{@code count} may be {@code [min,max]} — a range resolved at open time
 *       via the {@code csgobox:item_spec} marker.</li>
 *   <li>{@code tag} starting with {@code #} — an item tag reference expanded at
 *       load time to its current member items.</li>
 *   <li>{@code loot_table} — a loot-table reference resolved at open time
 *       (placeholder barrel carries the {@code csgobox:item_spec} marker).</li>
 *   <li>{@code enchant} — a random-enchant shortcut resolved at open time.</li>
 *   <li>Unknown item ids now distinguish "target mod not installed" from "item
 *       id typo" using {@link CsgoBox#isModLoaded}.</li>
 * </ul>
 */
public final class BoxItemCodec {

    private static final Gson GSON = new Gson();

    /** Shared Gson instance, reused by {@code CsboxCommand} for item JSON output. */
    public static Gson gson() {
        return GSON;
    }

    private BoxItemCodec() {
    }

    /**
     * Result of {@link #parseItem}: one or more {@link ItemStack}s (a tag
     * reference expands to its members) plus a parallel {@code weights} list.
     * {@code error == null} indicates success; callers route the error into the
     * box loader's LoadError list so it surfaces via {@code /csbox info error}
     * and the join-time announcement. Warnings are non-fatal diagnostics
     * (migrated formats, partially dropped components) surfaced the same way.
     */
    record ParseOutcome(List<ItemStack> stacks, List<Integer> weights,
                        String error, List<String> warnings) {
        boolean isSuccess() { return error == null; }
        boolean isEmpty() { return stacks.isEmpty(); }
        static ParseOutcome ok(ItemStack stack) { return ok(List.of(stack), List.of(1), List.of()); }
        static ParseOutcome ok(ItemStack stack, int weight) { return ok(List.of(stack), List.of(weight), List.of()); }
        static ParseOutcome ok(ItemStack stack, int weight, List<String> warnings) {
            return ok(List.of(stack), List.of(weight), warnings);
        }
        static ParseOutcome ok(List<ItemStack> stacks, List<Integer> weights, List<String> warnings) {
            return new ParseOutcome(List.copyOf(stacks), List.copyOf(weights), null, List.copyOf(warnings));
        }
        static ParseOutcome fail(String message) {
            return new ParseOutcome(List.of(), List.of(), message, List.of());
        }
    }

    /** Per-component decode output: the patch of successful entries plus one
     *  error string per failed entry. */
    record DecodeResult(DataComponentPatch patch, List<String> errors) {
        boolean hasErrors() { return !errors.isEmpty(); }
    }

    /**
     * Parses an item object (or a legacy JSON string containing that object).
     * Returns a {@link ParseOutcome} that distinguishes a clean parse from a
     * skipped item (missing id, unknown item id, malformed components).
     */
    static ParseOutcome parseItem(JsonElement elem) {
        List<String> warnings = new ArrayList<>();
        try {
            JsonObject obj;
            if (elem.isJsonPrimitive()) {
                // Legacy configs stored the item object as a JSON string.
                obj = GSON.fromJson(elem.getAsString(), JsonObject.class);
            } else {
                obj = elem.getAsJsonObject();
            }
            if (obj == null) {
                return ParseOutcome.fail("item JSON is null");
            }

            int weight = parseWeight(obj, warnings);

            // Loot-table reference: resolved at open time; the placeholder
            // barrel carries the spec so previews and the tooltip show it.
            if (obj.has("loot_table")) {
                return parseLootTable(obj, weight, warnings);
            }

            // Item-tag reference: expanded at load time to its members.
            if (obj.has("tag") && obj.get("tag").isJsonPrimitive()
                    && obj.get("tag").getAsString().startsWith("#")) {
                return parseItemTag(obj, weight, warnings);
            }

            if (!obj.has("id")) {
                CsgoBox.LOGGER.warn("Skipping item JSON without id: {}", elem);
                return ParseOutcome.fail("missing 'id' field");
            }

            String id = obj.get("id").getAsString();
            int count = parseCount(obj, warnings);

            Item item = BuiltInRegistries.ITEM.get(Identifier.parse(id)).map(Holder.Reference::value).orElse(null);
            if (item == null) {
                CsgoBox.LOGGER.warn("Unknown item in box JSON: {}", id);
                return ParseOutcome.fail(unknownItemReason(id));
            }

            // The registry Holder backing this stack carries a ResourceKey,
            // so it survives later serialization (e.g. into the player_data
            // attachment). Constructing a raw ItemStack without a key would
            // break box opening on the next launch.
            ItemStack stack = new ItemStack(item, count);

            applyItemSpec(obj, stack, warnings);
            applyComponents(obj, stack, warnings);

            return ParseOutcome.ok(stack, weight, warnings);
        } catch (Exception e) {
            CsgoBox.LOGGER.warn("Failed to parse item JSON: {}", elem, e);
            return ParseOutcome.fail("parse failed: " + e.getMessage());
        }
    }

    // ---- per-item spec: weight / count-range / enchant / loot table ----

    /** Reads the optional {@code weight} (default 1). A weight of 0 disables
     *  the entry; negatives are rejected with a warning. */
    private static int parseWeight(JsonObject obj, List<String> warnings) {
        if (!obj.has("weight")) {
            return 1;
        }
        try {
            int w = obj.get("weight").getAsInt();
            if (w < 0) {
                warnings.add("weight < 0 (" + w + ") treated as 0 (disabled)");
                return 0;
            }
            if (w > 10000) {
                warnings.add("weight " + w + " exceeds 10000, clamped");
                return 10000;
            }
            return w;
        } catch (Exception e) {
            warnings.add("invalid weight: " + e.getMessage());
            return 1;
        }
    }

    /** Reads {@code count}: an integer, or a [min,max] array resolved at open
     *  time. Returns the display/minimum count for the stored stack. */
    private static int parseCount(JsonObject obj, List<String> warnings) {
        if (!obj.has("count")) {
            return 1;
        }
        try {
            JsonElement c = obj.get("count");
            if (c.isJsonArray()) {
                JsonArray arr = c.getAsJsonArray();
                if (arr.size() != 2) {
                    warnings.add("count array must be [min,max], got " + arr.size() + " entries — using 1");
                    return 1;
                }
                int min = arr.get(0).getAsInt();
                int max = arr.get(1).getAsInt();
                if (min < 1 || max < min) {
                    warnings.add("invalid count range [" + min + "," + max + "] — using 1");
                    return 1;
                }
                return min; // stored min; open-time resolution rolls the range
            }
            int n = c.getAsInt();
            return Math.max(1, n);
        } catch (Exception e) {
            warnings.add("invalid count: " + e.getMessage());
            return 1;
        }
    }

    /** Applies the {@code csgobox:item_spec} marker for count-range/enchant. */
    private static void applyItemSpec(JsonObject obj, ItemStack stack, List<String> warnings) {
        StringBuilder spec = new StringBuilder();
        try {
            if (obj.has("count") && obj.get("count").isJsonArray()) {
                JsonArray arr = obj.get("count").getAsJsonArray();
                if (arr.size() == 2) {
                    spec.append("{\"c\":[").append(arr.get(0).getAsInt())
                            .append(",").append(arr.get(1).getAsInt()).append("]}");
                }
            }
            if (obj.has("enchant")) {
                JsonElement e = obj.get("enchant");
                if (e.isJsonPrimitive() && e.getAsBoolean()) {
                    appendSpecField(spec, "e", "true");
                } else if (e.isJsonObject()) {
                    appendSpecField(spec, "e", e.toString());
                } else {
                    warnings.add("enchant must be true or an object, ignored");
                }
            }
        } catch (Exception ex) {
            warnings.add("invalid item spec: " + ex.getMessage());
        }
        if (spec.length() > 0) {
            stack.set(ItemCsgoBox.ITEM_SPEC.get(), spec.toString());
        }
    }

    private static void appendSpecField(StringBuilder spec, String key, String valueJson) {
        if (spec.length() > 0 && spec.charAt(0) == '{') {
            spec.insert(spec.length() - 1, ",\"" + key + "\":" + valueJson);
        } else {
            spec.append("{\"").append(key).append("\":").append(valueJson).append("}");
        }
    }

    /** Loot-table entry: a barrel placeholder carrying the {@code item_spec}
     *  marker {@code {"l":"<id>"}}. The open path resolves it via the server's
     *  loot data. */
    private static ParseOutcome parseLootTable(JsonObject obj, int weight, List<String> warnings) {
        String tableId = obj.get("loot_table").getAsString();
        try {
            Identifier.parse(tableId);
        } catch (Exception e) {
            return ParseOutcome.fail("invalid loot_table id: " + tableId + " (" + e.getMessage() + ")");
        }
        ItemStack placeholder = new ItemStack(Items.BARREL, 1);
        placeholder.set(ItemCsgoBox.ITEM_SPEC.get(), "{\"l\":\"" + tableId + "\"}");
        return ParseOutcome.ok(placeholder, weight, warnings);
    }

    /** Item-tag reference {@code #namespace:path}: expands to the tag's current
     *  member items, each with the shared count/weight/components. */
    private static ParseOutcome parseItemTag(JsonObject obj, int weight, List<String> warnings) {
        String tagId = obj.get("tag").getAsString().substring(1);
        Identifier tagIdentifier;
        try {
            tagIdentifier = Identifier.parse(tagId);
        } catch (Exception e) {
            return ParseOutcome.fail("invalid tag id: " + tagId + " (" + e.getMessage() + ")");
        }
        int count = parseCount(obj, warnings);
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagIdentifier);
        List<ItemStack> stacks = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        Iterable<Holder<Item>> members;
        try {
            members = BuiltInRegistries.ITEM.getTagOrEmpty(tagKey);
        } catch (Exception e) {
            return ParseOutcome.fail("invalid tag: " + tagId + " (" + e.getMessage() + ")");
        }
        for (Holder<Item> holder : members) {
            if (holder == null) continue;
            ItemStack stack = new ItemStack(holder, count);
            applyItemSpec(obj, stack, warnings);
            applyComponents(obj, stack, warnings);
            if (!stack.isEmpty()) {
                stacks.add(stack);
                weights.add(weight);
            }
        }
        if (stacks.isEmpty()) {
            return ParseOutcome.fail("item tag is empty or unregistered: #" + tagId);
        }
        return ParseOutcome.ok(stacks, weights, warnings);
    }

    /** Distinguishes "target mod not installed" from "item id typo". */
    static String unknownItemReason(String id) {
        try {
            Identifier parsed = Identifier.parse(id);
            if (!parsed.getNamespace().equals("minecraft") && !CsgoBox.isModLoaded(parsed.getNamespace())) {
                return "unknown item id: " + id + " (mod '" + parsed.getNamespace()
                        + "' is not loaded — install it or remove this entry)";
            }
            return "unknown item id: " + id + " (mod '" + parsed.getNamespace()
                    + "' is loaded — check the item id spelling)";
        } catch (Exception e) {
            return "invalid item id: " + id;
        }
    }

    /** Applies {@code components} or the legacy NBT {@code tag} string. */
    private static void applyComponents(JsonObject obj, ItemStack stack, List<String> warnings) {
        if (obj.has("components")) {
            JsonElement componentsElem = obj.get("components");
            if (componentsElem.isJsonObject()) {
                DecodeResult dr = decodeComponents(componentsElem.getAsJsonObject());
                warnings.addAll(dr.errors());
                if (!dr.patch().isEmpty()) {
                    stack.applyComponents(dr.patch());
                }
            } else {
                warnings.add("'components' must be a JSON object, got "
                        + componentsElem.getClass().getSimpleName());
            }
        } else if (obj.has("tag")) {
            // Legacy NBT string (only when it is NOT a "#tag" item reference —
            // those are handled earlier in parseItem).
            try {
                String tagStr = obj.get("tag").getAsString();
                var tag = TagParser.parseCompoundFully(tagStr);
                DataResult<DataComponentPatch> result =
                        DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, tag);
                result.resultOrPartial(err -> warnings.add(
                        "NBT 'tag' components parse failed: " + err))
                        .filter(p -> !p.isEmpty())
                        .ifPresent(stack::applyComponents);
            } catch (Exception e) {
                CsgoBox.LOGGER.warn("Failed to parse NBT tag for item {}: {}", obj.get("id"), e.getMessage());
                warnings.add("invalid NBT tag: " + e.getMessage());
            }
        }
    }

    /**
     * Decodes a "components" JSON object entry-by-entry. Unlike
     * {@code DataComponentPatch.CODEC} — whose dispatchedMap fails the entire
     * patch on the first bad key or value — successful entries are kept and
     * each failure is collected with the offending component key. A
     * {@code null} value means "remove this component" (the JSON form
     * {@code DataComponentPatch} itself encodes removals as).
     */
    static DecodeResult decodeComponents(JsonObject componentsJson) {
        DataComponentPatch.Builder builder = DataComponentPatch.builder();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : componentsJson.entrySet()) {
            String key = entry.getKey();
            try {
                DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE
                        .get(Identifier.parse(key)).map(Holder::value).orElse(null);
                if (type == null) {
                    errors.add("components." + key + ": unknown data component");
                    continue;
                }
                Codec<?> codec = type.codec().orElse(null);
                if (codec == null) {
                    errors.add("components." + key + ": transient component cannot be stored in box JSON");
                    continue;
                }
                JsonElement value = entry.getValue();
                if (value.isJsonNull()) {
                    builder.remove(type);
                    continue;
                }
                DataResult<?> result = codec.parse(JsonOps.INSTANCE, value);
                result.resultOrPartial(err -> errors.add("components." + key + ": " + err))
                        .ifPresent(v -> setComponent(builder, type, v));
            } catch (Exception e) {
                errors.add("components." + key + ": " + e.getMessage());
            }
        }
        return new DecodeResult(builder.build(), errors);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setComponent(DataComponentPatch.Builder builder,
                                     DataComponentType type, Object value) {
        builder.set(type, value);
    }

    /** Reads the {@code csgobox:item_spec} marker of a stack (may be null). */
    static String readItemSpec(ItemStack stack) {
        return stack.get(ItemCsgoBox.ITEM_SPEC.get());
    }

    /** The loot-table id from a placeholder stack, or null. */
    static String lootTableId(ItemStack stack) {
        String spec = readItemSpec(stack);
        if (spec == null || !spec.contains("\"l\"")) {
            return null;
        }
        try {
            JsonObject obj = JsonParser.parseString(spec).getAsJsonObject();
            return obj.has("l") ? obj.get("l").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static JsonObject serializeItemStack(ItemStack stack) {
        JsonObject obj = new JsonObject();
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        obj.addProperty("id", itemId.toString());
        obj.addProperty("count", stack.getCount());

        DataComponentPatch patch = stack.getComponentsPatch();
        if (!patch.isEmpty()) {
            try {
                var result = DataComponentPatch.CODEC.encodeStart(JsonOps.INSTANCE, patch);
                result.resultOrPartial(err -> CsgoBox.LOGGER.warn(
                                "Partial components serialization for item {}: {}", itemId, err))
                        .ifPresent(elem -> obj.add("components", elem));
            } catch (Exception e) {
                CsgoBox.LOGGER.warn("Failed to serialize components for item: {}", itemId, e.getMessage());
            }
        }

        return obj;
    }
}
