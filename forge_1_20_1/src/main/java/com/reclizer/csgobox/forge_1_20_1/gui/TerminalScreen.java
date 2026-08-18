package com.reclizer.csgobox.forge_1_20_1.gui;

import com.reclizer.csgobox.forge_1_20_1.packet.PacketTerminalState;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class TerminalScreen {

    @Nullable
    public static TerminalScreen getOpen() {
        return null;
    }

    public void onTerminalState(PacketTerminalState state) {
    }

    public void onBuyResult(long requestId, int result, ItemStack givenItem) {
    }
}
