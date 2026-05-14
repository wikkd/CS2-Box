package com.reclizer.csgobox.packet;

import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.capability.ModCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PacketCsgoProgress(int buttonID, String item) implements CustomPacketPayload {

    public static final Type<PacketCsgoProgress> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "csgo_progress"));

    public static final StreamCodec<FriendlyByteBuf, PacketCsgoProgress> STREAM_CODEC = StreamCodec.of(
            PacketCsgoProgress::write,
            PacketCsgoProgress::read
    );

    private static void write(FriendlyByteBuf buf, PacketCsgoProgress packet) {
        buf.writeInt(packet.buttonID);
        buf.writeUtf(packet.item);
    }

    private static PacketCsgoProgress read(FriendlyByteBuf buf) {
        return new PacketCsgoProgress(buf.readInt(), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final PacketCsgoProgress message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (message.buttonID == 2) {
                if (player.getMainHandItem().getItem() instanceof com.reclizer.csgobox.item.ItemCsgoBox) {
                    player.getMainHandItem().shrink(1);
                    for (var stack : player.getInventory().items) {
                        if (java.util.Objects.requireNonNull(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())).toString().equals(message.item)) {
                            stack.shrink(1);
                        }
                    }
                }
            }
        });
    }
}