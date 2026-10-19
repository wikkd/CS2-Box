package com.reclizer.csgobox.forge_26_1_2.packet;

import com.reclizer.csgobox.forge_26_1_2.CsgoBox;
import com.reclizer.csgobox.forge_26_1_2.service.OpenService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Client-to-server request to bulk-open every matching box in the player's
 * inventory. The server re-counts the actual box+key availability and may
 * open fewer boxes than the client estimates. Transport only — the
 * validation/async-compute/finalize flow lives in {@link OpenService}.
 */
public record PacketCsgoBulkProgress(long requestId) implements CustomPacketPayload {

    public static final Type<PacketCsgoBulkProgress> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "csgo_bulk_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketCsgoBulkProgress> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeLong(packet.requestId),
            buf -> new PacketCsgoBulkProgress(buf.readLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final PacketCsgoBulkProgress message, final CustomPayloadEvent.Context context) {
        OpenService.handleBulkOpen(message, context);
    }
}
