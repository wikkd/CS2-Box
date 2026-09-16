package com.reclizer.csgobox.v26_1_2.event;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Fired on the NeoForge event bus BEFORE a terminal purchase consumes stock
 * or Armory Points, and BEFORE the item is granted. Canceling this event
 * aborts the purchase cleanly — nothing is consumed, the terminal stays in
 * the player's hand and the negotiation session stays open.
 *
 * <p>Listeners can use this event to:</p>
 * <ul>
 *   <li>Veto purchases (per-terminal daily caps, item blacklists, event gates)</li>
 *   <li>Observe buy attempts for analytics</li>
 * </ul>
 *
 * <p><b>KubeJS compatibility:</b> when KubeJS is installed, this event is
 * accessible from server scripts via
 * {@code NeoForgeEvents.onEvent('com.reclizer.csgobox.v26_1_2.event.TerminalBuyAttemptEvent', ...)}.
 * Call {@code event.cancel()} to refuse the trade.</p>
 *
 * <p><b>Boundary contract:</b> fires AFTER the session/round/price validation
 * and BEFORE stock consumption, Armory Point deduction and item resolution —
 * a canceled attempt costs the player nothing. The quoted price
 * ({@link #getPrice()}) is read-only: it was already shown to the client, so
 * changing it here would desync the display from the charge.</p>
 */
public class TerminalBuyAttemptEvent extends PlayerEvent implements ICancellableEvent {

    private final Identifier boxId;
    private final int grade;
    private final int price;
    private final float wearVal;
    private final ItemStack item;
    private final int offerRound;

    public TerminalBuyAttemptEvent(Player player, Identifier boxId, int grade,
                                   int price, float wearVal, ItemStack item, int offerRound) {
        super(player);
        this.boxId = boxId;
        this.grade = grade;
        this.price = price;
        this.wearVal = wearVal;
        this.item = item;
        this.offerRound = offerRound;
    }

    /** The terminal definition id being bought from (e.g. {@code csgobox:terminal}). */
    public Identifier getBoxId() {
        return boxId;
    }

    /** Grade (1-5) of the offered item. */
    public int getGrade() {
        return grade;
    }

    /** The server-authoritative price (Armory Points) about to be charged. */
    public int getPrice() {
        return price;
    }

    /** Wear value of the offer (0..1), already applied to {@link #getItem()}. */
    public float getWearVal() {
        return wearVal;
    }

    /** The item about to be granted (count 1 after the terminal's clamp). */
    public ItemStack getItem() {
        return item;
    }

    /** Negotiation round of this offer (1-5). */
    public int getOfferRound() {
        return offerRound;
    }
}