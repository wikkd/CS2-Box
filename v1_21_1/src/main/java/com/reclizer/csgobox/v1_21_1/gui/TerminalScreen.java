package com.reclizer.csgobox.v1_21_1.gui;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.terminal.TerminalPalette;
import com.reclizer.csgobox.v1_21_1.gui.terminal.TerminalActionBar;
import com.reclizer.csgobox.v1_21_1.gui.terminal.TerminalBottomRow;
import com.reclizer.csgobox.v1_21_1.gui.terminal.TerminalChatRegion;
import com.reclizer.csgobox.v1_21_1.gui.terminal.TerminalOfferRegion;
import com.reclizer.csgobox.v1_21_1.utils.AnimRenderOps;
import com.reclizer.csgobox.v1_21_1.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
 * era: legacy
 */
public class TerminalScreen extends Screen {

    private final NegotiationModel model = new NegotiationModel();
    private final TerminalChatRegion chatRegion = new TerminalChatRegion();
    private final TerminalActionBar actionBar = new TerminalActionBar();
    private final TerminalOfferRegion offerRegion = new TerminalOfferRegion();
    private final TerminalBottomRow bottomRow = new TerminalBottomRow();
    private long nowMs;

    public TerminalScreen() {
        super(Component.translatable("gui.csgobox.terminal.title"));
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
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        super.render(gg, mouseX, mouseY, partialTick);
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
        Component title = Component.translatable("gui.csgobox.terminal.title");
        int titleW = Math.round(font.width(title.getString()) * 1.5F) + 2 * (title.getString().length() - 1);
        RenderFontTool.drawSpacedText(gg, font, title.getString(),
                (tx0 + tx1) / 2F - titleW / 2F, ty0 + 9, 2F, 1.5F, TerminalPalette.TITLE);
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
        Component offerTitle = Component.translatable("csgobox.terminal.offer.title");
        int offerTitleW = Math.round(font.width(offerTitle.getString()) * 1.5F)
                + 2 * (offerTitle.getString().length() - 1);
        RenderFontTool.drawSpacedText(gg, font, offerTitle.getString(),
                (rx0 + rx1) / 2F - offerTitleW / 2F, ry0 + 5, 2F, 1.5F, TerminalPalette.TEXT);
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

    private void drawPanel(GuiGraphics gg, int x0, int y0, int x1, int y1) {
        AnimRenderOps.fill(gg, x0 - 2, y0 - 2, x1 + 2, y1 + 2, TerminalPalette.FRAME);
        AnimRenderOps.fill(gg, x0, y0, x1, y1, 0xFF17191C);
    }

    // ---- close button rect (for hit-testing) ----
    private int closeX, closeY, closeW, closeH;
    // last-known pointer position (Screen exposes none in this version)
    private int mouseX, mouseY;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.mouseX = (int) mouseX;
        this.mouseY = (int) mouseY;
        long now = System.currentTimeMillis();
        if (this.mouseX >= closeX && this.mouseX <= closeX + closeW && this.mouseY >= closeY && this.mouseY <= closeY + closeH) {
            onClose();
            return true;
        }
        if (actionBar.mouseDown(this.mouseX, this.mouseY, now, model)) {
            return true;
        }
        if (offerRegion.mouseDown(this.mouseX, this.mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.mouseX = (int) mouseX;
        this.mouseY = (int) mouseY;
        actionBar.mouseUp(System.currentTimeMillis(), model);
        offerRegion.mouseUp();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        this.mouseX = (int) mouseX;
        this.mouseY = (int) mouseY;
        if (offerRegion.mouseDragged(this.mouseX, this.mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        actionBar.close();
        if (this.minecraft != null) {
            this.minecraft.options.hideGui = false;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
