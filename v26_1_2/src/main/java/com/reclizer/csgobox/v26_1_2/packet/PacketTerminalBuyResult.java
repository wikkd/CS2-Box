package com.reclizer.csgobox.v26_1_2.packet;

import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.gui.TerminalScreen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server reply to {@link PacketTerminalBuy}: SUCCESS grants the item (wear
 * applied, grade stamped), INSUFFICIENT means the player lacks Armory Points
 * and INVALID means the terminal/offer failed server validation — both take
 * the "think it over" path on the client.
 */
public record PacketTerminalBuyResult(
        long requestId,
        int result,
        ItemStack givenItem,
        int grade
) implements CustomPacketPayload {

    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_INSUFFICIENT = 1;
    public static final int RESULT_INVALID = 2;

    public static final Type<PacketTerminalBuyResult> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "terminal_buy_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTerminalBuyResult> STREAM_CODEC = StreamCodec.of(
            PacketTerminalBuyResult::write,
            PacketTerminalBuyResult::read
    );

    private static void write(RegistryFriendlyByteBuf buf, PacketTerminalBuyResult packet) {
        buf.writeLong(packet.requestId);
        buf.writeVarInt(packet.result);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.givenItem);
        buf.writeVarInt(packet.grade);
    }

    private static PacketTerminalBuyResult read(RegistryFriendlyByteBuf buf) {
        long requestId = buf.readLong();
        int result = buf.readVarInt();
        ItemStack givenItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        int grade = buf.readVarInt();
        return new PacketTerminalBuyResult(requestId, result, givenItem, grade);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final PacketTerminalBuyResult message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            TerminalScreen ts = TerminalScreen.getOpen();
            if (ts != null) {
                ts.onBuyResult(message.requestId(), message.result(), message.givenItem());
            }
        });
    }
}
