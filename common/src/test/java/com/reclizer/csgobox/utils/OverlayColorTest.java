package com.reclizer.csgobox.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OverlayColor} design tokens (dark theme).
 */
final class OverlayColorTest {

    @Test
    void surfaceIsDark() {
        // Surface is the darkest base layer; its alpha is fully opaque.
        int c = OverlayColor.surface();
        assertTrue(((c >>> 24) & 0xFF) == 0xFF, "surface must be opaque");
        // surface should be darker than the panel
        assertTrue(luminance(OverlayColor.surface()) < luminance(OverlayColor.panel()),
                "surface must be darker than panel");
    }

    @Test
    void panelHierarchyIsOrdered() {
        // panel < panelHover < ...? Actually pressed is darker than panel;
        // check the intended relationships:
        // disabled and pressed are darker than normal panel; hover is lighter.
        assertTrue(luminance(OverlayColor.panelPressed()) < luminance(OverlayColor.panel()),
                "pressed should be darker than panel");
        assertTrue(luminance(OverlayColor.panelHover()) > luminance(OverlayColor.panel()),
                "hover should be lighter than panel");
    }

    @Test
    void dividerIsVisibleAgainstPanel() {
        // divider is a light hairline; it should be lighter than panel background
        assertTrue(luminance(OverlayColor.divider()) > luminance(OverlayColor.panel()),
                "divider should be lighter than panel");
    }

    @Test
    void backgroundTranslucentIsSemiTransparent() {
        int c = OverlayColor.getBackgroundTranslucent();
        int alpha = (c >>> 24) & 0xFF;
        assertTrue(alpha > 0 && alpha < 255, "translucent background must be semi-transparent, alpha=" + alpha);
        // RGB is preserved from getBackgroundColor
        int rgbBackground = OverlayColor.getBackgroundColor() & 0xFFFFFF;
        assertTrue((c & 0xFFFFFF) == rgbBackground, "translucent must keep RGB");
    }

    @Test
    void allTokensAreOpaqueOrExplicit() {
        // solid tokens should be opaque
        assertTrue(((OverlayColor.surface() >>> 24) & 0xFF) == 0xFF);
        assertTrue(((OverlayColor.panel() >>> 24) & 0xFF) == 0xFF);
        assertTrue(((OverlayColor.divider() >>> 24) & 0xFF) == 0xFF);
    }

    private static int luminance(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r + g + b) / 3;
    }
}
