package com.reclizer.csgobox.packet;

import com.reclizer.csgobox.CsgoBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PacketCsgoProgress(long seed) implements CustomPacketPayload {

    public static final Type<PacketCsgoProgress> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "csgo_progress"));

    public static final StreamCodec<FriendlyByteBuf, PacketCsgoProgress> STREAM_CODEC = StreamCodec.of(
            PacketCsgoProgress::write,
            PacketCsgoProgress::read
    );

    private static void write(FriendlyByteBuf buf, PacketCsgoProgress packet) {
        buf.writeLong(packet.seed);
    }

    private static PacketCsgoProgress read(FriendlyByteBuf buf) {
        return new PacketCsgoProgress(buf.readLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final PacketCsgoProgress message, final IPayloadContext context) {
    }
}
