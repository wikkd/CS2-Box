package com.reclizer.csgobox.v26_2.packet;

import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.v26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_2.item.ItemTerminal;
import com.reclizer.csgobox.v26_2.terminal.TerminalSession;
import com.reclizer.csgobox.v26_2.terminal.TerminalSessionManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server: the player rejected the current offer. Fire-and-forget —
 * the server commits the advance (next round, or FAILED on round 5) into the
 * locked session; the client's busy animation runs locally and needs no reply.
 */
public record PacketTerminalReject(int round) implements CustomPacketPayload {

    public static final Type<PacketTerminalReject> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "terminal_reject"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTerminalReject> STREAM_CODEC = StreamCodec.of(
            PacketTerminalReject::write,
            PacketTerminalReject::read
    );

    private static void write(RegistryFriendlyByteBuf buf, PacketTerminalReject packet) {
        buf.writeVarInt(packet.round);
    }

    private static PacketTerminalReject read(RegistryFriendlyByteBuf buf) {
        return new PacketTerminalReject(buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final PacketTerminalReject message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) {
                return;
            }
            ItemStack held = sp.getMainHandItem();
            if (!(held.getItem() instanceof ItemTerminal)) {
                return;
            }
            TerminalSession session = TerminalSessionManager.getByUid(sp, ItemCsgoBox.getTerminalUid(held));
            if (session == null || session.isFinished()
                    || session.model().round() != message.round()) {
                return;
            }
            // World clock (game ticks × 50): history timestamps must match the
            // client's render clock, otherwise reopened cards stay invisible.
            session.model().rejectForced(sp.level().getGameTime() * 50L);
            TerminalSessionManager.markDirty();
        });
    }
}
