package com.reclizer.csgobox.logic;

import java.util.Random;

/**
 * Weighted random grade selection for box opening.
 *
 * <p>Extracted from per-platform {@code RandomItem.randomItemsGrade} — algorithm
 * unchanged. Provides both a one-shot {@link #pickGrade(Random, int[])} and a
 * precomputed path ({@link #precomputeWeights} + {@link Precomputed#pickGrade})
 * so callers that roll many grades (e.g. the 50-slot animation strip) don't
 * re-scan the weight array on every roll.</p>
 */
public final class OddsCalculator {

    /**
     * Precomputed positive-weight cumulative table for fast repeated rolls.
     * Built once by {@link #precomputeWeights}; immutable.
     *
     * @param cumulative cumulative sum of positive weights (length = number of
     *                   positive weights; last element = total positive weight)
     * @param grades     the 1-based grade of each positive weight (parallel to
     *                   {@code cumulative})
     * @param total      total positive weight
     */
    public record Precomputed(long[] cumulative, int[] grades, long total) {

        /**
         * Picks a grade using the precomputed table. Avoids re-scanning the
         * weight array on every roll.
         *
         * @param rng random source
         * @return selected grade (1-based), or 1 when the table has no positive weights
         */
        public int pickGrade(Random rng) {
            if (total <= 0L || grades.length == 0) {
                return 1;
            }
            long rn = nextBoundedLong(rng, total);
            for (int i = 0; i < cumulative.length; i++) {
                if (rn < cumulative[i]) {
                    return grades[i];
                }
            }
            return grades[grades.length - 1];
        }
    }

    private OddsCalculator() {
    }

    /**
     * Builds a {@link Precomputed} table from a raw weight array. Non-positive
     * entries are skipped, matching {@link #pickGrade(Random, int[])} semantics.
     * Returns {@code null} when there is no positive weight (callers then pick
     * the default grade 1, same as the one-shot path).
     */
    public static Precomputed precomputeWeights(int[] weights) {
        if (weights == null || weights.length == 0) {
            return null;
        }
        int positiveCount = 0;
        for (int w : weights) {
            if (w > 0) {
                positiveCount++;
            }
        }
        if (positiveCount == 0) {
            return null;
        }
        long[] cumulative = new long[positiveCount];
        int[] grades = new int[positiveCount];
        int idx = 0;
        long running = 0L;
        for (int i = 0; i < weights.length; i++) {
            int weight = weights[i];
            if (weight <= 0) {
                continue;
            }
            running += weight;
            cumulative[idx] = running;
            grades[idx] = i + 1; // 1-based grade
            idx++;
        }
        return new Precomputed(cumulative, grades, running);
    }

    /**
     * Picks a grade (1-based) using weighted random selection.
     *
     * @param rng     random source
     * @param weights grade weights array (index 0 = grade 1, etc.)
     * @return selected grade (1 to weights.length), or 1 if weights is null/empty/all-zero
     */
    public static int pickGrade(Random rng, int[] weights) {
        Precomputed pre = precomputeWeights(weights);
        if (pre == null) {
            return 1;
        }
        return pre.pickGrade(rng);
    }

    /**
     * Bounded random long using rejection sampling. Mirrors the original
     * {@code RandomItem.nextLong} implementation.
     *
     * <p>Public (v2.1.0-fix) so {@code GradeMap.pickWeighted} and the
     * platform {@code TerminalSession.pickWeightedIndex} share one unbiased
     * implementation instead of the biased {@code Math.abs(nextLong()) % n}
     * shortcut (which returns a negative roll for {@code Long.MIN_VALUE}).
     */
    public static long nextBoundedLong(Random rng, long bound) {
        if (bound <= Integer.MAX_VALUE) {
            return rng.nextInt((int) bound);
        }
        long bits;
        long value;
        do {
            bits = rng.nextLong() >>> 1;
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0L);
        return value;
    }

    /** Result of a pity-aware roll: the 1-based grade and whether the roll
     *  was forced by the pity policy (as opposed to a plain weighted roll). */
    public record PityResult(int grade, boolean forced) {
    }

    /**
     * v2.1.1 pity-aware grade roll. When {@code policy != null} and the miss
     * streak reached the configured threshold, rolls ONLY within
     * {@code [targetLevel .. weights.length]} (weighted by the existing
     * per-grade weights) so the result is guaranteed to hit the target or
     * above. Falls back to a normal weighted roll when the pity slice has no
     * positive weight (misconfigured tiers — the box author must fix the
     * weights; a forced roll must never produce nothing).
     */
    public static PityResult pickGradeWithPity(Random rng, int[] weights,
                                               PityPolicy policy, int missStreak) {
        boolean force = policy != null && policy.shouldForce(missStreak);
        if (!force) {
            return new PityResult(pickGrade(rng, weights), false);
        }
        int targetLevel = policy.targetLevel();
        if (weights == null || targetLevel < 1 || targetLevel > weights.length) {
            return new PityResult(pickGrade(rng, weights), false);
        }
        int[] slice = new int[weights.length - targetLevel + 1];
        System.arraycopy(weights, targetLevel - 1, slice, 0, slice.length);
        Precomputed pre = precomputeWeights(slice);
        if (pre == null) {
            return new PityResult(pickGrade(rng, weights), false);
        }
        return new PityResult(targetLevel - 1 + pre.pickGrade(rng), true);
    }
}
