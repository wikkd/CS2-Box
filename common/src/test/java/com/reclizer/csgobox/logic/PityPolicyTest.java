package com.reclizer.csgobox.logic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PityPolicy} (pure streak/force math) and
 * {@link PityTracker} (in-memory per-player per-box state).
 */
final class PityPolicyTest {

    @Test
    @DisplayName("grade id maps to 1-based level and threshold is stored")
    void parsesGradeId() {
        PityPolicy p = PityPolicy.of("classified", 20);
        assertTrue(p != null);
        assertEquals(5, p.targetLevel());
        assertEquals(20, p.every());
        assertEquals(4, PityPolicy.of("restricted", 10).targetLevel());
        assertEquals(3, PityPolicy.of("mil_spec", 10).targetLevel());
        assertEquals(1, PityPolicy.of("consumer", 10).targetLevel());
    }

    @Test
    @DisplayName("unknown grade or every < 2 yields null (pity disabled)")
    void rejectsInvalidConfig() {
        assertNull(PityPolicy.of("bogus", 20));
        assertNull(PityPolicy.of("classified", 1));
        assertNull(PityPolicy.of("classified", 0));
        assertNull(PityPolicy.ofLevel(0, 20));
        assertNull(PityPolicy.ofLevel(6, 20));
    }

    @Test
    @DisplayName("force kicks in exactly at every-1 misses")
    void forceThreshold() {
        PityPolicy p = PityPolicy.of("classified", 3); // force on the 3rd miss
        assertFalse(p.shouldForce(0));
        assertFalse(p.shouldForce(1));
        assertTrue(p.shouldForce(2));
        assertTrue(p.shouldForce(5));
    }

    @Test
    @DisplayName("nextStreak resets on target hit and increments on miss")
    void streakProgression() {
        PityPolicy p = PityPolicy.of("classified", 3); // target level 5
        assertEquals(1, p.nextStreak(0, 4));   // below target → miss
        assertEquals(3, p.nextStreak(2, 1));   // keeps missing (streak + 1)
        assertEquals(0, p.nextStreak(2, 5));   // hit target → reset
        assertEquals(0, p.nextStreak(2, 6));   // above target → reset (defensive)
    }

    @Test
    @DisplayName("PityTracker records hits and misses per player/box")
    void trackerLifecycle() {
        PityTracker.reset();
        assertEquals(0, PityTracker.missStreak("u1", "boxA"));
        PityTracker.recordOpen("u1", "boxA", 4, 5);
        assertEquals(1, PityTracker.missStreak("u1", "boxA"));
        PityTracker.recordOpen("u1", "boxA", 3, 5);
        assertEquals(2, PityTracker.missStreak("u1", "boxA"));
        PityTracker.recordOpen("u1", "boxA", 5, 5);
        assertEquals(0, PityTracker.missStreak("u1", "boxA"));
        // independent per box / player
        assertEquals(0, PityTracker.missStreak("u2", "boxA"));
        assertEquals(0, PityTracker.missStreak("u1", "boxB"));
    }

    @Test
    @DisplayName("bulk start/finish round-trips the shared streak")
    void bulkRoundTrip() {
        PityTracker.reset();
        PityTracker.recordOpen("u1", "boxA", 1, 5);
        assertEquals(1, PityTracker.startStreak("u1", "boxA"));
        PityTracker.finishStreak("u1", "boxA", 4);
        assertEquals(4, PityTracker.missStreak("u1", "boxA"));
        PityTracker.finishStreak("u1", "boxA", 0);
        assertEquals(0, PityTracker.missStreak("u1", "boxA"));
        PityTracker.reset();
        assertEquals(0, PityTracker.missStreak("u1", "boxA"));
    }
}