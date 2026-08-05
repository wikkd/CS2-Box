package com.reclizer.csgobox.logic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AnimationStrip}.
 */
final class AnimationStripTest {

    @Test
    @DisplayName("constants have expected values")
    void constants() {
        assertEquals(50, AnimationStrip.ITEM_COUNT);
        assertEquals(35, AnimationStrip.MIN_WINNING_INDEX);
        assertEquals(44, AnimationStrip.MAX_WINNING_INDEX);
    }

    @Test
    @DisplayName("randomWinningIndex stays within [MIN, MAX] for full strip")
    void winningIndexRange() {
        Random rng = new Random(42);
        for (int i = 0; i < 1000; i++) {
            int idx = AnimationStrip.randomWinningIndex(rng, AnimationStrip.ITEM_COUNT);
            assertTrue(idx >= AnimationStrip.MIN_WINNING_INDEX
                            && idx <= AnimationStrip.MAX_WINNING_INDEX,
                    "Index out of range: " + idx);
        }
    }

    @Test
    @DisplayName("randomWinningIndex clamps when itemCount < MAX_WINNING_INDEX")
    void winningIndexSmallStrip() {
        Random rng = new Random(42);
        for (int i = 0; i < 100; i++) {
            int idx = AnimationStrip.randomWinningIndex(rng, 20);
            assertTrue(idx >= 0 && idx <= 19,
                    "Index out of bounds for small strip: " + idx);
        }
    }

    @Test
    @DisplayName("randomWinningIndex with itemCount=1 always returns 0")
    void winningIndexSingleItem() {
        Random rng = new Random(42);
        for (int i = 0; i < 10; i++) {
            assertEquals(0, AnimationStrip.randomWinningIndex(rng, 1));
        }
    }

    @Test
    @DisplayName("findNearestValid returns startingIndex when valid")
    void nearestValidDirect() {
        List<String> items = Arrays.asList("a", "b", "c", "d", "e");
        assertEquals(2, AnimationStrip.findNearestValid(items, 2, s -> !s.isEmpty()));
    }

    @Test
    @DisplayName("findNearestValid searches right first then left at same offset")
    void nearestValidSearchOrder() {
        List<String> items = Arrays.asList("a", "", "c", "d", "e");
        // Starting at 1 (empty): offset=1 → right=2 ("c" valid) found before left=0
        assertEquals(2, AnimationStrip.findNearestValid(items, 1, s -> !s.isEmpty()));
    }

    @Test
    @DisplayName("findNearestValid finds left when right unavailable")
    void nearestValidLeft() {
        List<String> items = Arrays.asList("a", "", "", "", "");
        assertEquals(0, AnimationStrip.findNearestValid(items, 2, s -> !s.isEmpty()));
    }

    @Test
    @DisplayName("findNearestValid returns -1 for null list")
    void nearestValidNull() {
        assertEquals(-1, AnimationStrip.findNearestValid(null, 0, s -> true));
    }

    @Test
    @DisplayName("findNearestValid returns -1 for empty list")
    void nearestValidEmpty() {
        assertEquals(-1, AnimationStrip.findNearestValid(Collections.emptyList(), 0, s -> true));
    }

    @Test
    @DisplayName("findNearestValid returns -1 when all invalid")
    void nearestValidAllInvalid() {
        List<String> items = Arrays.asList("", "", "");
        assertEquals(-1, AnimationStrip.findNearestValid(items, 1, s -> !s.isEmpty()));
    }

    @Test
    @DisplayName("findNearestValid clamps out-of-bounds starting index")
    void nearestValidClamp() {
        List<String> items = Arrays.asList("a", "b", "c");
        // startingIndex 99 clamped to 2, which is valid
        assertEquals(2, AnimationStrip.findNearestValid(items, 99, s -> !s.isEmpty()));
        // startingIndex -5 clamped to 0, which is valid
        assertEquals(0, AnimationStrip.findNearestValid(items, -5, s -> !s.isEmpty()));
    }
}
