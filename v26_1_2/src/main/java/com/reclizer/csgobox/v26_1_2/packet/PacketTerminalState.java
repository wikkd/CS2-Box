package com.reclizer.csgobox.v26_1_2.packet;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.gui.TerminalScreen;
import com.reclizer.csgobox.v26_1_2.terminal.TerminalRoundData;
import com.reclizer.csgobox.v26_1_2.terminal.TerminalSession;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server reply to {@link PacketTerminalOpen}: the full locked session —
 * negotiation round/status/history/countdown/cap, the pending offer, one
 * sampled item per round and the region-10 slot item. The client rebuilds
 * its {@link NegotiationModel} from this snapshot so a reopen is identical
 * to the last close until the lock releases (buy or five rejects).
 */
public record PacketTerminalState(
        String boxId,
        String terminalUid,
        long requestId,
        int round,
        int status,
        long generation,
        int cap,
        long countdownDeadlineMs,
        NegotiationModel.Offer pending,
        List<Object> history,
        List<RoundItem> rounds,
        ItemStack sessionItem
) implements CustomPacketPayload {

    /** One server-sampled round: script offer + actual item + box grade + terminal price. */
    public record RoundItem(int round, NegotiationModel.Offer offer, ItemStack item, int grade, int price) {
    }

    public static final Type<PacketTerminalState> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "terminal_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTerminalState> STREAM_CODEC = StreamCodec.of(
            PacketTerminalState::write,
            PacketTerminalState::read
    );

    public static PacketTerminalState fromSession(TerminalSession session, long requestId) {
        List<RoundItem> rounds = new ArrayList<>();
        for (TerminalRoundData rd : session.rounds().values()) {
            rounds.add(new RoundItem(rd.round(), rd.offer(), rd.item(), rd.grade(), rd.price()));
        }
        NegotiationModel.Snapshot snap = session.model().snapshot();
        return new PacketTerminalState(
                session.boxId().toString(), session.uid(), requestId,
                snap.round(), snap.status().ordinal(), snap.generation(), snap.cap(),
                snap.countdownDeadlineMs(), snap.pending(), snap.history(), rounds, session.sessionItem());
    }

    /** State for a terminal that self-destructed on timeout: FAILED + a destroyed notice. */
    public static PacketTerminalState destroyed(Identifier boxId, String terminalUid, long requestId) {
        long now = System.currentTimeMillis();
        List<Object> history = new ArrayList<>();
        history.add(new NegotiationModel.SystemEntry("csgobox.terminal.sys.destroyed", true, now));
        return new PacketTerminalState(boxId.toString(), terminalUid, requestId,
                1, NegotiationModel.Status.FAILED.ordinal(),
                0, NegotiationModel.CAP_UNLIMITED, 0L, null, history, List.of(), ItemStack.EMPTY);
    }

    /** State for a terminal whose box definition is missing: FAILED + an empty notice. */
    public static PacketTerminalState empty(Identifier boxId, String terminalUid, long requestId) {
        long now = System.currentTimeMillis();
        List<Object> history = new ArrayList<>();
        history.add(new NegotiationModel.SystemEntry("csgobox.terminal.sys.empty", true, now));
        return new PacketTerminalState(boxId == null ? "" : boxId.toString(), terminalUid, requestId,
                1, NegotiationModel.Status.FAILED.ordinal(),
                0, NegotiationModel.CAP_UNLIMITED, 0L, null, history, List.of(), ItemStack.EMPTY);
    }

    /**
     * State for a terminal locked to another player's live negotiation:
     * FAILED + a locked notice where the dealer points at the owner
     * ("去问问xxx吧", owner name travels as a translatable arg).
     */
    public static PacketTerminalState locked(Identifier boxId, String terminalUid, long requestId,
                                             String ownerName) {
        long now = System.currentTimeMillis();
        List<Object> history = new ArrayList<>();
        history.add(new NegotiationModel.SystemEntry("csgobox.terminal.sys.locked", true, now,
                new String[]{ownerName == null ? "" : ownerName}));
        return new PacketTerminalState(boxId == null ? "" : boxId.toString(), terminalUid, requestId,
                1, NegotiationModel.Status.FAILED.ordinal(),
                0, NegotiationModel.CAP_UNLIMITED, 0L, null, history, List.of(), ItemStack.EMPTY);
    }

    /** State for an open that never reached a session (held item changed
     * mid-open, death, or a server error): FAILED + a retry notice instead of
     * leaving the screen hanging forever waiting for a reply. */
    public static PacketTerminalState unreachable(Identifier boxId, String terminalUid, long requestId) {
        long now = System.currentTimeMillis();
        List<Object> history = new ArrayList<>();
        history.add(new NegotiationModel.SystemEntry("csgobox.terminal.sys.unreachable", true, now));
        return new PacketTerminalState(boxId == null ? "" : boxId.toString(), terminalUid, requestId,
                1, NegotiationModel.Status.FAILED.ordinal(),
                0, NegotiationModel.CAP_UNLIMITED, 0L, null, history, List.of(), ItemStack.EMPTY);
    }

    private static void write(RegistryFriendlyByteBuf buf, PacketTerminalState packet) {
        buf.writeUtf(packet.boxId);
        buf.writeUtf(packet.terminalUid == null ? "" : packet.terminalUid);
        buf.writeLong(packet.requestId);
        buf.writeVarInt(packet.round);
        buf.writeVarInt(packet.status);
        buf.writeLong(packet.generation);
        buf.writeVarInt(packet.cap);
        buf.writeLong(packet.countdownDeadlineMs);
        if (packet.pending == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            writeOffer(buf, packet.pending);
        }
        writeHistory(buf, packet.history);
        buf.writeVarInt(packet.rounds.size());
        for (RoundItem ri : packet.rounds) {
            buf.writeVarInt(ri.round());
            writeOffer(buf, ri.offer());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, ri.item());
            buf.writeVarInt(ri.grade());
            buf.writeVarInt(ri.price());
        }
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.sessionItem);
    }

    private static PacketTerminalState read(RegistryFriendlyByteBuf buf) {
        String boxId = buf.readUtf();
        String terminalUid = buf.readUtf();
        if (terminalUid.isEmpty()) {
            terminalUid = null;
        }
        long requestId = buf.readLong();
        int round = buf.readVarInt();
        int status = buf.readVarInt();
        long generation = buf.readLong();
        int cap = buf.readVarInt();
        long countdownDeadlineMs = buf.readLong();
        NegotiationModel.Offer pending = buf.readBoolean() ? readOffer(buf) : null;
        List<Object> history = readHistory(buf);
        int roundCount = buf.readVarInt();
        List<RoundItem> rounds = new ArrayList<>(roundCount);
        for (int i = 0; i < roundCount; i++) {
            int r = buf.readVarInt();
            NegotiationModel.Offer offer = readOffer(buf);
            ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            int grade = buf.readVarInt();
            int price = buf.readVarInt();
            rounds.add(new RoundItem(r, offer, item, grade, price));
        }
        ItemStack sessionItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        return new PacketTerminalState(boxId, terminalUid, requestId, round, status, generation, cap, countdownDeadlineMs,
                pending, history, rounds, sessionItem);
    }

    private static void writeOffer(RegistryFriendlyByteBuf buf, NegotiationModel.Offer offer) {
        buf.writeVarInt(offer.round());
        buf.writeVarInt(offer.skinIdx());
        buf.writeFloat(offer.wearVal());
        buf.writeVarInt(offer.style());
        buf.writeVarInt(offer.no());
        buf.writeVarInt(offer.pattern());
        buf.writeBoolean(offer.finalRound());
    }

    private static NegotiationModel.Offer readOffer(RegistryFriendlyByteBuf buf) {
        return new NegotiationModel.Offer(
                buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    private static void writeHistory(RegistryFriendlyByteBuf buf, List<Object> history) {
        buf.writeVarInt(history.size());
        for (Object entry : history) {
            if (entry instanceof NegotiationModel.LineEntry le) {
                buf.writeByte(0);
                buf.writeVarInt(le.round());
                buf.writeUtf(le.textKey());
                buf.writeLong(le.atMs());
            } else if (entry instanceof NegotiationModel.OfferEntry oe) {
                buf.writeByte(1);
                writeOffer(buf, oe.offer());
                buf.writeLong(oe.atMs());
                buf.writeVarInt(oe.status());
            } else if (entry instanceof NegotiationModel.SystemEntry se) {
                buf.writeByte(2);
                buf.writeUtf(se.textKey());
                buf.writeBoolean(se.failed());
                buf.writeLong(se.atMs());
                String[] args = se.args();
                buf.writeVarInt(args == null ? 0 : args.length);
                if (args != null) {
                    for (String arg : args) {
                        buf.writeUtf(arg == null ? "" : arg);
                    }
                }
            } else {
                // Unknown tag — never produced by the server; skip on read.
                buf.writeByte(127);
            }
        }
    }

    private static List<Object> readHistory(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Object> history = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            switch (buf.readByte()) {
                case 0 -> history.add(new NegotiationModel.LineEntry(
                        buf.readVarInt(), buf.readUtf(), buf.readLong()));
                case 1 -> history.add(new NegotiationModel.OfferEntry(
                        readOffer(buf), buf.readLong(), buf.readVarInt()));
                case 2 -> history.add(new NegotiationModel.SystemEntry(
                        buf.readUtf(), buf.readBoolean(), buf.readLong(), readArgs(buf)));
                default -> {
                    // skipped unknown tag (see writeHistory)
                }
            }
        }
        return history;
    }

    private static String[] readArgs(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count <= 0) {
            return null;
        }
        String[] args = new String[count];
        for (int i = 0; i < count; i++) {
            args[i] = buf.readUtf();
        }
        return args;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final PacketTerminalState message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            TerminalScreen ts = TerminalScreen.getOpen();
            if (ts != null) {
                ts.onTerminalState(message);
            }
        });
    }
}
