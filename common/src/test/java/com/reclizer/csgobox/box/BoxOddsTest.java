package com.reclizer.csgobox.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
