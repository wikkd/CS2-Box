package com.reclizer.csgobox.v26_2.box;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.reclizer.csgobox.v26_2.CsgoBox;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
     * Result of {@link #parseItem}: the parsed {@link ItemStack} on success,
     * or a human-readable error string on failure. {@code error == null}
     * indicates success; callers route the error into the box loader's
     * LoadError list so it surfaces via {@code /csbox info error} and the
     * join-time announcement. Warnings are non-fatal diagnostics (migrated
     * formats, partially dropped components) surfaced the same way.
     */
    record ParseOutcome(ItemStack stack, String error, List<String> warnings) {
        boolean isSuccess() { return error == null; }
        static ParseOutcome ok(ItemStack stack) { return ok(stack, List.of()); }
        static ParseOutcome ok(ItemStack stack, List<String> warnings) {
            return new ParseOutcome(stack, null, List.copyOf(warnings));
        }
        static ParseOutcome fail(String message) {
            return new ParseOutcome(ItemStack.EMPTY, message, List.of());
        }
    }

    /** Per-component decode output: the patch of successful entries plus one
     *  error string per failed entry. */
    record DecodeResult(DataComponentPatch patch, List<String> errors) {
        boolean hasErrors() { return !errors.isEmpty(); }
    }

    /**
     * Parses an item object, or a legacy JSON string containing that object.
     * Returns a {@link ParseOutcome} that distinguishes a clean parse from a
     * skipped item (missing id, unknown item id, malformed components).
     * Components / NBT tag parse failures remain warn-only — the item body
     * is still accepted, only the broken entry's data is dropped.
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
            if (!obj.has("id")) {
                CsgoBox.LOGGER.warn("Skipping item JSON without id: {}", elem);
                return ParseOutcome.fail("missing 'id' field");
            }

            String id = obj.get("id").getAsString();
            int count = obj.has("count") ? obj.get("count").getAsInt() : 1;

            Item item = BuiltInRegistries.ITEM.get(Identifier.parse(id)).map(Holder.Reference::value).orElse(null);
            if (item == null) {
                CsgoBox.LOGGER.warn("Unknown item in box JSON: {}", id);
                return ParseOutcome.fail("unknown item id: " + id);
            }

            // The registry Holder backing this stack carries a ResourceKey,
            // so it survives later serialization (e.g. into the player_data
            // attachment). Constructing a raw ItemStack without a key would
            // break box opening on the next launch.
            ItemStack stack = new ItemStack(item, count);

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
                    CsgoBox.LOGGER.warn("Failed to parse NBT tag for item {}: {}", id, e.getMessage());
                    warnings.add("invalid NBT tag: " + e.getMessage());
                }
            }

            return ParseOutcome.ok(stack, warnings);
        } catch (Exception e) {
            // Use Throwable variant so the real cause is not silently dropped
            // when the format string has only one {} placeholder.
            CsgoBox.LOGGER.warn("Failed to parse item JSON: {}", elem, e);
            return ParseOutcome.fail("parse failed: " + e.getMessage());
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