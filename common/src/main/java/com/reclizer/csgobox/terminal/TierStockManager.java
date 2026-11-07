package com.reclizer.csgobox.terminal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * v2.x per-tier stock for terminals (config {@code grades[].stock}, custom
 * tiers only): (boxId + tierId) -> remaining units, in-memory like
 * {@link TerminalStockManager} (resets on restart — a restart restocks).
 * Server-authoritative; the gate lives where offers are sampled and the
 * consume where a buy completes.
 */
public final class TierStockManager {

    private record Entry(int configured, AtomicInteger remaining) {
    }

    private static final Map<String, Entry> STOCK = new ConcurrentHashMap<>();

    private TierStockManager() {
    }

    private static String key(String boxId, String tierId) {
        return boxId + "|" + tierId;
    }

    /** Registers/refreshes the configured cap for (box, tier). */
    public static void configure(String boxId, String tierId, int stock) {
        if (stock <= 0) {
            STOCK.remove(key(boxId, tierId));
            return;
        }
        STOCK.compute(key(boxId, tierId), (k, cur) ->
                cur != null && cur.configured() == stock ? cur
                        : new Entry(stock, new AtomicInteger(stock)));
    }

    /** Remaining units (configured when untracked = unlimited). */
    public static int remaining(String boxId, String tierId) {
        Entry e = STOCK.get(key(boxId, tierId));
        return e == null ? Integer.MAX_VALUE : e.remaining().get();
    }

    /** True when the tier may still be offered (untracked = unlimited). */
    public static boolean offerable(String boxId, String tierId) {
        return remaining(boxId, tierId) > 0;
    }

    /** Consumes one unit; false when the tier is exhausted/unknown. */
    public static boolean consume(String boxId, String tierId) {
        Entry e = STOCK.get(key(boxId, tierId));
        return e != null && e.remaining().get() > 0 && e.remaining().decrementAndGet() >= 0;
    }
}
