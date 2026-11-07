package com.reclizer.csgobox.mcshared.util;

import net.minecraft.world.level.Level;

/**
 * The negotiation countdown clock: world time = game ticks x
 * {@link #MS_PER_TICK}. Advancing only while the world runs means a server
 * stop / pause never grants free time and offline time never counts, and the
 * absolute deadlines built from it survive graceful restarts (game time is
 * part of the world save). Sole tick-to-millisecond conversion point — do not
 * inline {@code getGameTime() * 50} again; tick-domain code (OpenBlockGuard,
 * BoxConstraintTracker) keeps raw ticks and must not go through here.
 */
public final class WorldClock {

    /** Milliseconds per game tick (vanilla 20 TPS). */
    public static final long MS_PER_TICK = 50L;

    private WorldClock() {
    }

    /** Current world-clock time in milliseconds. */
    public static long now(Level level) {
        return level.getGameTime() * MS_PER_TICK;
    }
}
