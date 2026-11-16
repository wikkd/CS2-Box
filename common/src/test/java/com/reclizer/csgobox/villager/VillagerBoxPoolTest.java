package com.reclizer.csgobox.villager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link VillagerBoxPool} — the per-villager random
 * box / terminal sale pool selection and pricing split.
 */
final class VillagerBoxPoolTest {

    private static final List<String> BOXES = List.of(
            "csgobox:csgo_box", "csgobox:sbw_crate", "csgobox:tacz_crate",
            "csgobox:normal_crate", "csgobox:gun_crate");

    @Test
    @DisplayName("pickBox is deterministic per seed regardless of iteration order")
    void deterministicRegardlessOfOrder() {
        long seed = 0xB0CAFE;
        String first = VillagerBoxPool.pickBox(seed, BOXES);
        for (int repeat = 0; repeat < 20; repeat++) {
            assertEquals(first, VillagerBoxPool.pickBox(seed, BOXES));
        }
        // reversed iteration order must not change the pick
        List<String> reversed = new ArrayList<>(BOXES);
        java.util.Collections.reverse(reversed);
        assertEquals(first, VillagerBoxPool.pickBox(seed, reversed));
        // shuffled orders must not change the pick either (tie-break by id)
        for (long salt = 0; salt < 50; salt++) {
            List<String> shuffled = new ArrayList<>(BOXES);
            java.util.Collections.shuffle(shuffled, new java.util.Random(salt));
            assertEquals(first, VillagerBoxPool.pickBox(seed, shuffled));
        }
    }

    @Test
    @DisplayName("the assigned box has no level component: same seed every level")
    void assignmentIsLevelFree() {
        // the pool pick never receives a level — assert the seed alone decides
        long seed = 0xC5B0B0L;
        assertEquals(VillagerBoxPool.pickBox(seed, BOXES), VillagerBoxPool.pickBox(seed, BOXES));
        // and different villagers pick different boxes across the pool
        Set<String> picks = new HashSet<>();
        for (long seed2 = 0; seed2 < 200; seed2++) {
            picks.add(VillagerBoxPool.pickBox(seed2, BOXES));
        }
        assertTrue(picks.size() > 1, "every villager got the same box");
    }

    @Test
    @DisplayName("pickBox returns null for an empty pool and a member for non-empty")
    void emptyPoolAndMembership() {
        assertNull(VillagerBoxPool.pickBox(42, List.of()));
        assertNull(VillagerBoxPool.pickBox(42, null));
        String pick = VillagerBoxPool.pickBox(42, List.of("csgobox:csgo_box"));
        assertEquals("csgobox:csgo_box", pick);
        for (long seed = 0; seed < 200; seed++) {
            String p = VillagerBoxPool.pickBox(seed, BOXES);
            assertTrue(BOXES.contains(p), "picked a non-member: " + p);
        }
    }

    @Test
    @DisplayName("every candidate box is assigned to at least one villager")
    void everyBoxReachable() {
        for (String box : BOXES) {
            boolean seen = false;
            for (long seed = 0; seed < 2000 && !seen; seed++) {
                seen = box.equals(VillagerBoxPool.pickBox(seed, BOXES));
            }
            assertTrue(seen, box + " never assigned");
        }
    }

    @Test
    @DisplayName("points part caps at 64 and gold change covers the remainder")
    void priceSplit() {
        assertEquals(8, VillagerBoxPool.pointsPart(8));
        assertEquals(64, VillagerBoxPool.pointsPart(64));
        assertEquals(64, VillagerBoxPool.pointsPart(3200));
        // price ≤ 64 → no gold change
        assertEquals(0, VillagerBoxPool.goldChange(8, 50));
        assertEquals(0, VillagerBoxPool.goldChange(64, 50));
        // price 100, gold base 50: points 64, remainder 36 → 1 ingot
        assertEquals(1, VillagerBoxPool.goldChange(100, 50));
        // price 3200: remainder 3136 → ceil(3136/50) = 63
        assertEquals(63, VillagerBoxPool.goldChange(3200, 50));
        // no gold base (unpriced gold) → no change → only ≤64 prices sellable
        assertEquals(0, VillagerBoxPool.goldChange(3200, 0));
        assertFalse(VillagerBoxPool.sellable(3200, 0));
        assertTrue(VillagerBoxPool.sellable(100, 50));
        // change beyond one gold stack caps the pool out
        assertFalse(VillagerBoxPool.sellable(64 + 64 * 50 + 1, 50));
        assertFalse(VillagerBoxPool.sellable(0, 50));
        assertTrue(VillagerBoxPool.sellable(1, 50));
    }

    @Test
    @DisplayName("evPrice floors to at least one point")
    void evPriceMath() {
        assertEquals(1, VillagerBoxPool.evPrice(0.4, 1.0));
        assertEquals(150, VillagerBoxPool.evPrice(150.0, 1.0));
        assertEquals(300, VillagerBoxPool.evPrice(150.0, 2.0));
        assertEquals(0, VillagerBoxPool.evPrice(150.0, 0.0)); // ratio 0 → dead pool
    }

    @Test
    @DisplayName("item spec parsing: count midpoints and enchant detection")
    void itemSpecParsing() {
        assertEquals(1.0, VillagerBoxPool.meanCount(null));
        assertEquals(1.0, VillagerBoxPool.meanCount(""));
        assertEquals(1.0, VillagerBoxPool.meanCount("not json"));
        assertEquals(1.0, VillagerBoxPool.meanCount("{}"));
        assertEquals(1.0, VillagerBoxPool.meanCount("{\"c\":[1,1]}"));
        assertEquals(1.5, VillagerBoxPool.meanCount("{\"c\":[1,2]}"));
        assertEquals(4.0, VillagerBoxPool.meanCount("{\"c\":[3,5]}"));
        assertFalse(VillagerBoxPool.hasRandomEnchant("{\"c\":[1,3]}"));
        assertTrue(VillagerBoxPool.hasRandomEnchant("{\"e\":true}"));
        assertTrue(VillagerBoxPool.hasRandomEnchant("{\"c\":[1,2],\"e\":{\"lvl\":1}}"));
        assertFalse(VillagerBoxPool.hasRandomEnchant(null));
        assertFalse(VillagerBoxPool.hasLootTable(null));
        assertFalse(VillagerBoxPool.hasLootTable("{\"c\":[1,3]}"));
        assertTrue(VillagerBoxPool.hasLootTable("{\"l\":\"minecraft:blocks/stone\"}"));
        assertTrue(VillagerBoxPool.hasRandomEnchant("{\"l\":\"x\",\"e\":true}"));
    }

    @Test
    @DisplayName("scores differ across boxes for the same villager (argmax is meaningful)")
    void scoresDiffer() {
        long seed = 0xC5B0B0L;
        Set<Long> scores = new HashSet<>();
        for (String box : BOXES) {
            scores.add(VillagerBoxPool.assignScore(seed, box));
        }
        assertEquals(BOXES.size(), scores.size(), "hash collision in test pool");
    }
}
