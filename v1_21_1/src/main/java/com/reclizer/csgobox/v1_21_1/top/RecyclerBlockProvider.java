package com.reclizer.csgobox.v1_21_1.top;

import com.reclizer.csgobox.v1_21_1.block.ArmoryRecyclerBlock;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** TOP block provider for the armory recycler: a one-line usage hint. */
public final class RecyclerBlockProvider implements IProbeInfoProvider {

    @Override
    public ResourceLocation getID() {
        return CsgoBoxTopPlugin.id("armory_recycler");
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, Player player, Level level,
                             BlockState blockState, IProbeHitData data) {
        if (!(blockState.getBlock() instanceof ArmoryRecyclerBlock)) {
            return;
        }
        probeInfo.text(Component.translatable("top.csgobox.recycler.hint"));
    }
}