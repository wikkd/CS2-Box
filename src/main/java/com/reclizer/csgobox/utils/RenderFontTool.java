package com.reclizer.csgobox.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class RenderFontTool {
    private RenderFontTool() {
    }

    public static int drawString(GuiGraphics guiGraphics, Font pFont, FormattedCharSequence pText, float pX, float pY, int ox, int oy, float scale, int pColor) {
        Font font = pFont != null ? pFont : Minecraft.getInstance().font;
        if (font == null) {
            return 0;
        }
        int z = 1;
        guiGraphics.pose().pushPose();
        Matrix4f pMatrix = guiGraphics.pose().last().pose();
        pMatrix.translate(-ox, -oy, z);
        pMatrix.translate(pX, pY, z);
        pMatrix.scale(scale);
        Vector4f origin = new Vector4f(0, 0, z, 1);
        pMatrix.transform(origin);
        int i = font.drawInBatch(pText, 0.0F, 0.0F, pColor, false, pMatrix, guiGraphics.bufferSource(), Font.DisplayMode.NORMAL, 0, 15728880);
        guiGraphics.pose().popPose();
        return i;
    }
}
