package com.reclizer.csgobox.forge_1_20_1.item;

public class ItemTerminal extends ItemCsgoBox {

    /**
     * Terminals are unstackable (one uid/lock per terminal).
     */
    public ItemTerminal(Properties properties) {
        super(properties.stacksTo(1));
    }
}
