package com.reclizer.csgobox.box;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PriceTable} — the central terminal price table
 * ({@code config/csbox/_prices.json}).
 */
final class PriceTableTest {

    private static PriceTable parse(String json, List<String> issues) {
        JsonObject obj = json == null ? null : JsonParser.parseString(json).getAsJsonObject();
        return PriceTable.parse(obj, issues);
    }

    @Test
    @DisplayName("flat item-id entries are stored and looked up")
    void flatEntries() {
        List<String> issues = new ArrayList<>();
        PriceTable t = parse("""
                {"minecraft:diamond_sword": 1500, "minecraft:arrow": 200}
                """, issues);
        assertTrue(issues.isEmpty(), issues.toString());
        assertEquals(2, t.size());
        assertEquals(1500, t.lookup("minecraft:diamond_sword"));
        assertEquals(200, t.lookup("minecraft:arrow"));
        assertEquals(PriceTable.UNPRICED, t.lookup("minecraft:stick"));
        assertEquals(PriceTable.UNPRICED, t.lookup(null));
    }

    @Test
    @DisplayName("variant sub-key is preferred, then plain-id fallback")
    void variantPrecedence() {
        List<String> issues = new ArrayList<>();
        PriceTable t = parse("""
                {
                  "tacz:modern_kinetic_gun": 500,
                  "tacz:modern_kinetic_gun#tacz:deagle_golden": 30000,
                  "tacz:ammo#tacz:12g": 1500
                }
                """, issues);
        assertTrue(issues.isEmpty(), issues.toString());
        // Variant entry wins for that gun…
        assertEquals(30000, t.lookup("tacz:modern_kinetic_gun", "tacz:deagle_golden"));
        assertEquals(30000, t.lookup("tacz:modern_kinetic_gun#tacz:deagle_golden"));
        // …other guns fall back to the plain-id price…
        assertEquals(500, t.lookup("tacz:modern_kinetic_gun", "tacz:ak47"));
        // …and a variant-only ammo has no plain fallback.
        assertEquals(1500, t.lookup("tacz:ammo", "tacz:12g"));
        assertEquals(PriceTable.UNPRICED, t.lookup("tacz:ammo"));
        assertEquals(PriceTable.UNPRICED, t.lookup("tacz:ammo", "tacz:9mm"));
    }

    @Test
    @DisplayName("invalid keys and values are reported and skipped")
    void invalidEntries() {
        List<String> issues = new ArrayList<>();
        PriceTable t = parse("""
                {
                  "minecraft:diamond": 1,
                  "NOT_A_KEY": 5,
                  "minecraft:stone": -2,
                  "minecraft:iron": 2.5,
                  "minecraft:coal": "abc",
                  "minecraft:emerald": true
                }
                """, issues);
        assertFalse(issues.isEmpty());
        assertEquals(5, issues.size()); // 1 bad key + 4 bad values
        // The single valid entry survives; every malformed one is dropped.
        assertEquals(1, t.size());
        assertEquals(1, t.lookup("minecraft:diamond"));
        assertEquals(PriceTable.UNPRICED, t.lookup("minecraft:stone"));
        assertEquals(PriceTable.UNPRICED, t.lookup("minecraft:iron"));
    }

    @Test
    @DisplayName("zero is a legal price (matches the box schema's old minimum)")
    void zeroAllowed() {
        List<String> issues = new ArrayList<>();
        PriceTable t = parse("""
                {"minecraft:air": 0}
                """, issues);
        assertTrue(issues.isEmpty(), issues.toString());
        assertEquals(0, t.lookup("minecraft:air"));
    }

    @Test
    @DisplayName("null root is an error and yields the empty table")
    void nullRoot() {
        List<String> issues = new ArrayList<>();
        PriceTable t = parse(null, issues);
        assertFalse(issues.isEmpty());
        assertTrue(t.isEmpty());
        assertSame(PriceTable.EMPTY, t);
    }

