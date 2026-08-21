package com.reclizer.csgobox.v26_2.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Text rendering with the HD bitmap-font tiers (csgobox:hd_small/mid/large).
 *
 * <p>Every scale used by the UI maps to a pre-rendered bitmap font drawn at
 * scale 1.0 (crisp, no bitmap up/down-scaling):
 * <ul>
 *   <li>[0.38, 0.6]  → hd_small (4px glyphs)</li>
 *   <li>(0.6, 0.95]  → hd_mid   (6px glyphs)</li>
 *   <li>(1.0, 1.6]   → hd_large (10px glyphs)</li>
 *   <li>1.0 or outside the ranges → the default (vanilla) font, scaled</li>
 * </ul>
 *
 * <p>Fixed-vocabulary UI text (translations) renders through the HD tiers;
 * dynamic / external text (item names, server-supplied strings) must keep
 * using the {@code *Vanilla} variants so uncovered characters never mix
 * scaled default glyphs into an HD string.
 */
public final class RenderFontTool {

    private static final Identifier FONT_HD_SMALL_ID = Identifier.parse("csgobox:hd_small");
    private static final Identifier FONT_HD_MID_ID = Identifier.parse("csgobox:hd_mid");
    private static final Identifier FONT_HD_LARGE_ID = Identifier.parse("csgobox:hd_large");
    private static final FontDescription FONT_HD_SMALL = new FontDescription.Resource(FONT_HD_SMALL_ID);
    private static final FontDescription FONT_HD_MID = new FontDescription.Resource(FONT_HD_MID_ID);
    private static final FontDescription FONT_HD_LARGE = new FontDescription.Resource(FONT_HD_LARGE_ID);

    private RenderFontTool() {
    }

    /** HD font tier for a render scale, or null to keep the default font. */
    public static FontDescription hdFontFor(float scale) {
        if (scale >= 0.38F && scale <= 0.60F) {
            return FONT_HD_SMALL;
        }
        if (scale > 0.60F && scale <= 0.95F) {
            return FONT_HD_MID;
        }
        if (scale > 1.0F && scale <= 1.6F) {
            return FONT_HD_LARGE;
        }
        return null;
    }

    /** Rebuild an FCS so every character carries the HD font description. */
    private static FormattedCharSequence withFont(FormattedCharSequence seq, FontDescription font) {
        return sink -> seq.accept((index, style, codepoint) ->
                sink.accept(index, style.withFont(font), codepoint));
    }

    private static FormattedCharSequence fcs(String text, FontDescription font) {
        return FormattedCharSequence.forward(text, Style.EMPTY.withFont(font));
    }

    private static FormattedCharSequence fcs(String text) {
        return FormattedCharSequence.forward(text, Style.EMPTY);
    }

    private static Font font(Font pFont) {
        return pFont != null ? pFont : Minecraft.getInstance().font;
    }

    /** HD-aware width of a plain string in rendered pixels. */
    public static int width(Font pFont, String text, float scale) {
        FontDescription fd = hdFontFor(scale);
        if (fd != null) {
            return font(pFont).width(fcs(text, fd));
        }
        return Math.round(font(pFont).width(text) * scale);
    }

    /** HD-aware width of an FCS in rendered pixels. */
    public static int width(Font pFont, FormattedCharSequence seq, float scale) {
        FontDescription fd = hdFontFor(scale);
        if (fd != null) {
            return font(pFont).width(withFont(seq, fd));
        }
        return Math.round(font(pFont).width(seq) * scale);
    }

