package com.reclizer.csgobox.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WearBands}.
 */
final class WearBandsTest {

    @Test
    @DisplayName("band stops match the HTML wear bar")
    void stops() {
        assertEquals(5, WearBands.COUNT);
        assertEquals(0.07F, WearBands.BAND_STOPS[0], 1e-6F);
        assertEquals(0.15F, WearBands.BAND_STOPS[1], 1e-6F);
        assertEquals(0.38F, WearBands.BAND_STOPS[2], 1e-6F);
        assertEquals(0.45F, WearBands.BAND_STOPS[3], 1e-6F);
        assertEquals(1.0F, WearBands.BAND_STOPS[4], 1e-6F);
        assertEquals(5, WearBands.BAND_COLORS.length);
        assertEquals(5, WearBands.ABBR.length);
        assertEquals(5, WearBands.NAME_KEYS.length);
    }

    @Test
    @DisplayName("tierIndex assigns boundary values to the next band")
    void tierIndex() {
        assertEquals(0, WearBands.tierIndex(0F));
        assertEquals(0, WearBands.tierIndex(0.0699F));
        assertEquals(1, WearBands.tierIndex(0.07F));     // boundary -> MW
        assertEquals(1, WearBands.tierIndex(0.1139F));   // skin 0
        assertEquals(2, WearBands.tierIndex(0.15F));
        assertEquals(2, WearBands.tierIndex(0.3074F));   // skin 2
        assertEquals(3, WearBands.tierIndex(0.38F));
        assertEquals(3, WearBands.tierIndex(0.4021F));   // skin 1
        assertEquals(4, WearBands.tierIndex(0.45F));
        assertEquals(4, WearBands.tierIndex(1F));
        assertEquals(0, WearBands.tierIndex(-1F));       // clamps
        assertEquals(4, WearBands.tierIndex(2F));
    }

    @Test
    @DisplayName("tier accessors clamp and round-trip")
    void accessors() {
        assertEquals("FN", WearBands.tierAbbr(0));
        assertEquals("MW", WearBands.tierAbbr(1));
        assertEquals("FT", WearBands.tierAbbr(2));
        assertEquals("WW", WearBands.tierAbbr(3));
        assertEquals("BS", WearBands.tierAbbr(4));
        assertEquals("FN", WearBands.tierAbbr(-3));
        assertEquals("BS", WearBands.tierAbbr(99));
        assertEquals("gui.csgobox.csgo_box.wear_mw", WearBands.tierNameKey(1));
        assertEquals(0F, WearBands.tierLo(0), 1e-6F);
        assertEquals(0.07F, WearBands.tierLo(1), 1e-6F);
        assertEquals(0.07F, WearBands.tierHi(0), 1e-6F);
        assertEquals(1F, WearBands.tierHi(4), 1e-6F);
        assertTrue(WearBands.tierColor(0) != WearBands.tierColor(4));
    }
}
