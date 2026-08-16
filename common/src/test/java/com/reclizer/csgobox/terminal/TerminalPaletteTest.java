package com.reclizer.csgobox.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit tests for {@link TerminalPalette} logic (color constants are pure data).
 */
final class TerminalPaletteTest {

    @Test
    void rarityColorMapsKnownGrades() {
        assertEquals(TerminalPalette.RARITY_MILITARY, TerminalPalette.rarityColorForGrade(1));
        assertEquals(TerminalPalette.RARITY_RESTRICTED, TerminalPalette.rarityColorForGrade(2));
        assertEquals(TerminalPalette.RARITY_CLASSIFIED, TerminalPalette.rarityColorForGrade(3));
        assertEquals(TerminalPalette.RARITY_COVERT, TerminalPalette.rarityColorForGrade(4));
        assertEquals(TerminalPalette.RARITY_GOLD, TerminalPalette.rarityColorForGrade(5));
    }

    @Test
    void rarityColorClampsBoundaries() {
        // grade 0 / negative clamp to grade 1 (military)
        assertEquals(TerminalPalette.RARITY_MILITARY, TerminalPalette.rarityColorForGrade(0));
        assertEquals(TerminalPalette.RARITY_MILITARY, TerminalPalette.rarityColorForGrade(-5));
        // grade > 5 clamp to grade 5 (gold)
        assertEquals(TerminalPalette.RARITY_GOLD, TerminalPalette.rarityColorForGrade(6));
        assertEquals(TerminalPalette.RARITY_GOLD, TerminalPalette.rarityColorForGrade(99));
    }

    @Test
    void rarityColorsAreDistinct() {
        assertNotEquals(TerminalPalette.rarityColorForGrade(1), TerminalPalette.rarityColorForGrade(2));
        assertNotEquals(TerminalPalette.rarityColorForGrade(2), TerminalPalette.rarityColorForGrade(3));
        assertNotEquals(TerminalPalette.rarityColorForGrade(3), TerminalPalette.rarityColorForGrade(4));
        assertNotEquals(TerminalPalette.rarityColorForGrade(4), TerminalPalette.rarityColorForGrade(5));
    }

    @Test
    void paletteConstantsAreOpaque() {
        // A representative set of palette colors are fully opaque ARGB.
        for (int c : new int[]{
                TerminalPalette.OUTSIDE, TerminalPalette.FRAME, TerminalPalette.TOPBAR,
                TerminalPalette.TITLE, TerminalPalette.ACTION_BG, TerminalPalette.ACTION_TEXT,
                TerminalPalette.INSPECT_BG, TerminalPalette.INSPECT_HOVER}) {
            assertEquals(0xFF, (c >>> 24) & 0xFF, "palette color must be opaque: " + Integer.toHexString(c));
        }
    }
}
