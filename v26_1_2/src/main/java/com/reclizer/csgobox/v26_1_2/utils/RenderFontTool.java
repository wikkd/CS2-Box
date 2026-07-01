package com.reclizer.csgobox.v26_1_2.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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

    /**
     * Draw {@code text} at the given position, but never wider than
     * {@code maxPixelWidth} (already scaled). If the natural rendered width
     * would overflow, the text is binary-search-truncated and suffixed with
     * "…" so the caller never sees a string spilling outside the supplied
     * rectangle.
     *
     * <p>The current implementation operates on plain {@link String} input.
     * Callers that need to preserve embedded {@link Style} attributes should
     * keep using {@link #drawString(GuiGraphicsExtractor, Font, FormattedCharSequence, float, float, int, int, float, int)};
     * this helper intentionally trades style fidelity for guaranteed fit, and
     * is meant for unstyled item names / localisation strings that already
     * read as a single visual block.</p>
     *
     * @return rendered width in unscaled pixels.
     */
    public static int drawStringClamped(GuiGraphicsExtractor guiGraphics, Font pFont, String text,
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
            // Even the ellipsis alone doesn't fit; render it anyway so the
            // caller gets visual feedback instead of a silent empty draw.
            return drawString(guiGraphics, font, FormattedCharSequence.forward(ellipsis, Style.EMPTY),
                    pX, pY, ox, oy, scale, pColor);
        }
        // Binary search for the longest prefix that, when rendered at scale,
        // still fits within availableScaled pixels.
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
    public static int drawStringClamped(GuiGraphicsExtractor guiGraphics, Font pFont, Component text,
                                        float pX, float pY, int ox, int oy, float scale,
                                        int maxPixelWidth, int pColor) {
        if (text == null) {
            return 0;
        }
        return drawStringClamped(guiGraphics, pFont, text.getString(),
                pX, pY, ox, oy, scale, maxPixelWidth, pColor);
    }
}