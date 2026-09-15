package com.reclizer.csgobox.terminal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v2.1.0 server-side terminal stock: a global (not per-player) remaining
 * stock per box id, decremented on each successful terminal purchase and
 * refilled after the box's configured {@code restock_minutes} of WORLD time
 * (game ticks × 50 ms). {@code stock == -1} (the default) means unlimited and
 * nothing is tracked. {@code restock_minutes == 0} means "never auto-restock"
 * (an exhausted terminal stays empty until the server restarts).
 *
 * <p>In-memory only (documented): stock resets to full on server restart.
 * The map is bounded by the number of configured stock-limited terminals.
 * Thread-safety mirrors {@code GradeMapCache}: the server tick and the netty
 * worker may both touch it.</p>
 */
public final class TerminalStockManager {

    private record StockEntry(int max, int remaining, int restockMinutes, long nextRestockMs) {
    }

    private static final Map<String, StockEntry> STOCK = new ConcurrentHashMap<>();

    private TerminalStockManager() {
    }

    /** Remaining stock for a box id (the box's configured {@code stock}, or
     *  -1 when unlimited / not yet initialized). */
    public static int remaining(String boxId, int configuredStock) {
        if (configuredStock < 0) {
            return -1;
        }
        StockEntry e = STOCK.computeIfAbsent(boxId,
                k -> new StockEntry(configuredStock, configuredStock, 0, Long.MAX_VALUE));
        return e.remaining();
    }

    /** Whether a stock-limited terminal may still sell (stock == -1 or > 0). */
    public static boolean available(String boxId, int configuredStock) {
        return remaining(boxId, configuredStock) != 0;
    }

    /**
     * Consumes one unit of stock; returns false when out of stock (or
     * unlimited). Call only after the purchase is fully validated.
     */
    public static boolean consume(String boxId, int configuredStock, int restockMinutes) {
        if (configuredStock < 0) {
            return true;
        }
        StockEntry e = STOCK.computeIfAbsent(boxId,
                k -> new StockEntry(configuredStock, configuredStock, restockMinutes, Long.MAX_VALUE));
        if (e.remaining() <= 0) {
            return false;
        }
        int newRemaining = e.remaining() - 1;
        // Start the restock timer the moment stock first drops below full.
        long next = Long.MAX_VALUE;
        if (newRemaining < e.max() && e.restockMinutes() > 0 && e.nextRestockMs() == Long.MAX_VALUE) {
            next = System.currentTimeMillis() + e.restockMinutes() * 60_000L;
        }
        STOCK.put(boxId, new StockEntry(e.max(), newRemaining, e.restockMinutes(), next));
        return true;
    }

    /**
     * Refills exhausted stock whose restock timer elapsed. Called from the
     * server tick; {@code nowMs} is wall-clock ms (matches the timer set in
     * {@link #consume}).
     */
    public static void tick(long nowMs) {
        if (STOCK.isEmpty()) {
            return;
        }
        for (Map.Entry<String, StockEntry> entry : STOCK.entrySet()) {
            StockEntry e = entry.getValue();
            if (e.remaining() >= e.max() || e.nextRestockMs() == Long.MAX_VALUE) {
                continue;
            }
            if (nowMs >= e.nextRestockMs()) {
                STOCK.put(entry.getKey(), new StockEntry(e.max(), e.max(), e.restockMinutes(), Long.MAX_VALUE));
            }
        }
    }

    /** Drops all stock state (server stop / world unload). */
    public static void reset() {
        STOCK.clear();
    }
}
