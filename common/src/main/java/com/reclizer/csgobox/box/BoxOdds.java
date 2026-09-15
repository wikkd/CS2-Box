package com.reclizer.csgobox.box;

import com.reclizer.csgobox.logic.OddsCalculator;

/**
 * Pure probability math for the box opening display (JEI category, commands).
 *
 * <p>Semantics mirror the server-authoritative roll exactly:
 * {@code OddsCalculator.pickGrade} sums only positive weights, so a grade's
 * chance is {@code weight / totalPositiveWeight}; within a grade,
 * {@code GradeMap.pickRandom} picks by per-item weight, so each item's chance
 * is the grade chance × item weight ÷ sum of the grade's positive item
 * weights (uniform when every item weight is 1 — the classic behaviour).</p>
 */
public final class BoxOdds {

    private BoxOdds() {
    }

    /**
     * Sum of positive weights; 0 when weights is null, empty or all non-positive.
     * Delegates to {@link OddsCalculator#precomputeWeights} so the display layer
     * and the roll layer share one "positive-weight sum" source of truth.
     */
    public static long totalWeight(int[] weights) {
        OddsCalculator.Precomputed pre = OddsCalculator.precomputeWeights(weights);
        return pre == null ? 0L : pre.total();
    }

    /**
     * Chance of a grade tier in [0, 1], 1-based level, or 0 when the level is
     * out of range or no positive weight exists.
     */
    public static double gradeChance(int[] weights, int gradeLevel) {
        if (weights == null || gradeLevel < 1 || gradeLevel > weights.length) {
            return 0.0;
        }
        long total = totalWeight(weights);
        if (total <= 0) {
            return 0.0;
        }
        int weight = weights[gradeLevel - 1];
        return weight > 0 ? (double) weight / (double) total : 0.0;
    }

    /**
     * Per-item chance within a grade, given the grade chance and the number of
     * items in it; 0 when the grade holds no items.
     */
    public static double itemChance(double gradeChance, int itemCount) {
        if (itemCount <= 0) {
            return 0.0;
        }
        return gradeChance / itemCount;
    }

    /**
     * Per-item chance within a weighted grade: {@code gradeChance × itemWeight
     * ÷ positiveItemWeightSum}. Returns 0 when the grade has no positive item
     * weight. Falls back to the uniform {@link #itemChance} when
     * {@code positiveItemWeightSum <= 0}.
     */
    public static double weightedItemChance(double gradeChance, int itemWeight, long positiveItemWeightSum) {
        if (itemWeight <= 0 || positiveItemWeightSum <= 0) {
            return 0.0;
        }
        return gradeChance * itemWeight / (double) positiveItemWeightSum;
    }

    /**
     * Weighted-aware per-item chance used by the JEI/REI/EMI display layers.
     * Uniform ({@code gradeChance / itemCount}) when the grade's positive
     * weights sum to exactly its item count (classic all-1 configs); weighted
     * ({@code gradeChance × itemWeight ÷ positiveItemWeightSum}) otherwise —
     * exactly mirroring {@code GradeMap.pickRandom}'s linear-scan selection.
     * Returns 0 when the grade chance is 0, the item weight is non-positive
     * (the item is excluded from the real pool) or the grade holds no positive
     * weight at all.
     *
     * @param gradeChance          the grade's chance from {@link #gradeChance}
     * @param itemWeight           the item's intra-grade weight
     * @param itemCount            number of items in the grade
     * @param positiveItemWeightSum sum from {@link #positiveItemWeightSum}
     */
    public static double itemChance(double gradeChance, int itemWeight, int itemCount, long positiveItemWeightSum) {
        if (gradeChance <= 0 || itemCount <= 0 || itemWeight <= 0 || positiveItemWeightSum <= 0) {
            return 0.0;
        }
        if (positiveItemWeightSum == itemCount) {
            return gradeChance / itemCount;
        }
        return gradeChance * itemWeight / (double) positiveItemWeightSum;
    }

    /** True when at least one grade weight is positive — the box can roll a
     *  grade at all. False for null/empty/all-non-positive weight arrays. */
    public static boolean hasOpenableWeights(int[] weights) {
        return totalWeight(weights) > 0;
    }

    /**
     * Sum of positive per-item weights in a grade's item list; 0 when there
     * are none. Callers pass {@code grade.items().size()} as the uniform
     * fallback count and this method as the weighted sum — the two agree when
     * every item weight is 1.
     */
    public static long positiveItemWeightSum(int[] itemWeights) {
        if (itemWeights == null) {
            return 0L;
        }
        long sum = 0L;
        for (int w : itemWeights) {
            if (w > 0) {
                sum += w;
            }
        }
        return sum;
    }

    /**
     * Formats a [0, 1] chance as a percent string with one decimal place,
     * e.g. {@code 0.7961 → "79.6%"}. Locale-independent (uses {@code Locale.ROOT})
     * so the six platform tooltips render identically everywhere.
     */
    public static String percent(double chance) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", chance * 100.0);
    }
}
