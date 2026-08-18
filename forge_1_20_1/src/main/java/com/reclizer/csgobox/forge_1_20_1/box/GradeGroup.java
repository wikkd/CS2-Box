package com.reclizer.csgobox.forge_1_20_1.box;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record GradeGroup(String id, String displayName, int color, int weight, List<ItemStack> items) {

    private static final int MAX_ITEMS = 256;

    public static final Codec<GradeGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(GradeGroup::id),
            Codec.STRING.fieldOf("display_name").forGetter(GradeGroup::displayName),
            Codec.INT.fieldOf("color").forGetter(GradeGroup::color),
            Codec.INT.fieldOf("weight").forGetter(GradeGroup::weight),
            ItemStack.CODEC.listOf().fieldOf("items").forGetter(GradeGroup::items)
    ).apply(instance, GradeGroup::new));

    public GradeGroup {
        id = Objects.requireNonNull(id, "grade id");
        displayName = displayName == null ? id : displayName;
        if (items == null || items.isEmpty()) {
            items = List.of();
        } else {
            List<ItemStack> copies = new ArrayList<>(items.size());
            for (ItemStack stack : items) {
                if (stack != null && !stack.isEmpty()) {
                    copies.add(stack.copy());
                }
            }
            items = List.copyOf(copies);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(displayName);
        buf.writeInt(color);
        buf.writeInt(weight);
        buf.writeVarInt(items.size());
        for (ItemStack stack : items) {
            buf.writeNbt(stack.save(new CompoundTag()));
        }
    }

    public static GradeGroup decode(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String displayName = buf.readUtf();
        int color = buf.readInt();
        int weight = buf.readInt();
        int count = buf.readVarInt();
        List<ItemStack> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CompoundTag tag = buf.readNbt();
            items.add(ItemStack.of(tag));
        }
        return new GradeGroup(id, displayName, color, weight, items);
    }
}
