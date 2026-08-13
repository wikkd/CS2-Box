package com.reclizer.csgobox.v26_1_2.gui.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
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

import java.util.List;

/**
 * Region 4+5: the dealer chat stream — avatar + bubble lines, system
 * messages, offer cards (flip-in rows) and the typing indicator. Pure
 * rendering + layout; all timing comes from {@link TerminalAnims} and all
 * state from {@link NegotiationModel}. Draws newest entries at the bottom
 * (HTML auto-scroll) and only the visible window (≤64 entries kept).
 *
 * era: decoupled
 */
public final class TerminalChatRegion {

    public static final Identifier TEX_AVATAR = Identifier.parse("csgobox:textures/gui/terminal/terminal_avatar.png");
    public static final Identifier TEX_ROUND_RECT = Identifier.parse("csgobox:textures/gui/terminal/terminal_round_rect.png");
    public static final Identifier TEX_DOT = Identifier.parse("csgobox:textures/gui/terminal/terminal_dot.png");
    public static final Identifier TEX_DOT_TILE = Identifier.parse("csgobox:textures/gui/terminal/terminal_dot_tile.png");
    public static final Identifier TEX_WEAPON = Identifier.parse("csgobox:textures/gui/terminal/weapon");
    /** Hard-edged white circle (32x32) — perfect pill corners (terminal_circle.png). */
    public static final Identifier TEX_CIRCLE = Identifier.parse("csgobox:textures/gui/terminal/terminal_circle.png");

    // HTML prototype px -> gui px (canvas 1356 -> gui 427, k = 427/1356).
    private static final int BUBBLE_RADIUS = 1;
    private static final int AVATAR_SIZE = 11;
    private static final int GAP = 2;
    private static final int CARD_W = 82;
    private static final int CARD_THUMB_W = 30;
    private static final int CARD_THUMB_H = 19;
    private static final int CARD_RADIUS = 2;
    private static final int ROW_H = 6; // offer-info line-height 1.55 @12px (TERMINAL-LAYOUT-SPEC §3)
    /** Card height = 4 info rows + vertical padding (design offer-info 87px -> gui 28). */
    private static final int CARD_H = 4 * ROW_H + 4;
    /** Bottom inset below the newest entry (HTML .scroll padding-bottom 18px). */
    private static final int BOTTOM_PAD = 5;
    private static final int MAX_ENTRIES = 64;
    /** 13px font × line-height 1.55 (docs/TERMINAL-LAYOUT-SPEC.md §3). */
    private static final int LINE_H = 6;
    /** Bubble text padding (HTML .bubble padding: 8px 12px). */
    private static final int BUBBLE_PAD_X = 4;
    private static final int BUBBLE_PAD_Y = 3;
    /** Bubble font scale: 13px / 8px glyph base (TERMINAL-LAYOUT-SPEC §1). */
    private static final float BUBBLE_SCALE = 0.51F;

    /** Wheel scroll state: offset in px, clamped to [0, maxScroll] by each render. */
    private int scrollOffset;
    private int maxScroll;
    /** True after the user wheels away from the newest entry (re-armed at the bottom). */
    private boolean userScrolled;

    /** 滚轮：scrollY>0 = 上滚（看更早），scrollOffset 向顶部（减小）。 */
    public void scrolled(double scrollY) {
        scrollOffset -= (int) Math.round(scrollY * 6);
        userScrolled = true;
    }

