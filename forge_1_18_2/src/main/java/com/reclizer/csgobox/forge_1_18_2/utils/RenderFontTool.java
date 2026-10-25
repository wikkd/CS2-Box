package com.reclizer.csgobox.forge_1_18_2.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class RenderFontTool {
    private RenderFontTool() {
    }

    public static int drawString(PoseStack pose, Font pFont, FormattedCharSequence pText, float pX, float pY, int ox, int oy, float scale, int pColor) {
        Font font = pFont != null ? pFont : Minecraft.getInstance().font;
        if (font == null) {
            return 0;
        }
        pose.pushPose();
        pose.translate(pX - ox, pY - oy, 0);
        if (scale != 1.0F) {
            pose.scale(scale, scale, 1.0F);
        }
        font.drawShadow(pose, pText, 0, 0, pColor);
        pose.popPose();
        return font.width(pText);
    }

    public static int drawSpacedText(PoseStack pose, Font pFont, String text,
                                     float pX, float pY, float spacingPx, float scale, int pColor) {
        Font font = pFont != null ? pFont : Minecraft.getInstance().font;
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        float x = pX;
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            drawString(pose, font, FormattedCharSequence.forward(c, Style.EMPTY),
                    x, pY, 0, 0, scale, pColor);
            x += Math.round(font.width(c) * scale);
            if (i < text.length() - 1) {
                x += spacingPx;
            }
        }
        return Math.round(x - pX);
    }

    public static int drawStringClamped(PoseStack pose, Font pFont, String text,
                                        float pX, float pY, int ox, int oy, float scale,
                                        int maxPixelWidth, int pColor) {
        Font font = pFont != null ? pFont : Minecraft.getInstance().font;
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        int scaledWidth = Math.round(font.width(text) * scale);
        if (scaledWidth <= maxPixelWidth) {
            return drawString(pose, font, FormattedCharSequence.forward(text, Style.EMPTY),
                    pX, pY, ox, oy, scale, pColor);
        }
        String ellipsis = "\u2026";
        int ellipsisScaled = Math.round(font.width(ellipsis) * scale);
        int availableScaled = maxPixelWidth - ellipsisScaled;
        if (availableScaled <= 0) {
            return drawString(pose, font, FormattedCharSequence.forward(ellipsis, Style.EMPTY),
                    pX, pY, ox, oy, scale, pColor);
        }
        int bestLen = 0;
        int low = 0;
        int high = text.length();
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int w = Math.round(font.width(text.substring(0, mid)) * scale);
            if (w <= availableScaled) {
                bestLen = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        String truncated = text.substring(0, bestLen) + ellipsis;
        return drawString(pose, font, FormattedCharSequence.forward(truncated, Style.EMPTY),
                pX, pY, ox, oy, scale, pColor);
    }

    public static int drawStringClamped(PoseStack pose, Font pFont, Component text,
                                        float pX, float pY, int ox, int oy, float scale,
                                        int maxPixelWidth, int pColor) {
        if (text == null) {
            return 0;
        }
        return drawStringClamped(pose, pFont, text.getString(),
                pX, pY, ox, oy, scale, maxPixelWidth, pColor);
    }
}
