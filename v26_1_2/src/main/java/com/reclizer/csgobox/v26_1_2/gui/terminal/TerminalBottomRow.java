package com.reclizer.csgobox.v26_1_2.gui.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.terminal.TerminalAnims;
import com.reclizer.csgobox.terminal.TerminalPalette;
import com.reclizer.csgobox.v26_1_2.utils.AnimRenderOps;
import com.reclizer.csgobox.v26_1_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Region 9+10+11: countdown panel, the box's single random item slot and the
 * collection strip — three separate olive-bordered panels with title strips,
 * matching the HTML prototype's bottom row (see docs/TERMINAL-LAYOUT-SPEC.md).
 *
 * era: decoupled
 */
public final class TerminalBottomRow {

    public static final Identifier TEX_CIRCLE_GLOW = Identifier.parse("csgobox:textures/gui/terminal/terminal_circle_glow.png");
    public static final Identifier TEX_BADGE = Identifier.parse("csgobox:textures/gui/terminal/terminal_badge.png");

    /** HTML .title-strip 28px canvas -> gui. */
    private static final int STRIP_H = 8;
    /** HTML .digits 21px canvas -> gui scale (21 * 1.253 / 32). */
    private static final float DIGIT_SCALE = 0.72F;
    /** HTML letter-spacing 0.5px canvas -> gui. */
    private static final int DIGIT_SPACE = 1;
    /** HTML .slot-badge 72px canvas -> gui (17 not 18: 18's 9px half-radius
     *  pressed the badge into the panel's top border at 3x). */
    private static final int BADGE_SIZE = 17;
    /** HTML .slot-badge canvas 44px / badge 72px. */
    private static final float ICON_RATIO = 44F / 72F;
    /** Panel gap (HTML bottom-row gap 14px canvas -> gui). */
    private static final int PANEL_GAP = 4;

    private String lastCountdown = "";
    private long countdownFlipAtMs;

    public void render(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1,
                       long nowMs, NegotiationModel model, Player player, Component terminalName) {
        Font font = Minecraft.getInstance().font;

        // ---- sizing (count panel adapts to the digits, slot fixed, xp fills) ----
        String text = TerminalAnims.countdownText(model.countdownRemainingMs());
        if (!text.equals(lastCountdown)) {
            lastCountdown = text;
            countdownFlipAtMs = nowMs;
        }
        int digitW = Math.round(font.width("0") * DIGIT_SCALE);
        int colonW = Math.round(font.width(":") * DIGIT_SCALE);
        int digitsW = 8 * digitW + 3 * colonW + 10 * DIGIT_SPACE;
        int countW = Math.max(46, digitsW + 8);
        int slotW = 31;
        int gap2 = PANEL_GAP * 2;
        int xpW = (x1 - x0) - countW - slotW - gap2;
        // HTML: slot panel top edge sits lower than 9/11 (y 0.895 vs 0.875)
        int slotY0 = y0 + 5;

        // ---- region 9: countdown panel ----
        drawPanel(gg, x0, y0, x0 + countW, y1);
        drawStrip(gg, x0, y0, x0 + countW, y0 + STRIP_H,
                Component.translatable("csgobox.terminal.validity").getString(), true);
        boolean expired = model.countdownRemainingMs() <= 0;
        int color = expired ? TerminalPalette.COUNT_EXPIRED : TerminalPalette.ACTION_TEXT;
        int digitH = Math.round(7F * DIGIT_SCALE);
        int ty = y0 + STRIP_H + (y1 - (y0 + STRIP_H) - digitH) / 2;
        // centre on the ACTUAL glyph widths (digits like "1" are narrower than
        // the "0" estimate, which skewed the row left — asymmetric gutters)
        int renderW = 0;
        for (int i = 0; i < text.length(); i++) {
            renderW += Math.round(font.width(String.valueOf(text.charAt(i))) * DIGIT_SCALE);
            if (i < text.length() - 1) {
                renderW += DIGIT_SPACE;
            }
        }
        int tx = x0 + (countW - renderW) / 2;
        String[] toks = {text.substring(0, 2), text.substring(3, 5),
                text.substring(6, 8), text.substring(9, 11)};
        for (int i = 0; i < 4; i++) {
            tx += RenderFontTool.drawSpacedText(gg, font, toks[i], tx, ty,
                    DIGIT_SPACE, DIGIT_SCALE, color);
            if (i < 3) {
                tx += DIGIT_SPACE;
                tx += RenderFontTool.drawSpacedText(gg, font, ":", tx, ty,
                        DIGIT_SPACE, DIGIT_SCALE, TerminalPalette.COUNT_COLON);
                tx += DIGIT_SPACE;
            }
        }

        // ---- region 10: slot panel (badge + session item icon) ----
        int s0 = x0 + countW + PANEL_GAP;
        int s1 = s0 + slotW;
        drawPanel(gg, s0, slotY0, s1, y1);
        // full-size circular badge, centred (HTML: one holographic badge per
        // terminal session; region 10 is display-only, no item-name caption)
        int badge = Math.min(BADGE_SIZE, slotW - 10);
        int bcx = (s0 + s1) / 2;
        int bcy = slotY0 + (y1 - slotY0) / 2 - 2;
        AnimRenderOps.blitTextured(gg, TEX_BADGE, bcx - badge / 2, bcy - badge / 2,
                badge, badge, 72, 72);
        ItemStack slotItem = TerminalOfferItems.sessionItem();
        AnimRenderOps.renderItem2D(player, gg, slotItem, bcx, bcy,
                (badge * ICON_RATIO) / 16F);

        // ---- region 11: collection panel (xp name + rarity dots) ----
        int xp0 = s1 + PANEL_GAP;
        int xp1 = x1;
        drawPanel(gg, xp0, y0, xp1, y1);
        drawStrip(gg, xp0, y0, xp1, y0 + STRIP_H,
                Component.translatable("csgobox.terminal.collection").getString(), false);
        // dots inside a pill container (design .xp-dots #262c33d9 / 1px #171b1f)
        int dotCore = 3;
        int dotGap = 2;
        int groupGap = 4;
        int padX = 4;
        int padY = 2;
        int dotsW = 0;
        for (int g = 0; g < NegotiationModel.DOT_GROUPS.length; g++) {
            int n = NegotiationModel.DOT_GROUPS[g].pattern.length;
            dotsW += n * dotCore + (n - 1) * dotGap;
            if (g < NegotiationModel.DOT_GROUPS.length - 1) {
                dotsW += groupGap;
            }
        }
        int pillW = dotsW + 2 * padX;
        int pillH = dotCore + 2 * padY;
        int pillX = xp1 - 6 - pillW;
        int pillY = y0 + STRIP_H + (y1 - (y0 + STRIP_H) - pillH) / 2;
        TerminalChatRegion.drawPill(gg, pillX, pillY, pillW, pillH, 0xD9262C33, 0xFF171B1F);
        // terminal's actual name: config "#RRGGBB" colour when set (HTML
        // .xp-name); anvil renames survive via ItemStack.getHoverName()
        String xpName = terminalName.getString();
        TextColor tc = terminalName.getStyle().getColor();
        int nameColor = tc != null ? (0xFF000000 | (tc.getValue() & 0xFFFFFF))
                : TerminalPalette.OFFER_PRICE;
        RenderFontTool.drawSpacedText(gg, font, xpName, xp0 + 4, pillY + 1,
                1F, 0.59F, nameColor);
        int dotX = pillX + padX;
        int dotY = pillY + padY;
        for (int g = 0; g < NegotiationModel.DOT_GROUPS.length; g++) {
            NegotiationModel.DotGroup grp = NegotiationModel.DOT_GROUPS[g];
            int n = grp.pattern.length;
            for (int v : grp.pattern) {
                drawDot(gg, dotX, dotY, v == 1, grp.color);
                dotX += dotCore + dotGap;
            }
            if (g < NegotiationModel.DOT_GROUPS.length - 1) {
                dotX += groupGap - dotGap;
            }
        }
    }

