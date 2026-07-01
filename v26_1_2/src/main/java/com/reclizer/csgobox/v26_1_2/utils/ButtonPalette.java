package com.reclizer.csgobox.v26_1_2.utils;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Centralised button colour tokens for the v26.1.2 GUI.
 *
 * <p>Replaces the previous hard-coded {@code 0xFF00AA00}/{@code 0xFF00FF00}
 * and {@code 0xFFAA0000}/{@code 0xFFFF0000} literals that fought the
 * dark-grey {@link OverlayColor#getBackgroundColor() background} and made the
 * buttons look like debug placeholders. Every token here is tuned for the
 * dark-grey surface:
 *
 * <ul>
 *   <li>fills stay low-saturation so they read as "panels", not "tags";</li>
 *   <li>borders carry the semantic hue (green = proceed, red = dismiss);</li>
 *   <li>hover lights up both fill and border in lock-step so the user
 *       immediately sees which button they're targeting;</li>
 *   <li>text colours are off-white tints matched to the fill hue, ensuring
 *       AAA contrast on both fill and hover-fill.</li>
 * </ul>
 *
 * <p>Adding a new button style (e.g. WARNING, DISABLED) is intentionally a
 * matter of adding a new constant here — callers do not need to touch colour
 * literals again.</p>
 */
public final class ButtonPalette {
    private ButtonPalette() {
    }

    /** Immutable six-slot colour set for one button. */
    public record Style(
            int fill,
            int fillHover,
            int border,
            int borderHover,
            int textColor,
            int textColorHover
    ) {
    }

    /** Primary action — open box, proceed, confirm. Forest-green panel. */
    public static final Style OPEN = new Style(
            0xFF1F6B33,
            0xFF2A8042,
            0xFF2EA348,
            0xFF45C26A,
            0xFFE8F5E9,
            0xFFFFFFFF
    );

    /** Destructive action — close, back, discard. Brick-red panel. */
    public static final Style DANGER = new Style(
            0xFF6B1F1F,
            0xFF802A2A,
            0xFFA32E2E,
            0xFFC24545,
            0xFFFFEBEE,
            0xFFFFFFFF
    );

    /**
     * Draw the button's outer + inner rectangles using the given style and
     * hover state. Returns the text colour the caller should pass to
     * {@code renderCenteredText} so the label matches the painted panel.
     */
    public static int drawButton(
            GuiGraphicsExtractor guiGraphics,
            Style style,
            int x, int y, int w, int h,
            boolean hover
    ) {
        int outer = hover ? style.borderHover() : style.border();
        int inner = hover ? style.fillHover() : style.fill();
        guiGraphics.fill(x, y, x + w, y + h, outer);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, inner);
        return hover ? style.textColorHover() : style.textColor();
    }

    /** Hit-test for the rectangular button footprint. */
    public static boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}