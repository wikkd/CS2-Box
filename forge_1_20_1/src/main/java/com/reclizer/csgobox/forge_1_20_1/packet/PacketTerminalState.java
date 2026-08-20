package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.terminal.TerminalRoundData;
import com.reclizer.csgobox.forge_1_20_1.terminal.TerminalSession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketTerminalState {

    private final String boxId;
    private final String terminalUid;
    private final long requestId;
    private final int round;
    private final int status;
    private final long generation;
    private final int cap;
    private final long countdownDeadlineMs;
    private final NegotiationModel.Offer pending;
    private final List<Object> history;
    private final List<RoundItem> rounds;
    private final ItemStack sessionItem;

    public static class RoundItem {
        private final int round;
        private final NegotiationModel.Offer offer;
        private final ItemStack item;
        private final int grade;
        private final int price;

        public RoundItem(int round, NegotiationModel.Offer offer, ItemStack item, int grade, int price) {
            this.round = round;
            this.offer = offer;
            this.item = item == null ? ItemStack.EMPTY : item.copy();
            this.grade = grade;
            this.price = price;
        }

        public int round() { return round; }
        public NegotiationModel.Offer offer() { return offer; }
        public ItemStack item() { return item; }
        public int grade() { return grade; }
        public int price() { return price; }
    }

    public PacketTerminalState(String boxId, String terminalUid, long requestId,
                               int round, int status, long generation, int cap,
                               long countdownDeadlineMs, NegotiationModel.Offer pending,
                               List<Object> history, List<RoundItem> rounds, ItemStack sessionItem) {
        this.boxId = boxId == null ? "" : boxId;
        this.terminalUid = terminalUid;
        this.requestId = requestId;
        this.round = round;
        this.status = status;
        this.generation = generation;
        this.cap = cap;
        this.countdownDeadlineMs = countdownDeadlineMs;
        this.pending = pending;
        this.history = history == null ? List.of() : history;
        this.rounds = rounds == null ? List.of() : rounds;
        this.sessionItem = sessionItem == null ? ItemStack.EMPTY : sessionItem.copy();
    }

    public PacketTerminalState(FriendlyByteBuf buf) {
        this.boxId = buf.readUtf();
        String uid = buf.readUtf();
        this.terminalUid = uid.isEmpty() ? null : uid;
        this.requestId = buf.readLong();
        this.round = buf.readVarInt();
        this.status = buf.readVarInt();
        this.generation = buf.readLong();
        this.cap = buf.readVarInt();
        this.countdownDeadlineMs = buf.readLong();
        this.pending = buf.readBoolean() ? readOffer(buf) : null;
        this.history = readHistory(buf);
        int roundCount = buf.readVarInt();
        List<RoundItem> r = new ArrayList<>(roundCount);
        for (int i = 0; i < roundCount; i++) {
            int ri = buf.readVarInt();
            NegotiationModel.Offer offer = readOffer(buf);
            CompoundTag tag = buf.readNbt();
            ItemStack item = tag == null ? ItemStack.EMPTY : ItemStack.of(tag);
            int grade = buf.readVarInt();
            int price = buf.readVarInt();
            r.add(new RoundItem(ri, offer, item, grade, price));
        }
        this.rounds = r;
        CompoundTag siTag = buf.readNbt();
        this.sessionItem = siTag == null ? ItemStack.EMPTY : ItemStack.of(siTag);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(boxId);
        buf.writeUtf(terminalUid == null ? "" : terminalUid);
        buf.writeLong(requestId);
        buf.writeVarInt(round);
        buf.writeVarInt(status);
        buf.writeLong(generation);
        buf.writeVarInt(cap);
        buf.writeLong(countdownDeadlineMs);
        if (pending == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            writeOffer(buf, pending);
        }
        writeHistory(buf, history);
        buf.writeVarInt(rounds.size());
        for (RoundItem ri : rounds) {
            buf.writeVarInt(ri.round());
            writeOffer(buf, ri.offer());
            buf.writeNbt(ri.item().save(new CompoundTag()));
            buf.writeVarInt(ri.grade());
            buf.writeVarInt(ri.price());
        }
        buf.writeNbt(sessionItem.save(new CompoundTag()));
    }

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

    public static PacketTerminalState destroyed(ResourceLocation boxId, String terminalUid, long requestId) {
        long now = System.currentTimeMillis();
        List<Object> history = new ArrayList<>();
        history.add(new NegotiationModel.SystemEntry("csgobox.terminal.sys.destroyed", true, now));
        return new PacketTerminalState(boxId.toString(), terminalUid, requestId,
                1, NegotiationModel.Status.FAILED.ordinal(),
                0, NegotiationModel.CAP_UNLIMITED, 0L, null, history, List.of(), ItemStack.EMPTY);
    }

    public static PacketTerminalState empty(ResourceLocation boxId, String terminalUid, long requestId) {
        long now = System.currentTimeMillis();
        List<Object> history = new ArrayList<>();
        history.add(new NegotiationModel.SystemEntry("csgobox.terminal.sys.empty", true, now));
        return new PacketTerminalState(boxId == null ? "" : boxId.toString(), terminalUid, requestId,
                1, NegotiationModel.Status.FAILED.ordinal(),
                0, NegotiationModel.CAP_UNLIMITED, 0L, null, history, List.of(), ItemStack.EMPTY);
    }

    public static PacketTerminalState locked(ResourceLocation boxId, String terminalUid, long requestId,
                                             String ownerName) {
        long now = System.currentTimeMillis();
        List<Object> history = new ArrayList<>();
        history.add(new NegotiationModel.SystemEntry("csgobox.terminal.sys.locked", true, now,
                new String[]{ownerName == null ? "" : ownerName}));
        return new PacketTerminalState(boxId == null ? "" : boxId.toString(), terminalUid, requestId,
                1, NegotiationModel.Status.FAILED.ordinal(),
                0, NegotiationModel.CAP_UNLIMITED, 0L, null, history, List.of(), ItemStack.EMPTY);
    }

    public static PacketTerminalState unreachable(ResourceLocation boxId, String terminalUid, long requestId) {
        long now = System.currentTimeMillis();
        List<Object> history = new ArrayList<>();
        history.add(new NegotiationModel.SystemEntry("csgobox.terminal.sys.unreachable", true, now));
        return new PacketTerminalState(boxId == null ? "" : boxId.toString(), terminalUid, requestId,
                1, NegotiationModel.Status.FAILED.ordinal(),
                0, NegotiationModel.CAP_UNLIMITED, 0L, null, history, List.of(), ItemStack.EMPTY);
    }

    private static void writeOffer(FriendlyByteBuf buf, NegotiationModel.Offer offer) {
        buf.writeVarInt(offer.round());
        buf.writeVarInt(offer.skinIdx());
        buf.writeFloat(offer.wearVal());
        buf.writeVarInt(offer.style());
        buf.writeVarInt(offer.no());
        buf.writeVarInt(offer.pattern());
        buf.writeBoolean(offer.finalRound());
    }

    private static NegotiationModel.Offer readOffer(FriendlyByteBuf buf) {
        return new NegotiationModel.Offer(
                buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    private static void writeHistory(FriendlyByteBuf buf, List<Object> history) {
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
                buf.writeByte(127);
            }
        }
    }

    private static List<Object> readHistory(FriendlyByteBuf buf) {
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
                default -> { }
            }
        }
        return history;
    }

    private static String[] readArgs(FriendlyByteBuf buf) {
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

    // Accessors
    public String boxId() { return boxId; }
    public String terminalUid() { return terminalUid; }
    public long requestId() { return requestId; }
    public int round() { return round; }
    public int status() { return status; }
    public long generation() { return generation; }
    public int cap() { return cap; }
    public long countdownDeadlineMs() { return countdownDeadlineMs; }
    public NegotiationModel.Offer pending() { return pending; }
    public List<Object> history() { return history; }
    public List<RoundItem> rounds() { return rounds; }
    public ItemStack sessionItem() { return sessionItem; }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.reclizer.csgobox.forge_1_20_1.gui.TerminalScreen ts =
                        com.reclizer.csgobox.forge_1_20_1.gui.TerminalScreen.getOpen();
                if (ts != null) {
                    ts.onTerminalState(PacketTerminalState.this);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
