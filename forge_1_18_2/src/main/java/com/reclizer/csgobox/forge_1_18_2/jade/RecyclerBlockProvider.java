package com.reclizer.csgobox.forge_1_18_2.jade;

import com.reclizer.csgobox.forge_1_18_2.CsgoBox;
import mcp.mobius.waila.api.BlockAccessor;
import mcp.mobius.waila.api.IComponentProvider;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.config.IPluginConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * Jade provider for the armory recycler: a one-line usage hint while hovering
 * the block. The block's own name is rendered by Jade's vanilla name provider,
 * so only the hint is appended here. Registered at TooltipPosition.BODY.
 */
public final class RecyclerBlockProvider implements IComponentProvider {

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        tooltip.add(new TranslatableComponent("jade.csgobox.recycler.hint"));
    }
}
