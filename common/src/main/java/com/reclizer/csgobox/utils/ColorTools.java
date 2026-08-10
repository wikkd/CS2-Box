package com.reclizer.csgobox.utils;

public final class ColorTools {
    private ColorTools() {
    }

    public static int argbColor(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Replaces the alpha channel of an ARGB color, scaling it by the given
     *  0..255 factor (255 = unchanged). Used by the page-turn transition to
     *  fade frames/labels in and out while keeping their RGB intact. */
    public static int withAlpha(int argb, int alpha) {
        int a = ((argb >>> 24) & 0xFF) * alpha / 255;
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int deepColor(int color) {
        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        red = (int) (red * 0.7);
        green = (int) (green * 0.7);
        blue = (int) (blue * 0.7);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public static int colorItems(int grade) {
        return switch (grade) {
            case 1 -> 0xff4c70ff;
            case 2 -> 0xff8d5eff;
            case 3 -> 0xffe54af2;
            case 4 -> 0xfff86351;
            case 5 -> 0xffffdc1d;
            default -> 0;
        };
    }
}