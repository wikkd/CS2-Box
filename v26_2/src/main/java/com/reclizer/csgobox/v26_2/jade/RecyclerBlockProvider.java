package com.reclizer.csgobox.v26_2.jade;

import com.reclizer.csgobox.v26_2.CsgoBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade provider for the armory recycler: a one-line usage hint while hovering
 * the block. The block's own name is rendered by Jade's vanilla name provider,
 * so only the hint is appended here.
 */
public final class RecyclerBlockProvider implements IBlockComponentProvider {

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "armory_recycler");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        tooltip.add(Component.translatable("jade.csgobox.recycler.hint"));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}