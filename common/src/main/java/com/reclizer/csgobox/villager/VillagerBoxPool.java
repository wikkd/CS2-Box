package com.reclizer.csgobox.villager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collection;

/**
 * The arms-dealer's random box / terminal sale pool (v2.x): the sell offers
 * are drawn from the live {@code BoxRegistry} (config/csbox/*.json) instead
 * of the generic fixed items, so newly added boxes become villager-sellable
 * automatically.
 *
 * <p>Selection: each villager deterministically picks ONE box from the
 * candidate pool via an argmax over {@code hash(UUID, boxId)} — the seed is
 * the villager's UUID <b>without any level component</b>, so the assigned
 * box survives level-ups, offer rebuilds and server restarts (unlike the
 * mineral-buy pool in {@link VillagerTradePool}, which re-rolls per level).
 * Adding new boxes can re-assign only the villagers whose new box hashes
 * higher; existing assignments are otherwise untouched.</p>
 *
 * <p>Pricing: the sale price is the box contents' table-price expectation
 * (grade chances × item weights × mean count × mean table price — the same
 * odds source as the tooltips / JEI / editor EV panel) × the
 * {@code box_sell_ratio} config knob (default 1.0 = zero expected margin, so
 * "buy box → open" can never arbitrage the villager). Everything is derived
 * from deterministic midpoints (no random sampling) because on 26.x the
 * item rewrite (loot function) and the cost providers evaluate separately
 * and must agree. Boxes whose contents can't be priced faithfully — a
 * {@code loot_table} entry (unpriced) or a random-enchant spec — are excluded
 * from the pool.</p>
 *
 * <p>Payment: prices above the 64-point stack cap are split into a full
 * points stack + gold-ingot change ({@code goldChange}, the key1/key2
 * convention); {@code goldBase} is the deterministic midpoint of the table's
 * gold price. A price whose change would exceed one gold stack caps the pool
 * out.</p>
 *
 * <p>Pure Java (no Minecraft imports) so it compiles in the common module
 * and behaves identically on all platforms.</p>
 */
public final class VillagerBoxPool {

    private VillagerBoxPool() {
    }

    /** Maximum Armory Points payable in one input stack ({@link VillagerPricing}). */
    public static final int POINT_STACK_LIMIT = VillagerPricing.POINT_STACK_LIMIT;

    /**
     * Deterministic argmax pick over the candidate box ids. Ties are broken
     * by id so the result never depends on the caller's iteration order.
     * Returns {@code null} when the pool is empty.
     */
    public static String pickBox(long villagerSeed, Collection<String> boxIds) {
        if (boxIds == null || boxIds.isEmpty()) {
            return null;
        }
        String best = null;
        long bestScore = Long.MIN_VALUE;
        for (String id : boxIds) {
            long score = assignScore(villagerSeed, id);
            if (best == null || score > bestScore
                    || (score == bestScore && id.compareTo(best) < 0)) {
                best = id;
                bestScore = score;
            }
        }
        return best;
    }

    /** Stable per-villager box score: the seed has no level component. */
    public static long assignScore(long villagerSeed, String boxId) {
        long h = villagerSeed ^ (boxId.hashCode() * 0x9E3779B97F4A7C15L);
        h ^= h >>> 32;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }

    /** The points-stack part of a sale price (capped at one stack). */
    public static int pointsPart(int price) {
        return Math.min(POINT_STACK_LIMIT, Math.max(1, price));
    }

    /**
     * Gold-ingot change for the value beyond one points stack
     * ({@code goldBase} = deterministic table midpoint of one gold ingot).
     * Returns 0 when the price fits the points stack.
     */
    public static int goldChange(int price, int goldBase) {
        if (goldBase <= 0) {
            return 0;
        }
        int remainder = price - pointsPart(price);
        if (remainder <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) remainder / goldBase);
    }

    /**
     * Whether a price is payable in one points stack (+ one gold stack of
     * change when it exceeds the stack). No change currency ({@code goldBase}
     * ≤ 0) caps sales at one points stack.
     */
    public static boolean sellable(int price, int goldBase) {
        if (price < 1) {
            return false;
        }
        if (price <= POINT_STACK_LIMIT) {
            return true;
        }
        if (goldBase <= 0) {
            return false;
        }
        int gold = goldChange(price, goldBase);
        return gold >= 1 && gold <= POINT_STACK_LIMIT;
    }

    /** EV × ratio, floored to at least 1 point (a zero ratio prices the
     *  box out of the pool at 0 — the callers also gate it). */
    public static int evPrice(double ev, double ratio) {
        double p = ev * Math.max(0.0, ratio);
        if (p <= 0.0) {
            return 0;
        }
        double f = Math.floor(p);
        if (f < 1.0) {
            return 1;
        }
        return f > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) f;
    }

    /**
     * Mean count for one stored item from its {@code csgobox:item_spec}
     * marker ({@code {"c":[min,max]}} — written by the loaders' count-range
     * parsing). No spec / unparseable spec → 1.
     */
    public static double meanCount(String itemSpec) {
        JsonObject spec = parseSpec(itemSpec);
        if (spec == null || !spec.has("c")) {
            return 1.0;
        }
        try {
            JsonElement c = spec.get("c");
            if (c.isJsonArray() && c.getAsJsonArray().size() == 2) {
                int min = c.getAsJsonArray().get(0).getAsInt();
                int max = c.getAsJsonArray().get(1).getAsInt();
                if (min >= 1 && max >= min) {
                    return (min + (double) max) / 2.0;
                }
            }
        } catch (RuntimeException ignored) {
            // tolerant: malformed spec behaves like no spec
        }
        return 1.0;
    }

    /**
     * Whether the item spec carries a random enchant ({@code "e"} field) —
     * such items can't be priced faithfully from the base-item table entry,
     * so boxes containing them stay out of the sale pool.
     */
    public static boolean hasRandomEnchant(String itemSpec) {
        JsonObject spec = parseSpec(itemSpec);
        return spec != null && spec.has("e");
    }

    /**
     * Whether the item spec is a loot-table placeholder ({@code "l"} field) —
     * its value rolls at open time and can't be priced deterministically;
     * boxes containing such entries stay out of the sale pool.
     */
    public static boolean hasLootTable(String itemSpec) {
        JsonObject spec = parseSpec(itemSpec);
        return spec != null && spec.has("l");
    }

    private static JsonObject parseSpec(String itemSpec) {
        if (itemSpec == null || itemSpec.isEmpty()) {
            return null;
        }
        try {
            JsonElement el = JsonParser.parseString(itemSpec);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
