package com.reclizer.csgobox.v26_1_2.gui.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.utils.ItemDrag3D;
import com.reclizer.csgobox.utils.Quat;
import com.reclizer.csgobox.terminal.TerminalAnims;
import com.reclizer.csgobox.terminal.TerminalPalette;
import com.reclizer.csgobox.terminal.WearBands;
import com.reclizer.csgobox.v26_1_2.utils.AnimRenderOps;
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

/**
 * Region 8: the offer inspection panel — A name box, B rarity block,
 * C inspect capsule (auto-spin), D 3D item (drag to rotate), E wear tier,
 * F five-band wear bar + arrow + scan, G wear value, H meta row.
 *
 * era: decoupled
 */
public final class TerminalOfferRegion {

    public static final Identifier TEX_CIRCLE_GLOW = Identifier.parse("csgobox:textures/gui/terminal/terminal_circle_glow.png");
    public static final Identifier TEX_AVATAR_WM = Identifier.parse("csgobox:textures/gui/terminal/terminal_avatar_wm.png");
    public static final Identifier TEX_SCAN_BAND = Identifier.parse("csgobox:textures/gui/terminal/terminal_scan_band.png");
    public static final Identifier TEX_WEAPON = Identifier.parse("csgobox:textures/gui/terminal/weapon");

    /**
     * HTML initial tilt: lean long items (pickaxe/sword) forward so the
     * preview reads as a CS2-style angled showcase instead of an upright
     * sliver — 3/4 view for flat items (armour/plans) too.
     */
    private static final float INITIAL_ROT_X = (float) Math.toRadians(-38);
    private static final float INITIAL_ROT_Y = (float) Math.toRadians(24);

    private boolean inspectOn;
    private boolean dragging;
    private int dragLastX;
    private int dragLastY;
    private final ItemDrag3D itemDrag = new ItemDrag3D(INITIAL_ROT_X, INITIAL_ROT_Y);
    /** The offer seen by the last render() pass (null until round 1 lands). */
    private NegotiationModel.Offer currentOffer;

    // hit-test rects (updated each render)
    private int inspectX, inspectY, inspectW, inspectH;
    private int itemCx, itemCy;

    public void render(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1,
                       long nowMs, NegotiationModel model, Player player,
                       int mx, int my) {
        this.itemDrag.tick();
        // body gradient
        AnimRenderOps.fillGradient(gg, x0, y0, x1, y1, TerminalPalette.BODY_LIGHT_TOP,
                TerminalPalette.BODY_LIGHT_BOTTOM);
        TerminalChatRegion.drawDotGrid(gg, x0 + 6, y0 + 6, x1 - x0 - 12, y1 - y0 - 12);

        NegotiationModel.Offer offer = model.pending();
        this.currentOffer = offer;
        Font font = Minecraft.getInstance().font;
        int cx = (x0 + x1) / 2;
        // design .wm-circle sits at 47% of the body height
        int cy = y0 + Math.round((y1 - y0) * 0.47F);

        // ---- D: watermark circle + 3D item (centre) ----
        int wmSize = Math.min(x1 - x0 - 19, y1 - y0 - 28);
        int wmX = cx - wmSize / 2;
        int wmY = cy - wmSize / 2;
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE_GLOW, wmX, wmY, wmSize, wmSize, 128, 128);
        // watermark glyph centred inside the circle (design .wm-circle img 62%, flex-centre)
        int wmInner = Math.round(wmSize * 0.62F);
        AnimRenderOps.blitTextured(gg, TEX_AVATAR_WM, wmX + (wmSize - wmInner) / 2,
                wmY + (wmSize - wmInner) / 2, wmInner, wmInner, 128, 128);

        if (offer != null) {
            itemCx = cx;
            itemCy = cy;
            // enlarged preview: ~88% of the watermark circle (audit: 0.95 read
            // as too big/overwhelming; with the forward lean the projected
            // span reads as a balanced showcase inside the halo)
            float itemSize = Math.max(14F, wmSize * 0.88F);
            // slow idle auto-spin (6 deg/s); inspect capsule boosts to 24 deg/s
            // The spin composes as an X-axis rotation pre-multiplied onto the
            // drag orientation (equivalent to the legacy rotY += spinDeg form).
            float spinDeg = inspectOn ? TerminalAnims.spinDeg(nowMs) : nowMs / 1000F * 3F;
            Quat spun = Quat.mul(Quat.fromAxisAngle(1, 0, 0, (float) Math.toRadians(spinDeg)),
                    this.itemDrag.rotation());
            ItemStack stack = TerminalOfferItems.itemFor(offer);
            if (AnimRenderOps.supports3D()) {
                // visual audit: the item's optical centre sat ~25px (3x) below
                // the halo's — raise the render anchor to match the halo centre.
                // renderItem3D takes the top-left of the preview square, so
                // centre the square on the (itemCx, itemCy) anchor.
                int itemHalf = Math.round(itemSize / 2F);
                AnimRenderOps.renderItem3D(gg, stack, player, itemCx - itemHalf, itemCy - itemHalf,
                        spun, itemSize / 16F);
            } else {
                AnimRenderOps.renderItem2D(player, gg, stack, itemCx, itemCy, itemSize / 16F);
            }
        }