    @Test
    @DisplayName("variant id is read from a legacy NBT tag string")
    void variantIdFromTag() {
        JsonObject gun = JsonParser.parseString("""
                {"id": "tacz:modern_kinetic_gun", "tag": "{GunId:\\"tacz:deagle_golden\\"}"}
                """).getAsJsonObject();
        assertEquals("tacz:deagle_golden", PriceTable.variantId(gun).orElseThrow());

        JsonObject ammo = JsonParser.parseString("""
                {"id": "tacz:ammo", "tag": "{AmmoId:\\"tacz:12g\\"}"}
                """).getAsJsonObject();
        assertEquals("tacz:12g", PriceTable.variantId(ammo).orElseThrow());

        JsonObject plain = JsonParser.parseString("""
                {"id": "minecraft:diamond"}
                """).getAsJsonObject();
        assertTrue(PriceTable.variantId(plain).isEmpty());
        assertTrue(PriceTable.variantId(null).isEmpty());
    }

    @Test
    @DisplayName("hash is stable for the same content and changes on edits")
    void hashStability() {
        List<String> issuesA = new ArrayList<>();
        List<String> issuesB = new ArrayList<>();
        PriceTable a = parse("""
                {"minecraft:diamond": 5, "minecraft:iron": 3}
                """, issuesA);
        PriceTable b = parse("""
                {"minecraft:iron": 3, "minecraft:diamond": 5}
                """, issuesB);
        assertEquals(a.hash(), b.hash(), "key order must not change the hash");

        List<String> issuesC = new ArrayList<>();
        PriceTable c = parse("""
                {"minecraft:diamond": 6, "minecraft:iron": 3}
                """, issuesC);
        assertNotEquals(a.hash(), c.hash(), "a price change must change the hash");
        assertNotEquals(PriceTable.EMPTY.hash(), a.hash());
    }

    @Test
    @DisplayName("recycler payout is 90% of the table price, rounded up")
    void recycleYield() {
        assertEquals(0, PriceTable.recycleYield(0), "zero-priced item pays nothing");
        assertEquals(0, PriceTable.recycleYield(PriceTable.UNPRICED), "unpriced item pays nothing");
        assertEquals(1, PriceTable.recycleYield(1), "ceil(0.9) -> 1");
        assertEquals(2, PriceTable.recycleYield(2), "ceil(1.8) -> 2");
        assertEquals(9, PriceTable.recycleYield(9), "ceil(8.1) -> 9");
        assertEquals(9, PriceTable.recycleYield(10), "exact 90% of 10 is 9");
        assertEquals(10, PriceTable.recycleYield(11), "ceil(9.9) -> 10");
        assertEquals(18, PriceTable.recycleYield(20), "exact 90% of 20 is 18");
        assertEquals(4050, PriceTable.recycleYield(4500), "4000-price terminal item");
        assertEquals(27, PriceTable.recycleYield(30), "grade5 default 30 -> 27");
    }

    @Test
    @DisplayName("empty and missing tables behave identically")
    void emptyTable() {
        assertTrue(PriceTable.EMPTY.isEmpty());
        assertEquals(0, PriceTable.EMPTY.size());
        assertEquals(PriceTable.UNPRICED, PriceTable.EMPTY.lookup("minecraft:diamond"));
    }

    @Test
    @DisplayName("range values [min, max] are parsed and looked up as ranges")
    void rangeEntries() {
        List<String> issues = new ArrayList<>();
        PriceTable t = parse("""
                {
                  "minecraft:diamond_sword": 1500,
                  "minecraft:arrow": [200, 400],
                  "tacz:modern_kinetic_gun#tacz:ak47": [2000, 2500]
                }
                """, issues);
        assertTrue(issues.isEmpty(), issues.toString());
        assertEquals(3, t.size());

        // Fixed values are ranges with min == max.
        PriceRange fixed = t.lookupRange("minecraft:diamond_sword", null);
        assertEquals(new PriceRange(1500, 1500), fixed);
        assertTrue(fixed.isFixed());

        PriceRange range = t.lookupRange("minecraft:arrow", null);
        assertEquals(new PriceRange(200, 400), range);
        assertFalse(range.isFixed());

        // Variant preference applies to ranges too.
        assertEquals(new PriceRange(2000, 2500),
                t.lookupRange("tacz:modern_kinetic_gun", "tacz:ak47"));

        // Compatibility int view resolves a range to its minimum.
        assertEquals(200, t.lookup("minecraft:arrow"));
        assertEquals(PriceTable.UNPRICED, t.lookup("minecraft:stick"));
        assertNull(t.lookupRange("minecraft:stone"), "absent entry has no range");
    }

