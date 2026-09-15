package com.reclizer.csgobox.v26_2.wthit;

import com.reclizer.csgobox.v26_2.block.ArmoryRecyclerBlock;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * WTHIT plugin: hover information for CS2-Box content — dropped box items show
 * the drop rate and per-grade chances, the armory recycler block shows a
 * usage hint. Everything is client-side (the box registry is already synced).
 *
 * <p>Discovery is the root {@code waila_plugins.json} entry pointing at this
 * class (WTHIT's documented plugin mechanism); the plugin classes reference
 * WTHIT API only, so the integration is inert when WTHIT is absent.</p>
 */
public final class CsgoBoxWthitPlugin implements IWailaPlugin {

    @Override
    public void register(IRegistrar registrar) {
        registrar.addComponent(new BoxItemEntityProvider(), TooltipPosition.BODY, ItemEntity.class);
        registrar.addComponent(new RecyclerBlockProvider(), TooltipPosition.BODY, ArmoryRecyclerBlock.class);
    }
}