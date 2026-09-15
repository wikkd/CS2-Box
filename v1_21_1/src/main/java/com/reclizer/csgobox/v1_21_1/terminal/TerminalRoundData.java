package com.reclizer.csgobox.v1_21_1.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
import net.minecraft.world.item.ItemStack;

/**
 * Server-sampled data for one terminal negotiation round: the script offer
 * (skin/style/serial/pattern) plus the ACTUAL item granted if the player
 * buys, its box grade and its terminal purchase price in Armory Points.
 * {@code price} is the already-sampled table price (a table range is drawn
 * once in {@link TerminalSession}); unpriced items are never offered (id
 * entries must be priced or the box refuses to load).
 */
public record TerminalRoundData(
        int round,
        NegotiationModel.Offer offer,
        ItemStack item,
        int grade,
        int price
) {
}
