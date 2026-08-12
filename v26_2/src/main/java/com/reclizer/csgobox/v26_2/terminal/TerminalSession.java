package com.reclizer.csgobox.v26_2.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.v26_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_2.box.GradeGroup;
import com.reclizer.csgobox.v26_2.packet.PacketTerminalState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The terminal "lock": one server-side negotiation per (player, box type)
 * that survives closing the screen. The wrapped {@link NegotiationModel}
 * owns the round/status/chat history and is the same state machine the
 * client renders; this class only adds the authoritative item data (one
 * sampled offer + actual item per round, plus the region-10 slot item).
 *
 * <p>The lock releases itself when the negotiation finishes: a completed
 * buy ({@code CLOSED}) or five rejected rounds ({@code FAILED}) — the next
 * open then starts a fresh negotiation with newly sampled items.</p>
 */
public final class TerminalSession {

    private final String playerUuid;
    private final String uid;
    private final Identifier boxId;
    private final NegotiationModel model;
    private final Map<Integer, TerminalRoundData> rounds = new LinkedHashMap<>();
    private final ItemStack sessionItem;

    private TerminalSession(String playerUuid, String uid, Identifier boxId, NegotiationModel model,
                            Map<Integer, TerminalRoundData> rounds, ItemStack sessionItem) {
        this.playerUuid = playerUuid;
        this.uid = uid;
        this.boxId = boxId;
        this.model = model;
        this.rounds.putAll(rounds);
        this.sessionItem = sessionItem;
    }

    /**
     * Sample a fresh negotiation for the box: rounds 1..5 + the slot item.
     * {@code nowMs} is the WORLD clock (game ticks × 50) — the countdown
     * deadline lives on the world clock so it expires only while the world
     * runs (see {@link NegotiationModel#start(long)}).
     */
    public static TerminalSession create(String playerUuid, String uid, Identifier boxId, BoxDefinition def,
                                         long nowMs) {
        Random rnd = new Random();
        Map<Integer, TerminalRoundData> sampled = new LinkedHashMap<>();
        for (int r = 1; r <= NegotiationModel.MAX_ROUNDS; r++) {
            int skinIdx = NegotiationModel.ROUND_SKIN[r - 1];
            NegotiationModel.Offer offer = new NegotiationModel.Offer(
                    r, skinIdx, NegotiationModel.SKIN_WEAR_VAL[skinIdx],
                    rnd.nextInt(5),                    // style 0..4 (style.* keys)
                    1000 + rnd.nextInt(900),           // serial no
                    rnd.nextInt(1000),                 // pattern
                    r == NegotiationModel.MAX_ROUNDS);
            Sample sample = sampleItem(def, 1 + rnd.nextInt(5), rnd);
            sampled.put(r, new TerminalRoundData(r, offer, sample.item(), sample.grade()));
        }
        ItemStack slotItem = sampleSessionItem(def, rnd);

        NegotiationModel model = new NegotiationModel();
        model.setOfferSource(r -> {
            TerminalRoundData rd = sampled.get(r);
            return rd == null ? null : rd.offer();
        });
        model.start(nowMs);
        return new TerminalSession(playerUuid, uid, boxId, model, sampled, slotItem);
    }

    /**
     * Rebuild a session from a persisted state snapshot (see
     * {@link TerminalStateStore}); the owner may be offline. The box
     * definition is only needed for sampling, so a restored session reuses the
     * saved offers/items and resumes its countdown where it left off.
     */
    public static TerminalSession fromState(String playerUuid, PacketTerminalState state, long nowMs) {
        Identifier boxId = Identifier.parse(state.boxId());
        Map<Integer, TerminalRoundData> restored = new LinkedHashMap<>();
        for (PacketTerminalState.RoundItem ri : state.rounds()) {
            restored.put(ri.round(), new TerminalRoundData(ri.round(), ri.offer(), ri.item(), ri.grade()));
        }
        NegotiationModel model = new NegotiationModel();
        model.setOfferSource(r -> {
            TerminalRoundData rd = restored.get(r);
            return rd == null ? null : rd.offer();
        });
        NegotiationModel.Status status = NegotiationModel.Status.values()[
                Math.max(0, Math.min(state.status(), NegotiationModel.Status.values().length - 1))];
        model.restore(new NegotiationModel.Snapshot(
                state.round(), status, state.generation(), state.cap(),
                state.countdownDeadlineMs(), state.pending(), state.history()), nowMs);
        return new TerminalSession(playerUuid, state.terminalUid(), boxId, model, restored, state.sessionItem());
    }

    public String playerUuid() {
        return playerUuid;
    }

    /** Unique id of the terminal item this negotiation is locked to. */
    public String uid() {
        return uid;
    }

    public Identifier boxId() {
        return boxId;
    }

    public NegotiationModel model() {
        return model;
    }

    public Map<Integer, TerminalRoundData> rounds() {
        return rounds;
    }

    public ItemStack sessionItem() {
        return sessionItem;
    }

    /** CLOSED (bought) or FAILED (five rejects) — the lock is released. */
    public boolean isFinished() {
        NegotiationModel.Status s = model.status();
        return s == NegotiationModel.Status.CLOSED || s == NegotiationModel.Status.FAILED;
    }

    // ---- sampling (mirrors the old client-side TerminalOfferItems logic) ----

    /** One sample from the grade pool, falling back down the tiers; iron sword if empty. */
    private static Sample sampleItem(BoxDefinition def, int baseGrade, Random rnd) {
        for (int g = baseGrade; g >= 1; g--) {
            List<ItemStack> pool = poolFor(def, g);
            if (pool != null && !pool.isEmpty()) {
                return new Sample(pool.get(rnd.nextInt(pool.size())).copy(), g);
            }
        }
        return new Sample(new ItemStack(Items.IRON_SWORD), 1);
    }

    /** One fixed random item across all tiers for the region-10 slot; diamond if empty. */
    private static ItemStack sampleSessionItem(BoxDefinition def, Random rnd) {
        List<ItemStack> all = new ArrayList<>();
        for (GradeGroup grade : def.grades()) {
            if (BoxDefinition.gradeLevel(grade.id()) > 0) {
                all.addAll(grade.items());
            }
        }
        return all.isEmpty()
                ? new ItemStack(Items.DIAMOND)
                : all.get(rnd.nextInt(all.size())).copy();
    }

    private static List<ItemStack> poolFor(BoxDefinition def, int gradeLevel) {
        for (GradeGroup grade : def.grades()) {
            if (BoxDefinition.gradeLevel(grade.id()) == gradeLevel) {
                return grade.items();
            }
        }
        return null;
    }

    private record Sample(ItemStack item, int grade) {
    }
}
