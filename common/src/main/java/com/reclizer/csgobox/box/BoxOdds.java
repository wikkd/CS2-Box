package com.reclizer.csgobox.box;

/**
 * Pure probability math for the box opening display (JEI category, commands).
 *
 * <p>Semantics mirror the server-authoritative roll exactly:
 * {@code OddsCalculator.pickGrade} sums only positive weights, so a grade's
 * chance is {@code weight / totalPositiveWeight}; within a grade,
 * {@code GradeMap.pickRandom} picks uniformly, so each item's chance is the
 * grade chance divided by the item count.</p>
 */
public final class BoxOdds {

    private BoxOdds() {
    }

    /** Sum of positive weights; 0 when weights is null, empty or all non-positive. */
    public static long totalWeight(int[] weights) {
        if (weights == null) {
            return 0L;
        }
        long total = 0L;
        for (int weight : weights) {
            if (weight > 0) {
                total += weight;
            }
        }
        return total;
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
}
