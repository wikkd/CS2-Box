package com.reclizer.csgobox.fabric_1_21_1.net;

import com.reclizer.csgobox.fabric_1_21_1.packet.PacketBoxBulkResult;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketCsgoBulkProgress;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketCsgoProgress;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketRequestBoxItems;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketSyncBoxDefinitions;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketSyncBoxItems;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketTerminalBuy;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketTerminalBuyResult;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketTerminalClose;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketTerminalOpen;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketTerminalReject;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketTerminalState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Payload + receiver registration (Fabric edition of the NeoForge
 * {@code RegisterPayloadHandlersEvent} wiring).
 *
 * <p>Both directions' codecs register in common init (Fabric requires the S2C
 * codec on the client and the C2S codec on the server; registering both on
 * both sides is the documented pattern). Server-side receivers live here;
 * client-side receivers are registered from {@code CsgoBoxClient} so the
 * client-only networking classes never load on a dedicated server.</p>
 */
public final class Networking {

    private Networking() {
    }

    public static void register() {
        // ---- codec registration (both directions, both sides) ----
        PayloadTypeRegistry.playC2S().register(PacketCsgoProgress.TYPE, PacketCsgoProgress.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PacketCsgoBulkProgress.TYPE, PacketCsgoBulkProgress.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PacketRequestBoxItems.TYPE, PacketRequestBoxItems.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PacketTerminalBuy.TYPE, PacketTerminalBuy.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PacketTerminalOpen.TYPE, PacketTerminalOpen.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PacketTerminalReject.TYPE, PacketTerminalReject.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PacketTerminalClose.TYPE, PacketTerminalClose.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketBoxOpenResult.TYPE, PacketBoxOpenResult.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketBoxBulkResult.TYPE, PacketBoxBulkResult.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketSyncBoxItems.TYPE, PacketSyncBoxItems.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketTerminalBuyResult.TYPE, PacketTerminalBuyResult.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketTerminalState.TYPE, PacketTerminalState.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketSyncBoxDefinitions.TYPE, PacketSyncBoxDefinitions.STREAM_CODEC);

        // ---- server-side receivers ----
        ServerPlayNetworking.registerGlobalReceiver(PacketCsgoProgress.TYPE,
                (payload, ctx) -> PacketCsgoProgress.handleServer(payload, new FabricPayloadContext(ctx)));
        ServerPlayNetworking.registerGlobalReceiver(PacketCsgoBulkProgress.TYPE,
                (payload, ctx) -> PacketCsgoBulkProgress.handleServer(payload, new FabricPayloadContext(ctx)));
        ServerPlayNetworking.registerGlobalReceiver(PacketRequestBoxItems.TYPE,
                (payload, ctx) -> PacketRequestBoxItems.handle(payload, new FabricPayloadContext(ctx)));
        ServerPlayNetworking.registerGlobalReceiver(PacketTerminalBuy.TYPE,
                (payload, ctx) -> PacketTerminalBuy.handleServer(payload, new FabricPayloadContext(ctx)));
        ServerPlayNetworking.registerGlobalReceiver(PacketTerminalOpen.TYPE,
                (payload, ctx) -> PacketTerminalOpen.handleServer(payload, new FabricPayloadContext(ctx)));
        ServerPlayNetworking.registerGlobalReceiver(PacketTerminalReject.TYPE,
                (payload, ctx) -> PacketTerminalReject.handleServer(payload, new FabricPayloadContext(ctx)));
        ServerPlayNetworking.registerGlobalReceiver(PacketTerminalClose.TYPE,
                (payload, ctx) -> PacketTerminalClose.handleServer(payload, new FabricPayloadContext(ctx)));
    }
}
