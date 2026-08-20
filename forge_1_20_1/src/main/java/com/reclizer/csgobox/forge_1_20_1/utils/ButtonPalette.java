package com.reclizer.csgobox.forge_1_20_1.utils;

import net.minecraft.client.gui.GuiGraphics;

public final class ButtonPalette {
    private ButtonPalette() {
    }

    public record Style(
            int fill,
            int fillHover,
            int border,
            int borderHover,
            int textColor,
            int textColorHover
    ) {
    }

    public static final Style OPEN = new Style(
            0xFF1F6B33,
            0xFF2A8042,
            0xFF2EA348,
            0xFF45C26A,
            0xFFE8F5E9,
            0xFFFFFFFF
    );

    public static final Style DANGER = new Style(
            0xFF6B1F1F,
            0xFF802A2A,
            0xFFA32E2E,
            0xFFC24545,
            0xFFFFEBEE,
            0xFFFFFFFF
    );

    public static final Style CLOSE = new Style(
            0xFF3A4148,
            0xFF4A535C,
            0xFF6C7680,
            0xFF8B96A0,
            0xFFE6EAEE,
            0xFFFFFFFF
    );

    public static final Style DISABLED = new Style(
            0xFF2A2A33,
            0xFF2A2A33,
            0xFF4A4A55,
            0xFF4A4A55,
            0xFF8A8A96,
            0xFF8A8A96
    );

    public static int drawButton(
            GuiGraphics guiGraphics,
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

    public static boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
