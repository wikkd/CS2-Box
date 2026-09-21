package com.reclizer.csgobox.logic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory per-(player, box) pity miss streaks backing {@link PityPolicy}.
 *
 * <p>Same lifecycle contract as {@link BoxConstraintTracker}: data lives only
 * in memory and resets on server restart — appropriate for a pity counter, no
 * save-format growth. Thread-safe ({@code ConcurrentHashMap}), so the single
 * open path (main thread) and the bulk compute pool can share it.</p>
 *
 * <p>v2.0.1-fix: every write is a single per-key {@code compute} (atomic
 * read-modify-write), entries carry a last-touch timestamp so stale records
 * are reaped, and the map is hard-capped to bounded memory on long-running
 * servers. Note the force-check still reads the streak before the roll — the
 * per-player open cooldown ({@code OpenBlockGuard}) is what prevents two
 * concurrent opens of the same (player, box) from racing that window.</p>
 */
public final class PityTracker {

    /** Entries untouched for this long are dropped (7 days). */
    private static final long STALE_MILLIS = 7L * 24 * 60 * 60 * 1000;

    /** Hard cap; when exceeded the oldest-touch entries are evicted. */
    private static final int MAX_ENTRIES = 100_000;

    /** Run a full sweep roughly every N mutations (amortized O(1) writes). */
    private static final int CLEANUP_INTERVAL = 2048;

    private static final Map<String, Entry> MISS_STREAKS = new ConcurrentHashMap<>();
    private static final AtomicInteger MUTATIONS = new AtomicInteger();

    /** Streak plus the last time it was touched (for stale reaping). */
    private record Entry(int streak, long lastTouchMillis) {
    }

    private PityTracker() {
    }

    private static String key(String playerUuid, String boxId) {
        return playerUuid + "|" + boxId;
    }

    /** Current miss streak for (player, box); 0 when none recorded (or last
     *  open hit the pity target). */
    public static int missStreak(String playerUuid, String boxId) {
        Entry e = MISS_STREAKS.get(key(playerUuid, boxId));
        return e == null ? 0 : e.streak();
    }

    /**
     * Records one successful open against the policy: resets the streak on a
     * hit (result at/above the target), otherwise increments. Call AFTER the
     * item was actually given — rejected/aborted opens must not advance pity.
     * A single atomic {@code compute} per key, so concurrent opens of the same
     * (player, box) cannot interleave the read-modify-write.
     */
    public static void recordOpen(String playerUuid, String boxId,
                                  int resultGrade, int pityTargetLevel) {
        String k = key(playerUuid, boxId);
        MISS_STREAKS.compute(k, (key, cur) -> {
            int base = cur == null ? 0 : cur.streak();
            if (pityTargetLevel > 0 && resultGrade >= pityTargetLevel) {
                return null;
            }
            return new Entry(base + 1, System.currentTimeMillis());
        });
        maybeCleanup();
    }

    /** Batch-local seed for the bulk path; final state is written back via
     *  {@link #finishStreak} only when the whole batch was granted. */
    public static int startStreak(String playerUuid, String boxId) {
        return missStreak(playerUuid, boxId);
    }

    /** Writes the final streak after a successful bulk grant. */
    public static void finishStreak(String playerUuid, String boxId, int finalStreak) {
        String k = key(playerUuid, boxId);
        if (finalStreak <= 0) {
            MISS_STREAKS.remove(k);
        } else {
            MISS_STREAKS.compute(k, (key, cur) -> new Entry(finalStreak, System.currentTimeMillis()));
        }
        maybeCleanup();
    }

    /**
     * v2.0.2-fix(lost update): applies the batch's <b>net streak delta</b>
     * ({@code finalStreak - snapshotStreak}) instead of overwriting the entry
     * with an absolute value. Additive composition stays correct no matter
     * how a concurrent single open (its own {@code recordOpen}) interleaves
     * inside the async compute window the 10-tick open guard cannot cover:
     * delta >= 0 (batch all misses) adds on top of anything recorded since;
     * delta < 0 (batch hit the pity target) decrements and clamps at 0,
     * preserving concurrent misses instead of erasing them.
     */
    public static void applyStreakDelta(String playerUuid, String boxId, int delta) {
        if (delta == 0) {
            return;
        }
        String k = key(playerUuid, boxId);
        MISS_STREAKS.compute(k, (key, cur) -> {
            int base = cur == null ? 0 : cur.streak();
            int next = Math.max(0, base + delta);
            if (next == 0) {
                return null;
            }
            return new Entry(next, System.currentTimeMillis());
        });
        maybeCleanup();
    }

    /** Drops all pity state (server stop). */
    public static void reset() {
        MISS_STREAKS.clear();
    }

    /** Amortized sweep: drop entries untouched for {@link #STALE_MILLIS} and,
     *  when still over {@link #MAX_ENTRIES}, evict the oldest-touch batch. */
    private static void maybeCleanup() {
        if (MUTATIONS.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        MISS_STREAKS.entrySet()
                .removeIf(e -> now - e.getValue().lastTouchMillis() > STALE_MILLIS);
        if (MISS_STREAKS.size() <= MAX_ENTRIES) {
            return;
        }
        long oldest = Long.MAX_VALUE;
        for (Entry v : MISS_STREAKS.values()) {
            oldest = Math.min(oldest, v.lastTouchMillis());
        }
        final long cutoff = oldest;
        MISS_STREAKS.entrySet()
                .removeIf(e -> e.getValue().lastTouchMillis() <= cutoff);
        if (MISS_STREAKS.size() > MAX_ENTRIES) {
            // Defensive last resort; the stale sweep plus oldest-batch
            // eviction above should always suffice. Clearing here trades
            // guaranteed progress for bounded memory in a pathological case.
            MISS_STREAKS.clear();
        }
    }
}