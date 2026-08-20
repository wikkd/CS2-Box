package com.reclizer.csgobox.forge_1_20_1.item;

import com.reclizer.csgobox.forge_1_20_1.gui.BoxScreenOpener;
import net.minecraft.world.item.ItemStack;

public class ItemTerminal extends ItemCsgoBox {

    /**
     * Terminals are unstackable (one uid/lock per terminal). Must use the
     * two-arg constructor: the one-arg {@code ItemCsgoBox(Properties)} chain
     * would overwrite this with the default stacksTo(16).
     */
    public ItemTerminal(Properties properties) {
        super(properties, 1);
    }

    /** Terminals open the terminal boot screen instead of the classic crate UI. */
    @Override
    public void openScreen(ItemStack stack) {
        BoxScreenOpener.openTerminal(stack);
    }
}
