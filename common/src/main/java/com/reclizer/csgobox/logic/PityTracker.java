package com.reclizer.csgobox.logic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-(player, box) pity miss streaks backing {@link PityPolicy}.
 *
 * <p>Same lifecycle contract as {@link BoxConstraintTracker}: data lives only
 * in memory and resets on server restart — appropriate for a pity counter, no
 * save-format growth. Thread-safe ({@code ConcurrentHashMap}), so the single
 * open path (main thread) and the bulk compute pool can share it.</p>
 */
public final class PityTracker {

    private static final Map<String, Integer> MISS_STREAKS = new ConcurrentHashMap<>();

    private PityTracker() {
    }

    private static String key(String playerUuid, String boxId) {
        return playerUuid + "|" + boxId;
    }

    /** Current miss streak for (player, box); 0 when none recorded (or last
     *  open hit the pity target). */
    public static int missStreak(String playerUuid, String boxId) {
        return MISS_STREAKS.getOrDefault(key(playerUuid, boxId), 0);
    }

    /**
     * Records one successful open against the policy: resets the streak on a
     * hit (result at/above the target), otherwise increments. Call AFTER the
     * item was actually given — rejected/aborted opens must not advance pity.
     */
    public static void recordOpen(String playerUuid, String boxId,
                                  int resultGrade, int pityTargetLevel) {
        String k = key(playerUuid, boxId);
        if (pityTargetLevel > 0 && resultGrade >= pityTargetLevel) {
            MISS_STREAKS.remove(k);
        } else {
            MISS_STREAKS.merge(k, 1, Integer::sum);
        }
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
            MISS_STREAKS.put(k, finalStreak);
        }
    }

    /** Drops all pity state (server stop). */
    public static void reset() {
        MISS_STREAKS.clear();
    }
}