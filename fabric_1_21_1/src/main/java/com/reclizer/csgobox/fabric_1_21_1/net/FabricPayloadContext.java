package com.reclizer.csgobox.fabric_1_21_1.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * Shape-compatible stand-in for NeoForge's {@code IPayloadContext} so the
 * packet handler bodies port without structural changes.
 *
 * <p>Fabric play-payload receivers already run on the main thread, so
 * {@link #enqueueWork(Runnable)} executes inline. {@link #reply} routes the
 * response back to the originating player.</p>
 */
public final class FabricPayloadContext {
    private final ServerPlayNetworking.Context serverCtx; // set on the server side
    private final ClientPlayNetworking.Context clientCtx; // set on the client side

    public FabricPayloadContext(ServerPlayNetworking.Context ctx) {
        this.serverCtx = ctx;
        this.clientCtx = null;
    }

    public FabricPayloadContext(ClientPlayNetworking.Context ctx) {
        this.serverCtx = null;
        this.clientCtx = ctx;
    }

    public Player player() {
        return serverCtx != null ? serverCtx.player() : clientCtx.player();
    }

    public void enqueueWork(Runnable runnable) {
        runnable.run();
    }

    public void reply(CustomPacketPayload payload) {
        if (serverCtx != null) {
            ServerPlayNetworking.send(serverCtx.player(), payload);
        }
    }
}
