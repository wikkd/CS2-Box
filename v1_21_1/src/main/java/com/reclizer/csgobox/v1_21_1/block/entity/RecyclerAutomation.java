package com.reclizer.csgobox.v1_21_1.block.entity;

import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.block.ModBlocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * v2.1.0 Create/automation compat: register the standard item-handler
 * capability on the Armory Recycler so mechanical arms, tunnels, modded pipes
 * and hoppers can feed graded items into the input slot and pull Armory Points
 * out of the output slot. Registered through the NeoForge capability registry
 * (no block-entity method override needed on this platform line).
 */
@EventBusSubscriber(modid = CsgoBox.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class RecyclerAutomation {
    private RecyclerAutomation() {
    }

    @SubscribeEvent
    public static void registerItemHandler(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlocks.ARMORY_RECYCLER_BE.get(),
                (be, side) -> new ArmoryRecyclerBlockEntity.RecyclerHandler(be));
    }
}
