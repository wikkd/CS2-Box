package com.reclizer.csgobox.v26_1_2.gui.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.terminal.TerminalAnims;
import com.reclizer.csgobox.terminal.TerminalPalette;
import com.reclizer.csgobox.terminal.WearBands;
import com.reclizer.csgobox.v26_1_2.utils.AnimRenderOps;
import com.reclizer.csgobox.v26_1_2.utils.GuiItemMove;
import com.reclizer.csgobox.v26_1_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Region 8: the offer inspection panel — A name box, B rarity block,
 * C inspect capsule (auto-spin), D 3D item (drag to rotate), E wear tier,
 * F five-band wear bar + arrow + scan, G wear value, H meta row.
 *
 * era: decoupled
 */
public final class TerminalOfferRegion {

    public static final Identifier TEX_CIRCLE_GLOW = Identifier.parse("csgobox:gui/terminal/terminal_circle_glow");
    public static final Identifier TEX_AVATAR_WM = Identifier.parse("csgobox:gui/terminal/terminal_avatar_wm");
    public static final Identifier TEX_SCAN_BAND = Identifier.parse("csgobox:gui/terminal/terminal_scan_band");
    public static final Identifier TEX_WEAPON = Identifier.parse("csgobox:gui/terminal/weapon");

    /** HTML initial tilt: rotX -6°, rotY 14° (radians). */
    private static final float INITIAL_ROT_X = (float) Math.toRadians(-6);
    private static final float INITIAL_ROT_Y = (float) Math.toRadians(14);

    private boolean inspectOn;
    private boolean dragging;
    private int dragLastX;
    private int dragLastY;
    private float rotX = INITIAL_ROT_X;
    private float rotY = INITIAL_ROT_Y;
    /** The offer seen by the last render() pass (null until round 1 lands). */
    private NegotiationModel.Offer currentOffer;

    // hit-test rects (updated each render)
    private int inspectX, inspectY, inspectW, inspectH;
    private int itemCx, itemCy;

    public void render(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1,
                       long nowMs, NegotiationModel model, Player player,
                       int mx, int my) {
        // body gradient
        AnimRenderOps.fillGradient(gg, x0, y0, x1, y1, TerminalPalette.BODY_TOP,
                TerminalPalette.BODY_BOTTOM);
        TerminalChatRegion.drawDotGrid(gg, x0 + 6, y0 + 6, x1 - x0 - 12, y1 - y0 - 12);

        NegotiationModel.Offer offer = model.pending();
        this.currentOffer = offer;
        Font font = Minecraft.getInstance().font;
        int cx = (x0 + x1) / 2;
        int cy = (y0 + y1) / 2;

        // ---- D: watermark circle + 3D item (centre) ----
        int wmSize = Math.min(x1 - x0 - 60, y1 - y0 - 90);
        int wmX = cx - wmSize / 2;
        int wmY = cy - wmSize / 2;
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE_GLOW, wmX, wmY, wmSize, wmSize, 128, 128);
        AnimRenderOps.blitTextured(gg, TEX_AVATAR_WM, wmX + wmSize / 6, wmY + wmSize / 6,
                wmSize - wmSize / 3, wmSize - wmSize / 3, 128, 128);

        if (offer != null) {
            itemCx = cx;
            itemCy = cy;
            float itemSize = Math.max(28F, wmSize * 0.42F);
            float rotYNow = inspectOn
                    ? rotY + (float) Math.toRadians(TerminalAnims.spinDeg(nowMs))
                    : rotY;
            ItemStack stack = new ItemStack(Items.IRON_SWORD);
            if (AnimRenderOps.supports3D()) {
                AnimRenderOps.renderItem3D(gg, stack, player, itemCx, itemCy,
                        rotX, rotYNow, itemSize / 16F);
            } else {
                AnimRenderOps.renderItem2D(player, gg, stack, itemCx, itemCy, itemSize / 16F);
            }
        }

