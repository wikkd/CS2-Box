package com.reclizer.csgobox.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WearPenalty}.
 */
final class WearPenaltyTest {

    @Test
    @DisplayName("zero wear has no surcharge; negative wear clamps to zero")
    void edges() {
        assertEquals(0, WearPenalty.surcharge(0F));
        assertEquals(0, WearPenalty.surcharge(-1F));
        assertEquals(0, WearPenalty.surcharge(Float.NaN));
    }

    @Test
    @DisplayName("roughly every 5% of wear costs one point, rounded up")
    void rate() {
        assertEquals(1, WearPenalty.surcharge(0.04F));
        assertEquals(2, WearPenalty.surcharge(0.09F));
        assertEquals(4, WearPenalty.surcharge(0.19F));
        assertEquals(10, WearPenalty.surcharge(0.49F));
        assertEquals(20, WearPenalty.surcharge(1F));
    }

    @Test
    @DisplayName("tier midpoints map to stable surcharges")
    void tierMidpoints() {
        assertEquals(3, WearPenalty.surcharge(0.11F));  // MW
        assertEquals(6, WearPenalty.surcharge(0.27F));  // FT
        assertEquals(9, WearPenalty.surcharge(0.41F));  // WW
        assertEquals(14, WearPenalty.surcharge(0.70F)); // BS
    }

    @Test
    @DisplayName("surcharge is monotonic in wear")
    void monotonic() {
        float prev = -1F;
        for (int i = 0; i <= 100; i++) {
            int s = WearPenalty.surcharge(i / 100F);
            assertTrue(s >= prev, "surcharge fell at " + i + "%");
            prev = s;
        }
    }
}
