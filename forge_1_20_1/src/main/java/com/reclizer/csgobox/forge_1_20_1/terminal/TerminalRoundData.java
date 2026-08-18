package com.reclizer.csgobox.forge_1_20_1.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
import net.minecraft.world.item.ItemStack;

public record TerminalRoundData(
        int round,
        NegotiationModel.Offer offer,
        ItemStack item,
        int grade
) {
}
