package com.reclizer.csgobox.forge_1_20_1.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.joml.Vector4f;

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

    private static final ResourceLocation FONT_HD_SMALL = ResourceLocation.fromNamespaceAndPath("csgobox", "hd_small");
    private static final ResourceLocation FONT_HD_MID = ResourceLocation.fromNamespaceAndPath("csgobox", "hd_mid");
    private static final ResourceLocation FONT_HD_LARGE = ResourceLocation.fromNamespaceAndPath("csgobox", "hd_large");

    private RenderFontTool() {
    }

    /** HD font tier for a render scale, or null to keep the default font. */
    public static ResourceLocation hdFontFor(float scale) {
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

    /** Rebuild an FCS so every character carries the HD font id. */
    private static FormattedCharSequence withFont(FormattedCharSequence seq, ResourceLocation font) {
        return sink -> seq.accept((index, style, codepoint) ->
                sink.accept(index, style.withFont(font), codepoint));
    }

    private static FormattedCharSequence fcs(String text, ResourceLocation font) {
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
        ResourceLocation fd = hdFontFor(scale);
        if (fd != null) {
            return font(pFont).width(fcs(text, fd));
        }
        return Math.round(font(pFont).width(text) * scale);
    }

    /** HD-aware width of an FCS in rendered pixels. */
    public static int width(Font pFont, FormattedCharSequence seq, float scale) {
        ResourceLocation fd = hdFontFor(scale);
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
        ResourceLocation fd = hdFontFor(scale);
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

    /** Draw {@code text} with HD font when the scale maps to a tier (drawn at
     *  scale 1.0), otherwise with the default font at {@code scale}. Returns
     *  the rendered width in unscaled pixels. */
    public static int drawString(GuiGraphics guiGraphics, Font pFont, FormattedCharSequence pText,
                                 float pX, float pY, int ox, int oy, float scale, int pColor) {
        Font f = font(pFont);
        if (f == null) {
            return 0;
        }
        ResourceLocation fd = hdFontFor(scale);
        FormattedCharSequence out = fd != null ? withFont(pText, fd) : pText;
        float s = fd != null ? 1.0F : scale;
        int z = 1;
        guiGraphics.pose().pushPose();
        Matrix4f pMatrix = guiGraphics.pose().last().pose();
        pMatrix.translate(-ox, -oy, z);
        pMatrix.translate(pX, pY, z);
        if (s != 1.0F) {
            pMatrix.scale(s);
        }
        Vector4f origin = new Vector4f(0, 0, z, 1);
        pMatrix.transform(origin);
        f.drawInBatch(out, 0.0F, 0.0F, pColor, false, pMatrix,
                guiGraphics.bufferSource(), Font.DisplayMode.NORMAL, 0, 15728880);
        guiGraphics.pose().popPose();
        return f.width(out);
    }

    /** Draw with the default font at {@code scale} (dynamic/external text). */
    public static int drawStringVanilla(GuiGraphics guiGraphics, Font pFont, FormattedCharSequence pText,
                                        float pX, float pY, int ox, int oy, float scale, int pColor) {
        Font f = font(pFont);
        if (f == null) {
            return 0;
        }
        int z = 1;
        guiGraphics.pose().pushPose();
        Matrix4f pMatrix = guiGraphics.pose().last().pose();
        pMatrix.translate(-ox, -oy, z);
        pMatrix.translate(pX, pY, z);
        if (scale != 1.0F) {
            pMatrix.scale(scale);
        }
        Vector4f origin = new Vector4f(0, 0, z, 1);
        pMatrix.transform(origin);
        f.drawInBatch(pText, 0.0F, 0.0F, pColor, false, pMatrix,
                guiGraphics.bufferSource(), Font.DisplayMode.NORMAL, 0, 15728880);
        guiGraphics.pose().popPose();
        return f.width(pText);
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
    public static int drawSpacedText(GuiGraphics guiGraphics, Font pFont, String text,
                                     float pX, float pY, float spacingPx, float scale, int pColor) {
        Font f = font(pFont);
        if (f == null || text == null || text.isEmpty()) {
            return 0;
        }
        ResourceLocation fd = hdFontFor(scale);
        float x = pX;
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            FormattedCharSequence cfcs = fd != null ? fcs(c, fd) : fcs(c);
            if (fd != null) {
                // HD tier renders at scale 1.0 with integer pixel positions —
                // no per-char matrix push/translate/pop (the previous
                // implementation issued ~200 matrix groups per frame).
                guiGraphics.drawString(f, cfcs, Math.round(x), Math.round(pY), pColor);
            } else {
                drawString(guiGraphics, f, cfcs, x, pY, 0, 0, scale, pColor);
            }
            x += fd != null ? f.width(cfcs) : Math.round(f.width(c) * scale);
            if (i < text.length() - 1) {
                x += spacingPx;
            }
        }
        return Math.round(x - pX);
    }

    /** Draw with the default font at {@code scale} (dynamic/external text). */
    public static int drawSpacedTextVanilla(GuiGraphics guiGraphics, Font pFont, String text,
                                            float pX, float pY, float spacingPx, float scale, int pColor) {
        Font f = font(pFont);
        if (f == null || text == null || text.isEmpty()) {
            return 0;
        }
        float x = pX;
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            drawStringVanilla(guiGraphics, f, fcs(c), x, pY, 0, 0, scale, pColor);
            x += Math.round(f.width(c) * scale);
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
    public static int drawStringClamped(GuiGraphics guiGraphics, Font pFont, String text,
                                        float pX, float pY, int ox, int oy, float scale,
                                        int maxPixelWidth, int pColor) {
        Font f = font(pFont);
        if (f == null || text == null || text.isEmpty()) {
            return 0;
        }
        int scaledWidth = width(f, text, scale);
        if (scaledWidth <= maxPixelWidth) {
            return drawString(guiGraphics, f, fcs(text), pX, pY, ox, oy, scale, pColor);
        }
        String ellipsis = "…";
        int ellipsisScaled = width(f, ellipsis, scale);
        int availableScaled = maxPixelWidth - ellipsisScaled;
        if (availableScaled <= 0) {
            return drawString(guiGraphics, f, fcs(ellipsis), pX, pY, ox, oy, scale, pColor);
        }
        int bestLen = 0;
        int low = 0;
        int high = text.length();
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (width(f, text.substring(0, mid), scale) <= availableScaled) {
                bestLen = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        String truncated = text.substring(0, bestLen) + ellipsis;
        return drawString(guiGraphics, f, fcs(truncated), pX, pY, ox, oy, scale, pColor);
    }

    /** Default-font variant for dynamic/external text. */
    public static int drawStringClampedVanilla(GuiGraphics guiGraphics, Font pFont, String text,
                                               float pX, float pY, int ox, int oy, float scale,
                                               int maxPixelWidth, int pColor) {
        Font f = font(pFont);
        if (f == null || text == null || text.isEmpty()) {
            return 0;
        }
        int scaledWidth = Math.round(f.width(text) * scale);
        if (scaledWidth <= maxPixelWidth) {
            return drawStringVanilla(guiGraphics, f, fcs(text), pX, pY, ox, oy, scale, pColor);
        }
        String ellipsis = "…";
        int ellipsisScaled = Math.round(f.width(ellipsis) * scale);
        int availableScaled = maxPixelWidth - ellipsisScaled;
        if (availableScaled <= 0) {
            return drawStringVanilla(guiGraphics, f, fcs(ellipsis), pX, pY, ox, oy, scale, pColor);
        }
        int bestLen = 0;
        int low = 0;
        int high = text.length();
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (Math.round(f.width(text.substring(0, mid)) * scale) <= availableScaled) {
                bestLen = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        String truncated = text.substring(0, bestLen) + ellipsis;
        return drawStringVanilla(guiGraphics, f, fcs(truncated), pX, pY, ox, oy, scale, pColor);
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

    /** Default-font variant for dynamic/external text. */
    public static int drawStringClampedVanilla(GuiGraphics guiGraphics, Font pFont, Component text,
                                               float pX, float pY, int ox, int oy, float scale,
                                               int maxPixelWidth, int pColor) {
        if (text == null) {
            return 0;
        }
        return drawStringClampedVanilla(guiGraphics, pFont, text.getString(),
                pX, pY, ox, oy, scale, maxPixelWidth, pColor);
    }
}
