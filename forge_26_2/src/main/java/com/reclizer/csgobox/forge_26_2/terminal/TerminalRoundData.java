package com.reclizer.csgobox.forge_26_2.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
import net.minecraft.world.item.ItemStack;

/**
 * Server-sampled data for one terminal negotiation round: the script offer
 * (skin/style/serial/pattern) plus the ACTUAL item granted if the player
 * buys, its box grade and its terminal purchase price in Armory Points.
 * {@code price} comes from the item's JSON {@code price} field, or -1 to
 * use the default grade-level price ({@code NegotiationModel.GRADE_PRICE}).
 */
public record TerminalRoundData(
        int round,
        NegotiationModel.Offer offer,
        ItemStack item,
        int grade,
        int price
) {
}
