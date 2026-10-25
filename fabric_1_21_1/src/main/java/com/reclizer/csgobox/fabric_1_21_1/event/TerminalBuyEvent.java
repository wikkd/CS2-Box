package com.reclizer.csgobox.fabric_1_21_1.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Fired on the mod event bus ({@link CsgoBus}) AFTER a terminal purchase
 * succeeds: Armory Points have been spent, the item has been granted, and the
 * terminal machine has been consumed. This is a <em>post-buy notification</em>
 * — it cannot refund or veto the purchase.
 *
 * <p>Terminal purchases do <em>not</em> fire {@link BoxOpenedEvent} — the
 * terminal protocol is a separate pipeline. Listeners that need both paths
 * (boxes and terminal) should subscribe to both events.</p>
 */
public class TerminalBuyEvent extends PlayerEvent {

    private final int grade;
    private final int price;
    private final float wearVal;
    private final ItemStack item;
    private final int round;

    public TerminalBuyEvent(Player player, int grade, int price, float wearVal, ItemStack item, int round) {
        super(player);
        this.grade = grade;
        this.price = price;
        this.wearVal = wearVal;
        this.item = item;
        this.round = round;
    }

    /** Rarity grade of the bought item (1–5). */
    public int getGrade() {
        return grade;
    }

    /** Armory Points charged for this purchase (server-authoritative price, wear surcharge included). */
    public int getPrice() {
        return price;
    }

    /** Wear value of the offer that was applied to the sold item. */
    public float getWearVal() {
        return wearVal;
    }

    /** The item granted by this purchase (count is always 1). */
    public ItemStack getItem() {
        return item;
    }

    /** Negotiation round in which the purchase happened (1–5). */
    public int getRound() {
        return round;
    }
}