    @Test
    @DisplayName("range sampling stays inside [min, max] and fixed prices sample themselves")
    void rangeSampling() {
        PriceRange fixed = new PriceRange(1500, 1500);
        assertEquals(1500, fixed.sample(b -> 12345), "fixed range ignores the bound");

        PriceRange range = new PriceRange(200, 400);
        // Bound must be max - min + 1 = 201; the "sample" is min + (bound-1) = max.
        assertEquals(400, range.sample(b -> b - 1));
        assertEquals(200, range.sample(b -> 0));

        java.util.Random rnd = new java.util.Random(42L);
        for (int i = 0; i < 1000; i++) {
            int v = range.sample(rnd::nextInt);
            assertTrue(v >= 200 && v <= 400, "sample " + v + " outside [200, 400]");
        }
    }

    @Test
    @DisplayName("malformed ranges are reported and skipped")
    void invalidRanges() {
        List<String> issues = new ArrayList<>();
        PriceTable t = parse("""
                {
                  "minecraft:good": [10, 20],
                  "minecraft:empty": [],
                  "minecraft:single": [50],
                  "minecraft:triple": [1, 2, 3],
                  "minecraft:reversed": [5, 2],
                  "minecraft:negative": [-1, 5],
                  "minecraft:fractional": [1.5, 2],
                  "minecraft:nonnumber": ["a", 2]
                }
                """, issues);
        assertEquals(7, issues.size(), issues.toString());
        assertEquals(1, t.size(), "only the valid range survives");
        assertEquals(new PriceRange(10, 20), t.lookupRange("minecraft:good"));
        assertEquals(PriceTable.UNPRICED, t.lookup("minecraft:reversed"));
    }

    @Test
    @DisplayName("hash changes when a fixed price becomes a range or the range moves")
    void rangeHash() {
        List<String> issuesA = new ArrayList<>();
        PriceTable a = parse("{\"minecraft:diamond\": 100}", issuesA);
        List<String> issuesB = new ArrayList<>();
        PriceTable b = parse("{\"minecraft:diamond\": [100, 200]}", issuesB);
        assertNotEquals(a.hash(), b.hash(), "fixed vs range must differ");

        List<String> issuesC = new ArrayList<>();
        PriceTable c = parse("{\"minecraft:diamond\": [100, 201]}", issuesC);
        assertNotEquals(b.hash(), c.hash(), "a range endpoint change must change the hash");
    }

    @Test
    @DisplayName("recycler yield samples the range then applies the 90% rule")
    void recycleYieldRange() {
        java.util.Random rnd = new java.util.Random(7L);
        assertEquals(9, PriceTable.recycleYield(new PriceRange(10, 10), rnd::nextInt));
        // nextBounded controls the draw: bound-1 -> max, 0 -> min.
        assertEquals(PriceTable.recycleYield(20), PriceTable.recycleYield(new PriceRange(10, 20), b -> b - 1));
        assertEquals(PriceTable.recycleYield(10), PriceTable.recycleYield(new PriceRange(10, 20), b -> 0));
        assertEquals(0, PriceTable.recycleYield(PriceRange.UNPRICED, rnd::nextInt));
        assertEquals(0, PriceTable.recycleYield(null, rnd::nextInt));
    }

    @Test
    @DisplayName("UNPRICED sentinel semantics")
    void unpricedSentinel() {
        assertTrue(PriceRange.UNPRICED.isUnpriced());
        assertFalse(PriceRange.UNPRICED.isFixed());
        assertEquals("-1", PriceRange.UNPRICED.toDisplayString());
        assertEquals("[100, 200]", new PriceRange(100, 200).toDisplayString());
        assertEquals("1500", PriceRange.fixed(1500).toDisplayString());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> PriceRange.UNPRICED.sample(b -> 1));
    }
}