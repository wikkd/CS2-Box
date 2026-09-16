package com.reclizer.csgobox.box;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quote-cap ladder derivation (报价上限): the tiers must be a strictly
 * ascending geometric ladder covering the price table, degrade gracefully on
 * degenerate tables, and never allow a stale value through validation.
 */
class QuoteCapsTest {

    private static PriceTable table(Object... entries) {
        JsonObject json = new JsonObject();
        for (int i = 0; i < entries.length; i += 2) {
            Object key = entries[i];
            Object value = entries[i + 1];
            if (value instanceof Long v) {
                json.addProperty((String) key, v);
            } else if (value instanceof Integer v) {
                json.addProperty((String) key, v);
            } else if (value instanceof int[] v) {
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                arr.add(v[0]);
                arr.add(v[1]);
                json.add((String) key, arr);
            } else {
                throw new IllegalArgumentException("bad price " + value);
            }
        }
        return PriceTable.parse(json, new java.util.ArrayList<>());
    }

    @Test
    @DisplayName("empty table has no priced tiers (unlimited only)")
    void emptyTable() {
        assertArrayEquals(QuoteCaps.NONE, QuoteCaps.tiers(PriceTable.EMPTY));
        assertArrayEquals(QuoteCaps.NONE, QuoteCaps.tiers((PriceTable) null));
        assertTrue(QuoteCaps.isAllowed(QuoteCaps.UNLIMITED, QuoteCaps.NONE));
        assertFalse(QuoteCaps.isAllowed(30, QuoteCaps.NONE));
    }

    @Test
    @DisplayName("all-zero table also degrades to unlimited only")
    void allZeroPrices() {
        PriceTable t = table("minecraft:air", 0, "minecraft:stone", 0);
        assertArrayEquals(QuoteCaps.NONE, QuoteCaps.tiers(t));
    }

    @Test
    @DisplayName("top tier is pinned to the most expensive entry")
    void topTierEqualsMax() {
        PriceTable t = table(
                "minecraft:arrow", 5,
                "minecraft:diamond", 100,
                "minecraft:elytra", 4000,
                "minecraft:gun#custom", new int[]{10000, 30000});
        int[] caps = QuoteCaps.tiers(t);
        assertEquals(30000, caps[caps.length - 1]);
        assertTrue(caps[0] > 0);
    }

    @Test
    @DisplayName("ladder is strictly ascending and contains no duplicates")
    void strictlyAscending() {
        PriceTable t = table(
                "minecraft:bread", 10,
                "minecraft:iron_ingot", 50,
                "minecraft:diamond_sword", 1500,
                "minecraft:netherite_sword", 4000,
                "minecraft:golden_gun", 30000);
        int[] caps = QuoteCaps.tiers(t);
        assertTrue(caps.length >= 1 && caps.length <= QuoteCaps.TIER_COUNT);
        for (int i = 1; i < caps.length; i++) {
            assertTrue(caps[i] > caps[i - 1], () -> "caps not ascending: " + Arrays.toString(caps));
        }
        for (int cap : caps) {
            assertTrue(QuoteCaps.isAllowed(cap, caps));
        }
    }

    @Test
    @DisplayName("degenerate tiny table collapses to a short ladder")
    void tinyMax() {
        int[] caps = QuoteCaps.tiersForMax(3, 4);
        assertArrayEquals(new int[]{2, 3}, caps);
        assertArrayEquals(new int[]{1}, QuoteCaps.tiersForMax(1, 4));
        assertArrayEquals(QuoteCaps.NONE, QuoteCaps.tiersForMax(0, 4));
    }

    @Test
    @DisplayName("ranges size the ladder by their max, not their min")
    void rangeMaxDrivesLadder() {
        PriceTable t = table("minecraft:diamond", new int[]{200, 400});
        int[] caps = QuoteCaps.tiers(t);
        assertEquals(400, caps[caps.length - 1]);
    }

    @Test
    @DisplayName("stale values from an older ladder fail validation and normalize")
    void staleCapIsRejected() {
        int[] caps = QuoteCaps.tiersForMax(800, 4);
        assertFalse(QuoteCaps.isAllowed(30, caps));
        assertFalse(QuoteCaps.isAllowed(64, caps));
        assertEquals(QuoteCaps.UNLIMITED, QuoteCaps.normalize(30, caps));
        int fresh = caps[0];
        assertTrue(QuoteCaps.isAllowed(fresh, caps));
        assertEquals(fresh, QuoteCaps.normalize(fresh, caps));
    }

    @Test
    @DisplayName("unlimited is always accepted regardless of the ladder")
    void unlimitedAlwaysAllowed() {
        int[] caps = QuoteCaps.tiersForMax(30000, 4);
        assertTrue(QuoteCaps.isAllowed(QuoteCaps.UNLIMITED, caps));
        assertTrue(QuoteCaps.isAllowed(QuoteCaps.UNLIMITED, QuoteCaps.NONE));
    }

    @Test
    @DisplayName("PriceTableRegistry derives tiers on publish and normalizes")
    void registryDerivesTiers() {
        try {
            PriceTable t = table("minecraft:bread", 10, "minecraft:elytra", 4000);
            PriceTableRegistry.set(t);
            int[] caps = PriceTableRegistry.quoteCaps();
            assertEquals(4000, caps[caps.length - 1]);
            assertTrue(PriceTableRegistry.isAllowedQuoteCap(caps[0]));
            assertFalse(PriceTableRegistry.isAllowedQuoteCap(30));
            assertEquals(QuoteCaps.UNLIMITED, PriceTableRegistry.normalizeQuoteCap(30));
            assertEquals(QuoteCaps.UNLIMITED, PriceTableRegistry.normalizeQuoteCap(QuoteCaps.UNLIMITED));
            // client-side ingest path (remote clients never read the file)
            PriceTableRegistry.setQuoteCaps(new int[]{100, 500});
            assertTrue(PriceTableRegistry.isAllowedQuoteCap(500));
            assertFalse(PriceTableRegistry.isAllowedQuoteCap(4000));
            PriceTableRegistry.setQuoteCaps(null);
            assertArrayEquals(QuoteCaps.NONE, PriceTableRegistry.quoteCaps());
        } finally {
            // never leak a non-default table into other tests
            PriceTableRegistry.set(PriceTable.EMPTY);
        }
    }
}