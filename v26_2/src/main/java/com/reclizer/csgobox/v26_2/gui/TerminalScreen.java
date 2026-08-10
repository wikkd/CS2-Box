package com.reclizer.csgobox.v26_2.gui;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.terminal.TerminalPalette;
import com.reclizer.csgobox.v26_2.gui.terminal.TerminalActionBar;
import com.reclizer.csgobox.v26_2.gui.terminal.TerminalBottomRow;
import com.reclizer.csgobox.v26_2.gui.terminal.TerminalChatRegion;
import com.reclizer.csgobox.v26_2.gui.terminal.TerminalOfferRegion;
import com.reclizer.csgobox.v26_2.utils.AnimRenderOps;
import com.reclizer.csgobox.v26_2.utils.HudVisibility;
import com.reclizer.csgobox.v26_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;

/**
 * Terminal machine screen — the full HTML prototype (design/terminal-chat.html)
 * migrated to Java: chat + offer cards (left), action bar (bottom-left),
 * offer inspection panel (right), countdown / item slot / collection strip
 * (bottom-right). All animation timing lives in common
 * {@link com.reclizer.csgobox.terminal.TerminalAnims}; this screen only
 * assembles the four region helpers and forwards input.
 *
 * era: decoupled
 */
public class TerminalScreen extends Screen {

    private final NegotiationModel model = new NegotiationModel();
    private final TerminalChatRegion chatRegion = new TerminalChatRegion();
    private final TerminalActionBar actionBar = new TerminalActionBar();
    private final TerminalOfferRegion offerRegion = new TerminalOfferRegion();
    private final TerminalBottomRow bottomRow = new TerminalBottomRow();
    private long nowMs;

    public TerminalScreen() {
        super(Minecraft.getInstance(), Minecraft.getInstance().font,
                Component.translatable("gui.csgobox.terminal.title"));
        this.model.start(System.currentTimeMillis());
    }

    // ---- layout fractions (HTML prototype) ----
    private int px(double f) {
        return (int) Math.round(width * f);
    }

