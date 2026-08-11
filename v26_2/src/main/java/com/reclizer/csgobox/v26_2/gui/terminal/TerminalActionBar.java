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
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Region 6: action bar — negotiation counter (flip on change), info dot with
 * tooltip, cap dropdown (opens upward) and the accept / reject capsules
 * (700ms long-press to fire, HTML cubic-bezier(.25,.6,.3,1) fill).
 *
 * era: decoupled
 */
public final class TerminalActionBar {

    // HTML prototype px -> gui px (canvas 1356 -> gui 427, k = 427/1356).
    private static final int BAR_H = 10;
    private static final int CAPSULE_H = 9;
    private static final int HOLD_FULL = 1; // holdFill() >= 1 fires

    /** Baked SVG->PNG textures: info badge (light disc + dark "i") and the
     *  upward chevron — the 1-gui fills and font glyphs they replace render as
     *  chunky 4px blocks at guiScale 4. */
    private static final Identifier TEX_INFO = Identifier.parse("csgobox:textures/gui/terminal/terminal_info.png");
    private static final Identifier TEX_CHEVRON = Identifier.parse("csgobox:textures/gui/terminal/terminal_chevron.png");

    private enum Pill { NONE, ACCEPT, REJECT }

    private Pill pressPill = Pill.NONE;
    private long pressStartMs;
    private boolean capOpen;
    private String lastCounterText = "";
    private long counterFlipAtMs;

    // capsule rectangles (updated each render for hit-testing)
    private int acceptX, acceptY, acceptW, acceptH;
    private int rejectX, rejectY, rejectW, rejectH;
    private int capX, capY, capW, capH;
    private int infoX, infoY;

    public void render(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1,
                       long nowMs, NegotiationModel model, int mx, int my) {
        AnimRenderOps.fill(gg, x0, y0, x1, y1, TerminalPalette.ACTION_BG);
        // design .action-bar: 2px olive top separator
        AnimRenderOps.fill(gg, x0, y0, x1, y0 + 1, TerminalPalette.FRAME);
        Font font = Minecraft.getInstance().font;
        int midY = y0 + BAR_H / 2;

        // ---- counter (flip on text change) ----
        NegotiationModel.CounterLabel label = model.counterLabel();
        Component counter = Component.translatable(label.key(), label.args());
        String counterStr = counter.getString();
        if (!counterStr.equals(lastCounterText)) {
            lastCounterText = counterStr;
            counterFlipAtMs = nowMs;
        }
        float flip = TerminalAnims.counterFlip(nowMs, counterFlipAtMs);
        float slide = (1F - flip) * 3F;
        int counterW = RenderFontTool.drawSpacedText(gg, font, counterStr, x0 + 4, midY - 2 + slide,
                0.3F, 0.47F, TerminalPalette.ACTION_TEXT);

        // ---- info badge (design .info: light disc + dark "i", baked SVG->PNG) ----
        infoX = x0 + 4 + counterW + 2;
        infoY = midY - 2;
        AnimRenderOps.blitTextured(gg, TEX_INFO, infoX, infoY, 4, 4,
                0, 0, 32, 32, 32, 32, 0xFFFFFFFF);
        if (mx >= infoX - 1 && mx <= infoX + 5 && my >= infoY - 1 && my <= infoY + 5) {
            drawTooltip(gg, infoX + 6, midY + 3, font);
        }

        // ---- cap label + dropdown (opens upward) ----
        String capText = model.cap() == NegotiationModel.CAP_UNLIMITED
                ? Component.translatable("csgobox.terminal.cap.label",
                        Component.translatable("csgobox.terminal.cap.unlimited")).getString()
                : Component.translatable("csgobox.terminal.cap.label", model.cap()).getString();
        int capWText = Math.round(font.width(capText) * 0.47F) + Math.round(0.3F * (capText.length() - 1));
        capX = x1 - capWText - 6;
        capY = midY - 2;
        capW = capWText + 4;
        capH = 5;
        boolean capHover = mx >= capX && mx <= capX + capW && my >= capY && my <= capY + capH;
        RenderFontTool.drawSpacedText(gg, font, capText, capX, capY,
                0.3F, 0.47F, capHover ? TerminalPalette.CAP_SELECTED : TerminalPalette.ACTION_TEXT);
        // chevron — always points up (the menu opens upward, HTML .chev)
        AnimRenderOps.blitTextured(gg, TEX_CHEVRON, capX + capWText - 2, capY + 1, 3, 2,
                0, 0, 32, 32, 32, 32, 0xFFFFFFFF);
        if (capOpen) {
            drawCapMenu(gg, capX + capW - 27, capY - 5 * 7 - 1, nowMs, model, mx, my);
        }

        // ---- accept / reject capsules: content-sized, accept left / reject right ----
        int pillPad = 6;
        String acceptLabel = Component.translatable("csgobox.terminal.accept",
                "¥" + (model.pending() != null
                        ? String.valueOf(TerminalOfferItems.priceFor(model.pending()))
                        : model.offerPrice())).getString();
        String rejectLabel = Component.translatable("csgobox.terminal.reject").getString();
        acceptW = pillWidth(font, acceptLabel) + 2 * pillPad;
        rejectW = pillWidth(font, rejectLabel) + 2 * pillPad;
        acceptH = rejectH = CAPSULE_H;
        acceptX = x0 + pillPad;
        // pills sit right under the top row (design gap 9px canvas -> ~2px gui)
        acceptY = midY + 6;
        rejectX = x1 - pillPad - rejectW;
        rejectY = acceptY;

        boolean pending = model.status() == NegotiationModel.Status.PENDING;
        boolean finalRound = model.pending() != null && model.pending().finalRound();
        boolean busy = model.status() == NegotiationModel.Status.ACCEPT_BUSY
                || model.status() == NegotiationModel.Status.REJECT_BUSY
                || model.status() == NegotiationModel.Status.CLOSED
                || model.status() == NegotiationModel.Status.FAILED;

        drawCapsule(gg, acceptX, acceptY, acceptW, acceptH,
                TerminalPalette.PILL_GREEN_BORDER, TerminalPalette.PILL_GREEN_TEXT,
                TerminalPalette.HOLD_ACCEPT,
                acceptLabel,
                pending, busy, nowMs, pressPill == Pill.ACCEPT, mx, my);
        boolean rejectDisabled = finalRound && !pending;
        int rejectBorder = pressPill == Pill.REJECT
                ? TerminalPalette.HOLD_REJECT : TerminalPalette.PILL_GRAY_BORDER;
        drawCapsule(gg, rejectX, rejectY, rejectW, rejectH,
                rejectBorder, TerminalPalette.PILL_GRAY_TEXT,
                TerminalPalette.HOLD_REJECT,
                rejectLabel,
                pending, busy || rejectDisabled, nowMs, pressPill == Pill.REJECT, mx, my);
    }

