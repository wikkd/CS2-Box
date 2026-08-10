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
import net.minecraft.util.FormattedCharSequence;

/**
 * Region 6: action bar — negotiation counter (flip on change), info dot with
 * tooltip, cap dropdown (opens upward) and the accept / reject capsules
 * (700ms long-press to fire, HTML cubic-bezier(.25,.6,.3,1) fill).
 *
 * era: decoupled
 */
public final class TerminalActionBar {

    private static final int BAR_H = 30;
    private static final int CAPSULE_H = 24;
    private static final int CAPSULE_GAP = 10;
    private static final int HOLD_FULL = 1; // holdFill() >= 1 fires

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
        AnimRenderOps.fill(gg, x0, y0, x1, y1, 0xFF1F2428);
        AnimRenderOps.fill(gg, x0, y0, x1, y0 + 1, 0xFF39444C);
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
        float slide = (1F - flip) * 10F;
        RenderFontTool.drawSpacedText(gg, font, counterStr, x0 + 12, midY - 7 + slide,
                1F, 1.5F, TerminalPalette.TEXT);

        // ---- info dot + tooltip ----
        infoX = x0 + 12;
        infoY = midY + 6;
        AnimRenderOps.fill(gg, infoX, infoY, infoX + 10, infoY + 10, 0xFF000000);
        RenderFontTool.drawString(gg, font, fcs("i"), infoX + 2, infoY - 3, 0, 0, 1.25F,
                TerminalPalette.ICON_DIM);
        if (mx >= infoX - 2 && mx <= infoX + 12 && my >= infoY - 2 && my <= infoY + 12) {
            drawTooltip(gg, x0 + 24, midY + 16, font);
        }

        // ---- cap label + dropdown (opens upward) ----
        String capText = model.cap() == NegotiationModel.CAP_UNLIMITED
                ? Component.translatable("csgobox.terminal.cap.label",
                        Component.translatable("csgobox.terminal.cap.unlimited")).getString()
                : Component.translatable("csgobox.terminal.cap.label", model.cap()).getString();
        int capWText = Math.round(font.width(capText) * 1.5F) + 1 * (capText.length() - 1);
        capX = x1 - capWText - 20;
        capY = midY - 8;
        capW = capWText + 14;
        capH = 16;
        boolean capHover = mx >= capX && mx <= capX + capW && my >= capY && my <= capY + capH;
        RenderFontTool.drawSpacedText(gg, font, capText, capX, capY - 2,
                1F, 1.5F, capHover ? TerminalPalette.CAP_SELECTED : TerminalPalette.CAP_DIM);
        // chevron
        RenderFontTool.drawString(gg, font, fcs(capOpen ? "▲" : "▼"), capX + capW - 6, capY + 1, 0, 0, 0.9F,
                TerminalPalette.CHEVRON);
        if (capOpen) {
            drawCapMenu(gg, capX + capW - 86, capY - 5 * 22 - 4, nowMs, model, mx, my);
        }

        // ---- accept / reject capsules ----
        int avail = x1 - x0;
        acceptW = Math.round((avail - CAPSULE_GAP) / 2F) - 12;
        rejectW = acceptW;
        acceptH = rejectH = CAPSULE_H;
        acceptX = x0 + 12;
        acceptY = y1 - CAPSULE_H - 8;
        rejectX = x0 + 12 + acceptW + CAPSULE_GAP;
        rejectY = acceptY;

        boolean pending = model.status() == NegotiationModel.Status.PENDING;
        boolean finalRound = model.pending() != null && model.pending().finalRound();
        boolean busy = model.status() == NegotiationModel.Status.ACCEPT_BUSY
                || model.status() == NegotiationModel.Status.REJECT_BUSY
                || model.status() == NegotiationModel.Status.CLOSED
                || model.status() == NegotiationModel.Status.FAILED;

        drawCapsule(gg, acceptX, acceptY, acceptW, acceptH,
                TerminalPalette.PILL_GREEN_BORDER, TerminalPalette.PILL_GREEN_TEXT,
                TerminalPalette.PILL_GREEN_FILL,
                Component.translatable("csgobox.terminal.accept",
                        "¥" + model.offerPrice()).getString(),
                pending, busy, nowMs, pressPill == Pill.ACCEPT, mx, my);
        boolean rejectDisabled = finalRound && !pending;
        drawCapsule(gg, rejectX, rejectY, rejectW, rejectH,
                TerminalPalette.PILL_GRAY_BORDER, TerminalPalette.PILL_GRAY_TEXT,
                TerminalPalette.PILL_GRAY_FILL,
                Component.translatable("csgobox.terminal.reject").getString(),
                pending, busy || rejectDisabled, nowMs, pressPill == Pill.REJECT, mx, my);
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
        if (hover && !busy && enabled) {
            borderC = 0xFFFFFFFF;
        }
        TerminalChatRegion.drawRounded(gg, x, y, w, h, 0x00000000, borderC);
        // fill progress
        float fill = 0F;
        if (pressing && enabled) {
            fill = TerminalAnims.holdFill(nowMs, pressStartMs);
            int fw = Math.max(0, Math.round((w - 2) * Math.min(1F, fill)));
            AnimRenderOps.fill(gg, x + 1, y + 1, x + 1 + fw, y + h - 1, fillColor);
            if (fill >= HOLD_FULL) {
                textC = TerminalPalette.WHITE;
            }
        }
        Font font = Minecraft.getInstance().font;
        int labelW = Math.round(font.width(label) * 1.625F) + 2 * (label.length() - 1);
        RenderFontTool.drawSpacedText(gg, font, label,
                x + (w - labelW) / 2F, y + (h - 8) / 2F - 2, 2F, 1.625F, textC);
    }

    /** Dropdown anchored above the cap label, 6 options, selected green. */
    private void drawCapMenu(GuiGraphicsExtractor gg, int x, int yTop, long nowMs,
                             NegotiationModel model, int mx, int my) {
        int w = 80;
        int rowH = 22;
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
            RenderFontTool.drawSpacedText(gg, font, text, x + 8, rowY + 5,
                    0.5F, 1.5F, color);
        }
    }

    private void drawTooltip(GuiGraphicsExtractor gg, int x, int y, Font font) {
        String tip = Component.translatable("csgobox.terminal.tip").getString();
        int w = Math.round(font.width(tip) * 1.375F) + 16;
        int h = 26;
        TerminalChatRegion.drawRounded(gg, x, y, w, h, TerminalPalette.TOOLTIP_BG,
                TerminalPalette.TOOLTIP_BORDER);
        RenderFontTool.drawString(gg, font, fcs(tip), x + 8, y + 7, 0, 0, 1.375F, TerminalPalette.TEXT);
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
            int menuX = capX + capW - 86;
            int menuY = capY - 5 * 22 - 4;
            for (int i = 0; i < NegotiationModel.CAPS.length + 1; i++) {
                int rowY = menuY + i * 22;
                if (absX >= menuX && absX <= menuX + 80 && absY >= rowY && absY <= rowY + 22) {
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
