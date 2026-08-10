package com.reclizer.csgobox.utils;

/**
 * Three-tier design tokens for the dark CS2-Box theme (P2-2).
 *
 * <p>Levels, from darkest to lightest:
 * <ul>
 *   <li>{@link #surface()} — screen background, the base dark layer</li>
 *   <li>{@link #panel()}   — container panels that float above the surface
 *       (preview area, item grid, label rows)</li>
 *   <li>{@link #divider()} — separators and hairline accents between panels</li>
 * </ul>
 * Hover/pressed variants are provided for interactive panels so buttons and
 * clickable areas get a consistent feedback hierarchy without each screen
 * hardcoding its own colors.</p>
 */
public final class OverlayColor {
    private OverlayColor() {
    }

    /** Screen background (base layer). */
    public static int surface() {
        return 0xFF1b1b22;
    }

    /** Slightly lighter than the old flat background — keeps dark identity
     *  while giving panels room to stand out. */
    public static int getBackgroundColor() {
        return 0xFF2a2a33;
    }

    /** Translucent screen backdrop (theme gray @ alpha 140/255) — lets the
     *  blurred world behind the screen show through (Blur-mod compatible). */
    public static int getBackgroundTranslucent() {
        return ColorTools.withAlpha(getBackgroundColor(), 0x8C);
    }

    /** Container panel (preview area, item grid, button well). */
    public static int panel() {
        return 0xFF33333d;
    }

    /** Panel when hovered by the mouse. */
    public static int panelHover() {
        return 0xFF3d3d49;
    }

    /** Panel when pressed down (mouse button held). */
    public static int panelPressed() {
        return 0xFF2e2e38;
    }

    /** Hairline divider / separator. */
    public static int divider() {
        return 0xFF4a4a55;
    }

    /** Subtle inner shadow tone for panel top edges. */
    public static int dividerDim() {
        return 0xFF3a3a44;
    }

    /** Disabled panel (content not available yet). */
    public static int panelDisabled() {
        return 0xFF26262e;
    }
}
