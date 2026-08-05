package com.reclizer.csgobox.v1_21_8.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

public final class RenderFontTool {
    private RenderFontTool() {
    }

    public static int drawString(GuiGraphics guiGraphics, Font pFont, FormattedCharSequence pText, float pX, float pY, int ox, int oy, float scale, int pColor) {
        Font font = pFont != null ? pFont : Minecraft.getInstance().font;
        if (font == null) {
            return 0;
        }
        int w = font.width(pText);
        guiGraphics.drawString(font, pText, (int)(pX - ox), (int)(pY - oy), pColor);
        return w;
    }
}
