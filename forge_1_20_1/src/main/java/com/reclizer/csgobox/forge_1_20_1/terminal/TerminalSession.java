package com.reclizer.csgobox.forge_1_20_1.terminal;

import java.security.SecureRandom;
import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.PriceRange;
import com.reclizer.csgobox.logic.OddsCalculator;
import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.terminal.TerminalStockManager;
import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.GradeGroup;
import com.reclizer.csgobox.forge_1_20_1.packet.PacketTerminalState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class TerminalSession {

    private final String playerUuid;
    private final String uid;
    private final ResourceLocation boxId;
    private final NegotiationModel model;
    private final Map<Integer, TerminalRoundData> rounds = new LinkedHashMap<>();
    private final ItemStack sessionItem;

    private TerminalSession(String playerUuid, String uid, ResourceLocation boxId, NegotiationModel model,
                            Map<Integer, TerminalRoundData> rounds, ItemStack sessionItem) {
        this.playerUuid = playerUuid;
        this.uid = uid;
        this.boxId = boxId;
        this.model = model;
        this.rounds.putAll(rounds);
        this.sessionItem = sessionItem;
    }

    /** v2.0.1-fix: server-authoritative CSPRNG for session seed (was the predictable default Random). */
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    public static TerminalSession create(String playerUuid, String uid, ResourceLocation boxId, BoxDefinition def,
                                         long nowMs) {
        // v2.0.1: a stock-limited terminal that is sold out offers nothing —
        // the screen shows an empty (unconfigured-like) state and the buy
        // handler keeps refusing (stock is in-memory and resets on restart).
        if (!TerminalStockManager.available(boxId.toString(), def.stock())) {
            return null;
        }
        // v2.0.1: no grade default price fallback anymore — only items with
        // an entry in _prices.json are sellable. A box without any priced
        // item is treated like an unconfigured terminal (empty state).
        if (!hasPricedItems(def)) {
            return null;
        }
        Random rnd = new Random(SECURE_RANDOM.nextLong());
        Map<Integer, TerminalRoundData> sampled = new LinkedHashMap<>();
        for (int r = 1; r <= NegotiationModel.MAX_ROUNDS; r++) {
            int skinIdx = NegotiationModel.ROUND_SKIN[r - 1];
            NegotiationModel.Offer offer = new NegotiationModel.Offer(
                    r, skinIdx, rnd.nextFloat(),
                    rnd.nextInt(5),
                    1000 + rnd.nextInt(900),
                    rnd.nextInt(1000),
                    r == NegotiationModel.MAX_ROUNDS);
            Sample sample = sampleItem(def, 1 + rnd.nextInt(5), rnd);
            if (sample == null) {
                continue; // defensive: create's pre-check guarantees one priced item
            }
            sampled.put(r, new TerminalRoundData(r, offer, sample.item(), sample.grade(), sample.price()));
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

    /** True when at least one item in any grade has a price-table entry. */
    private static boolean hasPricedItems(BoxDefinition def) {
        for (GradeGroup grade : def.grades()) {
            for (PriceRange r : grade.prices()) {
                if (r != null && !r.isUnpriced()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static TerminalSession fromState(String playerUuid, PacketTerminalState state, long nowMs) {
        ResourceLocation boxId = new ResourceLocation(state.boxId());
        Map<Integer, TerminalRoundData> restored = new LinkedHashMap<>();
        for (PacketTerminalState.RoundItem ri : state.rounds()) {
            if (ri.round() >= 1 && ri.round() <= NegotiationModel.MAX_ROUNDS) {
                restored.put(ri.round(), new TerminalRoundData(ri.round(), ri.offer(), ri.item(), ri.grade(), ri.price()));
            }
        }
        NegotiationModel model = new NegotiationModel();
        model.setOfferSource(r -> {
            TerminalRoundData rd = restored.get(r);
            return rd == null ? null : rd.offer();
        });
        NegotiationModel.Status status = NegotiationModel.Status.values()[
                Math.max(0, Math.min(state.status(), NegotiationModel.Status.values().length - 1))];
        int round = Math.max(1, Math.min(state.round(), NegotiationModel.MAX_ROUNDS));
        model.restore(new NegotiationModel.Snapshot(
                round, status, state.generation(), state.cap(),
                state.countdownDeadlineMs(), state.pending(), state.history()), nowMs);
        return new TerminalSession(playerUuid, state.terminalUid(), boxId, model, restored, state.sessionItem());
    }

    public String playerUuid() { return playerUuid; }
    public String uid() { return uid; }
    public ResourceLocation boxId() { return boxId; }
    public NegotiationModel model() { return model; }
    public Map<Integer, TerminalRoundData> rounds() { return rounds; }
    public ItemStack sessionItem() { return sessionItem; }

    public boolean isFinished() {
        NegotiationModel.Status s = model.status();
        return s == NegotiationModel.Status.CLOSED || s == NegotiationModel.Status.FAILED;
    }

    /** One sample from the priced items of the grade pool, falling back down
     *  the tiers. v2.0.1: intra-grade weights are honoured and only items
     *  with an {@code _prices.json} entry are offered (no grade-default
     *  fallback); a table range {@code [min, max]} is sampled once per offer.
     *  Returns null only if no priced item exists anywhere (create's
     *  pre-check prevents that in practice). */
    private static Sample sampleItem(BoxDefinition def, int baseGrade, Random rnd) {
        for (int g = baseGrade; g >= 1; g--) {
            GradeGroup gradeGroup = findGrade(def, g);
            if (gradeGroup == null || gradeGroup.items().isEmpty()) {
                continue;
            }
            int idx = pickWeightedPricedIndex(gradeGroup, rnd);
            if (idx < 0) {
                continue; // this tier holds no priced item — fall to a lower tier
            }
            PriceRange range = gradeGroup.priceForIndex(idx);
            int price = range.sample(rnd::nextInt);
            price = def.discountedPrice(price);
            return new Sample(gradeGroup.items().get(idx).copy(), g, price);
        }
        return null;
    }

    /** Weighted pick restricted to priced items (uniform when every weight
     *  is 1). Returns -1 when the tier has no priced item at all. */
    private static int pickWeightedPricedIndex(GradeGroup gradeGroup, Random rnd) {
        List<ItemStack> pool = gradeGroup.items();
        long total = 0;
        for (int i = 0; i < pool.size(); i++) {
            PriceRange r = gradeGroup.priceForIndex(i);
            if (r != null && !r.isUnpriced()) {
                total += Math.max(0, gradeGroup.itemWeightAt(i));
            }
        }
        if (total <= 0) {
            return -1;
        }
        long roll = OddsCalculator.nextBoundedLong(rnd, total);
        long running = 0;
        for (int i = 0; i < pool.size(); i++) {
            PriceRange r = gradeGroup.priceForIndex(i);
            if (r == null || r.isUnpriced()) {
                continue;
            }
            running += Math.max(0, gradeGroup.itemWeightAt(i));
            if (roll < running) {
                return i;
            }
        }
        return -1;
    }

    private static ItemStack sampleSessionItem(BoxDefinition def, Random rnd) {
        List<ItemStack> all = new ArrayList<>();
        for (GradeGroup grade : def.grades()) {
            if (BoxGrades.gradeLevel(grade.id()) > 0) {
                all.addAll(grade.items());
            }
        }
        return all.isEmpty()
                ? new ItemStack(Items.DIAMOND)
                : all.get(rnd.nextInt(all.size())).copy();
    }

    private static List<ItemStack> poolFor(BoxDefinition def, int gradeLevel) {
        GradeGroup grade = findGrade(def, gradeLevel);
        return grade != null ? grade.items() : null;
    }

    private static GradeGroup findGrade(BoxDefinition def, int gradeLevel) {
        for (GradeGroup grade : def.grades()) {
            if (BoxGrades.gradeLevel(grade.id()) == gradeLevel) {
                return grade;
            }
        }
        return null;
    }

    private record Sample(ItemStack item, int grade, int price) {
    }
}
