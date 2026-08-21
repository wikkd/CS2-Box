package com.reclizer.csgobox.v1_21_1.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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

    /**
     * Draw {@code text} character by character with a fixed pixel gap between
     * glyphs — the Java equivalent of the HTML prototype's {@code letter-spacing}
     * (see docs/TERMINAL-LAYOUT-SPEC.md §2). The gap {@code spacingPx} is in
     * framebuffer pixels (after scale), matching the prototype's fixed px values.
     *
     * <p>The returned width is the total rendered width in scaled pixels
     * ({@code round(font.width(text) * scale) + spacingPx * (len - 1)}) — use it
     * for centring / container sizing instead of {@link Font#width(String)}.</p>
     *
     * @return rendered width in scaled (framebuffer) pixels.
     */
    public static int drawSpacedText(GuiGraphics guiGraphics, Font pFont, String text,
                                     float pX, float pY, float spacingPx, float scale, int pColor) {
        Font font = pFont != null ? pFont : Minecraft.getInstance().font;
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        float x = pX;
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            drawString(guiGraphics, font, FormattedCharSequence.forward(c, Style.EMPTY),
                    x, pY, 0, 0, scale, pColor);
            x += Math.round(font.width(c) * scale);
            if (i < text.length() - 1) {
                x += spacingPx;
            }
        }
        return Math.round(x - pX);
    }

    /**
     * Draw {@code text} at the given position, but never wider than
     * {@code maxPixelWidth} (already scaled). If the natural rendered width
     * would overflow, the text is binary-search-truncated and suffixed with
     * "…" so the caller never sees a string spilling outside the supplied
     * rectangle. Mirrors the 26.x helper; unstyled strings only.
     */
    public static int drawStringClamped(GuiGraphics guiGraphics, Font pFont, String text,
                                        float pX, float pY, int ox, int oy, float scale,
                                        int maxPixelWidth, int pColor) {
        Font font = pFont != null ? pFont : Minecraft.getInstance().font;
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        int scaledWidth = Math.round(font.width(text) * scale);
        if (scaledWidth <= maxPixelWidth) {
            return drawString(guiGraphics, font, FormattedCharSequence.forward(text, Style.EMPTY),
                    pX, pY, ox, oy, scale, pColor);
        }
        String ellipsis = "…";
        int ellipsisScaled = Math.round(font.width(ellipsis) * scale);
        int availableScaled = maxPixelWidth - ellipsisScaled;
        if (availableScaled <= 0) {
            return drawString(guiGraphics, font, FormattedCharSequence.forward(ellipsis, Style.EMPTY),
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
        return drawString(guiGraphics, font, FormattedCharSequence.forward(truncated, Style.EMPTY),
                pX, pY, ox, oy, scale, pColor);
    }

    /**
     * Convenience overload for unstyled {@link Component} labels (item names,
     * localisation keys). Truncates with "…" if the rendered output would
     * exceed {@code maxPixelWidth} (in already-scaled pixels).
     */
    public static int drawStringClamped(GuiGraphics guiGraphics, Font pFont, Component text,
                                        float pX, float pY, int ox, int oy, float scale,
                                        int maxPixelWidth, int pColor) {
        if (text == null) {
            return 0;
        }
        return drawStringClamped(guiGraphics, pFont, text.getString(),
                pX, pY, ox, oy, scale, maxPixelWidth, pColor);
    }
}
