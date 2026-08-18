package com.reclizer.csgobox.forge_1_20_1.box;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class BoxItemCodec {

    private static final Gson GSON = new Gson();

    public static Gson gson() {
        return GSON;
    }

    private BoxItemCodec() {
    }

    record ParseOutcome(ItemStack stack, String error) {
        boolean isSuccess() { return error == null; }
        static ParseOutcome ok(ItemStack stack) { return new ParseOutcome(stack, null); }
        static ParseOutcome fail(String message) { return new ParseOutcome(ItemStack.EMPTY, message); }
    }

    static ParseOutcome parseItem(JsonElement elem) {
        try {
            JsonObject obj;
            if (elem.isJsonPrimitive()) {
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

            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            if (item == null) {
                CsgoBox.LOGGER.warn("Unknown item in box JSON: {}", id);
                return ParseOutcome.fail("unknown item id: " + id);
            }

            ItemStack stack = new ItemStack(item, count);

            if (obj.has("tag")) {
                try {
                    String tagStr;
                    if (obj.get("tag").isJsonPrimitive()) {
                        tagStr = obj.get("tag").getAsString();
                    } else {
                        tagStr = obj.get("tag").toString();
                    }
                    CompoundTag tag = TagParser.parseTag(tagStr);
                    stack.setTag(tag);
                } catch (Exception e) {
                    CsgoBox.LOGGER.warn("Failed to parse NBT tag for item {}: {}", id, e.getMessage());
                }
            }

            return ParseOutcome.ok(stack);
        } catch (Exception e) {
            CsgoBox.LOGGER.warn("Failed to parse item JSON: {}", elem, e);
            return ParseOutcome.fail("parse failed: " + e.getMessage());
        }
    }

    public static JsonObject serializeItemStack(ItemStack stack) {
        JsonObject obj = new JsonObject();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        obj.addProperty("id", itemId.toString());
        obj.addProperty("count", stack.getCount());

        CompoundTag tag = stack.getTag();
        if (tag != null && !tag.isEmpty()) {
            try {
                obj.addProperty("tag", tag.toString());
            } catch (Exception e) {
                CsgoBox.LOGGER.warn("Failed to serialize NBT tag for item: {}", itemId, e.getMessage());
            }
        }

        return obj;
    }
}
