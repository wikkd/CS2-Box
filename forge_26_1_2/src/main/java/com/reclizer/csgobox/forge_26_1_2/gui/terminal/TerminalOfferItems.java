package com.reclizer.csgobox.forge_26_1_2.gui.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.terminal.WearPenalty;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the ACTUAL offered item for each negotiation round from the
 * server's locked terminal session (the server samples one item per round
 * and the region-10 slot item; the client never randomizes). Populated from
 * {@code PacketTerminalState} when the screen opens.
 *
 * era: decoupled
 */
public final class TerminalOfferItems {

    private static final Map<Integer, ItemStack> ROUND_ITEM = new HashMap<>();
    private static final Map<Integer, Integer> ROUND_GRADE = new HashMap<>();
    private static final Map<Integer, Float> ROUND_WEAR = new HashMap<>();
    private static final Map<Integer, Integer> ROUND_PRICE = new HashMap<>();
    private static ItemStack sessionItem = ItemStack.EMPTY;

    private TerminalOfferItems() {
    }

    /** Start a fresh session view: drop the previous terminal's sampled data. */
    public static void reset() {
        ROUND_ITEM.clear();
        ROUND_GRADE.clear();
        ROUND_WEAR.clear();
        ROUND_PRICE.clear();
        sessionItem = ItemStack.EMPTY;
    }

    /** Register the server-sampled item + box grade + offered wear + terminal price for one round. */
    public static void setRoundItem(int round, ItemStack item, int grade, float wearVal, int price) {
        ROUND_ITEM.put(round, item.copy());
        ROUND_GRADE.put(round, grade);
        ROUND_WEAR.put(round, wearVal);
        ROUND_PRICE.put(round, price);
    }

    /** Register the server-sampled region-10 slot item. */
    public static void setSessionItem(ItemStack item) {
        sessionItem = item.copy();
    }

    /** The round's actual offered item (server-sampled), or empty before sync. */
    public static ItemStack itemFor(NegotiationModel.Offer offer) {
        return ROUND_ITEM.getOrDefault(offer.round(), ItemStack.EMPTY);
    }

    /** Box grade (1..5) of the round's offered item (server-sampled). */
    public static int gradeFor(NegotiationModel.Offer offer) {
        return ROUND_GRADE.getOrDefault(offer.round(), 1);
    }

    /** Display name of the round's actual offered item. */
    public static String nameFor(NegotiationModel.Offer offer) {
        return itemFor(offer).getHoverName().getString();
    }

    /** Rarity tier key ("mil_spec".."contraband") of the offered item. */
    public static String rarityKeyFor(NegotiationModel.Offer offer) {
        return NegotiationModel.rarityKeyForGrade(gradeFor(offer));
    }

    /** The round's custom terminal price (-1 = use default grade price). */
    public static int priceForRoundRaw(int round) {
        return ROUND_PRICE.getOrDefault(round, -1);
    }

    /**
     * Whole Armory Point price of the offered item: the per-item price (or
     * the shared grade default) plus the wear surcharge when the item has no
     * durability bar (matches the server's authoritative buy price).
     */
    public static int priceFor(NegotiationModel.Offer offer) {
        return priceFor(itemFor(offer), gradeFor(offer), offer.wearVal(),
                ROUND_PRICE.getOrDefault(offer.round(), -1));
    }

    /** Whole Armory Point price of a round by its server-sampled grade. */
    public static int priceForRound(int round) {
        return priceFor(ROUND_ITEM.getOrDefault(round, ItemStack.EMPTY),
                ROUND_GRADE.getOrDefault(round, 1),
                ROUND_WEAR.getOrDefault(round, 0F),
                ROUND_PRICE.getOrDefault(round, -1));
    }

    /** Base price without the wear surcharge (for the penalty breakdown). */
    public static int basePriceFor(NegotiationModel.Offer offer) {
        int custom = ROUND_PRICE.getOrDefault(offer.round(), -1);
        return custom >= 0 ? custom : NegotiationModel.priceForGrade(gradeFor(offer));
    }

    private static int priceFor(ItemStack item, int grade, float wearVal, int customPrice) {
        int price = customPrice >= 0 ? customPrice : NegotiationModel.priceForGrade(grade);
        if (!item.isEmpty() && !item.isDamageableItem()) {
            price += WearPenalty.surcharge(wearVal);
        }
        return price;
    }

    /** The server-sampled region-10 slot item. */
    public static ItemStack sessionItem() {
        return sessionItem;
    }
}
