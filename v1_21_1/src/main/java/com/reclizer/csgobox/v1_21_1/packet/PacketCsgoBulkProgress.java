package com.reclizer.csgobox.v1_21_1.packet;

import com.reclizer.csgobox.v1_21_1.CsgoBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server request to bulk-open every matching box in the player's
 * inventory. The server re-counts the actual box+key availability and may
 * open fewer boxes than the client estimates.
 *
 * <p>The orchestration lives in {@link BoxOpenExecutor#handleBulkOpen} so the
 * packet stays a thin transport shell.</p>
 */
public record PacketCsgoBulkProgress(long requestId) implements CustomPacketPayload {

    public static final Type<PacketCsgoBulkProgress> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "csgo_bulk_progress"));

    public static final StreamCodec<FriendlyByteBuf, PacketCsgoBulkProgress> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeLong(packet.requestId),
            buf -> new PacketCsgoBulkProgress(buf.readLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final PacketCsgoBulkProgress message, final IPayloadContext context) {
        BoxOpenExecutor.handleBulkOpen(message, context);
    }
}
