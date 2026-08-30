package com.reclizer.csgobox.forge_26_1_2.gui;

import com.reclizer.csgobox.forge_26_1_2.gui.terminal.TerminalChatRegion;
import com.reclizer.csgobox.forge_26_1_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The right-click context menu with a single "检视" (Inspect) action, shown
 * above the clicked item in the box / terminal preview pages.
 *
 * <p>While open, every left click either hits the "检视" button (returns
 * {@link #INSPECT}) or closes the menu (returns {@link #NONE}, consumed) —
 * so the surrounding screen's buttons are never triggered accidentally.</p>
 */
public final class InspectMenu {

    /** Left-click on the "检视" button. */
    public static final int INSPECT = 1;
    /** Menu not open, or the click just closed it. */
    public static final int NONE = 0;

    private boolean open;
    private int x;
    private int y;
    private int w;
    private int h;

    public void openAt(int mx, int my) {
        Font font = Minecraft.getInstance().font;
        String label = Component.translatable("gui.csgobox.inspect.open").getString();
        int textW = Math.round(font.width(label) * 0.7F) + 14;
        this.w = Math.max(42, textW);
        this.h = 13;
        // Show above-left of the click point; clamp to screen bounds.
        this.x = mx - this.w / 2;
        this.y = my - this.h - 8;
        if (this.x < 2) {
            this.x = 2;
        }
        if (this.x + this.w > Minecraft.getInstance().getWindow().getGuiScaledWidth() - 2) {
            this.x = Minecraft.getInstance().getWindow().getGuiScaledWidth() - this.w - 2;
        }
        if (this.y < 2) {
            this.y = 2;
        }
        this.open = true;
    }

    public void close() {
        this.open = false;
    }

    public boolean isOpen() {
        return this.open;
    }

    public void render(GuiGraphicsExtractor gg, int mx, int my) {
        if (!this.open) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        boolean hover = isInside(mx, my, this.x, this.y, this.w, this.h);
        // Small rounded black pill, white text.
        TerminalChatRegion.drawRounded(gg, this.x, this.y, this.w, this.h,
                hover ? 0xFF2A2A30 : 0xFF16161A, 0xFF000000);

        Component label = Component.translatable("gui.csgobox.inspect.open");
        FormattedCharSequence fcs = label.getVisualOrderText();
        float scale = 0.7F;
        float textX = this.x + (this.w - font.width(fcs) * scale) / 2.0F;
        float textY = this.y + (this.h - font.lineHeight * scale) / 2.0F + 1;
        RenderFontTool.drawString(gg, font, fcs, textX, textY, 0, 0, scale, 0xFFFFFFFF);
    }

    /**
     * Handles a click while the menu is open.
     *
     * @return {@link #INSPECT} when the "检视" button was clicked, else
     *         {@link #NONE} (menu closed / not open).
     */
    public int mouseClicked(int button, int mx, int my) {
        if (!this.open) {
            return NONE;
        }
        if (button == 0 && isInside(mx, my, this.x, this.y, this.w, this.h)) {
            this.open = false;
            return INSPECT;
        }
        // Any other click (left or right) just closes the menu.
        this.open = false;
        return NONE;
    }

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
