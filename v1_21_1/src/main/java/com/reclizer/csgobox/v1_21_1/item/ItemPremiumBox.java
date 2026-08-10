package com.reclizer.csgobox.v1_21_1.item;

/**
 * Village-exclusive premium case item — a box-type item that opens the
 * classic crate screen ({@code gui.CsboxScreen}) bound to the decoupled
 * {@code v1_21_1:premium_supply_box} definition (never drops from mobs;
 * sold only by the arms-dealer villager at level 3).
 *
 * <p>Extends {@link ItemCsgoBox} so the whole server-authoritative open
 * pipeline (box_id component resolution, key consumption, server RNG, stats,
 * {@code BoxOpenedEvent}) accepts it unchanged: the server only checks
 * {@code instanceof ItemCsgoBox}. The bound box definition is read from the
 * same {@code v1_21_1:box_id} component as regular boxes; a stack without
 * the component falls back to the item's own registry id, which matches
 * {@code config/csbox/premium_supply_box.json}.
 */
public class ItemPremiumBox extends ItemCsgoBox {

    public ItemPremiumBox() {
        super();
    }
}
