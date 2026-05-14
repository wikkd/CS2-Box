package com.reclizer.csgobox.capability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

public record CsboxPlayerData(long seed, int mode, ItemStack item, int grade) {

    public CsboxPlayerData() {
        this(0L, 0, ItemStack.EMPTY, 0);
    }

    public static final Codec<CsboxPlayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(CsboxPlayerData::seed),
            Codec.INT.optionalFieldOf("mode", 0).forGetter(CsboxPlayerData::mode),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(CsboxPlayerData::item),
            Codec.INT.optionalFieldOf("grade", 0).forGetter(CsboxPlayerData::grade)
    ).apply(instance, CsboxPlayerData::new));

    public CsboxPlayerData withSeed(long seed) {
        return new CsboxPlayerData(seed, mode, item, grade);
    }

    public CsboxPlayerData withMode(int mode) {
        if (mode > -2 && mode < 2) {
            return new CsboxPlayerData(seed, mode, item, grade);
        }
        return this;
    }

    public CsboxPlayerData withItem(ItemStack item) {
        return new CsboxPlayerData(seed, mode, item, grade);
    }

    public CsboxPlayerData withGrade(int grade) {
        if (grade > 0 && grade < 6) {
            return new CsboxPlayerData(seed, mode, item, grade);
        }
        return this;
    }
}