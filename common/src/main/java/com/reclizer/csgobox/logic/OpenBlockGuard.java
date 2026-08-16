package com.reclizer.csgobox.logic;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative per-player open cooldown guard.
 *
 * <p>After a (bulk) open request is accepted the player is blocked from
 * opening another box for {@link #DEFAULT_COOLDOWN_TICKS} game ticks. This is
 * anti-spam protection on top of the key/box consumption, which is itself
 * authoritative on the server. Semantics are lifted verbatim from the former
 * per-platform {@code PacketCsgoProgress.OPEN_BLOCKED_UNTIL_TICK} maps so the
 * migration changes no runtime behaviour.</p>
 *
 * <p>Pure JDK state ({@link ConcurrentHashMap}); no Minecraft types. All
 * timestamps are the overworld game time supplied by the caller.</p>
 */
public final class OpenBlockGuard {

    /** Cooldown window applied after each accepted open request, in game ticks. */
    public static final int DEFAULT_COOLDOWN_TICKS = 10;

    private static final Map<UUID, Long> BLOCKED_UNTIL_TICK = new ConcurrentHashMap<>();

    private OpenBlockGuard() {
    }

    /**
     * Returns {@code true} while {@code id} is inside its cooldown window at
     * {@code now}. Expired entries are removed lazily on lookup, matching the
     * original platform behaviour.
     *
     * <p>The expiry removal is conditional ({@code remove(key, value)}) so a
     * concurrent {@link #block} that updates the deadline between our read and
     * remove is never clobbered — an unconditional remove could wipe a just-set
     * newer deadline and let a player open immediately (ABA race).</p>
     */
    public static boolean isBlocked(UUID id, long now) {
        Long blockedUntil = BLOCKED_UNTIL_TICK.get(id);
        if (blockedUntil == null) {
            return false;
        }
        if (now >= blockedUntil) {
            // Remove only if the value is still the one we read; if another
            // thread wrote a new deadline meanwhile, leave it untouched.
            BLOCKED_UNTIL_TICK.remove(id, blockedUntil);
            return false;
        }
        return true;
    }

    /**
     * Blocks further opens for {@code id} until {@code now + cooldownTicks}.
     * A later call overwrites an earlier deadline (last-write-wins), as the
     * original {@code blockFurtherOpensStatic} did.
     */
    public static void block(UUID id, long now, int cooldownTicks) {
        BLOCKED_UNTIL_TICK.put(id, now + cooldownTicks);
    }

    /**
     * Removes expired cooldown entries so the map does not grow without bound.
     * Invoked periodically from the server tick loop ({@code ModEvents#serverTick}).
     */
    public static void tick(long now) {
        BLOCKED_UNTIL_TICK.entrySet().removeIf(entry -> now >= entry.getValue());
    }

    /** Test hook: clears all cooldown state. */
    static void clearForTesting() {
        BLOCKED_UNTIL_TICK.clear();
    }
}
