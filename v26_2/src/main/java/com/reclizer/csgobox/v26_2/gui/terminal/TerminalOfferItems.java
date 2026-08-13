package com.reclizer.csgobox.v26_2.gui.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
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
    private static ItemStack sessionItem = ItemStack.EMPTY;

    private TerminalOfferItems() {
    }

    /** Start a fresh session view: drop the previous terminal's sampled data. */
    public static void reset() {
        ROUND_ITEM.clear();
        ROUND_GRADE.clear();
        sessionItem = ItemStack.EMPTY;
    }

    /** Register the server-sampled item + box grade for one round. */
    public static void setRoundItem(int round, ItemStack item, int grade) {
        ROUND_ITEM.put(round, item.copy());
        ROUND_GRADE.put(round, grade);
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

    /** Whole Armory Point price of the offered item (shared grade price table). */
    public static int priceFor(NegotiationModel.Offer offer) {
        return NegotiationModel.priceForGrade(gradeFor(offer));
    }

    /** Whole Armory Point price of a round by its server-sampled grade. */
    public static int priceForRound(int round) {
        return NegotiationModel.priceForGrade(ROUND_GRADE.getOrDefault(round, 1));
    }

    /** The server-sampled region-10 slot item. */
    public static ItemStack sessionItem() {
        return sessionItem;
    }
}