    private int py(double f) {
        return (int) Math.round(height * f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(gg, mouseX, mouseY, partialTicks);
        this.nowMs = System.currentTimeMillis();
        model.tick(nowMs);
        Player player = Minecraft.getInstance().player;

        // ---- stage background ----
        AnimRenderOps.fill(gg, 0, 0, width, height, TerminalPalette.OUTSIDE);

        // frame: olive border + inner bg
        int fx0 = px(0.010), fy0 = py(0.012), fx1 = px(0.990), fy1 = py(0.988);
        AnimRenderOps.fill(gg, fx0 - 2, fy0 - 2, fx1 + 2, fy1 + 2, TerminalPalette.FRAME);
        AnimRenderOps.fill(gg, fx0, fy0, fx1, fy1, 0xFF17191C);

        // ---- top bar ----
        int tx0 = fx0, ty0 = fy0, tx1 = fx1, ty1 = py(0.082);
        AnimRenderOps.fill(gg, tx0, ty0, tx1, ty1, TerminalPalette.TOPBAR);
        AnimRenderOps.fill(gg, tx0, ty1 - 1, tx1, ty1, TerminalPalette.FRAME);
        Font font = Minecraft.getInstance().font;
        // left: battery + signal (icons)
        int iconY = ty0 + (ty1 - ty0) / 2 - 4;
        AnimRenderOps.fill(gg, tx0 + 14, iconY, tx0 + 22, iconY + 8, 0xFF14181C);
        AnimRenderOps.fill(gg, tx0 + 15, iconY + 1, tx0 + 21, iconY + 7, TerminalPalette.BATTERY);
        for (int i = 0; i < 3; i++) {
            AnimRenderOps.fill(gg, tx0 + 30 + i * 4, iconY + 8 - (i + 1) * 3,
                    tx0 + 32 + i * 4, iconY + 8, TerminalPalette.BATTERY);
        }
        // centre: title
        RenderFontTool.drawString(gg, font,
                Component.translatable("gui.csgobox.terminal.title").getVisualOrderText(),
                (tx0 + tx1) / 2F, ty0 + 9, 1, 0, 1.5F, TerminalPalette.TITLE);
        // right: close button
        closeX = tx1 - 26;
        closeY = ty0 + 7;
        closeW = 18;
        closeH = 18;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + closeW
                && mouseY >= closeY && mouseY <= closeY + closeH;
        AnimRenderOps.fill(gg, closeX, closeY, closeX + closeW, closeY + closeH,
                closeHover ? TerminalPalette.CLOSE_HOVER : TerminalPalette.CLOSE);
        RenderFontTool.drawString(gg, font, fcs("✕"), closeX + 5, closeY + 2, 0, 0, 1.2F,
                TerminalPalette.X);

        // ---- left column: chat (region 4+5) ----
        int lx0 = px(0.020), ly0 = py(0.122), lx1 = px(0.358), ly1 = py(0.873);
        drawPanel(gg, lx0, ly0, lx1, ly1);
        chatRegion.render(gg, lx0, ly0, lx1, ly1, nowMs, model);

        // ---- action bar (region 6) ----
        int ax0 = px(0.020), ay0 = py(0.883), ax1 = px(0.358), ay1 = py(0.968);
        drawPanel(gg, ax0, ay0, ax1, ay1);
        actionBar.render(gg, ax0, ay0, ax1, ay1, nowMs, model, mouseX, mouseY);

        // ---- right column: offer panel (region 7+8) ----
        int rx0 = px(0.370), ry0 = py(0.122), rx1 = px(0.998), ry1 = py(0.853);
        drawPanel(gg, rx0, ry0, rx1, ry1);
        // region 7 title strip
        int rty = ry0 + 22;
        AnimRenderOps.fill(gg, rx0, ry0, rx1, rty, TerminalPalette.TITLE);
        RenderFontTool.drawString(gg, font,
                Component.translatable("csgobox.terminal.offer.title").getVisualOrderText(),
                (rx0 + rx1) / 2F, ry0 + 5, 1, 0, 1.4F, TerminalPalette.TEXT);
        offerRegion.render(gg, rx0, rty, rx1, ry1, nowMs, model, player, mouseX, mouseY);

        // ---- bottom row (region 9+10+11) ----
        int bx0 = px(0.370), by0 = py(0.863), bx1 = px(0.998), by1 = py(0.968);
        drawPanel(gg, bx0, by0, bx1, by1);
        bottomRow.render(gg, bx0, by0, bx1, by1, nowMs, model, player);
    }

    /** FormattedCharSequence wrapper for plain strings. */
    private static FormattedCharSequence fcs(String s) {
        return FormattedCharSequence.forward(s, Style.EMPTY);
    }

    private void drawPanel(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1) {
        AnimRenderOps.fill(gg, x0 - 2, y0 - 2, x1 + 2, y1 + 2, TerminalPalette.FRAME);
        AnimRenderOps.fill(gg, x0, y0, x1, y1, 0xFF17191C);
    }

    // ---- close button rect (for hit-testing) ----
    private int closeX, closeY, closeW, closeH;
    // last-known pointer position (Screen has no mouse fields in 26.x)
    private int mouseX, mouseY;

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.mouseX = (int) event.x();
        this.mouseY = (int) event.y();
        long now = System.currentTimeMillis();
        if (mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH) {
            onClose();
            return true;
        }
        if (actionBar.mouseDown(mouseX, mouseY, now, model)) {
            return true;
        }
        if (offerRegion.mouseDown(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.mouseX = (int) event.x();
        this.mouseY = (int) event.y();
        actionBar.mouseUp(System.currentTimeMillis(), model);
        offerRegion.mouseUp();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        this.mouseX = (int) event.x();
        this.mouseY = (int) event.y();
        if (offerRegion.mouseDragged(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // GLFW_KEY_ESCAPE
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        actionBar.close();
        // 26.2 has no Options.hideGui; restore the HUD through the wrapper.
        HudVisibility.show();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