        // ---- C: inspect capsule (top-right) ----
        String inspectText = Component.translatable("gui.csgobox.csgo_box.toolbar.inspect").getString();
        int iw = Math.round(font.width(inspectText) * 0.47F) + Math.round(0.6F * (inspectText.length() - 1)) + 7;
        inspectX = x1 - iw - 4;
        inspectY = y0 + 4;
        inspectW = iw;
        inspectH = 7;
        boolean hover = mx >= inspectX && mx <= inspectX + iw && my >= inspectY && my <= inspectY + 22;
        int bg = inspectOn ? 0xFFE5C558 : (hover ? TerminalPalette.INSPECT_HOVER : TerminalPalette.INSPECT_BG);
        int fg = inspectOn ? 0xFF3A3520 : TerminalPalette.INSPECT_TEXT;
        TerminalChatRegion.drawPill(gg, inspectX, inspectY, iw, 7, bg, 0xFF000000);
        if (inspectOn) {
            AnimRenderOps.blitTextured(gg, TEX_CIRCLE_GLOW, inspectX - 3, inspectY - 3,
                    iw + 6, 13, 128, 128);
        }
        int inspectTextW = Math.round(font.width(inspectText) * 0.47F) + Math.round(0.6F * (inspectText.length() - 1));
        RenderFontTool.drawSpacedText(gg, font, inspectText,
                inspectX + (iw - inspectTextW) / 2F, inspectY + 2, 0.6F, 0.47F, fg);

        if (offer == null) {
            return;
        }

        // ---- A: name box (top-left) — ACTUAL offered item name, padded ----
        String name = TerminalOfferItems.nameFor(offer);
        // drawSpacedText renders glyphs + letter-spacing; size the box to the
        // ACTUAL rendered width so the right edge hugs the text (old +8 padding
        // + no-spacing width calc left a big empty tail).
        int nameRenderW = Math.round(font.width(name) * 0.59F)
                + Math.round(1.25F * (name.length() - 1));
        int nameW = nameRenderW + 6;
        TerminalChatRegion.drawRounded(gg, x0 + 4, y0 + 4, nameW, 9, TerminalPalette.BLACK_BOX,
                TerminalPalette.BLACK_BOX);
        RenderFontTool.drawSpacedText(gg, font, name, x0 + 7, y0 + 6,
                1.25F, 0.59F, TerminalPalette.WHITE);

        // ---- B: rarity block (below the name) — 5-tier CS2-style label ----
        int rarity = TerminalChatRegion.rarityColor(offer);
        String rarityName = Component.translatable(
                "csgobox.terminal.rarity." + TerminalOfferItems.rarityKeyFor(offer)).getString();
        int rw = Math.round(font.width(rarityName) * 0.51F) + Math.round(0.9F * (rarityName.length() - 1)) + 6;
        AnimRenderOps.fill(gg, x0 + 4, y0 + 16, x0 + 4 + rw, y0 + 24, rarity);
        RenderFontTool.drawSpacedText(gg, font, rarityName, x0 + 7, y0 + 18,
                0.9F, 0.51F, TerminalPalette.WHITE);

        // ---- E: wear tier (bottom-left) — square label, no corner tab ----
        int tier = WearBands.tierIndex(offer.wearVal());
        String wearName = Component.translatable(WearBands.tierNameKey(tier)).getString();
        int wearW = Math.round(font.width(wearName) * 0.51F) + Math.round(0.6F * (wearName.length() - 1)) + 8;
        int wearY = y1 - 38;
        AnimRenderOps.fill(gg, x0 + 4, wearY, x0 + 4 + wearW, wearY + 8, TerminalPalette.WEAR_BG);
        RenderFontTool.drawSpacedText(gg, font, wearName, x0 + 4, wearY + 2,
                0.6F, 0.51F, TerminalPalette.WHITE);

