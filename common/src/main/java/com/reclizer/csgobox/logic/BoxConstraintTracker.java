package com.reclizer.csgobox.logic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * v2.0.1 server-side open constraints: per-player per-box open counts and
 * last-open world ticks, backing the {@code max_per_player} and
 * {@code cooldown_seconds} box config fields.
 *
 * <p>In-memory only (documented): counts and cooldowns reset on server
 * restart, which matches how these fields are typically used (event/activity
 * caps, anti-spam gates) without growing the player save format.</p>
 *
 * <p>Bounded memory (v2.2.0-fix): entries carry a wall-clock last-touch
 * timestamp and are reaped after {@link #STALE_MILLIS}, plus a hard cap
 * {@link #MAX_ENTRIES} evicts the oldest-touch batch on overflow — a
 * long-running server that never restarts stays bounded. (The pre-fix maps
 * grew forever: one entry per (player, box) ever opened.) Thread-safety
 * mirrors {@link GradeMapCache}: concurrent access from the netty worker and
 * the main server thread is safe; every write is a single per-key
 * {@code compute} (atomic read-modify-write), so concurrent opens of the same
 * (player, box) cannot interleave.</p>
 */
public final class BoxConstraintTracker {

    /** Entries untouched for this long are dropped (7 days). */
    private static final long STALE_MILLIS = 7L * 24 * 60 * 60 * 1000;

    /** Hard cap; when exceeded the oldest-touch entries are evicted. */
    private static final int MAX_ENTRIES = 100_000;

    /** Run a full sweep roughly every N mutations (amortized O(1) writes). */
    private static final int CLEANUP_INTERVAL = 2048;

    /** Per (player uuid, box id): open count, last successful open world
     *  tick, and wall-clock last touch (for stale reaping). */
    private record Entry(int count, long lastOpenTick, long lastTouchMillis) {
    }

    private static final Map<String, Entry> STATE = new ConcurrentHashMap<>();
    private static final AtomicInteger MUTATIONS = new AtomicInteger();

    private BoxConstraintTracker() {
    }

    private static String key(String playerUuid, String boxId) {
        return playerUuid + "|" + boxId;
    }

    /** Opens recorded so far for (player, box); 0 when none. */
    public static int openCount(String playerUuid, String boxId) {
        Entry e = STATE.get(key(playerUuid, boxId));
        return e == null ? 0 : e.count();
    }

    /** Whether the player may open again under {@code maxPerPlayer}
     *  ({@code maxPerPlayer < 0} = unlimited). */
    public static boolean underPerPlayerCap(String playerUuid, String boxId, int maxPerPlayer) {
        if (maxPerPlayer < 0) {
            return true;
        }
        return openCount(playerUuid, boxId) < maxPerPlayer;
    }

    /** Whether enough ticks have passed since the last open for this box.
     *  {@code cooldownSeconds <= 0} = no cooldown. */
    public static boolean cooldownElapsed(String playerUuid, String boxId,
                                          int cooldownSeconds, long currentGameTime) {
        if (cooldownSeconds <= 0) {
            return true;
        }
        Entry e = STATE.get(key(playerUuid, boxId));
        if (e == null) {
            return true;
        }
        long elapsed = currentGameTime - e.lastOpenTick();
        return elapsed >= cooldownSeconds * 20L;
    }

    /** Records a successful open (increments the counter, updates the last
     *  open tick). Call AFTER the roll is fully validated and the item given.
     *  A single atomic {@code compute} per key, so concurrent opens of the
     *  same (player, box) cannot interleave the read-modify-write. */
    public static void recordOpen(String playerUuid, String boxId, long currentGameTime) {
        String k = key(playerUuid, boxId);
        STATE.compute(k, (key, cur) -> new Entry(
                (cur == null ? 0 : cur.count()) + 1,
                currentGameTime,
                System.currentTimeMillis()));
        maybeCleanup();
    }

    /** Drops all tracking (server stop / world unload). */
    public static void reset() {
        STATE.clear();
    }

    /** Test/package hook: full sweep at a caller-supplied wall-clock time.
     *  Drops entries untouched for {@link #STALE_MILLIS}; when still over
     *  {@link #MAX_ENTRIES}, evicts the oldest-touch batch (defensive clear
     *  only in a pathological case, mirroring {@code PityTracker}). */
    static void sweep(long now) {
        STATE.entrySet()
                .removeIf(e -> now - e.getValue().lastTouchMillis() > STALE_MILLIS);
        if (STATE.size() <= MAX_ENTRIES) {
            return;
        }
        long oldest = Long.MAX_VALUE;
        for (Entry v : STATE.values()) {
            oldest = Math.min(oldest, v.lastTouchMillis());
        }
        final long cutoff = oldest;
        STATE.entrySet()
                .removeIf(e -> e.getValue().lastTouchMillis() <= cutoff);
        if (STATE.size() > MAX_ENTRIES) {
            STATE.clear();
        }
    }

    /** Amortized sweep: run roughly every {@link #CLEANUP_INTERVAL} mutations. */
    private static void maybeCleanup() {
        if (MUTATIONS.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        sweep(System.currentTimeMillis());
    }
}