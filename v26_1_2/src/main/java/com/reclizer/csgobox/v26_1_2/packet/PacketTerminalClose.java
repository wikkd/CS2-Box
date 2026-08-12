package com.reclizer.csgobox.v26_1_2.packet;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.terminal.TerminalSession;
import com.reclizer.csgobox.v26_1_2.terminal.TerminalSessionManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server on screen close: pins the locked session to the exact
 * view the player left (round, TYPING vs PENDING, cap) so the next open
 * resumes identically instead of replaying the typing window. The countdown
 * is NOT reported — it is server-authoritative (see TerminalSessionManager).
 */
public record PacketTerminalClose(
        String terminalUid,
        int round,
        boolean pending,
        long pendingAtMs,
        int cap
) implements CustomPacketPayload {

    public static final Type<PacketTerminalClose> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "terminal_close"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTerminalClose> STREAM_CODEC = StreamCodec.of(
            PacketTerminalClose::write,
            PacketTerminalClose::read
    );

    private static void write(RegistryFriendlyByteBuf buf, PacketTerminalClose packet) {
        buf.writeUtf(packet.terminalUid == null ? "" : packet.terminalUid);
        buf.writeVarInt(packet.round);
        buf.writeBoolean(packet.pending);
        buf.writeLong(packet.pendingAtMs);
        buf.writeVarInt(packet.cap);
    }

    private static PacketTerminalClose read(RegistryFriendlyByteBuf buf) {
        String terminalUid = buf.readUtf();
        if (terminalUid.isEmpty()) {
            terminalUid = null;
        }
        return new PacketTerminalClose(
                terminalUid, buf.readVarInt(), buf.readBoolean(), buf.readLong(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final PacketTerminalClose message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) {
                return;
            }
            TerminalSession session = TerminalSessionManager.getByUid(sp, message.terminalUid());
            if (session == null) {
                return;
            }
            // A close sent during the 450 ms reject burst must never rewind a
            // session the server already advanced (the rejected round replays).
            if (message.round() < session.model().round()) {
                return;
            }
            // History timestamps live on the WORLD clock (game ticks × 50),
            // same as the countdown — the client renders them against the
            // world clock, so a mixed wall-clock timestamp would hide entries.
            // cap is a client-side display preference (action-bar selector);
            // only accept values from the known set, keep the server's
            // current value otherwise — a junk value must never be persisted.
            int cap = NegotiationModel.isValidCap(message.cap()) ? message.cap() : session.model().cap();
            session.model().syncClose(message.round(), message.pending(), message.pendingAtMs(),
                    cap, sp.level().getGameTime() * 50L);
            TerminalSessionManager.markDirty();
        });
    }
}