        // ---- F: five-band wear bar (below the tier) ----
        int barX = x0 + 4;
        int barY = wearY + 18;
        int barW = Math.min(107, (int) ((x1 - x0) * 0.34F));
        int barH = 5;
        long offerAtMs = model.lastOfferEntry() != null ? model.lastOfferEntry().atMs() : nowMs;
        drawWearBar(gg, barX, barY, barW, barH, WearBands.tierIndex(offer.wearVal()));
        // arrow slide zone: light-grey track flush against the bar top
        int zoneY = barY - 3;
        AnimRenderOps.fill(gg, barX, zoneY, barX + barW, zoneY + 3, TerminalPalette.BAR_TRACK);
        // arrow glides toward the wear value (0.95s)
        float arrowT = TerminalAnims.arrowLeft(nowMs, offerAtMs);
        int target = barX + Math.round(barW * offer.wearVal());
        int arrowX = barX + Math.round((target - barX) * arrowT);
        drawArrow(gg, arrowX, barY - 2, barY, offer.wearVal(), nowMs, offerAtMs, barW);

        // ---- G: wear value (compact square box, CS-style 8 decimals) ----
        String wearVal = String.format("%.8f", offer.wearVal());
        int wvW = Math.round(font.width(wearVal) * 0.55F) + Math.round(0.8F * (wearVal.length() - 1)) + 5;
        int wvX = Math.min(barX + barW + 3, x1 - wvW - 4);
        int wvY = barY - 1;
        // near-black square (non-rounded), compact
        AnimRenderOps.fill(gg, wvX, wvY, wvX + wvW, wvY + 5, 0xFF1A1E23);
        RenderFontTool.drawSpacedText(gg, font, wearVal, wvX + 2, wvY + 1,
                0.8F, 0.55F, TerminalPalette.WHITE);

        // ---- H: meta row (bottom-left) — omit pattern when the item has none ----
        String style = Component.translatable(
                "gui.csgobox.csgo_box.style." + styleKey(offer.style())).getString();
        String meta = offer.pattern() > 0
                ? Component.translatable("csgobox.terminal.meta", style,
                        offer.no(), offer.pattern()).getString()
                : Component.translatable("csgobox.terminal.meta_no_pattern",
                        style, offer.no()).getString();
        RenderFontTool.drawSpacedText(gg, font, meta, x0 + 4, y1 - 10,
                0.3F, 0.47F, TerminalPalette.META_TEXT);
    }

    /** FormattedCharSequence wrapper for plain strings. */
    private static FormattedCharSequence fcs(String s) {
        return FormattedCharSequence.forward(s, Style.EMPTY);
    }

    /** Five coded tier bands + edge border (HTML wear-bar border #14181c). */
    private void drawWearBar(GuiGraphicsExtractor gg, int x, int y, int w, int h, int tierIdx) {
        AnimRenderOps.fill(gg, x, y, x + w, y + h, 0xFF14181C);
        int seg = x + 1;
        for (int i = 0; i < WearBands.COUNT; i++) {
            int segW = Math.round((w - 2) * (WearBands.tierHi(i) - WearBands.tierLo(i)));
            AnimRenderOps.fill(gg, seg, y + 1, seg + segW, y + h - 1, WearBands.tierColor(i));
            if (i == tierIdx) {
                // highlight the current tier's band so the wear reading pops
                AnimRenderOps.fill(gg, seg, y + 1, seg + segW, y + 2, 0xCCFFFFFF);
            }
            if (i < WearBands.COUNT - 1) {
                // 1px darker divider so the five coded bands stay distinguishable
                AnimRenderOps.fill(gg, seg + segW - 1, y + 1, seg + segW, y + h - 1,
                        0xCC000000);
            }
            seg += segW;
        }
    }

    /** Triangle arrow (row-by-row fills) + scan band over the wear bar. */
    private void drawArrow(GuiGraphicsExtractor gg, int x, int y, int barY, float wearVal,
                           long nowMs, long startMs, int barW) {
        // 3px-tall triangle, wide top narrowing to an apex down onto the bar
        for (int r = 0; r < 3; r++) {
            int half = 3 - r;
            AnimRenderOps.fill(gg, x - half, y + r, x + half + 1, y + r + 1,
                    TerminalPalette.BAR_WHITE);
        }
        // scan band: horizontal gradient band sweeping left -34% -> 100%
        int scanW = Math.round(barW * 0.34F);
        float s = TerminalAnims.scanX(nowMs, startMs);
        int sx = Math.round(x + (barW - scanW) * s) - scanW;
        AnimRenderOps.blitTextured(gg, TEX_SCAN_BAND, sx, barY + 1, scanW, 1, 8, 24);
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

    /** Drag accumulates raw deltas into the shared drag-feel state. */
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
        // This region's legacy mapping was dx -> X-axis, dy -> Y-axis (the
        // box screens are the opposite); accumulate(dy, dx) preserves that.
        this.itemDrag.accumulate(dy, dx);
        return true;
    }

    public void mouseUp() {
        dragging = false;
        this.itemDrag.release();
    }

    public void reset() {
        inspectOn = false;
        this.itemDrag.reset();
    }
}
