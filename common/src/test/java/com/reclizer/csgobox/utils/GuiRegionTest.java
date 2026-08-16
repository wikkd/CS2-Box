package com.reclizer.csgobox.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure GUI layout helpers in {@link GuiRegion}.
 */
final class GuiRegionTest {

    @Test
    void pctCalculatesPercentage() {
        assertEquals(50, GuiRegion.pctW(1000, 5));
        assertEquals(10, GuiRegion.pctW(100, 10));
        assertEquals(0, GuiRegion.pctW(100, 0));
        assertEquals(100, GuiRegion.pctH(200, 50));
    }

    @Test
    void centerCoordinates() {
        assertEquals(300, GuiRegion.centerX(1000, 400)); // (1000-400)/2
        assertEquals(150, GuiRegion.centerY(400, 100));
        assertEquals(0, GuiRegion.centerX(100, 100));    // full width
    }

    @Test
    void regionAccessors() {
        GuiRegion.Region r = new GuiRegion.Region(10, 20, 100, 50);
        assertEquals(110, r.right());
        assertEquals(70, r.bottom());
        assertEquals(60, r.centerX()); // 10 + 100/2
        assertEquals(45, r.centerY()); // 20 + 50/2
    }

    @Test
    void fullWidthRowCoversScreenWidth() {
        GuiRegion.Region r = GuiRegion.fullWidthRow(1920, 1080, 10, 5);
        assertEquals(0, r.x());
        assertEquals(1920, r.w());
        assertEquals(108, r.y());   // 10% of 1080
        assertEquals(54, r.h());    // 5% of 1080
    }

    @Test
    void centeredRegionIsCenteredHorizontally() {
        GuiRegion.Region r = GuiRegion.centered(1000, 800, 50, 40, 30);
        assertEquals(500, r.w());   // 50% width
        assertEquals(320, r.h());   // 40% height
        assertEquals((1000 - 500) / 2, r.x());
        assertEquals(240, r.y());   // 30% height
    }

    @Test
    void titleRegionSitsAtTop() {
        GuiRegion.Region r = GuiRegion.title(1000, 800);
        assertEquals(0, r.x());
        assertEquals(80, r.y());   // 10%
        assertEquals(64, r.h());   // 8%
    }

    @Test
    void previewRegionIsSquareAndCentered() {
        GuiRegion.Region r = GuiRegion.preview(1000, 800);
        // size = max(144, min(22%*1000=220, 30%*800=240)) = 220
        assertEquals(220, r.w());
        assertEquals(220, r.h());
        assertEquals((1000 - 220) / 2, r.x()); // centered
    }

    @Test
    void listRegionCoversLowerMajority() {
        GuiRegion.Region r = GuiRegion.list(1000, 800);
        assertTrue(r.w() >= 900);                // ~94%
        assertEquals(30, r.x());                 // 3% of width 1000
        assertEquals(424, r.y());                // 53%
        assertEquals(280, r.h());                // 35%
    }

    @Test
    void actionsRegionAtBottom() {
        GuiRegion.Region r = GuiRegion.actions(1000, 800);
        assertEquals(624, r.y()); // 78%
        assertEquals(40, r.h());  // 5%
    }

    @Test
    void actionPairSplitsLeftRight() {
        GuiRegion.Region[] pair = GuiRegion.actionPair(1000, 800, 20);
        assertEquals(2, pair.length);
        // buttons are at bottom row and do not overlap
        assertEquals(pair[0].y(), pair[1].y());
        assertTrue(pair[0].right() <= pair[1].x(), "left and right buttons must not overlap");
        assertTrue(pair[0].w() >= 96 && pair[1].w() >= 96);
    }
}
