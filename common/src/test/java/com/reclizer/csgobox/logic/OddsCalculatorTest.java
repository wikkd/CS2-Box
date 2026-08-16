package com.reclizer.csgobox.logic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OddsCalculator}.
 */
final class OddsCalculatorTest {

    @Test
    @DisplayName("null weights returns default grade 1")
    void nullWeights() {
        assertEquals(1, OddsCalculator.pickGrade(new Random(42), null));
    }

    @Test
    @DisplayName("empty weights returns default grade 1")
    void emptyWeights() {
        assertEquals(1, OddsCalculator.pickGrade(new Random(42), new int[0]));
    }

    @Test
    @DisplayName("all-zero weights returns default grade 1")
    void allZeroWeights() {
        assertEquals(1, OddsCalculator.pickGrade(new Random(42), new int[]{0, 0, 0, 0, 0}));
    }

    @Test
    @DisplayName("all-negative weights returns default grade 1")
    void allNegativeWeights() {
        assertEquals(1, OddsCalculator.pickGrade(new Random(42), new int[]{-1, -5, -3}));
    }

    @Test
    @DisplayName("single non-zero entry always returns that grade")
    void singleEntry() {
        Random rng = new Random(42);
        for (int i = 0; i < 100; i++) {
            assertEquals(3, OddsCalculator.pickGrade(rng, new int[]{0, 0, 10, 0, 0}));
        }
    }

    @Test
    @DisplayName("result is always within [1, weights.length]")
    void boundsCheck() {
        Random rng = new Random(123);
        int[] weights = {625, 125, 25, 5, 2};
        for (int i = 0; i < 1000; i++) {
            int grade = OddsCalculator.pickGrade(rng, weights);
            assertTrue(grade >= 1 && grade <= 5,
                    "Grade out of bounds: " + grade);
        }
    }

    @Test
    @DisplayName("weight distribution roughly matches expected proportions")
    void distributionCheck() {
        Random rng = new Random(999);
        int[] weights = {80, 20};
        int countGrade1 = 0;
        int trials = 10000;
        for (int i = 0; i < trials; i++) {
            if (OddsCalculator.pickGrade(rng, weights) == 1) {
                countGrade1++;
            }
        }
        // Expect ~80% grade 1; allow wide margin for randomness
        double ratio = countGrade1 / (double) trials;
        assertTrue(ratio > 0.70 && ratio < 0.90,
                "Expected ~0.80 ratio, got " + ratio);
    }

    @Test
    @DisplayName("negative weights are skipped, positive ones still work")
    void negativeWeightsSkipped() {
        Random rng = new Random(42);
        int[] weights = {-1, 50, -1, 50, -1};
        for (int i = 0; i < 100; i++) {
            int grade = OddsCalculator.pickGrade(rng, weights);
            assertTrue(grade == 2 || grade == 4,
                    "Expected grade 2 or 4, got " + grade);
        }
    }

    @Test
    @DisplayName("large weights exceeding Integer.MAX_VALUE work correctly")
    void largeWeights() {
        Random rng = new Random(42);
        int[] weights = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        for (int i = 0; i < 100; i++) {
            int grade = OddsCalculator.pickGrade(rng, weights);
            assertTrue(grade >= 1 && grade <= 3,
                    "Grade out of bounds with large weights: " + grade);
        }
    }

    @Test
    @DisplayName("Precomputed.pickGrade matches one-shot under large weights (long accumulation)")
    void precomputedMatchesOneShotLargeWeights() {
        // B1/B3 accumulate weights in a long; verify the precomputed path stays
        // identical to the one-shot roll even when the total approaches
        // Long.MAX_VALUE (three Integer.MAX_VALUE weights sum to ~6.4e9, well
        // above any int-based total).
        int[] weights = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        OddsCalculator.Precomputed pre = OddsCalculator.precomputeWeights(weights);
        assertEquals(3L * Integer.MAX_VALUE, pre.total());

        Random a = new Random(7);
        Random b = new Random(7);
        for (int i = 0; i < 2000; i++) {
            int oneShot = OddsCalculator.pickGrade(a, weights);
            int cached = pre.pickGrade(b);
            assertEquals(oneShot, cached,
                    "large-weight mismatch at roll " + i + ": oneShot=" + oneShot + " cached=" + cached);
        }
    }

    @Test
    @DisplayName("Precomputed.pickGrade is sequence-identical to pickGrade(int[])")
    void precomputedMatchesOneShotSequence() {
        // The B1/B3 path precomputes the weight table once and reuses it for
        // many rolls; it must produce the exact same grades as the original
        // per-roll pickGrade(int[]) for the same RNG seed.
        int[] weights = {625, 125, 25, 5, 2};
        OddsCalculator.Precomputed pre = OddsCalculator.precomputeWeights(weights);

        Random a = new Random(2024);
        Random b = new Random(2024);
        for (int i = 0; i < 2000; i++) {
            int oneShot = OddsCalculator.pickGrade(a, weights);
            int cached = pre.pickGrade(b);
            assertEquals(oneShot, cached,
                    "mismatch at roll " + i + ": oneShot=" + oneShot + " cached=" + cached);
        }
    }

    @Test
    @DisplayName("precomputeWeights returns null when there is no positive weight")
    void precomputeNullForNoPositiveWeight() {
        assertNull(OddsCalculator.precomputeWeights(null));
        assertNull(OddsCalculator.precomputeWeights(new int[0]));
        assertNull(OddsCalculator.precomputeWeights(new int[]{0, -1, -3, 0}));
    }
}
