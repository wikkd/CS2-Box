package com.reclizer.csgobox.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit tests for the pure ARGB color helpers in {@link ColorTools}.
 */
final class ColorToolsTest {

    @Test
    void argbPacksChannels() {
        assertEquals(0xFF123456, ColorTools.argbColor(0xFF, 0x12, 0x34, 0x56));
        assertEquals(0x00112233, ColorTools.argbColor(0x00, 0x11, 0x22, 0x33));
        assertEquals(0xFF000000, ColorTools.argbColor(0xFF, 0, 0, 0));
    }

    @Test
    void withAlphaKeepsRgb() {
        int c = 0xFF123456;
        // alpha=255 -> unchanged (scaled by 255/255)
        assertEquals(0xFF123456, ColorTools.withAlpha(c, 255));
        // alpha=0 -> fully transparent, RGB kept
        assertEquals(0x00123456, ColorTools.withAlpha(c, 0));
        // alpha=128 -> ~half alpha (255*128/255=128)
        assertEquals(0x80123456, ColorTools.withAlpha(c, 128));
    }

    @Test
    void deepColorDarkensRgbKeepsAlpha() {
        int c = ColorTools.argbColor(0xAA, 100, 100, 100);
        int d = ColorTools.deepColor(c);
        // alpha preserved
        assertEquals(0xAA000000, d & 0xFF000000);
        // each RGB channel ~70% (100*0.7=70)
        int red = (d >> 16) & 0xFF;
        assertEquals(70, red, 1);
    }

    @Test
    void colorItemsMapsAllGrades() {
        // distinct per grade
        assertNotEquals(ColorTools.colorItems(1), ColorTools.colorItems(2));
        assertNotEquals(ColorTools.colorItems(2), ColorTools.colorItems(3));
        assertNotEquals(ColorTools.colorItems(3), ColorTools.colorItems(4));
        assertNotEquals(ColorTools.colorItems(4), ColorTools.colorItems(5));
        // unknown grade -> 0
        assertEquals(0, ColorTools.colorItems(0));
        assertEquals(0, ColorTools.colorItems(99));
        // grade 1..5 are non-zero ARGB
        for (int g = 1; g <= 5; g++) {
            assertNotEquals(0, ColorTools.colorItems(g));
        }
    }

    @Test
    void colorItemsKnownValues() {
        assertEquals(0xff4c70ff, ColorTools.colorItems(1));
        assertEquals(0xff8d5eff, ColorTools.colorItems(2));
        assertEquals(0xffe54af2, ColorTools.colorItems(3));
        assertEquals(0xfff86351, ColorTools.colorItems(4));
        assertEquals(0xffffdc1d, ColorTools.colorItems(5));
    }
}
