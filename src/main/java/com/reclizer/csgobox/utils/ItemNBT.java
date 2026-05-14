package com.reclizer.csgobox.utils;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class ItemNBT {

    public static ItemStack getStacks(String itemData) {
        if (itemData == null || itemData.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 优先尝试 JSON 格式解析（ItemStack 1.21.1 扁平格式）
        try {
            var result = ItemStack.CODEC.decode(JsonOps.INSTANCE, JsonParser.parseString(itemData)).result();
            if (result.isPresent()) {
                return result.get().getFirst();
            }
        } catch (Exception ignored) {
        }

        // 兼容旧格式：NBT 格式解析（如 {"id":"...","Count":1,"tag":{...}}）
        try {
            Tag nbt = TagParser.parseTag(itemData);
            if (nbt instanceof CompoundTag compound) {
                RegistryAccess registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
                return ItemStack.parseOptional(registryAccess, compound);
            }
        } catch (Exception ignored) {
        }

        return ItemStack.EMPTY;
    }

    public static ItemStack getStacks(com.google.gson.JsonElement jsonElement) {
        try {
            var result = ItemStack.CODEC.decode(JsonOps.INSTANCE, jsonElement).result();
            if (result.isPresent()) {
                return result.get().getFirst();
            }
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
    }

    public static void setStacks(ItemStack itemStack, String nbtData) {
        if (itemStack == ItemStack.EMPTY) return;
        if (nbtData != null && !nbtData.isEmpty()) {
            try {
                Tag nbt = TagParser.parseTag(nbtData);
                if (nbt instanceof CompoundTag compound) {
                    itemStack.set(DataComponents.CUSTOM_DATA, CustomData.of(compound));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