    public void render(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1,
                       long nowMs, NegotiationModel model) {
        // panel background: dot grid tiles (24px period, 1 blit per dot —
        // replaces the old per-frame fill storm of drawDotGrid).
        int bodyTop = y0 + 9; // below the title strip (28px strip -> gui 9)
        drawDotGrid(gg, x0 + 3, bodyTop + 1, x1 - x0 - 5, y1 - bodyTop - 3);
        // title strip
        Font font = Minecraft.getInstance().font;
        RenderFontTool.drawSpacedText(gg, font,
                Component.translatable("csgobox.terminal.chat.title").getString(),
                x0 + 4, y0 + 2, 0.6F, 0.47F, TerminalPalette.TEXT);

        // chat stream: top-down — the first entry sits at the panel top and
        // new entries pop in below it (HTML top-down compact flow). When the
        // stream overflows the viewport it auto-follows the newest entry;
        // scrollOffset = px scrolled DOWN from the top (0 = top, maxScroll =
        // bottom/newest). Wheel-up moves back toward older entries.
        List<Object> entries = model.history();
        int start = Math.max(0, entries.size() - MAX_ENTRIES);
        int viewportH = y1 - bodyTop - 1;
        int totalH = 0;
        for (int i = start; i < entries.size(); i++) {
            totalH += entryHeight(gg, entries.get(i), x1 - x0 - 8) + GAP;
        }
        maxScroll = Math.max(0, totalH - viewportH);
        if (!userScrolled || scrollOffset >= maxScroll - 2) {
            scrollOffset = maxScroll; // follow the newest entry
            userScrolled = false;
        } else {
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
        int top = bodyTop + 1 - scrollOffset;
        for (int i = start; i < entries.size(); i++) {
            Object e = entries.get(i);
            int h = entryHeight(gg, e, x1 - x0 - 8);
            if (top + h < bodyTop + 1) { // fully scrolled out above: skip
                top += h + GAP;
                continue;
            }
            if (top >= y1 - 1) {
                break;
            }
            drawEntry(gg, x0 + 3, top, x1 - x0 - 5, h, nowMs, model, e);
            top += h + GAP;
        }
    }

    /** Vertical extent of one chat entry (bubble / offer card). */
    private int entryHeight(GuiGraphicsExtractor gg, Object e, int availW) {
        Font font = Minecraft.getInstance().font;
        if (e instanceof NegotiationModel.LineEntry le) {
            return hFor(le, Math.min(availW - AVATAR_SIZE - GAP, 95));
        }
        if (e instanceof NegotiationModel.OfferEntry) {
            return CARD_H;
        }
        if (e instanceof NegotiationModel.SystemEntry se) {
            String text = sysText(se);
            return Math.round(font.width(text) * 0.43F / (availW - 6)) * LINE_H + 4;
        }
        return 6;
    }

    private void drawEntry(GuiGraphicsExtractor gg, int x, int y, int availW, int h,
                           long nowMs, NegotiationModel model, Object e) {
        if (e instanceof NegotiationModel.LineEntry le) {
            drawLine(gg, x, y, availW, nowMs, model, le);
        } else if (e instanceof NegotiationModel.OfferEntry oe) {
            drawOfferCard(gg, x, y, availW, nowMs, oe);
        } else if (e instanceof NegotiationModel.SystemEntry se) {
            drawSystem(gg, x, y, availW, se);
        }
    }

    /** Dealer bubble: avatar + rounded bubble + text (or typing dots). */
    private void drawLine(GuiGraphicsExtractor gg, int x, int y, int availW,
                          long nowMs, NegotiationModel model, NegotiationModel.LineEntry le) {
        // avatar
        AnimRenderOps.blitTextured(gg, TEX_AVATAR, x, y, AVATAR_SIZE, AVATAR_SIZE, 64, 64);
        AnimRenderOps.fill(gg, x - 1, y - 1, x + AVATAR_SIZE + 1, y - 1 + 1, 0xFF000000);
        AnimRenderOps.fill(gg, x - 1, y + AVATAR_SIZE, x + AVATAR_SIZE + 1, y + AVATAR_SIZE + 1, 0xFF000000);
        AnimRenderOps.fill(gg, x - 1, y, x, y + AVATAR_SIZE, 0xFF000000);
        AnimRenderOps.fill(gg, x + AVATAR_SIZE, y, x + AVATAR_SIZE + 1, y + AVATAR_SIZE, 0xFF000000);

        int bx = x + AVATAR_SIZE + GAP;
        int bw = Math.min(availW - AVATAR_SIZE - GAP, 95);
        boolean typing = model.status() == NegotiationModel.Status.TYPING
                && le.round() == model.round();
        // bubble
        drawRounded(gg, bx, y, bw, hFor(le, bw), TerminalPalette.BUBBLE, 0xFF39444C);
        if (typing) {
            // 3 typing dots (region 5), animated in the bubble
            for (int i = 0; i < 3; i++) {
                float a = TerminalAnims.typingDotAlpha(nowMs, i);
                float dy = TerminalAnims.typingDotY(nowMs, i);
                int cx = bx + 4 + i * 4;
                int cy = y + 5;
                blitDotAlpha(gg, cx, cy + (int) dy, a);
            }
        } else {
            Font font = Minecraft.getInstance().font;
            String text = Component.translatable(le.textKey()).getString();
            // Clamp to the bubble interior (max-width 82% + padding, P3).
            RenderFontTool.drawStringClamped(gg, font, text,
                    bx + BUBBLE_PAD_X, y + BUBBLE_PAD_Y, 0, 0, BUBBLE_SCALE,
                    bw - 2 * BUBBLE_PAD_X, TerminalPalette.TEXT);
        }
    }

    private int hFor(NegotiationModel.LineEntry le, int bw) {
        Font font = Minecraft.getInstance().font;
        String text = Component.translatable(le.textKey()).getString();
        int textW = Math.round(font.width(text) * BUBBLE_SCALE);
        int lines = textW > bw - 2 * BUBBLE_PAD_X ? 2 : 1;
        return Math.max(AVATAR_SIZE, lines * LINE_H + 2 * BUBBLE_PAD_Y);
    }

    /** Offer card: dark rounded card + rarity stripe + weapon thumb + 4 rows. */
    private void drawOfferCard(GuiGraphicsExtractor gg, int x, int y, int availW,
                               long nowMs, NegotiationModel.OfferEntry oe) {
        NegotiationModel.Offer offer = oe.offer();
        boolean finalRound = offer.finalRound();
        int cardBg = finalRound ? TerminalPalette.OFFER_WHITE_CARD : TerminalPalette.OFFER_CARD;
        int cardBorder = finalRound ? 0xFF39444C : TerminalPalette.OFFER_CARD_BORDER;
        int textColor = finalRound ? TerminalPalette.TEXT_WHITE_CARD : TerminalPalette.CARD_NAME;
        int dimColor = finalRound ? TerminalPalette.TEXT_WHITE_CARD_DIM : TerminalPalette.META_TEXT;

        drawRounded(gg, x, y, CARD_W, CARD_H, cardBg, cardBorder);

        // thumb: dark gradient backdrop (design .thumb linear-gradient) + item;
        // gradient starts right of the 3px rarity stripe so the stripe stays visible
        AnimRenderOps.fillGradient(gg, x + 4, y + 1, x + 4 + CARD_THUMB_W, y + CARD_H - 1,
                TerminalPalette.THUMB_TOP, TerminalPalette.THUMB_BOTTOM);
        // rarity stripe (3px, left) — tier colour of the actual offered item
        int rarity = rarityColor(offer);
        AnimRenderOps.fill(gg, x + 1, y + 1, x + 4, y + CARD_H - 1, rarity);
        net.minecraft.world.entity.player.Player p = net.minecraft.client.Minecraft.getInstance().player;
        // renderItem2D on the decoupled platforms already pins the visual
        // centre on (pX,pY) (bbox compensation inside the facade) — passing
        // the thumb centre directly keeps the icon in the thumb slot instead
        // of floating to the card's top-left corner (~92% fill, 88/96)
        float thumbScale = (CARD_THUMB_W - 2) / 16F;
        AnimRenderOps.renderItem2D(p, gg, TerminalOfferItems.itemFor(offer),
                x + 4 + CARD_THUMB_W / 2F,
                y + 1 + (CARD_H - 2) / 2F,
                thumbScale);

        // 4 info rows, flip-in staggered 90ms (slide + alpha approximation)
        int ix = x + 39;
        int iy = y + 3;
        String head = finalRound
                ? Component.translatable("csgobox.terminal.offer.final").getString()
                : Component.translatable("csgobox.terminal.offer.head", offer.round()).getString();
        if (oe.status() == NegotiationModel.OFFER_REJECTED) {
            head += Component.translatable("csgobox.terminal.card.rejected").getString();
        } else if (oe.status() == NegotiationModel.OFFER_ACCEPTED) {
            head += Component.translatable("csgobox.terminal.card.accepted").getString();
        }
        String name = TerminalOfferItems.nameFor(offer);
        // Wear is rolled per offer (same uniform 0..1 as box opening), so the
        // card shows the ACTUAL tier of this offer's wear value.
        String wear = Component.translatable(
                WearBands.tierNameKey(WearBands.tierIndex(offer.wearVal()))).getString();
        String price = finalRound
                ? Component.translatable("csgobox.terminal.offer.price.green", offerPrice(offer)).getString()
                : Component.translatable("csgobox.terminal.offer.price", offerPrice(offer)).getString();
        Font font = Minecraft.getInstance().font;
        float headAlpha = TerminalAnims.flipAlpha(nowMs, oe.atMs(), 0);
        if (headAlpha > 0F) {
            float headDy = TerminalAnims.flipSlideY(nowMs, oe.atMs(), 0);
            int headColor = finalRound ? dimColor : TerminalPalette.OFFER_HEAD;
            int alphaColor = (headColor & 0x00FFFFFF)
                    | (Math.round(255 * Math.min(1F, headAlpha)) << 24);
            RenderFontTool.drawStringClamped(gg, font, head, ix, iy + headDy,
                    0, 0, 0.47F, availW - 39 - 3, alphaColor);
        }
        row(gg, font, name, ix, iy + ROW_H, nowMs, oe.atMs(), 1, 0.47F, textColor);
        row(gg, font, wear, ix, iy + 2 * ROW_H, nowMs, oe.atMs(), 2, 0.47F, dimColor);
        row(gg, font, price, ix, iy + 3 * ROW_H, nowMs, oe.atMs(), 3, 0.47F,
                finalRound ? TerminalPalette.GREEN : TerminalPalette.OFFER_PRICE);
    }

    private void row(GuiGraphicsExtractor gg, Font font, String text, int x, int y,
                     long nowMs, long startMs, int row, float scale, int color) {
        if (TerminalAnims.flipAlpha(nowMs, startMs, row) <= 0F) {
            return;
        }
        float dy = TerminalAnims.flipSlideY(nowMs, startMs, row);
        RenderFontTool.drawString(gg, font, fcs(text), x, y + dy, 0, 0, scale, color);
    }

    private String offerPrice(NegotiationModel.Offer offer) {
        return String.valueOf(TerminalOfferItems.priceFor(offer));
    }

    /** System bubble: centred dim text (failed => red). */
    private void drawSystem(GuiGraphicsExtractor gg, int x, int y, int availW,
                            NegotiationModel.SystemEntry se) {
        Font font = Minecraft.getInstance().font;
        String text = sysText(se);
        int color = se.failed() ? TerminalPalette.SYS_FAILED : TerminalPalette.SYS_MUTED;
        int textW = Math.round(font.width(text) * 0.43F) + Math.round(0.16F * (text.length() - 1));
        RenderFontTool.drawSpacedText(gg, font, text,
                x + (availW - textW) / 2F, y + 1, 0.16F, 0.43F, color);
    }

    /**
     * System text: server-supplied translatable args win (e.g. the terminal
     * owner's name in the locked refusal); without args the local player name
     * is used as the default single %s arg (multi-arg safe).
     */
    private static String sysText(NegotiationModel.SystemEntry se) {
        String[] args = se.args();
        if (args != null) {
            return Component.translatable(se.textKey(), (Object[]) args).getString();
        }
        net.minecraft.world.entity.player.Player p = Minecraft.getInstance().player;
        return Component.translatable(se.textKey(),
                p == null ? "?" : p.getName().getString()).getString();
    }

    /** FormattedCharSequence wrapper for plain strings. */
    private static FormattedCharSequence fcs(String s) {
        return FormattedCharSequence.forward(s, Style.EMPTY);
    }

    /**
     * Rounded rectangle with a small FIXED corner radius (2px): the old
     * 16x16 membrane scaled its 4px corner to ~25% of the box width, which
     * read as a big arc/capsule on cards and bubbles instead of a proper
     * rounded rectangle. Corners come from the hard-edged circle texture.
     */
    public static void drawRounded(GuiGraphicsExtractor gg, int x, int y, int w, int h,
                                   int fill, int border) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(1, Math.min(2, Math.min(w, h) / 2));
        int d = 2 * r;
        // border ring (1px larger)
        int bd = d + 2;
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE, x - 1, y - 1, bd, bd,
                0, 0, 32, 32, 32, 32, border);
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE, x + w + 1 - bd, y - 1, bd, bd,
                0, 0, 32, 32, 32, 32, border);
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE, x - 1, y + h + 1 - bd, bd, bd,
                0, 0, 32, 32, 32, 32, border);
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE, x + w + 1 - bd, y + h + 1 - bd, bd, bd,
                0, 0, 32, 32, 32, 32, border);
        AnimRenderOps.fill(gg, x + r - 1, y - 1, x + w - r + 1, y + 1, border);
        AnimRenderOps.fill(gg, x + r - 1, y + h - 1, x + w - r + 1, y + h + 1, border);
        AnimRenderOps.fill(gg, x - 1, y + r - 1, x + 1, y + h - r + 1, border);
        AnimRenderOps.fill(gg, x + w - 1, y + r - 1, x + w + 1, y + h - r + 1, border);
        // fill
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE, x, y, d, d,
                0, 0, 32, 32, 32, 32, fill);
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE, x + w - d, y, d, d,
                0, 0, 32, 32, 32, 32, fill);
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE, x, y + h - d, d, d,
                0, 0, 32, 32, 32, 32, fill);
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE, x + w - d, y + h - d, d, d,
                0, 0, 32, 32, 32, 32, fill);
        AnimRenderOps.fill(gg, x + r, y, x + w - r, y + h, fill);
        AnimRenderOps.fill(gg, x, y + r, x + w, y + h - r, fill);
    }

    /**
     * Perfect pill/capsule: a rectangle body plus two full semicircle ends
     * built from a hard-edged circle texture (terminal_circle.png) — no
     * 2px-corner stair-stepping even at large sizes. Border drawn 1px larger.
     */
    public static void drawPill(GuiGraphicsExtractor gg, int x, int y, int w, int h,
                                int fill, int border) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(1, h / 2);
        int d = 2 * r;
        // border caps: diameter d+2 (always even) — concentric with the fill
        // caps so the 1px ring is uniform. Even h: d+2 = h+2, caps and rect
        // both span h+2 rows. Odd h: caps span h+1 rows and the h+2-row rect
        // adds the flat bottom strip mirroring the fill's own; an odd h+2
        // circle would bulge 1px at the bottom arcs instead.
        int bd = d + 2;
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE, x - 1, y - 1, bd, bd,
                0, 0, 32, 32, 32, 32, border);
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE, x + w + 1 - bd, y - 1, bd, bd,
                0, 0, 32, 32, 32, 32, border);
        AnimRenderOps.fill(gg, x + r, y - 1, x + w - r, y + h + 1, border);
        // fill (radius r)
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE, x, y, d, d,
                0, 0, 32, 32, 32, 32, fill);
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE, x + w - d, y, d, d,
                0, 0, 32, 32, 32, 32, fill);
        AnimRenderOps.fill(gg, x + r, y, x + w - r, y + h, fill);
    }

    /** Typing dot with alpha via the tint channel (dot.png is #9aa4ad). */
    private void blitDotAlpha(GuiGraphicsExtractor gg, int x, int y, float alpha) {
        int a = (int) (255 * Math.max(0F, Math.min(1F, alpha)));
        int tint = (a << 24) | 0x9AA4AD;
        AnimRenderOps.blitTextured(gg, TEX_DOT, x, y, 2, 2,
                0, 0, 6, 6, 6, 6, tint);
    }

    /** Dot grid: one 24x24 tile blit per point (replaces drawDotGrid fills). */
    public static void drawDotGrid(GuiGraphicsExtractor gg, int x0, int y0, int w, int h) {
        int period = 8;
        int ox = x0 - (x0 % period + period) % period;
        int oy = y0 - (y0 % period + period) % period;
        for (int y = oy; y < y0 + h; y += period) {
            for (int xx = ox; xx < x0 + w; xx += period) {
                AnimRenderOps.blitTextured(gg, TEX_DOT_TILE, xx, y, period, period, 512, 512);
            }
        }
    }

    /** Rarity stripe colour for the offer's actual item (tier by grade). */
    public static int rarityColor(NegotiationModel.Offer offer) {
        return TerminalPalette.rarityColorForGrade(TerminalOfferItems.gradeFor(offer));
    }
}
