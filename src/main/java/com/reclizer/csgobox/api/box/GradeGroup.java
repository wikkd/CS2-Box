package com.reclizer.csgobox.api.box;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record GradeGroup(String id, String displayName, int color, int weight, List<ItemStack> items) {

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
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), GradeGroup::items,
            GradeGroup::new
    );

    public GradeGroup {
        if (items == null) {
            items = List.of();
        }
    }

    public GradeGroup(String id, String displayName, int color, int weight) {
        this(id, displayName, color, weight, new ArrayList<>());
    }

    public GradeGroup withItem(ItemStack item) {
        List<ItemStack> newItems = new ArrayList<>(items);
        newItems.add(item);
        return new GradeGroup(id, displayName, color, weight, newItems);
    }
}