        // ---- C: inspect capsule (top-right) ----
        String inspectText = Component.translatable("gui.csgobox.csgo_box.toolbar.inspect").getString();
        int iw = Math.round(font.width(inspectText) * 1.1F) + 22;
        inspectX = x1 - iw - 14;
        inspectY = y0 + 12;
        inspectW = iw;
        inspectH = 22;
        boolean hover = mx >= inspectX && mx <= inspectX + iw && my >= inspectY && my <= inspectY + 22;
        int bg = inspectOn ? 0xFFE5C558 : (hover ? TerminalPalette.INSPECT_HOVER : TerminalPalette.INSPECT_BG);
        int fg = inspectOn ? 0xFF3A3520 : TerminalPalette.INSPECT_TEXT;
        TerminalChatRegion.drawRounded(gg, inspectX, inspectY, iw, 22, bg, 0xFF000000);
        if (inspectOn) {
            AnimRenderOps.blitTextured(gg, TEX_CIRCLE_GLOW, inspectX - 10, inspectY - 10,
                    iw + 20, 42, 128, 128);
        }
        RenderFontTool.drawString(gg, font, fcs(inspectText),
                inspectX + (iw - font.width(inspectText)) / 2F, inspectY + 5, 0, 0, 1.1F, fg);

        if (offer == null) {
            return;
        }

        // ---- A: name box (top-left) ----
        String name = Component.translatable(NegotiationModel.SKIN_NAME_KEYS[offer.skinIdx()]).getString();
        int nameW = Math.round(font.width(name) * 1.35F) + 24;
        TerminalChatRegion.drawRounded(gg, x0 + 12, y0 + 12, nameW, 22, TerminalPalette.BLACK_BOX,
                TerminalPalette.BLACK_BOX);
        RenderFontTool.drawString(gg, font, fcs(name), x0 + 24, y0 + 16, 0, 0, 1.35F,
                TerminalPalette.WHITE);

        // ---- B: rarity block (below the name) ----
        int rarity = TerminalChatRegion.rarityColor(offer.skinIdx());
        String rarityName = Component.translatable(
                "csgobox.terminal.rarity." + NegotiationModel.SKIN_RARITY[offer.skinIdx()]).getString();
        int rw = Math.round(font.width(rarityName) * 1.1F) + 18;
        AnimRenderOps.fill(gg, x0 + 12, y0 + 42, x0 + 12 + rw, y0 + 58, rarity);
        RenderFontTool.drawString(gg, font, fcs(rarityName), x0 + 22, y0 + 45, 0, 0, 1.1F,
                TerminalPalette.WHITE);

        // ---- E: wear tier (bottom-left) ----
        int tier = WearBands.tierIndex(offer.wearVal());
        String wearName = Component.translatable(WearBands.tierNameKey(tier)).getString();
        String wearAbbr = WearBands.tierAbbr(tier);
        int wearW = Math.round(font.width(wearName) * 1.3F) + 26;
        int wearY = y1 - 108;
        TerminalChatRegion.drawRounded(gg, x0 + 12, wearY, wearW, 24, TerminalPalette.WEAR_BG,
                TerminalPalette.WEAR_BG);
        // corner tab
        int tabW = Math.round(font.width(wearAbbr) * 0.95F) + 12;
        AnimRenderOps.fill(gg, x0 + 12, wearY + 8, x0 + 12 + tabW, wearY + 24, 0xFF20242A);
        RenderFontTool.drawString(gg, font, fcs(wearAbbr), x0 + 16, wearY + 11, 0, 0, 0.95F,
                TerminalPalette.WEAR_TAB_TEXT);
        RenderFontTool.drawString(gg, font, fcs(wearName), x0 + 12 + tabW + 8, wearY + 5, 0, 0, 1.3F,
                TerminalPalette.WHITE);

        // ---- F: five-band wear bar (below the tier) ----
        int barX = x0 + 12;
        int barY = wearY + 30;
        int barW = Math.min(340, (int) ((x1 - x0) * 0.34F));
        int barH = 10;
        long offerAtMs = model.lastOfferEntry() != null ? model.lastOfferEntry().atMs() : nowMs;
        drawWearBar(gg, barX, barY, barW, barH);
        // arrow glides toward the wear value (0.95s, HTML curve)
        float arrowT = TerminalAnims.arrowLeft(nowMs, offerAtMs);
        int target = barX + Math.round(barW * offer.wearVal());
        int arrowX = barX + Math.round((target - barX) * arrowT);
        drawArrow(gg, arrowX, barY - 4, offer.wearVal(), nowMs, offerAtMs, barW);

        // ---- G: wear value (black box) ----
        String wearVal = String.format("%.4f", offer.wearVal());
        int wvW = Math.round(font.width(wearVal) * 1.6F) + 20;
        TerminalChatRegion.drawRounded(gg, barX, barY + 16, wvW, 20, TerminalPalette.BLACK_BOX,
                TerminalPalette.BLACK_BOX);
        RenderFontTool.drawString(gg, font, fcs(wearVal), barX + 10, barY + 19, 0, 0, 1.6F,
                TerminalPalette.WHITE);