    /** HD-aware spaced width (letter-spacing), matching {@link #drawSpacedText}. */
    public static int widthSpaced(Font pFont, String text, float spacingPx, float scale) {
        Font f = font(pFont);
        if (text == null || text.isEmpty()) {
            return 0;
        }
        FontDescription fd = hdFontFor(scale);
        float w = 0F;
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            w += fd != null ? f.width(fcs(c, fd)) : Math.round(f.width(c) * scale);
            if (i < text.length() - 1) {
                w += spacingPx;
            }
        }
        return Math.round(w);
    }

    /**
     * Draw {@code text} with HD font when the scale maps to a tier (drawn at
     * scale 1.0), otherwise with the default font at {@code scale}. Returns
     * the rendered width in unscaled pixels.
     */
    public static int drawString(GuiGraphicsExtractor guiGraphics, Font pFont, FormattedCharSequence pText,
                                 float pX, float pY, int ox, int oy, float scale, int pColor) {
        Font font = font(pFont);
        if (font == null) {
            return 0;
        }
        FontDescription fd = hdFontFor(scale);
        FormattedCharSequence out = fd != null ? withFont(pText, fd) : pText;
        float s = fd != null ? 1.0F : scale;
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(pX - ox, pY - oy);
        if (s != 1.0F) {
            guiGraphics.pose().scale(s, s);
        }
        guiGraphics.text(font, out, 0, 0, pColor);
        guiGraphics.pose().popMatrix();
        return font.width(out);
    }

    /** Draw with the default font at {@code scale} (dynamic/external text). */
    public static int drawStringVanilla(GuiGraphicsExtractor guiGraphics, Font pFont, FormattedCharSequence pText,
                                        float pX, float pY, int ox, int oy, float scale, int pColor) {
        Font font = font(pFont);
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
     * Draw {@code text} character by character with a fixed pixel gap between
     * glyphs — the Java equivalent of the HTML prototype's {@code letter-spacing}
     * (see docs/TERMINAL-LAYOUT-SPEC.md §2). The gap {@code spacingPx} is in
     * framebuffer pixels (after scale), matching the prototype's fixed px values.
     *
     * <p>HD tiers render at scale 1.0 with integer pixel positions (no per-char
     * matrix push/scale/pop — the previous implementation issued ~200 matrix
     * groups per frame for the terminal screens). The returned width is the
     * total rendered width in scaled pixels — use it for centring / container
     * sizing instead of {@link Font#width(String)}.</p>
     *
     * @return rendered width in scaled (framebuffer) pixels.
     */
    public static int drawSpacedText(GuiGraphicsExtractor guiGraphics, Font pFont, String text,
                                     float pX, float pY, float spacingPx, float scale, int pColor) {
        Font font = font(pFont);
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        FontDescription fd = hdFontFor(scale);
        if (fd == null) {
            float x = pX;
            for (int i = 0; i < text.length(); i++) {
                String c = String.valueOf(text.charAt(i));
                drawStringVanilla(guiGraphics, font, fcs(c), x, pY, 0, 0, scale, pColor);
                x += Math.round(font.width(c) * scale);
                if (i < text.length() - 1) {
                    x += spacingPx;
                }
            }
            return Math.round(x - pX);
        }
        float x = pX;
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            FormattedCharSequence cfcs = fcs(c, fd);
            guiGraphics.text(font, cfcs, Math.round(x), Math.round(pY), pColor);
            x += font.width(cfcs);
            if (i < text.length() - 1) {
                x += spacingPx;
            }
        }
        return Math.round(x - pX);
    }

    /** Draw with the default font at {@code scale} (dynamic/external text). */
    public static int drawSpacedTextVanilla(GuiGraphicsExtractor guiGraphics, Font pFont, String text,
                                            float pX, float pY, float spacingPx, float scale, int pColor) {
        Font font = font(pFont);
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        float x = pX;
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            drawStringVanilla(guiGraphics, font, fcs(c), x, pY, 0, 0, scale, pColor);
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
     * rectangle.
     *
     * @return rendered width in unscaled pixels.
     */
    public static int drawStringClamped(GuiGraphicsExtractor guiGraphics, Font pFont, String text,
                                        float pX, float pY, int ox, int oy, float scale,
                                        int maxPixelWidth, int pColor) {
        Font font = font(pFont);
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        int scaledWidth = width(font, text, scale);
        if (scaledWidth <= maxPixelWidth) {
            return drawString(guiGraphics, font, fcs(text), pX, pY, ox, oy, scale, pColor);
        }
        String ellipsis = "…";
        int ellipsisScaled = width(font, ellipsis, scale);
        int availableScaled = maxPixelWidth - ellipsisScaled;
        if (availableScaled <= 0) {
            // Even the ellipsis alone doesn't fit; render it anyway for feedback.
            return drawString(guiGraphics, font, fcs(ellipsis), pX, pY, ox, oy, scale, pColor);
        }
        // Binary search the longest prefix that fits within availableScaled pixels.
        int bestLen = 0;
        int low = 0;
        int high = text.length();
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (width(font, text.substring(0, mid), scale) <= availableScaled) {
                bestLen = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        String truncated = text.substring(0, bestLen) + ellipsis;
        return drawString(guiGraphics, font, fcs(truncated), pX, pY, ox, oy, scale, pColor);
    }

    /** Default-font variant for dynamic/external text. */
    public static int drawStringClampedVanilla(GuiGraphicsExtractor guiGraphics, Font pFont, String text,
                                               float pX, float pY, int ox, int oy, float scale,
                                               int maxPixelWidth, int pColor) {
        Font font = font(pFont);
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        int scaledWidth = Math.round(font.width(text) * scale);
        if (scaledWidth <= maxPixelWidth) {
            return drawStringVanilla(guiGraphics, font, fcs(text), pX, pY, ox, oy, scale, pColor);
        }
        String ellipsis = "…";
        int ellipsisScaled = Math.round(font.width(ellipsis) * scale);
        int availableScaled = maxPixelWidth - ellipsisScaled;
        if (availableScaled <= 0) {
            return drawStringVanilla(guiGraphics, font, fcs(ellipsis), pX, pY, ox, oy, scale, pColor);
        }
        int bestLen = 0;
        int low = 0;
        int high = text.length();
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (Math.round(font.width(text.substring(0, mid)) * scale) <= availableScaled) {
                bestLen = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        String truncated = text.substring(0, bestLen) + ellipsis;
        return drawStringVanilla(guiGraphics, font, fcs(truncated), pX, pY, ox, oy, scale, pColor);
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

    /** Default-font variant for dynamic/external text. */
    public static int drawStringClampedVanilla(GuiGraphicsExtractor guiGraphics, Font pFont, Component text,
                                               float pX, float pY, int ox, int oy, float scale,
                                               int maxPixelWidth, int pColor) {
        if (text == null) {
            return 0;
        }
        return drawStringClampedVanilla(guiGraphics, pFont, text.getString(),
                pX, pY, ox, oy, scale, maxPixelWidth, pColor);
    }
}
