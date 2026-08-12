package com.reclizer.csgobox.v26_2.box;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.reclizer.csgobox.v26_2.CsgoBox;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Parses and serializes individual ItemStack entries within box JSON.
 *
 * <p>Both directions tolerate the legacy "tag" NBT string format and the
 * modern "components" data-component patch format.</p>
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
     * join-time announcement.
     */
    record ParseOutcome(ItemStack stack, String error) {
        boolean isSuccess() { return error == null; }
        static ParseOutcome ok(ItemStack stack) { return new ParseOutcome(stack, null); }
        static ParseOutcome fail(String message) { return new ParseOutcome(ItemStack.EMPTY, message); }
    }

    /**
     * Parses an item object, or a legacy JSON string containing that object.
     * Returns a {@link ParseOutcome} that distinguishes a clean parse from a
     * skipped item (missing id, unknown item id, malformed components).
     * Components / NBT tag parse failures remain warn-only — the item body
     * is still accepted, only its data-component patch is empty.
     */
    static ParseOutcome parseItem(JsonElement elem) {
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
                    var tag = TagParser.parseCompoundFully(tagStr);
                    DataComponentPatch patch = DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, tag)
                            .result().orElse(DataComponentPatch.EMPTY);
                    stack.applyComponents(patch);
                } catch (Exception e) {
                    CsgoBox.LOGGER.warn("Failed to parse NBT tag for item {}: {}", id, e.getMessage());
                }
            }

            return ParseOutcome.ok(stack);
        } catch (Exception e) {
            // Use Throwable variant so the real cause is not silently dropped
            // when the format string has only one {} placeholder.
            CsgoBox.LOGGER.warn("Failed to parse item JSON: {}", elem, e);
            return ParseOutcome.fail("parse failed: " + e.getMessage());
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
                result.result().ifPresent(elem -> obj.add("components", elem));
            } catch (Exception e) {
                CsgoBox.LOGGER.warn("Failed to serialize components for item: {}", itemId, e.getMessage());
            }
        }

        return obj;
    }
}
