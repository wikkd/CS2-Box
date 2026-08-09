package com.reclizer.csgobox.v1_21_10.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class ItemCsgoKey extends Item {
    public ItemCsgoKey(Item.Properties properties) {
        super(properties.rarity(Rarity.COMMON));
    }
}
