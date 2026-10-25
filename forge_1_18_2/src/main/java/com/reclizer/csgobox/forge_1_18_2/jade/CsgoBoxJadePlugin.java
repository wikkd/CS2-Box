package com.reclizer.csgobox.forge_1_18_2.jade;

import com.reclizer.csgobox.forge_1_18_2.block.ArmoryRecyclerBlock;
import net.minecraft.world.entity.item.ItemEntity;
import mcp.mobius.waila.api.IWailaClientRegistration;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.WailaPlugin;

/**
 * Jade plugin: hover information for CS2-Box content.
 *
 * <ul>
 *   <li>Dropped box items ({@link ItemEntity}) show the box drop rate and the
 *       per-grade chances, colored by grade (client-side, reads the already
 *       synced client {@code BoxRegistry}).</li>
 *   <li>The single {@link ArmoryRecyclerBlock} shows a short hint line.</li>
 * </ul>
 *
 * <p>Discovery is {@code @WailaPlugin} annotation scanning performed only by
 * Jade — the classes are inert when Jade is absent. 1.18.2's Jade 5.x keeps
 * the WAILA-lineage {@code mcp.mobius.waila.api} package.</p>
 */
@WailaPlugin("csgobox")
public final class CsgoBoxJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerComponentProvider(new BoxItemEntityProvider(),
                mcp.mobius.waila.api.TooltipPosition.BODY, ItemEntity.class);
        registration.registerComponentProvider(new RecyclerBlockProvider(),
                mcp.mobius.waila.api.TooltipPosition.BODY, ArmoryRecyclerBlock.class);
    }
}
