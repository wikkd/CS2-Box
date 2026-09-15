package com.reclizer.csgobox.v1_21_1.jade;

import com.reclizer.csgobox.v1_21_1.block.ArmoryRecyclerBlock;
import net.minecraft.world.entity.item.ItemEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

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
 * Jade — the classes are inert when Jade is absent.</p>
 */
@WailaPlugin("csgobox")
public final class CsgoBoxJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(new BoxItemEntityProvider(), ItemEntity.class);
        registration.registerBlockComponent(new RecyclerBlockProvider(), ArmoryRecyclerBlock.class);
    }
}