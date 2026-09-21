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
     * v2.0.2-fix: a single per-key {@code compute} (atomic read-modify-write)
     * so the decrement and the refusal decision cannot interleave, matching
     * the documented thread-safety contract.
     */
    public static boolean consume(String boxId, int configuredStock) {
        if (configuredStock < 0) {
            return true;
        }
        boolean[] ok = {false};
        STOCK.compute(boxId, (k, cur) -> {
            int max = cur != null ? cur.max() : configuredStock;
            int remaining = cur != null ? cur.remaining() : configuredStock;
            if (remaining <= 0) {
                return new StockEntry(max, remaining);
            }
            ok[0] = true;
            return new StockEntry(max, remaining - 1);
        });
        return ok[0];
    }

    /** Drops all stock state (server stop / world unload). */
    public static void reset() {
        STOCK.clear();
    }
}