    /** Olive-bordered dark panel (HTML .count-panel/.slot-panel/.xp-panel). */
    private void drawPanel(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1) {
        AnimRenderOps.fillGradient(gg, x0, y0, x1, y1, TerminalPalette.BODY_LIGHT_TOP,
                TerminalPalette.BODY_LIGHT_BOTTOM);
        AnimRenderOps.fill(gg, x0, y0, x1, y0 + 1, TerminalPalette.FRAME);
        AnimRenderOps.fill(gg, x0, y1 - 1, x1, y1, TerminalPalette.FRAME);
        AnimRenderOps.fill(gg, x0, y0, x0 + 1, y1, TerminalPalette.FRAME);
        AnimRenderOps.fill(gg, x1 - 1, y0, x1, y1, TerminalPalette.FRAME);
    }

    /** HTML .title-strip: gradient bar + olive bottom edge + 12px label. */
    private void drawStrip(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1,
                           String label, boolean center) {
        AnimRenderOps.fillGradient(gg, x0, y0, x1, y1, 0xFF66798A, TerminalPalette.TITLE);
        AnimRenderOps.fill(gg, x0, y1 - 1, x1, y1, TerminalPalette.FRAME);
        Font font = Minecraft.getInstance().font;
        int w = 0;
        for (int i = 0; i < label.length(); i++) {
            w += Math.round(font.width(String.valueOf(label.charAt(i))) * 0.47F);
            if (i < label.length() - 1) {
                w += 1;
            }
        }
        float lx = center ? x0 + (x1 - x0 - w) / 2F : x0 + 3F;
        RenderFontTool.drawSpacedText(gg, font, label, lx, y0 + 1, 1F, 0.47F, TerminalPalette.TEXT);
    }

    /** Round dot: filled = 3px circle + glow; hollow = 1px ring (HTML .dot 10px). */
    private void drawDot(GuiGraphicsExtractor gg, int x, int y, boolean filled, int color) {
        if (filled) {
            AnimRenderOps.blitTextured(gg, TEX_CIRCLE_GLOW, x - 2, y - 2, 7, 7, 128, 128);
            AnimRenderOps.blitTextured(gg, TerminalChatRegion.TEX_CIRCLE, x, y, 3, 3,
                    0, 0, 32, 32, 32, 32, color);
        } else {
            AnimRenderOps.blitTextured(gg, TerminalChatRegion.TEX_CIRCLE, x, y, 3, 3,
                    0, 0, 32, 32, 32, 32, color);
            AnimRenderOps.blitTextured(gg, TerminalChatRegion.TEX_CIRCLE, x + 1, y + 1, 1, 1,
                    0, 0, 32, 32, 32, 32, 0xFF262C33);
        }
    }
}
