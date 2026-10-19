package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server request to bulk-open every matching box in the player's
 * inventory. The orchestration lives in
 * {@link BoxOpenExecutor#handleBulkOpen} so the packet stays a thin
 * transport shell.
 */
public class PacketCsgoBulkProgress {

    private final long requestId;

    public PacketCsgoBulkProgress(long requestId) {
        this.requestId = requestId;
    }

    public PacketCsgoBulkProgress(FriendlyByteBuf buf) {
        this.requestId = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(requestId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleServer(this, ctx.get()));
        ctx.get().setPacketHandled(true);
    }

    public long getRequestId() {
        return requestId;
    }

    public static void handleServer(final PacketCsgoBulkProgress message, final NetworkEvent.Context context) {
        BoxOpenExecutor.handleBulkOpen(message, context);
    }
}
