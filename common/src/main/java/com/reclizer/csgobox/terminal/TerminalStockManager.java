package com.reclizer.csgobox.terminal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v2.0.1 server-side terminal stock: a global (not per-player) remaining
 * stock per box id, decremented on each successful terminal purchase.
 * {@code stock == -1} (the default) means unlimited and nothing is tracked;
 * an exhausted terminal stays empty until the server restarts.
 *
 * <p>In-memory only (documented): stock resets to full on server restart.
 * The map is bounded by the number of configured stock-limited terminals.
 * Thread-safety mirrors {@code GradeMapCache}: the server tick and the netty
 * worker may both touch it.</p>
 */
public final class TerminalStockManager {

    private record StockEntry(int max, int remaining) {
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
        return STOCK.computeIfAbsent(boxId, k -> new StockEntry(configuredStock, configuredStock))
                .remaining();
    }

    /** Whether a stock-limited terminal may still sell (stock == -1 or > 0). */
    public static boolean available(String boxId, int configuredStock) {
        return remaining(boxId, configuredStock) != 0;
    }

    /**
     * Consumes one unit of stock; returns false when out of stock (or
     * unlimited). Call only after the purchase is fully validated.
     */
    public static boolean consume(String boxId, int configuredStock) {
        if (configuredStock < 0) {
            return true;
        }
        StockEntry e = STOCK.computeIfAbsent(boxId, k -> new StockEntry(configuredStock, configuredStock));
        if (e.remaining() <= 0) {
            return false;
        }
        STOCK.put(boxId, new StockEntry(e.max(), e.remaining() - 1));
        return true;
    }

    /** Drops all stock state (server stop / world unload). */
    public static void reset() {
        STOCK.clear();
    }
}
