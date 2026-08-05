package com.reclizer.csgobox.logic;

import java.util.Random;

/**
 * Weighted random grade selection for box opening.
 *
 * <p>Extracted from per-platform {@code RandomItem.randomItemsGrade} — algorithm
 * unchanged.</p>
 */
public final class OddsCalculator {

    private OddsCalculator() {
    }

    /**
     * Picks a grade (1-based) using weighted random selection.
     *
     * @param rng     random source
     * @param weights grade weights array (index 0 = grade 1, etc.)
     * @return selected grade (1 to weights.length), or 1 if weights is null/empty/all-zero
     */
    public static int pickGrade(Random rng, int[] weights) {
        if (weights == null || weights.length == 0) return 1;
        long totalWeight = 0L;
        for (int num : weights) {
            if (num > 0) {
                totalWeight += num;
            }
        }
        if (totalWeight <= 0) return 1;

        long rn = nextLong(rng, totalWeight);
        long cumulative = 0L;
        for (int i = 0; i < weights.length; i++) {
            int weight = weights[i];
            if (weight <= 0) continue;
            if (rn < cumulative + weight) {
                return i + 1;
            }
            cumulative += weight;
        }
        return weights.length;
    }

    /**
     * Bounded random long using rejection sampling. Mirrors the original
     * {@code RandomItem.nextLong} implementation.
     */
    private static long nextLong(Random rng, long bound) {
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
}
