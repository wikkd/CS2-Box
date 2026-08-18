package com.reclizer.csgobox.forge_26_2.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
import net.minecraft.world.item.ItemStack;

/**
 * Server-sampled data for one terminal negotiation round: the script offer
 * (skin/style/serial/pattern) plus the ACTUAL item granted if the player
 * buys, and its box grade (drives price + rarity).
 */
public record TerminalRoundData(
        int round,
        NegotiationModel.Offer offer,
        ItemStack item,
        int grade
) {
}
