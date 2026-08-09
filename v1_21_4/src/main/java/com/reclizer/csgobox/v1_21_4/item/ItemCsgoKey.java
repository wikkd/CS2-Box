package com.reclizer.csgobox.v1_21_4.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class ItemCsgoKey extends Item {
    public ItemCsgoKey() {
        this(new Properties());
    }

    public ItemCsgoKey(Properties properties) {
        super(properties.rarity(Rarity.COMMON));
    }
}
