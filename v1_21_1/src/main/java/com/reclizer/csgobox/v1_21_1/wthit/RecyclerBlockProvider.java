package com.reclizer.csgobox.v1_21_1.wthit;

import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;

/**
 * WTHIT provider for the armory recycler: a one-line usage hint below the
 * block's name (rendered by WTHIT's vanilla name provider).
 */
public final class RecyclerBlockProvider implements IBlockComponentProvider {

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        tooltip.addLine(Component.translatable("wthit.csgobox.recycler.hint"));
    }
}