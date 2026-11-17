package com.reclizer.csgobox.villager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link VillagerTradePool} — the per-villager random
 * mineral-buy pool gating.
 */
final class VillagerTradePoolTest {

    private static final String[] MINERALS = {
            VillagerTradePool.COAL, VillagerTradePool.COPPER, VillagerTradePool.IRON,
            VillagerTradePool.EMERALD, VillagerTradePool.GOLD, VillagerTradePool.DIAMOND};

    /** Counts active minerals for one seed+level over the full id space. */
    private static int activeCount(long seed, int level, boolean poolEnabled) {
        int n = 0;
        for (String mineral : MINERALS) {
            if (VillagerTradePool.isActive(seed, level, mineral, poolEnabled)) {
                n++;
            }
        }
        return n;
    }

    @Test
    @DisplayName("level 1 activates exactly 2 of the 5-mineral pool")
    void level1DrawsTwoOfFive() {
        for (long seed = 0; seed < 200; seed++) {
            assertEquals(2, activeCount(seed, 1, true), "seed=" + seed);
        }
    }

    @Test
    @DisplayName("levels 2/3/5 activate exactly 1 buy; level 4 activates none")
    void otherLevelDrawCounts() {
        for (long seed = 0; seed < 200; seed++) {
            assertEquals(1, activeCount(seed, 2, true), "seed=" + seed);
            assertEquals(1, activeCount(seed, 3, true), "seed=" + seed);
            assertEquals(1, activeCount(seed, 5, true), "seed=" + seed);
            assertEquals(0, activeCount(seed, 4, true), "seed=" + seed);
        }
    }

    @Test
    @DisplayName("the selection is deterministic per seed+level")
    void deterministic() {
        long seed = 0xC5B0B0L; // arbitrary constant seed
        for (int level = 1; level <= 5; level++) {
            boolean[] first = new boolean[MINERALS.length];
            for (int i = 0; i < MINERALS.length; i++) {
                first[i] = VillagerTradePool.isActive(seed, level, MINERALS[i], true);
            }
            for (int repeat = 0; repeat < 50; repeat++) {
                for (int i = 0; i < MINERALS.length; i++) {
                    assertEquals(first[i],
                            VillagerTradePool.isActive(seed, level, MINERALS[i], true),
                            "level=" + level + " mineral=" + MINERALS[i]);
                }
            }
        }
    }

    @Test
    @DisplayName("every pool mineral shows up across seeds (no dead entries)")
    void allPoolMineralsReachable() {
        String[][] pools = {
                {VillagerTradePool.COAL, VillagerTradePool.COPPER, VillagerTradePool.IRON,
                        VillagerTradePool.EMERALD, VillagerTradePool.GOLD},
                {VillagerTradePool.IRON, VillagerTradePool.EMERALD, VillagerTradePool.GOLD},
                {VillagerTradePool.GOLD, VillagerTradePool.DIAMOND},
                {VillagerTradePool.IRON, VillagerTradePool.EMERALD,
                        VillagerTradePool.GOLD, VillagerTradePool.DIAMOND},
        };
        int[] levels = {1, 2, 3, 5};
        for (int p = 0; p < pools.length; p++) {
            for (String mineral : pools[p]) {
                boolean seen = false;
                for (long seed = 0; seed < 500 && !seen; seed++) {
                    seen = VillagerTradePool.isActive(seed, levels[p], mineral, true);
                }
                assertTrue(seen, levels[p] + "/" + mineral + " never drawn");
            }
        }
    }

    @Test
    @DisplayName("pool disabled restores the pre-pool fixed lists exactly")
    void disabledRestoresOriginalLists() {
        for (long seed = 0; seed < 200; seed++) {
            for (String mineral : MINERALS) {
                boolean active1 = VillagerTradePool.isActive(seed, 1, mineral, false);
                boolean expect1 = mineral.equals(VillagerTradePool.IRON)
                        || mineral.equals(VillagerTradePool.EMERALD);
                assertEquals(expect1, active1, "L1 " + mineral);
                boolean active2 = VillagerTradePool.isActive(seed, 2, mineral, false);
                assertEquals(VillagerTradePool.GOLD.equals(mineral), active2, "L2 " + mineral);
                boolean active3 = VillagerTradePool.isActive(seed, 3, mineral, false);
                assertEquals(VillagerTradePool.DIAMOND.equals(mineral), active3, "L3 " + mineral);
                assertFalse(VillagerTradePool.isActive(seed, 4, mineral, false), "L4 " + mineral);
                assertFalse(VillagerTradePool.isActive(seed, 5, mineral, false), "L5 " + mineral);
            }
        }
    }

    @Test
    @DisplayName("different villagers (seeds) get different selections at the same level")
    void selectionsVaryAcrossVillagers() {
        // with 3 combos at level 2 one single variant would mean no pooling
        long firstSeed = 0;
        StringBuilder first = new StringBuilder();
        for (String mineral : MINERALS) {
            if (VillagerTradePool.isActive(firstSeed, 2, mineral, true)) {
                first.append(mineral);
            }
        }
        boolean sawDifferent = false;
        for (long seed = 1; seed < 200 && !sawDifferent; seed++) {
            StringBuilder other = new StringBuilder();
            for (String mineral : MINERALS) {
                if (VillagerTradePool.isActive(seed, 2, mineral, true)) {
                    other.append(mineral);
                }
            }
            sawDifferent = !first.toString().equals(other.toString());
        }
        assertTrue(sawDifferent, "level 2 always picks the same mineral");
    }

    @Test
    @DisplayName("pool_enabled parses, defaults on, and round-trips via toJson")
    void poolEnabledConfigRoundTrip() {
        List<String> issues = new ArrayList<>();
        VillagerPricingConfig defaults = VillagerPricingConfig.parse(
                JsonParser.parseString("{}").getAsJsonObject(), issues);
        assertTrue(defaults.poolEnabled(), "pool_enabled should default to true");
        assertTrue(issues.isEmpty(), issues.toString());

        JsonObject off = JsonParser.parseString("{\"pool_enabled\": false}").getAsJsonObject();
        assertFalse(VillagerPricingConfig.parse(off, issues).poolEnabled());

        JsonObject json = JsonParser.parseString(
                VillagerPricingConfig.DEFAULT.toJson()).getAsJsonObject();
        assertTrue(json.has("pool_enabled"));
        assertTrue(json.get("pool_enabled").getAsBoolean());
        // new minerals ship with defaults in the default file
        assertEquals(15, json.getAsJsonObject("fallbacks").get("minecraft:coal").getAsInt());
        assertEquals(25, json.getAsJsonObject("fallbacks").get("minecraft:copper_ingot").getAsInt());
    }
}
