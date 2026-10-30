package com.reclizer.csgobox.fabric_1_21_1;

import com.reclizer.csgobox.fabric_1_21_1.box.BoxJsonLoader;
import com.reclizer.csgobox.fabric_1_21_1.event.ClickEvent;
import com.reclizer.csgobox.fabric_1_21_1.event.FirstPersonInspectHandler;
import com.reclizer.csgobox.fabric_1_21_1.event.ScreenBlurBoost;
import com.reclizer.csgobox.fabric_1_21_1.gui.ArmoryRecyclerScreen;
import com.reclizer.csgobox.fabric_1_21_1.menu.ModMenus;
import com.reclizer.csgobox.fabric_1_21_1.net.FabricPayloadContext;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketBoxBulkResult;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketSyncBoxDefinitions;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketSyncBoxItems;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketTerminalBuyResult;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketTerminalState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric client entry point: client packet receivers, menu screen binding,
 * the dynamic-box model fallback and the client event wiring.
 */
public final class CsgoBoxClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CsgoBox.LOGGER.info("CS2 Box client setup complete");
        // Load once more now that EVERY mod's main init has run (Fabric runs
        // all client entrypoints after all main entrypoints): other mods'
        // items are registered by now, so id validation sees them.
        BoxJsonLoader.loadAll();
        // '#tag' entries cannot expand yet — item tags are only bound during
        // the client's first resource load, well after every entrypoint.
        // CLIENT_STARTED fires once that load (and the tag bind) is done;
        // rescanning there lets tag entries finally expand. Error-carrying
        // parses are never cache-reused (BoxJsonLoader), so this rescan
        // re-parses instead of serving the early tag-less result.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> BoxJsonLoader.loadAll());

        // Map the recycler {@code MenuType} to its screen.
        MenuScreens.register(ModMenus.ARMORY_RECYCLER.get(), ArmoryRecyclerScreen::new);

        registerClientReceivers();
        registerClientEvents();
    }

    private static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(PacketBoxOpenResult.TYPE,
                (payload, ctx) -> PacketBoxOpenResult.handle(payload, new FabricPayloadContext(ctx)));
        ClientPlayNetworking.registerGlobalReceiver(PacketBoxBulkResult.TYPE,
                (payload, ctx) -> PacketBoxBulkResult.handle(payload, new FabricPayloadContext(ctx)));
        ClientPlayNetworking.registerGlobalReceiver(PacketSyncBoxItems.TYPE,
                (payload, ctx) -> PacketSyncBoxItems.handle(payload, new FabricPayloadContext(ctx)));
        ClientPlayNetworking.registerGlobalReceiver(PacketTerminalBuyResult.TYPE,
                (payload, ctx) -> PacketTerminalBuyResult.handle(payload, new FabricPayloadContext(ctx)));
        ClientPlayNetworking.registerGlobalReceiver(PacketTerminalState.TYPE,
                (payload, ctx) -> PacketTerminalState.handle(payload, new FabricPayloadContext(ctx)));
        ClientPlayNetworking.registerGlobalReceiver(PacketSyncBoxDefinitions.TYPE,
                (payload, ctx) -> PacketSyncBoxDefinitions.handle(payload, new FabricPayloadContext(ctx)));
    }

    private static void registerClientEvents() {
        ClickEvent.register();
        ScreenBlurBoost.register();
        ClientTickEvents.END_CLIENT_TICK.register(event -> FirstPersonInspectHandler.onClientTick());
    }
}
