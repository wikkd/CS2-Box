package com.reclizer.csgobox.villager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reclizer.csgobox.box.PriceTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link VillagerPricing} — the price-table-anchored dynamic
 * arms-dealer pricing.
 */
final class VillagerPricingTest {

    /** nextBounded always returns 0 → unit draw u = 0 → factor = 1 − f. */
    private static final IntUnaryOperator MIN_FACTOR_RNG = n -> 0;

    private static PriceTable table(String json) {
        List<String> issues = new ArrayList<>();
        JsonObject obj = json == null ? null : JsonParser.parseString(json).getAsJsonObject();
        PriceTable t = PriceTable.parse(obj, issues);
        assertTrue(issues.isEmpty(), issues.toString());
        return t;
    }

    @Test
    @DisplayName("disabled config yields no dynamic quote")
    void disabled() {
        VillagerPricingConfig cfg = new VillagerPricingConfig(
                false, 0.06, 1.0, 0.1, 9, 0.05, 0.9, 2.0, VillagerPricingConfig.DEFAULT_FALLBACKS);
        assertNull(VillagerPricing.quote(PriceTable.EMPTY, cfg, MIN_FACTOR_RNG));
    }

    @Test
    @DisplayName("empty price table falls back to configured mineral bases")
    void emptyTableFallsBack() {
        VillagerPricing.Quote q = VillagerPricing.quote(PriceTable.EMPTY,
                VillagerPricingConfig.DEFAULT, MIN_FACTOR_RNG);
        assertNotNull(q);
        // buyRate 0.06 × fallback × (1 − 0.1): iron 50 → 3, gold 50 → 3,
        // diamond 200 → 11, emerald 55 → 3 (rounded)
        assertTrue(q.iron() >= 1 && q.iron() <= 4, "iron=" + q.iron());
        assertTrue(q.gold() >= 1 && q.gold() <= 4, "gold=" + q.gold());
        assertTrue(q.diamond() >= 9 && q.diamond() <= 14, "diamond=" + q.diamond());
        assertTrue(q.emerald() >= 1 && q.emerald() <= 4, "emerald=" + q.emerald());
        // key0 = 9 × (1 − 0.05) = 8.55 → 9
        assertEquals(9, q.key0());
        // key1 = 3 × 50 × 0.9 = 135
        assertEquals(135, q.key1());
        // key2 = 3 × 200 (fallback diamond) × 0.9 = 540 → points 64,
        // diamonds ceil(476/200) = 3
        assertEquals(64, q.key2Points());
        assertTrue(q.key2Diamonds() >= 1, "key2Diamonds=" + q.key2Diamonds());
        // box = 9 × 0.9 × 0.9 = 7.29 → 7; terminal = 9 × 2 × 0.9 = 16.2 → 16
        assertTrue(q.box() >= 1);
        assertTrue(q.terminal() >= 1);
    }

    @Test
    @DisplayName("table-anchored quote with the bundled price-table values")
    void tableAnchored() {
        PriceTable t = table("""
                {
                  "minecraft:iron_ingot": 50,
                  "minecraft:gold_ingot": 50,
                  "minecraft:diamond": 200,
                  "minecraft:emerald_block": 500
                }
                """);
        VillagerPricing.Quote q = VillagerPricing.quote(t, VillagerPricingConfig.DEFAULT, MIN_FACTOR_RNG);
        assertNotNull(q);
        assertEquals(3, q.iron());      // 50 × 0.06 × 0.9 = 2.7 → 3
        assertEquals(3, q.gold());      // 50 × 0.06 × 0.9 = 2.7 → 3
        assertEquals(11, q.diamond());  // 200 × 0.06 × 0.9 = 10.8 → 11
        assertEquals(3, q.emerald());   // 500/9=55 × 0.06 × 0.9 = 2.97 → 3
        assertEquals(9, q.key0());
        assertEquals(135, q.key1());    // 3 × 50 × 1.0 × 0.9
        // key2: 3 × 200 × 0.9 = 540 → 64 points + ceil(476/200)=3 diamonds
        assertEquals(64, q.key2Points());
        assertEquals(3, q.key2Diamonds());
        assertEquals(7, q.box());       // 9 × 0.9 × 0.9 = 7.29 → 7
        assertEquals(16, q.terminal()); // 9 × 2 × 0.9 = 16.2 → 16
    }

