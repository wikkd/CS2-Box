package com.reclizer.csgobox.v26_2.gui.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.terminal.TerminalAnims;
import com.reclizer.csgobox.terminal.TerminalPalette;
import com.reclizer.csgobox.v26_2.utils.AnimRenderOps;
import com.reclizer.csgobox.v26_2.utils.RenderFontTool;
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

    public static final Identifier TEX_AVATAR = Identifier.parse("csgobox:gui/terminal/terminal_avatar");
    public static final Identifier TEX_ROUND_RECT = Identifier.parse("csgobox:gui/terminal/terminal_round_rect");
    public static final Identifier TEX_DOT = Identifier.parse("csgobox:gui/terminal/terminal_dot");
    public static final Identifier TEX_DOT_TILE = Identifier.parse("csgobox:gui/terminal/terminal_dot_tile");
    public static final Identifier TEX_WEAPON = Identifier.parse("csgobox:gui/terminal/weapon");

    private static final int BUBBLE_RADIUS = 4;
    private static final int AVATAR_SIZE = 34;
    private static final int GAP = 6;
    private static final int CARD_W = 262;
    private static final int CARD_THUMB_W = 96;
    private static final int CARD_RADIUS = 6;
    private static final int ROW_H = 19; // offer-info line-height 1.55 @12px (TERMINAL-LAYOUT-SPEC §3)
    private static final int MAX_ENTRIES = 64;
    /** 13px font × line-height 1.55 (docs/TERMINAL-LAYOUT-SPEC.md §3). */
    private static final int LINE_H = 20;
    /** Bubble text padding (HTML .bubble padding: 8px 12px). */
    private static final int BUBBLE_PAD_X = 12;
    private static final int BUBBLE_PAD_Y = 8;
    /** Bubble font scale: 13px / 8px glyph base (TERMINAL-LAYOUT-SPEC §1). */
    private static final float BUBBLE_SCALE = 1.625F;

    /** Wheel scroll state: offset in px, clamped to [0, maxScroll] by each render. */
    private int scrollOffset;
    private int maxScroll;

    /** 滚轮：scrollY>0 = 上滚（看更早）。范围由下一次 render 钳制。 */
    public void scrolled(double scrollY) {
        scrollOffset += (int) Math.round(scrollY * 20);
    }

    public void render(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1,
                       long nowMs, NegotiationModel model) {
        // panel background: dot grid tiles (24px period, 1 blit per dot —
        // replaces the old per-frame fill storm of drawDotGrid).
        int bodyTop = y0 + 20; // below the title strip
        drawDotGrid(gg, x0 + 8, bodyTop + 4, x1 - x0 - 16, y1 - bodyTop - 8);
        // title strip
        Font font = Minecraft.getInstance().font;
        RenderFontTool.drawSpacedText(gg, font,
                Component.translatable("csgobox.terminal.chat.title").getString(),
                x0 + 12, y0 + 5, 2F, 1.5F, TerminalPalette.TITLE);

        // chat stream: newest at the bottom, only the visible window;
        // scrollOffset = 从最新条目回退的像素数（>0 表示滚回看更早）
        List<Object> entries = model.history();
        int start = Math.max(0, entries.size() - MAX_ENTRIES);
        int viewportH = y1 - bodyTop - 4;
        boolean pinned = scrollOffset <= 0;
        int totalH = 0;
        for (int i = entries.size() - 1; i >= start; i--) {
            totalH += entryHeight(gg, entries.get(i), x1 - x0 - 24) + GAP;
        }
        maxScroll = Math.max(0, totalH - viewportH);
        if (pinned) {
            scrollOffset = 0;
        } else {
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
        int bottom = y1 - 4 + scrollOffset;
        for (int i = entries.size() - 1; i >= start; i--) {
            Object e = entries.get(i);
            int h = entryHeight(gg, e, x1 - x0 - 24);
            if (bottom - h >= y1 - 4) { // 完全滚出面板底部：跳过但仍消耗高度
                bottom -= h + GAP;
                continue;
            }
            if (bottom - h < bodyTop + 2) {
                break;
            }
            drawEntry(gg, x0 + 8, bottom - h, x1 - x0 - 16, h, nowMs, model, e);
            bottom -= h + GAP;
        }
    }

    /** Vertical extent of one chat entry (bubble / offer card). */
    private int entryHeight(GuiGraphicsExtractor gg, Object e, int availW) {
        Font font = Minecraft.getInstance().font;
        if (e instanceof NegotiationModel.LineEntry le) {
            return hFor(le, Math.min(availW - AVATAR_SIZE - GAP, 300));
        }
        if (e instanceof NegotiationModel.OfferEntry) {
            return CARD_RADIUS * 2 + 60 + 4 * ROW_H; // padding + thumb + 4 rows
        }
        if (e instanceof NegotiationModel.SystemEntry se) {
            String text = sysText(se);
            return Math.round(font.width(text) * 1.375F / (availW - 20)) * LINE_H + 14;
        }
        return 20;
    }

    private void drawEntry(GuiGraphicsExtractor gg, int x, int y, int availW, int h,
                           long nowMs, NegotiationModel model, Object e) {
        if (e instanceof NegotiationModel.LineEntry le) {
            drawLine(gg, x, y, availW, nowMs, model, le);
        } else if (e instanceof NegotiationModel.OfferEntry oe) {
            drawOfferCard(gg, x, y, nowMs, oe);
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
        int bw = Math.min(availW - AVATAR_SIZE - GAP, 300);
        boolean typing = model.status() == NegotiationModel.Status.TYPING
                && le.round() == model.round();
        // bubble
        drawRounded(gg, bx, y, bw, hFor(le, bw), TerminalPalette.BUBBLE, 0xFF39444C);
        if (typing) {
            // 3 typing dots (region 5), animated in the bubble
            for (int i = 0; i < 3; i++) {
                float a = TerminalAnims.typingDotAlpha(nowMs, i);
                float dy = TerminalAnims.typingDotY(nowMs, i);
                int cx = bx + 14 + i * 12;
                int cy = y + 15;
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
    private void drawOfferCard(GuiGraphicsExtractor gg, int x, int y, long nowMs,
                               NegotiationModel.OfferEntry oe) {
        NegotiationModel.Offer offer = oe.offer();
        boolean finalRound = offer.finalRound();
        int cardBg = finalRound ? TerminalPalette.OFFER_WHITE_CARD : TerminalPalette.OFFER_CARD;
        int cardBorder = finalRound ? 0xFF39444C : TerminalPalette.OFFER_CARD_BORDER;
        int textColor = finalRound ? TerminalPalette.TEXT_WHITE_CARD : TerminalPalette.TEXT;
        int dimColor = finalRound ? TerminalPalette.TEXT_WHITE_CARD_DIM : TerminalPalette.TEXT_DIM;

        drawRounded(gg, x, y, CARD_W, CARD_RADIUS * 2 + 60 + 4 * ROW_H, cardBg, cardBorder);

        // rarity stripe (4px, left)
        int rarity = rarityColor(offer.skinIdx());
        AnimRenderOps.fill(gg, x + 3, y + 3, x + 7, y + CARD_RADIUS * 2 + 60 + 4 * ROW_H - 3, rarity);

        // thumb: weapon png (64x40 -> 96x60), baked 14deg tilt + gradient
        String wp = NegotiationModel.SKIN_WP[offer.skinIdx()];
        Identifier tex = TEX_WEAPON.withSuffix("_" + wp);
        AnimRenderOps.blitTextured(gg, tex, x + 18, y + 12, 96, 60, 128, 80);

        // 4 info rows, flip-in staggered 90ms (slide + alpha approximation)
        int ix = x + 124;
        int iy = y + 10;
        String head = finalRound
                ? Component.translatable("csgobox.terminal.offer.final").getString()
                : Component.translatable("csgobox.terminal.offer.head", offer.round()).getString();
        if (oe.status() == NegotiationModel.OFFER_REJECTED) {
            head += Component.translatable("csgobox.terminal.card.rejected").getString();
        } else if (oe.status() == NegotiationModel.OFFER_ACCEPTED) {
            head += Component.translatable("csgobox.terminal.card.accepted").getString();
        }
        String name = Component.translatable(NegotiationModel.SKIN_NAME_KEYS[offer.skinIdx()]).getString();
        String wear = Component.translatable(NegotiationModel.SKIN_WEAR_KEYS[offer.skinIdx()]).getString();
        String price = finalRound
                ? Component.translatable("csgobox.terminal.offer.price.green", offerPrice(offer)).getString()
                : Component.translatable("csgobox.terminal.offer.price", offerPrice(offer)).getString();
        Font font = Minecraft.getInstance().font;
        float headAlpha = TerminalAnims.flipAlpha(nowMs, oe.atMs(), 0);
        if (headAlpha > 0F) {
            float headDy = TerminalAnims.flipSlideY(nowMs, oe.atMs(), 0);
            int headColor = finalRound ? dimColor : TerminalPalette.RARITY_TEXT;
            int alphaColor = (headColor & 0x00FFFFFF)
                    | (Math.round(255 * Math.min(1F, headAlpha)) << 24);
            RenderFontTool.drawStringClamped(gg, font, head, ix, iy + headDy,
                    0, 0, 1.5F, CARD_W - 124 - 8, alphaColor);
        }
        row(gg, font, name, ix, iy + ROW_H, nowMs, oe.atMs(), 1, 1.5F, textColor);
        row(gg, font, wear, ix, iy + 2 * ROW_H, nowMs, oe.atMs(), 2, 1.5F, dimColor);
        row(gg, font, price, ix, iy + 3 * ROW_H, nowMs, oe.atMs(), 3, 1.5F,
                finalRound ? TerminalPalette.GREEN : TerminalPalette.PILL_GREEN_TEXT);
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
        return NegotiationModel.SKIN_PRICE[offer.skinIdx()];
    }

    /** System bubble: centred dim text (failed => red). */
    private void drawSystem(GuiGraphicsExtractor gg, int x, int y, int availW,
                            NegotiationModel.SystemEntry se) {
        Font font = Minecraft.getInstance().font;
        String text = sysText(se);
        int color = se.failed() ? TerminalPalette.SYS_FAILED : TerminalPalette.SYS_MUTED;
        int textW = Math.round(font.width(text) * 1.375F) + 1 * (text.length() - 1);
        RenderFontTool.drawSpacedText(gg, font, text,
                x + (availW - textW) / 2F, y + 2, 0.5F, 1.375F, color);
    }

    /** System text with the local player name as %s (multi-arg safe). */
    private static String sysText(NegotiationModel.SystemEntry se) {
        net.minecraft.world.entity.player.Player p = Minecraft.getInstance().player;
        return Component.translatable(se.textKey(),
                p == null ? "?" : p.getName().getString()).getString();
    }

    /** FormattedCharSequence wrapper for plain strings. */
    private static FormattedCharSequence fcs(String s) {
        return FormattedCharSequence.forward(s, Style.EMPTY);
    }

    /** Rounded rect via the white membrane + tint (round_rect 16x16). */
    public static void drawRounded(GuiGraphicsExtractor gg, int x, int y, int w, int h,
                                   int fill, int border) {
        if (w <= 0 || h <= 0) {
            return;
        }
        // border: draw a slightly larger rounded rect underneath
        AnimRenderOps.blitTextured(gg, TEX_ROUND_RECT, x - 1, y - 1, w + 2, h + 2,
                0, 0, 16, 16, 16, 16, border);
        AnimRenderOps.blitTextured(gg, TEX_ROUND_RECT, x, y, w, h,
                0, 0, 16, 16, 16, 16, fill);
    }

    /** Typing dot with alpha via the tint channel (dot.png is #9aa4ad). */
    private void blitDotAlpha(GuiGraphicsExtractor gg, int x, int y, float alpha) {
        int a = (int) (255 * Math.max(0F, Math.min(1F, alpha)));
        int tint = (a << 24) | 0x9AA4AD;
        AnimRenderOps.blitTextured(gg, TEX_DOT, x, y, 6, 6,
                0, 0, 6, 6, 6, 6, tint);
    }

    /** Dot grid: one 24x24 tile blit per point (replaces drawDotGrid fills). */
    public static void drawDotGrid(GuiGraphicsExtractor gg, int x0, int y0, int w, int h) {
        int period = 24;
        int ox = x0 - (x0 % period + period) % period;
        int oy = y0 - (y0 % period + period) % period;
        for (int y = oy; y < y0 + h; y += period) {
            for (int xx = ox; xx < x0 + w; xx += period) {
                AnimRenderOps.blitTextured(gg, TEX_DOT_TILE, xx, y, period, period, 512, 512);
            }
        }
    }

    /** Rarity stripe colour for a skin index (HTML --rarity-*). */
    public static int rarityColor(int skinIdx) {
        return "purple".equals(NegotiationModel.SKIN_RARITY[skinIdx])
                ? TerminalPalette.RARITY_CLASSIFIED : TerminalPalette.RARITY_RESTRICTED;
    }
}
