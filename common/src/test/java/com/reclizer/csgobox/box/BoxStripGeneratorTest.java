package com.reclizer.csgobox.box;

import com.reclizer.csgobox.logic.AnimationStrip;
import com.reclizer.csgobox.logic.GradeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the common generic {@link BoxStripGenerator}. Items are
 * plain strings so the strip algorithm is tested without any Minecraft type.
 */
final class BoxStripGeneratorTest {

    private static final String EMPTY = "";
    private static final int[] WEIGHTS = new int[]{625, 125, 25, 6, 4};

    /** GradeMap over strings: empty string is the invalid sentinel. */
    private static GradeMap<String> poolOf(Map<String, Integer> items) {
        return GradeMap.build(items, s -> !s.isEmpty(), Function.identity());
    }

    private static Map<String, Integer> populatedPool() {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("common1", 1);
        items.put("common2", 1);
        items.put("rare1", 3);
        items.put("covert1", 5);
        return items;
    }

    @Test
    @DisplayName("fixed seed: strip has exactly ITEM_COUNT slots with clamped grades")
    void stripShape() {
        GradeMap<String> pool = poolOf(populatedPool());
        BoxStripGenerator.Strip<String> strip =
                BoxStripGenerator.generate(pool, WEIGHTS, new Random(42L), EMPTY);

        assertEquals(AnimationStrip.ITEM_COUNT, strip.items().size());
        assertEquals(AnimationStrip.ITEM_COUNT, strip.grades().size());
        for (int grade : strip.grades()) {
            assertTrue(grade >= 1 && grade <= 5, "grade out of range: " + grade);
        }
    }

    @Test
    @DisplayName("fixed seed: winning index points at a valid (non-empty) slot")
    void winningIndexIsValid() {
        GradeMap<String> pool = poolOf(populatedPool());
        BoxStripGenerator.Strip<String> strip =
                BoxStripGenerator.generate(pool, WEIGHTS, new Random(42L), EMPTY);

        int winningIndex = strip.winningIndex();
        assertTrue(winningIndex >= 0 && winningIndex < strip.items().size(),
                "winningIndex out of range: " + winningIndex);
        assertTrue(!strip.items().get(winningIndex).isEmpty(),
                "winning slot must hold a valid item");
    }

    @Test
    @DisplayName("fixed seed: generation is deterministic for the same seed")
    void deterministic() {
        GradeMap<String> pool = poolOf(populatedPool());
        BoxStripGenerator.Strip<String> a =
                BoxStripGenerator.generate(pool, WEIGHTS, new Random(7L), EMPTY);
        BoxStripGenerator.Strip<String> b =
                BoxStripGenerator.generate(pool, WEIGHTS, new Random(7L), EMPTY);

        assertEquals(a.items(), b.items());
        assertEquals(a.grades(), b.grades());
        assertEquals(a.winningIndex(), b.winningIndex());
    }

    @Test
    @DisplayName("empty pool degrades: every slot is the empty sentinel, no winner")
    void emptyPoolDegrades() {
        GradeMap<String> pool = poolOf(Map.of());
        BoxStripGenerator.Strip<String> strip =
                BoxStripGenerator.generate(pool, WEIGHTS, new Random(42L), EMPTY);

        assertEquals(AnimationStrip.ITEM_COUNT, strip.items().size());
        assertTrue(strip.items().stream().allMatch(EMPTY::equals));
        assertEquals(-1, strip.winningIndex(),
                "an all-empty strip must report no winner");
    }

    @Test
    @DisplayName("single-item pool fills every slot with fallback copies")
    void singleItemPool() {
        GradeMap<String> pool = poolOf(Map.of("only", 1));
        BoxStripGenerator.Strip<String> strip =
                BoxStripGenerator.generate(pool, WEIGHTS, new Random(99L), EMPTY);

        // Grade 2..5 picks miss the pool; the descending fallback must land on "only".
        assertTrue(strip.items().stream().allMatch("only"::equals));
        assertTrue(strip.winningIndex() >= 0);
    }

    @Test
    @DisplayName("null/empty/all-non-positive weights degrade to grade 1 without throwing")
    void nonPositiveWeightsDegradeToGrade1() {
        // B2 precomputes the weight table once and falls back to grade 1 when
        // there is no positive weight; verify the strip still builds and lands
        // on a valid item in grade 1 (the "only" pool entry).
        GradeMap<String> pool = poolOf(Map.of("only", 1));
        for (int[] bad : new int[][]{null, new int[0], new int[]{0, -3, 0, -1, 0}}) {
            BoxStripGenerator.Strip<String> strip =
                    BoxStripGenerator.generate(pool, bad, new Random(42L), EMPTY);
            assertEquals(AnimationStrip.ITEM_COUNT, strip.items().size());
            assertTrue(strip.items().stream().allMatch("only"::equals),
                    "all slots must resolve to grade-1 pool item");
            assertTrue(strip.winningIndex() >= 0);
        }
    }
}
