package com.reclizer.csgobox.forge_26_1_2.gui.terminal;

import com.reclizer.csgobox.terminal.TerminalPalette;
import com.reclizer.csgobox.terminal.WearBands;
import com.reclizer.csgobox.forge_26_1_2.utils.AnimRenderOps;
import com.reclizer.csgobox.forge_26_1_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Modal trade-confirmation dialog layered over {@code TerminalScreen} when
 * the player presses the accept pill: shows the offered item, its wear tier
 * + value and the Armory Point price, with green confirm / gray cancel
 * buttons. While a buy request is in flight it switches to a waiting state
 * that consumes all input.
 *
 * era: decoupled
 */
public final class TerminalConfirmDialog {

    /** Button hit result from a click. */
    public enum Hit { NONE, BLOCK, CONFIRM, CANCEL }

    private enum State { CLOSED, OPEN, WAITING }

    private State state = State.CLOSED;
    private ItemStack itemStack = ItemStack.EMPTY;
    private int price;
    private int basePrice;
    private float wearVal;

    // button rects (updated each render)
    private int confirmX, confirmY, confirmW, confirmH;
    private int cancelX, cancelY, cancelW, cancelH;

    public boolean isOpen() {
        return state != State.CLOSED;
    }

    public boolean isWaiting() {
        return state == State.WAITING;
    }

    public void open(ItemStack stack, int price, int basePrice, float wearVal) {
        this.itemStack = stack.copy();
        this.price = price;
        this.basePrice = basePrice;
        this.wearVal = wearVal;
        this.state = State.OPEN;
    }

    public void close() {
        this.state = State.CLOSED;
        this.itemStack = ItemStack.EMPTY;
    }

    public void setWaiting() {
        if (state == State.OPEN) {
            state = State.WAITING;
        }
    }

    public void render(GuiGraphicsExtractor gg, int screenW, int screenH, Player player) {
        if (state == State.CLOSED) {
            return;
        }
        // scrim dims the terminal underneath
        AnimRenderOps.fill(gg, 0, 0, screenW, screenH, 0x99000000);

        int w = Math.min(Math.round(screenW * 0.40F), 210);
        int h = 60;
        int x = (screenW - w) / 2;
        int y = Math.max(16, (screenH - h) / 2);
        TerminalChatRegion.drawRounded(gg, x, y, w, h, 0xFF1B2026, TerminalPalette.FRAME);

        Font font = Minecraft.getInstance().font;
        // title
        String title = Component.translatable("csgobox.terminal.confirm.title").getString();
        int titleW = RenderFontTool.drawSpacedText(gg, font, title,
                x + w / 2F, y + 3, 0.62F, 0.55F, TerminalPalette.WHITE);
        AnimRenderOps.fill(gg, x + (w - titleW) / 2, y + 9, x + (w + titleW) / 2, y + 10,
                TerminalPalette.FRAME);

        // item icon + name + wear
        int iconX = x + 9;
        int iconY = y + 15;
        // 26.x renderItem2D takes the ICON CENTRE: pass the 16px icon-box
        // centre (8 * scale = 8.8) directly instead of the 1.21.1 top-left
        // anchor (which added the +8*scale internally).
        AnimRenderOps.renderItem2D(player, gg, itemStack, iconX + 8.8F, iconY + 8.8F, 1.1F);
        String name = itemStack.getHoverName().getString();
        RenderFontTool.drawStringClamped(gg, font, name, x + 26, y + 13,
                0, 0, 0.52F, w - 36, TerminalPalette.TEXT);
        int tier = WearBands.tierIndex(wearVal);
        String wear = Component.translatable("csgobox.terminal.confirm.wear",
                Component.translatable(WearBands.tierNameKey(tier)),
                String.format(Locale.ROOT, "%.8f", wearVal)).getString();
        RenderFontTool.drawStringClamped(gg, font, wear, x + 26, y + 21,
                0, 0, 0.45F, w - 36, TerminalPalette.META_TEXT);

        // price
        int penalty = Math.max(0, price - basePrice);
        String priceText = penalty > 0
                ? Component.translatable("csgobox.terminal.confirm.price.penalty", price, penalty).getString()
                : Component.translatable("csgobox.terminal.confirm.price", price).getString();
        RenderFontTool.drawStringClamped(gg, font, priceText, x + 9, y + 32,
                0, 0, 0.5F, w - 18, TerminalPalette.WHITE);

        // buttons
        int btnW = Math.min(56, (w - 20) / 2);
        int btnH = 9;
        int btnY = y + h - 13;
        int gap = 6;
        int startX = x + (w - 2 * btnW - gap) / 2;
        confirmX = startX;
        confirmY = btnY;
        confirmW = btnW;
        confirmH = btnH;
        cancelX = startX + btnW + gap;
        cancelY = btnY;
        cancelW = btnW;
        cancelH = btnH;

        if (state == State.WAITING) {
            String waiting = Component.translatable("csgobox.terminal.confirm.waiting").getString();
            int waitW = Math.round(font.width(waiting) * 0.5F) + Math.round(0.4F * (waiting.length() - 1));
            RenderFontTool.drawSpacedText(gg, font, waiting, x + w / 2F - waitW / 2F, btnY + 1,
                    0.5F, 0.4F, 0x88FFFFFF);
            return;
        }

        drawButton(gg, confirmX, confirmY, confirmW, confirmH,
                Component.translatable("csgobox.terminal.confirm.accept").getString(),
                TerminalPalette.PILL_GREEN_BORDER, TerminalPalette.PILL_GREEN_TEXT,
                TerminalPalette.HOLD_ACCEPT);
        drawButton(gg, cancelX, cancelY, cancelW, cancelH,
                Component.translatable("csgobox.terminal.confirm.cancel").getString(),
                TerminalPalette.PILL_GRAY_BORDER, TerminalPalette.PILL_GRAY_TEXT,
                TerminalPalette.HOLD_REJECT);
    }

    /** Outline pill with a solid fill once pressed (single click fires). */
    private void drawButton(GuiGraphicsExtractor gg, int x, int y, int w, int h,
                            String label, int border, int text, int fillColor) {
        TerminalChatRegion.drawPill(gg, x, y, w, h, 0xFF1B2026, border);
        Font font = Minecraft.getInstance().font;
        int labelW = Math.round(font.width(label) * 0.55F) + Math.round(0.55F * (label.length() - 1));
        RenderFontTool.drawSpacedText(gg, font, label,
                x + (w - labelW) / 2F, y + (h - 3) / 2F - 1, 0.55F, 0.55F, text);
    }

    /**
     * Click handling while open: fires immediately on the button press and
     * consumes every click (including on the scrim) so the terminal regions
     * stay inert underneath. WAITING consumes without firing.
     */
    public Hit mouseDown(int mx, int my, long nowMs) {
        if (state == State.CLOSED) {
            return Hit.NONE;
        }
        if (state == State.OPEN) {
            if (mx >= confirmX && mx <= confirmX + confirmW
                    && my >= confirmY && my <= confirmY + confirmH) {
                return Hit.CONFIRM;
            }
            if (mx >= cancelX && mx <= cancelX + cancelW
                    && my >= cancelY && my <= cancelY + cancelH) {
                return Hit.CANCEL;
            }
        }
        return Hit.BLOCK;
    }
}
