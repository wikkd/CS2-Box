package com.reclizer.csgobox.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WearPenalty}.
 */
final class WearPenaltyTest {

    /** Grade 5 (classified) default price — the widest markup budget. */
    private static final int CLASSIFIED = 30;

    @Test
    @DisplayName("zero/negative/NaN wear and non-positive base price yield no surcharge")
    void edges() {
        assertEquals(0, WearPenalty.surcharge(CLASSIFIED, 0F));
        assertEquals(0, WearPenalty.surcharge(CLASSIFIED, -1F));
        assertEquals(0, WearPenalty.surcharge(CLASSIFIED, Float.NaN));
        assertEquals(0, WearPenalty.surcharge(0, 1F));
        assertEquals(0, WearPenalty.surcharge(-5, 1F));
    }

    @Test
    @DisplayName("markup is 0.2% of the base price per 1% of wear, rounded up")
    void rate() {
        assertEquals(1, WearPenalty.surcharge(CLASSIFIED, 0.11F)); // 0.66
        assertEquals(2, WearPenalty.surcharge(CLASSIFIED, 0.27F)); // 1.62
        assertEquals(3, WearPenalty.surcharge(CLASSIFIED, 0.41F)); // 2.46
        assertEquals(5, WearPenalty.surcharge(CLASSIFIED, 0.70F)); // 4.20
        assertEquals(6, WearPenalty.surcharge(CLASSIFIED, 1F));    // 6.00 = 20%
    }

    @Test
    @DisplayName("full wear adds exactly 20% of the base price, rounded up")
    void fullWearIsTwentyPercent() {
        assertEquals(2, WearPenalty.surcharge(6, 1F));  // 1.2
        assertEquals(2, WearPenalty.surcharge(10, 1F)); // 2.0
        assertEquals(5, WearPenalty.surcharge(22, 1F)); // 4.4
        assertEquals(6, WearPenalty.surcharge(30, 1F)); // 6.0
    }

    @Test
    @DisplayName("exact boundaries do not drift (integer math, no ulp overshoot)")
    void exactBoundaries() {
        assertEquals(1, WearPenalty.surcharge(10, 0.5F)); // 1.0, not 2
        assertEquals(1, WearPenalty.surcharge(25, 0.2F)); // 1.0
        assertEquals(1, WearPenalty.surcharge(5, 1F));    // 1.0
        assertEquals(4, WearPenalty.surcharge(20, 1F));   // 4.0
    }

    @Test
    @DisplayName("wear is quantised to whole percent: below 0.5% is free, 1% already costs a point")
    void quantisation() {
        assertEquals(0, WearPenalty.surcharge(CLASSIFIED, 0.004F));
        assertEquals(1, WearPenalty.surcharge(CLASSIFIED, 0.01F));
        assertEquals(1, WearPenalty.surcharge(6, 0.01F));
    }

    @Test
    @DisplayName("wear above 100% clamps to the 20% ceiling")
    void clampsOverWear() {
        assertEquals(6, WearPenalty.surcharge(CLASSIFIED, 2F));
        assertEquals(WearPenalty.surcharge(CLASSIFIED, 1F), WearPenalty.surcharge(CLASSIFIED, 1.5F));
    }

    @Test
    @DisplayName("surcharge is monotonic in wear for every grade price")
    void monotonicInWear() {
        int[] prices = {6, 10, 16, 22, 30};
        for (int price : prices) {
            int prev = -1;
            for (int i = 0; i <= 100; i++) {
                int s = WearPenalty.surcharge(price, i / 100F);
                assertTrue(s >= prev, "surcharge fell at " + i + "% for base " + price);
                prev = s;
            }
        }
    }

    @Test
    @DisplayName("surcharge is monotonic in the base price")
    void monotonicInBasePrice() {
        int prev = -1;
        for (int price = 0; price <= 60; price++) {
            int s = WearPenalty.surcharge(price, 0.7F);
            assertTrue(s >= prev, "surcharge fell at base " + price);
            prev = s;
        }
    }
}
