package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.terminal.TerminalSession;
import com.reclizer.csgobox.forge_1_20_1.terminal.TerminalSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketTerminalClose {

    private final String terminalUid;
    private final int round;
    private final boolean pending;
    private final long pendingAtMs;
    private final int cap;

    public PacketTerminalClose(String terminalUid, int round, boolean pending, long pendingAtMs, int cap) {
        this.terminalUid = terminalUid;
        this.round = round;
        this.pending = pending;
        this.pendingAtMs = pendingAtMs;
        this.cap = cap;
    }

    public PacketTerminalClose(FriendlyByteBuf buf) {
        String uid = buf.readUtf();
        this.terminalUid = uid.isEmpty() ? null : uid;
        this.round = buf.readVarInt();
        this.pending = buf.readBoolean();
        this.pendingAtMs = buf.readLong();
        this.cap = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(terminalUid == null ? "" : terminalUid);
        buf.writeVarInt(round);
        buf.writeBoolean(pending);
        buf.writeLong(pendingAtMs);
        buf.writeVarInt(cap);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleServer(this, ctx.get()));
        ctx.get().setPacketHandled(true);
    }

    public static void handleServer(final PacketTerminalClose message, final NetworkEvent.Context context) {
        ServerPlayer sp = context.getSender();
        if (sp == null) {
            return;
        }
        TerminalSession session = TerminalSessionManager.getByUid(sp, message.terminalUid);
        if (session == null) {
            return;
        }
        if (!TerminalSessionManager.isOpenBinding(sp.getStringUUID(), message.terminalUid)) {
            return;
        }
        if (message.round != session.model().round()) {
            return;
        }
        int cap = NegotiationModel.isValidCap(message.cap) ? message.cap : session.model().cap();
        session.model().syncClose(message.round, message.pending, message.pendingAtMs,
                cap, sp.level().getGameTime() * 50L);
        TerminalSessionManager.markDirty();
        TerminalSessionManager.clearOpenIf(sp.getStringUUID(), message.terminalUid);
    }
}
