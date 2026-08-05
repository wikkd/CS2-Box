package com.reclizer.csgobox.logic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GradeMap} using String as T.
 */
final class GradeMapTest {

    private static final Predicate<String> NOT_EMPTY = s -> !s.isEmpty();
    private static final Function<String, String> IDENTITY = Function.identity();

    private static GradeMap<String> build(Map<String, Integer> itemMap) {
        return GradeMap.build(itemMap, NOT_EMPTY, IDENTITY);
    }

    @Test
    @DisplayName("build from null map produces empty GradeMap")
    void buildNull() {
        GradeMap<String> gm = build(null);
        assertTrue(gm.isEmpty());
        assertNull(gm.pickRandom(new Random(42), 1));
        assertNull(gm.findFallback(1));
    }

    @Test
    @DisplayName("build from empty map produces empty GradeMap")
    void buildEmpty() {
        GradeMap<String> gm = build(Map.of());
        assertTrue(gm.isEmpty());
    }

    @Test
    @DisplayName("build filters out invalid entries")
    void buildFiltersInvalid() {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("sword", 1);
        items.put("", 2);       // invalid — filtered
        items.put("shield", 3);
        GradeMap<String> gm = build(items);

        assertNotNull(gm.pickRandom(new Random(42), 1));
        assertNull(gm.pickRandom(new Random(42), 2)); // "" was filtered
        assertNotNull(gm.pickRandom(new Random(42), 3));
    }

    @Test
    @DisplayName("build filters out null grade entries")
    void buildFiltersNullGrade() {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("sword", 1);
        items.put("shield", null); // null grade — filtered
        GradeMap<String> gm = build(items);

        assertNotNull(gm.pickRandom(new Random(42), 1));
        assertNull(gm.pickRandom(new Random(42), 2));
    }

    @Test
    @DisplayName("pickRandom returns item of correct grade")
    void pickRandomCorrectGrade() {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("common1", 1);
        items.put("common2", 1);
        items.put("rare1", 3);
        GradeMap<String> gm = build(items);
        Random rng = new Random(42);

        for (int i = 0; i < 50; i++) {
            String pick = gm.pickRandom(rng, 1);
            assertTrue(pick.equals("common1") || pick.equals("common2"),
                    "Unexpected pick: " + pick);
        }
        for (int i = 0; i < 50; i++) {
            assertEquals("rare1", gm.pickRandom(rng, 3));
        }
    }

    @Test
    @DisplayName("pickRandom returns null for missing grade")
    void pickRandomMissingGrade() {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("sword", 1);
        GradeMap<String> gm = build(items);
        assertNull(gm.pickRandom(new Random(42), 5));
    }

    @Test
    @DisplayName("findFallback returns same-grade item first")
    void fallbackSameGrade() {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("common1", 1);
        items.put("rare1", 3);
        GradeMap<String> gm = build(items);
        assertEquals("rare1", gm.findFallback(3));
    }

    @Test
    @DisplayName("findFallback descends grades when target missing")
    void fallbackDescends() {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("common1", 1);
        items.put("uncommon1", 2);
        // grade 5 missing → should fall to grade 2 (nearest lower)
        GradeMap<String> gm = build(items);
        assertEquals("uncommon1", gm.findFallback(5));
    }

    @Test
    @DisplayName("findFallback returns any valid item as last resort")
    void fallbackLastResort() {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("epic1", 4);
        GradeMap<String> gm = build(items);
        // Target grade 1, no grade 1 items, descending finds nothing below 1,
        // last resort scans all → epic1
        assertEquals("epic1", gm.findFallback(1));
    }

    @Test
    @DisplayName("findFallback returns null for empty GradeMap")
    void fallbackEmpty() {
        GradeMap<String> gm = build(Map.of());
        assertNull(gm.findFallback(1));
    }

    @Test
    @DisplayName("copier function is applied to returned items")
    void copierApplied() {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("sword", 1);
        GradeMap<String> gm = GradeMap.build(items, NOT_EMPTY, s -> s + "_copy");

        assertEquals("sword_copy", gm.pickRandom(new Random(42), 1));
        assertEquals("sword_copy", gm.findFallback(1));
    }

    @Test
    @DisplayName("multiple items per grade all reachable")
    void multiplePerGrade() {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("a", 2);
        items.put("b", 2);
        items.put("c", 2);
        GradeMap<String> gm = build(items);
        Random rng = new Random(42);

        boolean sawA = false, sawB = false, sawC = false;
        for (int i = 0; i < 200; i++) {
            String pick = gm.pickRandom(rng, 2);
            if ("a".equals(pick)) sawA = true;
            if ("b".equals(pick)) sawB = true;
            if ("c".equals(pick)) sawC = true;
        }
        assertTrue(sawA && sawB && sawC, "All items should be reachable");
    }
}
