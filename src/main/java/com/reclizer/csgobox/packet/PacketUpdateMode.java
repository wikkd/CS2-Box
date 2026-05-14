package com.reclizer.csgobox.packet;

import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.capability.ModCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PacketUpdateMode(int mode) implements CustomPacketPayload {

    public static final Type<PacketUpdateMode> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "update_mode"));

    public static final StreamCodec<FriendlyByteBuf, PacketUpdateMode> STREAM_CODEC = StreamCodec.of(
            PacketUpdateMode::write,
            PacketUpdateMode::read
    );

    private static void write(FriendlyByteBuf buf, PacketUpdateMode packet) {
        buf.writeInt(packet.mode);
    }

    private static PacketUpdateMode read(FriendlyByteBuf buf) {
        return new PacketUpdateMode(buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final PacketUpdateMode message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player != null) {
                var data = player.getData(ModCapability.PLAYER_DATA.get());
                player.setData(ModCapability.PLAYER_DATA.get(), data.withMode(message.mode));
            }
        });
    }
}