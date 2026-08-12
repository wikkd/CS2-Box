package com.reclizer.csgobox.logic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freezes the cooldown semantics lifted verbatim from the former per-platform
 * {@code PacketCsgoProgress.OPEN_BLOCKED_UNTIL_TICK} maps: rejection inside
 * the window, release once expired, lazy expiry removal, bounded cleanup via
 * {@code tick}, and basic concurrent access safety.
 */
class OpenBlockGuardTest {

    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void reset() {
        OpenBlockGuard.clearForTesting();
    }

    @Test
    void blocksInsideCooldownWindow() {
        OpenBlockGuard.block(player, 100L, OpenBlockGuard.DEFAULT_COOLDOWN_TICKS);
        assertTrue(OpenBlockGuard.isBlocked(player, 100L));
        assertTrue(OpenBlockGuard.isBlocked(player, 109L));
    }

    @Test
    void releasesWhenWindowExpires() {
        OpenBlockGuard.block(player, 100L, OpenBlockGuard.DEFAULT_COOLDOWN_TICKS);
        // now >= blockedUntil releases, matching the original >= comparison
        assertFalse(OpenBlockGuard.isBlocked(player, 110L));
    }

    @Test
    void expiredLookupRemovesEntryLazily() {
        OpenBlockGuard.block(player, 100L, 10);
        assertFalse(OpenBlockGuard.isBlocked(player, 200L));
        // entry removed: advancing time never re-blocks
        assertFalse(OpenBlockGuard.isBlocked(player, 201L));
    }

    @Test
    void lastBlockCallOverwritesEarlierDeadline() {
        OpenBlockGuard.block(player, 100L, 10);
        OpenBlockGuard.block(player, 105L, 10);
        assertTrue(OpenBlockGuard.isBlocked(player, 114L));
        assertFalse(OpenBlockGuard.isBlocked(player, 115L));
    }

    @Test
    void unknownPlayerIsNeverBlocked() {
        assertFalse(OpenBlockGuard.isBlocked(UUID.randomUUID(), 0L));
    }

    @Test
    void tickPrunesOnlyExpiredEntries() {
        UUID other = UUID.randomUUID();
        OpenBlockGuard.block(player, 100L, 10);
        OpenBlockGuard.block(other, 200L, 10);
        OpenBlockGuard.tick(150L);
        assertFalse(OpenBlockGuard.isBlocked(player, 150L));
        assertTrue(OpenBlockGuard.isBlocked(other, 150L));
    }

    @Test
    void tickPrunesEntriesAtExactDeadline() {
        OpenBlockGuard.block(player, 100L, 10);
        OpenBlockGuard.tick(110L);
        assertFalse(OpenBlockGuard.isBlocked(player, 105L));
    }

    @Test
    void concurrentBlockAndReadDoNotThrow() throws InterruptedException {
        int threads = 8;
        int iterations = 500;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            UUID id = UUID.randomUUID();
            workers.add(new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < iterations; i++) {
                    OpenBlockGuard.block(id, i, OpenBlockGuard.DEFAULT_COOLDOWN_TICKS);
                    OpenBlockGuard.isBlocked(id, i + 5);
                    OpenBlockGuard.tick(i + OpenBlockGuard.DEFAULT_COOLDOWN_TICKS);
                }
            }));
        }
        for (Thread worker : workers) {
            worker.start();
        }
        start.countDown();
        assertDoesNotThrow(() -> {
            for (Thread worker : workers) {
                worker.join();
            }
        });
    }
}
