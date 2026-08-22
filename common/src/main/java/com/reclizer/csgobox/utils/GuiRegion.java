package com.reclizer.csgobox.utils;

/**
 * Container-based GUI layout helpers (P1-1).
 *
 * <p>Replaces ad-hoc {@code width * N / 100} / {@code height * N / 100}
 * literals in the screens with named regions. Layout numbers are unchanged
 * from the previous percentage hardcoding — this is a pure refactor that
 * gives every element a stable, discoverable home and lets screens share
 * the same region vocabulary (title / preview / list / action).</p>
 *
 * <p>Pure arithmetic only: no Minecraft imports, safe for the common
 * source set.</p>
 */
public final class GuiRegion {

    /** Immutable axis-aligned rectangle in GUI pixel space. */
    public record Region(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        /** Horizontal centre of the region. */
        public int centerX() {
            return x + w / 2;
        }

        /** Vertical centre of the region. */
        public int centerY() {
            return y + h / 2;
        }
    }

    private GuiRegion() {
    }

    public static int pctW(int width, int pct) {
        return width * pct / 100;
    }

    public static int pctH(int height, int pct) {
        return height * pct / 100;
    }

    /** X coordinate for horizontally centering an element of the given width. */
    public static int centerX(int width, int elementWidth) {
        return (width - elementWidth) / 2;
    }

    /** X coordinate for horizontally centering an element of the given width. */
    public static int centerY(int height, int elementHeight) {
        return (height - elementHeight) / 2;
    }

    /** Full-width region starting at {@code topPct}% of the screen height. */
    public static Region fullWidthRow(int width, int height, int topPct, int hPct) {
        int y = pctH(height, topPct);
        return new Region(0, y, width, pctH(height, hPct));
    }

    /** Centered region of the given percentage size. */
    public static Region centered(int width, int height, int wPct, int hPct, int topPct) {
        int w = pctW(width, wPct);
        int h = pctH(height, hPct);
        return new Region(centerX(width, w), pctH(height, topPct), w, h);
    }

    // ---- Named screen containers (percentages match the previous layout) ----

    /** Screen title area (top of screen). */
    public static Region title(int width, int height) {
        return new Region(0, pctH(height, 10), width, pctH(height, 8));
    }

    /**
     * Main preview area (held box 3D view). Sits between the title (10%)
     * and the info rows (50%): the rotated 3D model overflows its square
     * by ~20%, so the region is capped at 24% of the screen height to keep
     * the model clear of the box/key count labels below.
     */
    public static Region preview(int width, int height) {
        int size = Math.max(96, Math.min(pctW(width, 18), pctH(height, 24)));
        return new Region(centerX(width, size), pctH(height, 28) - size / 2, size, size);
    }

    /** Item list / grade grid area. */
    public static Region list(int width, int height) {
        return new Region(pctW(width, 3), pctH(height, 53), pctW(width, 94), pctH(height, 35));
    }

    /** Bottom action row (buttons). */
    public static Region actions(int width, int height) {
        int btnH = pctH(height, 5);
        return new Region(0, pctH(height, 78), width, btnH);
    }

    /** Centered pair of buttons: returns [left, right] regions. */
    public static Region[] actionPair(int width, int height, int gapPx) {
        int btnW = Math.max(96, pctW(width, 12));
        int btnH = pctH(height, 5);
        int btnY = pctH(height, 78);
        int leftX = Math.max(8, width / 2 - btnW - gapPx);
        int rightX = width / 2 + gapPx;
        return new Region[]{
                new Region(leftX, btnY, btnW, btnH),
                new Region(rightX, btnY, btnW, btnH)
        };
    }
}
