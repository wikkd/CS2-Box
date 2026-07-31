package com.reclizer.csgobox.v1_21_8.capability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

public record CsboxPlayerData(long seed, int mode, ItemStack item, int grade) {

    public CsboxPlayerData {
        item = item == null ? ItemStack.EMPTY : item.copy();
    }

    public CsboxPlayerData() {
        this(0L, 0, ItemStack.EMPTY, 0);
    }

    public static final MapCodec<CsboxPlayerData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(CsboxPlayerData::seed),
            Codec.INT.optionalFieldOf("mode", 0).forGetter(CsboxPlayerData::mode),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(CsboxPlayerData::item),
            Codec.INT.optionalFieldOf("grade", 0).forGetter(CsboxPlayerData::grade)
    ).apply(instance, CsboxPlayerData::new));
}
