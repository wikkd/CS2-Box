package com.reclizer.csgobox.box;

import com.reclizer.csgobox.logic.AnimationStrip;
import com.reclizer.csgobox.logic.GradeMap;
import com.reclizer.csgobox.logic.OddsCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the animation strip (item + grade per slot, plus the nearest valid
 * winning index) shared by the single-open and bulk-open paths. The grade pool
 * is taken from the caller-provided {@link GradeMap}, keep-alive for exact
 * parity with the pool the caller later uses for fallbacks and grants.
 *
 * <p>The caller decides what an absent winner ({@code winningIndex < 0}, every
 * strip slot empty) means: the single-open path rejects the whole open while
 * the bulk path coerces the index to 0.</p>
 *
 * <p>Generic over the item type so every platform shares one implementation:
 * platforms pass their own empty sentinel ({@code ItemStack.EMPTY}) and the
 * pool's validity predicate lives in the {@link GradeMap} itself.</p>
 *
 * <p>The per-grade weights are precomputed once per strip via
 * {@link OddsCalculator#precomputeWeights} so the repeated per-slot rolls
 * don't re-scan the weight array (important: strips have
 * {@link AnimationStrip#ITEM_COUNT} slots).</p>
 */
public final class BoxStripGenerator {

    private BoxStripGenerator() {
    }

    /**
     * Mutable strip carrier; the callers may still patch the winning slot
     * (fallback resolution) before the strip is transmitted.
     */
    public record Strip<T>(List<T> items, List<Integer> grades, int winningIndex) {
    }

    /**
     * @param gradeMap  grade pool to draw items from
     * @param weights   per-grade weights (grade1 → grade5 order)
     * @param rng       roll source (server-authoritative on callers)
     * @param emptyValue sentinel used when a slot resolves to nothing
     * @param <T>       item type
     */
    public static <T> Strip<T> generate(GradeMap<T> gradeMap, int[] weights, Random rng, T emptyValue) {
        List<T> items = new ArrayList<>(AnimationStrip.ITEM_COUNT);
        List<Integer> grades = new ArrayList<>(AnimationStrip.ITEM_COUNT);

        // Precompute the positive-weight cumulative table once: the 50-slot
        // strip would otherwise re-scan the weight array on every roll.
        // null (all-non-positive weights) -> grade 1 fallback below.
        OddsCalculator.Precomputed pre = OddsCalculator.precomputeWeights(weights);
        for (int j = 0; j < AnimationStrip.ITEM_COUNT; j++) {
            int g = (pre != null)
                    ? pre.pickGrade(rng)
                    : 1;
            T s = gradeMap.pickRandom(rng, g);
            if (s == null) {
                s = gradeMap.findFallback(g);
            }
            if (s == null) {
                s = emptyValue;
            }
            items.add(s);
            grades.add(Math.min(Math.max(g, 1), 5));
        }
        int winningIndex = AnimationStrip.randomWinningIndex(rng, items.size());
        winningIndex = AnimationStrip.findNearestValid(items, winningIndex, gradeMap::isValid);
        return new Strip<>(items, grades, winningIndex);
    }
}
