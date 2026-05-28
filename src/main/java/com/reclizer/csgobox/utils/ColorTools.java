package com.reclizer.csgobox.utils;

public class ColorTools {
    public static int argbColor(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
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
