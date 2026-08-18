package com.reclizer.csgobox.forge_1_20_1.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class ItemCsgoKey extends Item {
    public ItemCsgoKey(Item.Properties properties) {
        super(properties.rarity(Rarity.COMMON));
    }
}