        // ---- H: meta row (bottom-right, 3 columns) ----
        String style = Component.translatable(
                "gui.csgobox.csgo_box.style." + styleKey(offer.style())).getString();
        String meta = Component.translatable("csgobox.terminal.meta", style,
                offer.no(), offer.pattern()).getString();
        int metaW = Math.round(font.width(meta) * 1.2F);
        RenderFontTool.drawString(gg, font, fcs(meta), x1 - metaW - 12, y1 - 24, 0, 0, 1.2F,
                TerminalPalette.META_BOLD);
    }

    /** FormattedCharSequence wrapper for plain strings. */
    private static FormattedCharSequence fcs(String s) {
        return FormattedCharSequence.forward(s, Style.EMPTY);
    }

    /** Five coded tier bands + edge border (HTML wear-bar). */
    private void drawWearBar(GuiGraphicsExtractor gg, int x, int y, int w, int h) {
        AnimRenderOps.fill(gg, x, y, x + w, y + h, TerminalPalette.BAR_EDGE);
        int seg = x + 1;
        for (int i = 0; i < WearBands.COUNT; i++) {
            int segW = Math.round((w - 2) * (WearBands.tierHi(i) - WearBands.tierLo(i)));
            AnimRenderOps.fill(gg, seg, y + 1, seg + segW, y + h - 1, WearBands.tierColor(i));
            seg += segW;
        }
    }

    /** Triangle arrow (row-by-row fills) + scan band over the wear bar. */
    private void drawArrow(GuiGraphicsExtractor gg, int x, int y, float wearVal,
                           long nowMs, long startMs, int barW) {
        // arrow: 7px wide, 10px tall triangle, white, tip pointing up
        int rows = 10;
        for (int r = 0; r < rows; r++) {
            int w = Math.max(1, 7 - 2 * (rows - 1 - r) * 7 / (2 * (rows - 1)));
            AnimRenderOps.fill(gg, x + (7 - w) / 2, y + r, x + (7 - w) / 2 + w, y + r + 1,
                    TerminalPalette.BAR_WHITE);
        }
        // scan band: horizontal gradient band sweeping left -34% -> 100%
        int scanW = Math.round(barW * 0.34F);
        float s = TerminalAnims.scanX(nowMs, startMs);
        int sx = Math.round(x + (barW - scanW) * s) - scanW;
        AnimRenderOps.blitTextured(gg, TEX_SCAN_BAND, sx, y + 8, scanW, 2, 8, 24);
    }

    private static String styleKey(int style) {
        return switch (style) {
            case 0 -> "custom_paint";
            case 1 -> "gunsmith";
            case 2 -> "patina";
            case 3 -> "hydrographic";
            default -> "spray_paint";
        };
    }

    // ---- interaction ----

    /** Click on the inspect capsule toggles the auto-spin. */
    public boolean mouseDown(int mx, int my) {
        if (mx >= inspectX && mx <= inspectX + inspectW && my >= inspectY && my <= inspectY + inspectH) {
            inspectOn = !inspectOn;
            return true;
        }
        if (currentOffer == null) {
            return false; // no 3D item to drag yet
        }
        // drag the 3D item
        int radius = Math.max(40, itemCx != 0 ? (inspectW + 80) : 60);
        if (mx >= itemCx - radius && mx <= itemCx + radius
                && my >= itemCy - radius && my <= itemCy + radius) {
            dragging = true;
            dragLastX = mx;
            dragLastY = my;
            return true;
        }
        return false;
    }

    /** Drag accumulates rotation through GuiItemMove's clamped math. */
    public boolean mouseDragged(int mx, int my) {
        if (!dragging) {
            return false;
        }
        int dx = mx - dragLastX;
        int dy = my - dragLastY;
        dragLastX = mx;
        dragLastY = my;
        if (dx == 0 && dy == 0) {
            return true;
        }
        rotY = GuiItemMove.renderRotAngleY(dx, rotY);
        rotX = GuiItemMove.renderRotAngleX(dy, rotX);
        return true;
    }

    public void mouseUp() {
        dragging = false;
    }

    public void reset() {
        inspectOn = false;
        rotX = INITIAL_ROT_X;
        rotY = INITIAL_ROT_Y;
    }
}
