package com.reclizer.csgobox.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BoxOdds}, cross-checked against the server roll
 * semantics of {@code OddsCalculator.pickGrade} and {@code GradeMap.pickRandom}.
 */
class BoxOddsTest {

    private static final int[] DEFAULT_WEIGHTS = BoxGrades.DEFAULT_WEIGHTS;

    @Test
    @DisplayName("default grade weights sum to 785")
    void totalWeightSumsDefaults() {
        assertEquals(785L, BoxOdds.totalWeight(DEFAULT_WEIGHTS));
    }

    @Test
    @DisplayName("grade chance equals weight over positive total (grade1 = 625/785)")
    void gradeChanceMatchesOddsCalculatorSemantics() {
        assertEquals(625.0 / 785.0, BoxOdds.gradeChance(DEFAULT_WEIGHTS, 1), 1e-9);
        assertEquals(4.0 / 785.0, BoxOdds.gradeChance(DEFAULT_WEIGHTS, 5), 1e-9);
    }

    @Test
    @DisplayName("zero and negative weights are ignored and never picked")
    void nonPositiveWeightsAreIgnored() {
        int[] weights = {0, -3, 10, 0, 5};
        assertEquals(15L, BoxOdds.totalWeight(weights));
        assertEquals(0.0, BoxOdds.gradeChance(weights, 1));
        assertEquals(0.0, BoxOdds.gradeChance(weights, 2));
        assertEquals(10.0 / 15.0, BoxOdds.gradeChance(weights, 3), 1e-9);
        assertEquals(5.0 / 15.0, BoxOdds.gradeChance(weights, 5), 1e-9);
    }

    @Test
    @DisplayName("all-zero weights yield zero chance for every grade")
    void allZeroWeightsYieldZero() {
        int[] weights = {0, 0, 0, 0, 0};
        assertEquals(0L, BoxOdds.totalWeight(weights));
        for (int level = 1; level <= 5; level++) {
            assertEquals(0.0, BoxOdds.gradeChance(weights, level));
        }
    }

    @Test
    @DisplayName("null and empty weights are safe")
    void nullAndEmptyAreSafe() {
        assertEquals(0L, BoxOdds.totalWeight(null));
        assertEquals(0.0, BoxOdds.gradeChance(null, 1));
        assertEquals(0.0, BoxOdds.gradeChance(new int[0], 1));
        assertEquals(0.0, BoxOdds.gradeChance(DEFAULT_WEIGHTS, 0));
        assertEquals(0.0, BoxOdds.gradeChance(DEFAULT_WEIGHTS, 6));
    }

    @Test
    @DisplayName("item chance divides grade chance uniformly within the grade")
    void itemChanceDividesUniformly() {
        assertEquals(0.0, BoxOdds.itemChance(0.5, 0));
        assertEquals(0.25, BoxOdds.itemChance(0.5, 2), 1e-9);
        assertEquals(0.5, BoxOdds.itemChance(0.5, 1), 1e-9);
    }

    @Test
    @DisplayName("per-item chance of the rarest grade in defaults: 4/785/1")
    void itemChanceOnDefaults() {
        double grade5 = BoxOdds.gradeChance(DEFAULT_WEIGHTS, 5);
        assertEquals(grade5, BoxOdds.itemChance(grade5, 1), 1e-9);
    }

    @Test
    @DisplayName("percent formats locale-independently with one decimal")
    void percentFormats() {
        assertEquals("79.6%", BoxOdds.percent(625.0 / 785.0));
        assertEquals("0.5%", BoxOdds.percent(4.0 / 785.0));
        assertEquals("0.0%", BoxOdds.percent(0.0));
        assertEquals("100.0%", BoxOdds.percent(1.0));
    }

// ---- v2.1.0 weighted item chance ----

    @Test
    @DisplayName("weightedItemChance scales item chance by weight over grade sum")
    void weightedItemChanceScales() {
        // grade chance 0.5, items weights [9, 1] → 0.45 and 0.05
        assertEquals(0.45, BoxOdds.weightedItemChance(0.5, 9, 10), 1e-9);
        assertEquals(0.05, BoxOdds.weightedItemChance(0.5, 1, 10), 1e-9);
    }

    @Test
    @DisplayName("weightedItemChance returns 0 for zero/negative weight or sum")
    void weightedItemChanceZero() {
        assertEquals(0.0, BoxOdds.weightedItemChance(0.5, 0, 10), 0.0);
        assertEquals(0.0, BoxOdds.weightedItemChance(0.5, 3, 0), 0.0);
        assertEquals(0.0, BoxOdds.weightedItemChance(0.5, -1, 10), 0.0);
    }

    @Test
    @DisplayName("positiveItemWeightSum sums only positive weights")
    void positiveItemWeightSum() {
        assertEquals(10L, BoxOdds.positiveItemWeightSum(new int[]{9, 1, -2, 0}));
        assertEquals(0L, BoxOdds.positiveItemWeightSum(new int[]{0, -1}));
        assertEquals(0L, BoxOdds.positiveItemWeightSum(null));
    }

    @Test
    @DisplayName("weighted and uniform item chances agree when all weights are 1")
    void weightedEqualsUniformForUnitWeights() {
        double gradeChance = 0.25;
        assertEquals(BoxOdds.itemChance(gradeChance, 4),
                BoxOdds.weightedItemChance(gradeChance, 1, 4), 1e-12);
    }

// ---- v2.1.1 weighted-aware display helper ----

    @Test
    @DisplayName("itemChance(4-arg) is uniform when weights sum to the item count")
    void weightedAwareItemChanceUniformWhenAllOnes() {
        assertEquals(0.1, BoxOdds.itemChance(0.5, 1, 5, 5L), 1e-9);
        // all weights 2: weighted math still yields 2/(2*5) = 1/5
        assertEquals(0.1, BoxOdds.itemChance(0.5, 2, 5, 10L), 1e-9);
    }

    @Test
    @DisplayName("itemChance(4-arg) scales by item weight over the grade sum")
    void weightedAwareItemChanceScalesWithWeights() {
        double gradeChance = 0.5;
        assertEquals(0.45, BoxOdds.itemChance(gradeChance, 9, 2, 10L), 1e-9);
        assertEquals(0.05, BoxOdds.itemChance(gradeChance, 1, 2, 10L), 1e-9);
    }

    @Test
    @DisplayName("itemChance(4-arg) returns 0 for disabled items or empty pools")
    void weightedAwareItemChanceZeroWhenDisabled() {
        assertEquals(0.0, BoxOdds.itemChance(0.5, 0, 2, 10L), 0.0);
        assertEquals(0.0, BoxOdds.itemChance(0.5, -1, 2, 10L), 0.0);
        assertEquals(0.0, BoxOdds.itemChance(0.5, 1, 2, 0L), 0.0);
        assertEquals(0.0, BoxOdds.itemChance(0.0, 1, 2, 10L), 0.0);
        assertEquals(0.0, BoxOdds.itemChance(0.5, 1, 0, 0L), 0.0);
    }

    @Test
    @DisplayName("hasOpenableWeights distinguishes openable from disabled boxes")
    void hasOpenableWeights() {
        assertTrue(BoxOdds.hasOpenableWeights(new int[]{0, 125, 0, 0, 0}));
        assertFalse(BoxOdds.hasOpenableWeights(new int[]{0, 0, 0}));
        assertFalse(BoxOdds.hasOpenableWeights(new int[0]));
        assertFalse(BoxOdds.hasOpenableWeights(null));
    }
}
