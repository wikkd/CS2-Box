package com.reclizer.csgobox.box;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(GradeGroup::items)
    ).apply(instance, GradeGroup::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GradeGroup> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GradeGroup::id,
            ByteBufCodecs.STRING_UTF8, GradeGroup::displayName,
            ByteBufCodecs.INT, GradeGroup::color,
            ByteBufCodecs.INT, GradeGroup::weight,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ITEMS)), GradeGroup::items,
            GradeGroup::new
    );

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
}
