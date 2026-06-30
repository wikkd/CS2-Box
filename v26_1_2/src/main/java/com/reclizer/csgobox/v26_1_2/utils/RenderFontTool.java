package com.reclizer.csgobox.v26_1_2.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

public final class RenderFontTool {
    private RenderFontTool() {
    }

    public static int drawString(GuiGraphicsExtractor guiGraphics, Font pFont, FormattedCharSequence pText, float pX, float pY, int ox, int oy, float scale, int pColor) {
        Font font = pFont != null ? pFont : Minecraft.getInstance().font;
        if (font == null) {
            return 0;
        }
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(pX - ox, pY - oy);
        if (scale != 1.0F) {
            guiGraphics.pose().scale(scale, scale);
        }
        guiGraphics.text(font, pText, 0, 0, pColor);
        guiGraphics.pose().popMatrix();
        return font.width(pText);
    }
}