    @Test
    @DisplayName("no-fluctuation config with fixed table yields exact anchors")
    void noFluctuation() {
        VillagerPricingConfig cfg = new VillagerPricingConfig(
                true, 0.06, 1.0, 0.0, 9, 0.0, 0.9, 2.0, VillagerPricingConfig.DEFAULT_FALLBACKS);
        PriceTable t = table("""
                {
                  "minecraft:iron_ingot": 50,
                  "minecraft:gold_ingot": 50,
                  "minecraft:diamond": 200,
                  "minecraft:emerald_block": 500
                }
                """);
        VillagerPricing.Quote q = VillagerPricing.quote(t, cfg, MIN_FACTOR_RNG);
        assertNotNull(q);
        assertEquals(3, q.iron());
        assertEquals(3, q.gold());
        assertEquals(12, q.diamond());  // 200 × 0.06 = 12 (matches the old static price)
        assertEquals(3, q.emerald());
        assertEquals(9, q.key0());
        assertEquals(150, q.key1());    // 3 × 50
        assertEquals(64, q.key2Points());   // 3 × 200 = 600 → capped at 64
        assertEquals(3, q.key2Diamonds());  // ceil((600−64)/200) = 3
        assertEquals(8, q.box());       // 9 × 0.9 = 8.1 → 8 (matches the old static price)
        assertEquals(18, q.terminal()); // 9 × 2 (matches the old static price)
    }

    @Test
    @DisplayName("range prices are sampled once per quote")
    void rangePricesSampled() {
        // diamond [100, 400]: with MIN_FACTOR_RNG the sample always picks min=100.
        PriceTable t = table("""
                {"minecraft:diamond": [100, 400], "minecraft:iron_ingot": 50}
                """);
        VillagerPricingConfig cfg = new VillagerPricingConfig(
                true, 0.06, 1.0, 0.0, 9, 0.0, 0.9, 2.0, VillagerPricingConfig.DEFAULT_FALLBACKS);
        VillagerPricing.Quote q = VillagerPricing.quote(t, cfg, MIN_FACTOR_RNG);
        assertNotNull(q);
        assertEquals(6, q.diamond());   // 100 × 0.06 = 6
        assertEquals(64, q.key2Points());
        // key2 value = 300 → points 64 → diamonds ceil(236/100) = 3
        assertEquals(3, q.key2Diamonds());
    }

    @Test
    @DisplayName("key2 points never exceed the 64-point stack limit")
    void key2StackLimit() {
        PriceTable t = table("""
                {"minecraft:diamond": 1000, "minecraft:gold_ingot": 50, "minecraft:iron_ingot": 50}
                """);
        VillagerPricing.Quote q = VillagerPricing.quote(t, VillagerPricingConfig.DEFAULT, MIN_FACTOR_RNG);
        assertNotNull(q);
        assertTrue(q.key2Points() <= VillagerPricing.POINT_STACK_LIMIT);
        assertTrue(q.key2Diamonds() >= 1, "expensive key2 must ask diamonds");
    }

    @Test
    @DisplayName("parse tolerates missing/invalid fields and keeps defaults")
    void parseTolerant() {
        List<String> issues = new ArrayList<>();
        JsonObject obj = JsonParser.parseString("""
                {"enabled": true, "buy_rate": 0.2, "fluctuation": "oops"}
                """).getAsJsonObject();
        VillagerPricingConfig cfg = VillagerPricingConfig.parse(obj, issues);
        assertNotNull(cfg);
        assertEquals(0.2, cfg.buyRate());
        assertEquals(VillagerPricingConfig.DEFAULT.fluctuation(), cfg.fluctuation());
        assertTrue(issues.stream().anyMatch(s -> s.contains("fluctuation")));
        assertEquals(VillagerPricingConfig.DEFAULT.key0Price(), cfg.key0Price());
    }
}
