package com.reclizer.csgobox.forge_26_1_2.gui.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.terminal.TerminalAnims;
import com.reclizer.csgobox.terminal.TerminalPalette;
import com.reclizer.csgobox.terminal.WearBands;
import com.reclizer.csgobox.forge_26_1_2.utils.AnimRenderOps;
import com.reclizer.csgobox.forge_26_1_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Region 4+5: dealer chat stream — avatar, bubble lines, system messages,
 * offer cards and the typing indicator. Pure rendering + layout; timing from
 * {@link TerminalAnims}, state from {@link NegotiationModel}. Newest entries
 * at the bottom, only the visible window drawn (≤64 kept).
 *
 * era: decoupled
 */
public final class TerminalChatRegion {

    public static final Identifier TEX_AVATAR = Identifier.parse("csgobox:textures/gui/terminal/terminal_avatar.png");
    public static final Identifier TEX_DOT_TILE = Identifier.parse("csgobox:textures/gui/terminal/terminal_dot_tile.png");
    public static final Identifier TEX_WEAPON = Identifier.parse("csgobox:textures/gui/terminal/weapon");

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
        // panel background: dot grid tiles (24px period, 1 blit per dot)
        int bodyTop = y0 + 9; // below the title strip (28px strip -> gui 9)
        drawDotGrid(gg, x0 + 3, bodyTop + 1, x1 - x0 - 5, y1 - bodyTop - 3);
        // title strip
        Font font = Minecraft.getInstance().font;
        RenderFontTool.drawSpacedText(gg, font,
                Component.translatable("csgobox.terminal.chat.title").getString(),
                x0 + 4, y0 + 2, 0.6F, 0.47F, TerminalPalette.TEXT);

        // Top-down stream: newest entries at the bottom; overflow auto-follows
        // the newest. scrollOffset = px scrolled down (0 = top, max = newest).
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
        int textW = RenderFontTool.width(font, text, BUBBLE_SCALE);
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

        // thumb: gradient backdrop + item; starts right of the rarity stripe
        AnimRenderOps.fillGradient(gg, x + 4, y + 1, x + 4 + CARD_THUMB_W, y + CARD_H - 1,
                TerminalPalette.THUMB_TOP, TerminalPalette.THUMB_BOTTOM);
        // rarity stripe (3px, left) — tier colour of the actual offered item
        int rarity = rarityColor(offer);
        AnimRenderOps.fill(gg, x + 1, y + 1, x + 4, y + CARD_H - 1, rarity);
        net.minecraft.world.entity.player.Player p = net.minecraft.client.Minecraft.getInstance().player;
        // renderItem2D pins the visual centre on (pX,pY), so pass the thumb
        // centre directly to keep the icon inside the thumb slot
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
        row(gg, font, name, ix, iy + ROW_H, nowMs, oe.atMs(), 1, 0.47F, textColor, false);
        row(gg, font, wear, ix, iy + 2 * ROW_H, nowMs, oe.atMs(), 2, 0.47F, dimColor, true);
        row(gg, font, price, ix, iy + 3 * ROW_H, nowMs, oe.atMs(), 3, 0.47F,
                finalRound ? TerminalPalette.GREEN : TerminalPalette.OFFER_PRICE, true);
    }

    private void row(GuiGraphicsExtractor gg, Font font, String text, int x, int y,
                     long nowMs, long startMs, int row, float scale, int color, boolean hd) {
        if (TerminalAnims.flipAlpha(nowMs, startMs, row) <= 0F) {
            return;
        }
        float dy = TerminalAnims.flipSlideY(nowMs, startMs, row);
        if (hd) {
            RenderFontTool.drawString(gg, font, fcs(text), x, y + dy, 0, 0, scale, color);
        } else {
            RenderFontTool.drawStringVanilla(gg, font, fcs(text), x, y + dy, 0, 0, scale, color);
        }
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
        RenderFontTool.drawSpacedTextVanilla(gg, font, text,
                x + (availW - textW) / 2F, y + 1, 0.16F, 0.43F, color);
    }

    /** Server-supplied args win; otherwise the local player name fills the single %s. */
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



    /** Typing dot: crisp 2x2 fill, alpha carried in the color channel. */
    private void blitDotAlpha(GuiGraphicsExtractor gg, int x, int y, float alpha) {
        int a = (int) (255 * Math.max(0F, Math.min(1F, alpha)));
        int tint = (a << 24) | 0x9AA4AD;
        AnimRenderOps.fill(gg, x, y, x + 2, y + 2, tint);
    }

    /** Dot grid: one 24x24 tile blit per point (replaces drawDotGrid fills). */
    public static void drawDotGrid(GuiGraphicsExtractor gg, int x0, int y0, int w, int h) {
        int period = 24;
        int ox = x0 - (x0 % period + period) % period;
        int oy = y0 - (y0 % period + period) % period;
        for (int y = oy; y < y0 + h; y += period) {
            for (int xx = ox; xx < x0 + w; xx += period) {
                AnimRenderOps.blitTextured(gg, TEX_DOT_TILE, xx, y, period, period, 24, 24);
            }
        }
    }

    /** Rarity stripe colour for the offer's actual item (tier by grade). */
    public static int rarityColor(NegotiationModel.Offer offer) {
        return TerminalPalette.rarityColorForGrade(TerminalOfferItems.gradeFor(offer));
    }
}
