package com.reclizer.csgobox.v26_2.villager;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.box.PriceRange;
import com.reclizer.csgobox.box.PriceTable;
import com.reclizer.csgobox.v26_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_2.box.GradeGroup;
import com.reclizer.csgobox.v26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.villager.VillagerBoxPool;
import com.reclizer.csgobox.villager.VillagerPricingConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Resolves the arms-dealer's box / terminal sale offer against the live
 * {@link BoxRegistry} (config/csbox/*.json): newly added boxes become
 * villager-sellable automatically instead of the generic fixed items.
 *
 * <p>Candidate filtering: matching type ({@code csbox} vs {@code terminal}),
 * {@code enabled} and satisfied {@code requires}; the price is the contents'
 * table-price expectation (grade odds × item weights × mean count × mean
 * table price — the same odds source as the tooltips / JEI) ×
 * {@code box_sell_ratio}, derived from deterministic midpoints so the loot
 * function (item rewrite) and the cost providers evaluate separately but
 * still agree. Boxes with a {@code loot_table} entry or a random enchant
 * spec can't be priced faithfully and stay out of the pool; prices beyond
 * one points stack + one gold stack also cap out. An empty pool means the
 * trade does not appear at all (no more selling unbound empty boxes).</p>
 *
 * <p>The per-villager pick ({@link VillagerBoxPool#pickBox}) is seeded by the
 * villager's UUID <b>without any level component</b>, so the assigned box
 * survives level-ups and offer rebuilds.</p>
 */
final class VillagerBoxSell {

    /** One resolved sale: the box id, its EV price in points and the gold base. */
    record Offer(Identifier boxId, boolean terminal, int price, int goldBase) {
    }

    private VillagerBoxSell() {
    }

    /**
     * The villager's sale offer, or {@code null} when nothing is sellable
     * (empty registry, nothing priced, or {@code box_sell_ratio} = 0).
     */
    static Offer resolve(long villagerSeed, boolean terminal,
                         VillagerPricingConfig cfg, PriceTable table) {
        if (cfg == null || cfg.boxSellRatio() <= 0) {
            return null;
        }
        PriceTable t = table != null ? table : PriceTable.EMPTY;
        int goldBase = goldBase(t, cfg.fallbackFor("minecraft:gold_ingot"));
        List<String> ids = new ArrayList<>();
        Map<String, Offer> byId = new HashMap<>();
        for (BoxDefinition def : BoxRegistry.getAll()) {
            if (def.isTerminal() != terminal) {
                continue;
            }
            if (!def.enabled() || def.missingRequirement()) {
                continue;
            }
            OptionalInt price = evPrice(def, t, cfg.boxSellRatio());
            if (price.isEmpty()) {
                continue;
            }
            int p = price.getAsInt();
            if (!VillagerBoxPool.sellable(p, goldBase)) {
                continue;
            }
            String id = def.id().toString();
            ids.add(id);
            byId.put(id, new Offer(def.id(), terminal, p, goldBase));
        }
        String pick = VillagerBoxPool.pickBox(villagerSeed, ids);
        return pick == null ? null : byId.get(pick);
    }

    /**
     * Contents' table-price expectation × ratio, from deterministic midpoints
     * (no sampling — the loot function and the cost providers evaluate
     * separately and must agree). Empty when the box can't be priced
     * faithfully: a {@code loot_table} entry (unpriced) or a random-enchant
     * spec.
     */
    private static OptionalInt evPrice(BoxDefinition def, PriceTable table, double ratio) {
        List<GradeGroup> grades = def.grades();
        int tierCount = grades.size();
        if (tierCount == 0) {
            return OptionalInt.empty();
        }
        int[] weights = def.getWeightArray();
        double ev = 0;
        for (int i = 0; i < tierCount; i++) {
            GradeGroup grade = grades.get(i);
            int level = BoxGrades.levelAt(tierCount, i);
            if (level - 1 < 0 || level - 1 >= weights.length || weights[level - 1] <= 0) {
                continue; // dead tier (explicit weight 0) contributes nothing
            }
            double gradeChance = BoxOdds.gradeChance(weights, level);
            long weightSum = grade.positiveItemWeightSum();
            if (weightSum <= 0) {
                continue;
            }
            for (int j = 0; j < grade.items().size(); j++) {
                PriceRange price = grade.priceForIndex(j);
                if (price.isUnpriced()) {
                    return OptionalInt.empty();
                }
                String spec = grade.items().get(j).get(ItemCsgoBox.ITEM_SPEC.get());
                if (VillagerBoxPool.hasRandomEnchant(spec) || VillagerBoxPool.hasLootTable(spec)) {
                    return OptionalInt.empty();
                }
                double chance = BoxOdds.weightedItemChance(gradeChance, grade.itemWeightAt(j), weightSum);
                ev += chance * VillagerBoxPool.meanCount(spec)
                        * (price.min() + (double) price.max()) / 2.0;
            }
        }
        if (ev <= 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(VillagerBoxPool.evPrice(ev, ratio));
    }

    /**
     * Deterministic gold base for the price split: the table's gold midpoint
     * (ranges never sampled), else the config fallback, else 0 (no change
     * currency → only ≤ 64-point boxes sell).
     */
    private static int goldBase(PriceTable table, int fallback) {
        PriceRange range = table.lookupRange("minecraft:gold_ingot");
        if (range != null) {
            return (range.min() + range.max()) / 2;
        }
        return fallback;
    }
}
