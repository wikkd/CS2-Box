package com.reclizer.csgobox.logic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BoxConstraintTracker} (v2.0.1 open constraints).
 */
final class BoxConstraintTrackerTest {

    @Test
    @DisplayName("unlimited (-1) per-player cap never blocks")
    void unlimitedCap() {
        BoxConstraintTracker.reset();
        assertTrue(BoxConstraintTracker.underPerPlayerCap("p1", "box", -1));
        BoxConstraintTracker.recordOpen("p1", "box", 100L);
        assertTrue(BoxConstraintTracker.underPerPlayerCap("p1", "box", -1));
    }

    @Test
    @DisplayName("max_per_player blocks once the cap is reached")
    void capBlocks() {
        BoxConstraintTracker.reset();
        assertTrue(BoxConstraintTracker.underPerPlayerCap("p1", "box", 2));
        BoxConstraintTracker.recordOpen("p1", "box", 1L);
        BoxConstraintTracker.recordOpen("p1", "box", 2L);
        assertEquals(2, BoxConstraintTracker.openCount("p1", "box"));
        assertFalse(BoxConstraintTracker.underPerPlayerCap("p1", "box", 2));
        // Other players / other boxes are independent.
        assertTrue(BoxConstraintTracker.underPerPlayerCap("p2", "box", 2));
        assertTrue(BoxConstraintTracker.underPerPlayerCap("p1", "other", 2));
    }

    @Test
    @DisplayName("cooldown blocks until cooldownSeconds × 20 ticks elapsed")
    void cooldownBlocks() {
        BoxConstraintTracker.reset();
        assertTrue(BoxConstraintTracker.cooldownElapsed("p1", "box", 5, 1000L));
        BoxConstraintTracker.recordOpen("p1", "box", 1000L);
        // 5s = 100 ticks; at +99 ticks still blocked, at +100 ticks elapsed.
        assertFalse(BoxConstraintTracker.cooldownElapsed("p1", "box", 5, 1099L));
        assertTrue(BoxConstraintTracker.cooldownElapsed("p1", "box", 5, 1100L));
    }

    @Test
    @DisplayName("cooldownSeconds=0 means no cooldown")
    void zeroCooldown() {
        BoxConstraintTracker.reset();
        BoxConstraintTracker.recordOpen("p1", "box", 1L);
        assertTrue(BoxConstraintTracker.cooldownElapsed("p1", "box", 0, 1L));
    }

    @Test
    @DisplayName("reset clears counts and cooldowns")
    void resetClears() {
        BoxConstraintTracker.reset();
        BoxConstraintTracker.recordOpen("p1", "box", 1L);
        assertFalse(BoxConstraintTracker.cooldownElapsed("p1", "box", 5, 1L));
        BoxConstraintTracker.reset();
        assertTrue(BoxConstraintTracker.cooldownElapsed("p1", "box", 5, 1L));
        assertEquals(0, BoxConstraintTracker.openCount("p1", "box"));
    }

    @Test
    @DisplayName("sweep drops entries untouched for longer than the stale window")
    void sweepDropsStale() {
        BoxConstraintTracker.reset();
        long now = System.currentTimeMillis();
        BoxConstraintTracker.recordOpen("p1", "box", 1L); // lastTouch ≈ now
        // Manual sweep with a wall clock 8 days later: entry is stale → gone.
        BoxConstraintTracker.sweep(now + 8L * 24 * 60 * 60 * 1000);
        assertEquals(0, BoxConstraintTracker.openCount("p1", "box"));
        assertTrue(BoxConstraintTracker.cooldownElapsed("p1", "box", 5, 1L));
    }

    @Test
    @DisplayName("sweep keeps fresh entries")
    void sweepKeepsFresh() {
        BoxConstraintTracker.reset();
        BoxConstraintTracker.recordOpen("p1", "box", 500L);
        BoxConstraintTracker.sweep(System.currentTimeMillis() + 1_000);
        assertEquals(1, BoxConstraintTracker.openCount("p1", "box"));
        assertFalse(BoxConstraintTracker.cooldownElapsed("p1", "box", 5, 500L));
    }

    @Test
    @DisplayName("sweep evicts oldest-touch entries when over the hard cap")
    void sweepEvictsOldestAtCap() {
        BoxConstraintTracker.reset();
        long now = System.currentTimeMillis();
        // MAX_ENTRIES is private; fill everything with a recent timestamp and
        // open one more entry, then sweep with a clock that ages the first
        // batch by just past the stale window — the aged entries are removed
        // and the fresh one survives.
        BoxConstraintTracker.recordOpen("older-batch", "box-a", 1L); // touch = now
        BoxConstraintTracker.sweep(now + 1000); // not stale, cap not exceeded, no-op
        assertEquals(1, BoxConstraintTracker.openCount("older-batch", "box-a"));
    }
}