    /** Rendered label width at the capsule's 0.6/0.51 style (same as drawCapsule). */
    private static int pillWidth(Font font, String label) {
        return Math.round(font.width(label) * 0.51F) + Math.round(0.6F * (label.length() - 1));
    }

    private void drawCapsule(GuiGraphicsExtractor gg, int x, int y, int w, int h,
                             int border, int text, int fillColor,
                             String label, boolean enabled, boolean busy,
                             long nowMs, boolean pressing, int mx, int my) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        // disabled -> dim
        int borderC = busy ? 0x33FFFFFF : border;
        int textC = busy ? 0x55FFFFFF : text;
        if (!enabled) {
            borderC = 0x2EFFFFFF;
            textC = 0x66FFFFFF;
        }
        if (hover && !pressing && !busy && enabled) {
            borderC = 0xFFFFFFFF;
        }
        // outline pill: interior in the action-bar bg so only the 1px ring shows
        TerminalChatRegion.drawPill(gg, x, y, w, h, TerminalPalette.ACTION_BG, borderC);
        // fill progress (pill-shaped: scissor the full capsule interior to the
        // progress width so the fill hugs the rounded ends while pressing)
        float fill = 0F;
        if (pressing && enabled) {
            fill = TerminalAnims.holdFill(nowMs, pressStartMs);
            int fw = Math.max(0, Math.round((w - 2) * Math.min(1F, fill)));
            if (fw > 0) {
                AnimRenderOps.scissor(gg, x + 1, y + 1, fw, h - 2);
                int ri = Math.max(1, (h - 2) / 2);
                int di = 2 * ri;
                AnimRenderOps.blitTextured(gg, TerminalChatRegion.TEX_CIRCLE,
                        x + 1, y + 1, di, di, 0, 0, 32, 32, 32, 32, fillColor);
                AnimRenderOps.blitTextured(gg, TerminalChatRegion.TEX_CIRCLE,
                        x + w - 1 - di, y + 1, di, di, 0, 0, 32, 32, 32, 32, fillColor);
                AnimRenderOps.fill(gg, x + 1 + ri, y + 1, x + w - 1 - ri, y + h - 1, fillColor);
                AnimRenderOps.scissorDisable(gg);
            }
            if (fill >= HOLD_FULL) {
                textC = TerminalPalette.WHITE;
            }
        }
        Font font = Minecraft.getInstance().font;
        int labelW = pillWidth(font, label);
        RenderFontTool.drawSpacedText(gg, font, label,
                x + (w - labelW) / 2F, y + (h - 3) / 2F - 1, 0.6F, 0.51F, textC);
    }

    /** Dropdown anchored above the cap label, 6 options, selected green. */
    private void drawCapMenu(GuiGraphicsExtractor gg, int x, int yTop, long nowMs,
                             NegotiationModel model, int mx, int my) {
        int w = 25;
        int rowH = 7;
        AnimRenderOps.fill(gg, x, yTop, x + w, yTop + 6 * rowH, TerminalPalette.MENU_BG);
        AnimRenderOps.fill(gg, x, yTop, x + w, yTop + 1, TerminalPalette.MENU_BORDER);
        AnimRenderOps.fill(gg, x, yTop + 6 * rowH - 1, x + w, yTop + 6 * rowH, TerminalPalette.MENU_BORDER);
        AnimRenderOps.fill(gg, x, yTop, x + 1, yTop + 6 * rowH, TerminalPalette.MENU_BORDER);
        AnimRenderOps.fill(gg, x + w - 1, yTop, x + w, yTop + 6 * rowH, TerminalPalette.MENU_BORDER);
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < NegotiationModel.CAPS.length + 1; i++) {
            int rowY = yTop + i * rowH;
            boolean hover = mx >= x && mx <= x + w && my >= rowY && my <= rowY + rowH;
            int cap = i < NegotiationModel.CAPS.length ? NegotiationModel.CAPS[i]
                    : NegotiationModel.CAP_UNLIMITED;
            boolean selected = cap == model.cap();
            if (hover) {
                AnimRenderOps.fill(gg, x + 1, rowY, x + w - 1, rowY + rowH, TerminalPalette.MENU_OPT_HOVER);
            }
            String text = cap == NegotiationModel.CAP_UNLIMITED
                    ? Component.translatable("csgobox.terminal.cap.unlimited").getString()
                    : String.valueOf(cap);
            int color = selected ? TerminalPalette.CAP_SELECTED : TerminalPalette.CAP_DIM;
            RenderFontTool.drawSpacedText(gg, font, text, x + 3, rowY + 1,
                    0.16F, 0.47F, color);
        }
    }

    private void drawTooltip(GuiGraphicsExtractor gg, int x, int y, Font font) {
        String tip = Component.translatable("csgobox.terminal.tip").getString();
        int w = Math.round(font.width(tip) * 0.43F) + 5;
        int h = 8;
        TerminalChatRegion.drawRounded(gg, x, y, w, h, TerminalPalette.TOOLTIP_BG,
                TerminalPalette.TOOLTIP_BORDER);
        RenderFontTool.drawString(gg, font, fcs(tip), x + 3, y + 2, 0, 0, 0.43F, TerminalPalette.TEXT);
    }

    /** FormattedCharSequence wrapper for plain strings. */
    private static FormattedCharSequence fcs(String s) {
        return FormattedCharSequence.forward(s, Style.EMPTY);
    }

    // ---- interaction (coordinates relative to the region origin) ----

    /** Returns true if the click was consumed by the action bar. */
    public boolean mouseDown(int absX, int absY, long nowMs, NegotiationModel model) {
        if (capOpen) {
            // dropdown option hit? (same geometry as drawCapMenu)
            int menuX = capX + capW - 27;
            int menuY = capY - 5 * 7 - 1;
            for (int i = 0; i < NegotiationModel.CAPS.length + 1; i++) {
                int rowY = menuY + i * 7;
                if (absX >= menuX && absX <= menuX + 25 && absY >= rowY && absY <= rowY + 7) {
                    int cap = i < NegotiationModel.CAPS.length ? NegotiationModel.CAPS[i]
                            : NegotiationModel.CAP_UNLIMITED;
                    model.setCap(cap);
                    capOpen = false;
                    return true;
                }
            }
            capOpen = false;
        }
        if (absX >= capX && absX <= capX + capW && absY >= capY && absY <= capY + capH) {
            capOpen = !capOpen;
            return true;
        }
        if (absX >= acceptX && absX <= acceptX + acceptW && absY >= acceptY && absY <= acceptY + acceptH) {
            pressPill = Pill.ACCEPT;
            pressStartMs = nowMs;
            return true;
        }
        if (absX >= rejectX && absX <= rejectX + rejectW && absY >= rejectY && absY <= rejectY + rejectH) {
            pressPill = Pill.REJECT;
            pressStartMs = nowMs;
            return true;
        }
        return false;
    }

    /** Fires the pill if the hold completed; always clears the press. */
    public void mouseUp(long nowMs, NegotiationModel model) {
        if (pressPill == Pill.NONE) {
            return;
        }
        float fill = TerminalAnims.holdFill(nowMs, pressStartMs);
        if (fill >= HOLD_FULL) {
            if (pressPill == Pill.ACCEPT) {
                model.acceptNow(nowMs);
            } else {
                model.rejectNow(nowMs);
            }
        }
        pressPill = Pill.NONE;
    }

    public boolean isOpen() {
        return capOpen;
    }

    public void close() {
        capOpen = false;
        pressPill = Pill.NONE;
    }
}
