package com.reclizer.csgobox.logic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for {@link GradeMapCache}, which is the single shared grade-pool
 * cache used by both the single-open and bulk-open server paths.
 */
final class GradeMapCacheTest {

    private static GradeMap<String> build(String item) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put(item, 1);
        return GradeMap.build(m, s -> !s.isEmpty(), Function.identity());
    }

    @Test
    @DisplayName("get with same boxId does not rebuild")
    void getReusesCachedValue() {
        AtomicInteger builds = new AtomicInteger();
        GradeMap<String> first = GradeMapCache.get("box_a", () -> {
            builds.incrementAndGet();
            return build("sword");
        });
        GradeMap<String> second = GradeMapCache.get("box_a", () -> {
            builds.incrementAndGet();
            return build("sword");
        });
        assertSame(first, second);
        assertEquals(1, builds.get());
        GradeMapCache.invalidateAll();
    }

    @Test
    @DisplayName("different boxIds build independent maps")
    void distinctKeysDoNotShare() {
        AtomicInteger builds = new AtomicInteger();
        GradeMapCache.get("box_a", () -> {
            builds.incrementAndGet();
            return build("sword");
        });
        GradeMapCache.get("box_b", () -> {
            builds.incrementAndGet();
            return build("shield");
        });
        assertEquals(2, builds.get());
        GradeMapCache.invalidateAll();
    }

    @Test
    @DisplayName("invalidate drops only one key")
    void invalidateAffectsSingleKey() {
        GradeMap<String> a = GradeMapCache.get("box_a", () -> build("sword"));
        GradeMap<String> b = GradeMapCache.get("box_b", () -> build("shield"));
        GradeMapCache.invalidate("box_a");
        GradeMap<String> rebuilt = GradeMapCache.get("box_a", () -> build("dagger"));
        GradeMap<String> untouchedB = GradeMapCache.get("box_b", () -> build("axe"));
        assertEquals("dagger", rebuilt.pickRandom(nullSafeRandom(), 1));
        assertSame(b, untouchedB); // b was never invalidated
        GradeMapCache.invalidateAll();
    }

    @Test
    @DisplayName("invalidateAll clears every key (all rebuilt on next get)")
    void invalidateAllClearsEverything() {
        GradeMap<String> a = GradeMapCache.get("box_a", () -> build("sword"));
        GradeMap<String> b = GradeMapCache.get("box_b", () -> build("shield"));
        GradeMapCache.invalidateAll();
        GradeMap<String> rebuiltA = GradeMapCache.get("box_a", () -> build("dagger"));
        GradeMap<String> rebuiltB = GradeMapCache.get("box_b", () -> build("axe"));
        // Both must be rebuilt (new instances) after invalidateAll.
        org.junit.jupiter.api.Assertions.assertNotSame(a, rebuiltA, "box_a must rebuild after invalidateAll");
        org.junit.jupiter.api.Assertions.assertNotSame(b, rebuiltB, "box_b must rebuild after invalidateAll");
        GradeMapCache.invalidateAll();
    }

    private static java.util.Random nullSafeRandom() {
        return new java.util.Random(42);
    }
}