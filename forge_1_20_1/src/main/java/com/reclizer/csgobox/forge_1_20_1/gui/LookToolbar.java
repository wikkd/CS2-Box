package com.reclizer.csgobox.forge_1_20_1.gui;

import com.reclizer.csgobox.forge_1_20_1.utils.AnimRenderOps;
import com.reclizer.csgobox.forge_1_20_1.utils.ButtonPalette;
import com.reclizer.csgobox.forge_1_20_1.utils.RenderFontTool;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The bottom-left toolbar of the look screen: icon buttons that slide up
 * into place on open, glow while hovered, and show a small caption above.
 * Two layouts — the shipment toolbar carries the wear (ⓘ) button, the
 * compact inspect toolbar replaces it with the sticker button (no dead
 * slot). Render-only state (hover index + glow) lives here; the click
 * handling stays on the screen, which re-derives the same rects from
 * {@link LookLayout}.
 */
public final class LookToolbar {
    /** Content bounding boxes (px in the 32x32 texture) per toolbar slot,
     *  kept in sync with the artwork: inspect 30x29, info 26x26,
     *  sticker 30x23, more 5x21. One row per layout — the compact layout
     *  holds different icons than shipment at the same slot (sticker is
     *  slot 1 compact but slot 2 shipment), so the layouts cannot share
     *  positional arrays. */
    private static final int[][] SHIPMENT_CONTENT = {{30, 29}, {26, 26}, {30, 23}, {5, 21}};
    private static final int[][] COMPACT_CONTENT = {{30, 29}, {30, 23}, {5, 21}};

    /** Tooltip lang keys per toolbar button (shipment toolbar, with wear). */
    private static final String[] TOOLTIP_KEYS = {
            "gui.csgobox.csgo_box.toolbar.inspect",
            "gui.csgobox.csgo_box.toolbar.info",
            "gui.csgobox.csgo_box.toolbar.sticker",
            "gui.csgobox.csgo_box.toolbar.more"
    };
    /** Tooltip keys for the compact inspect toolbar (no wear button: the
     *  right-click inspect flow confirms nothing about an item's wear). */
    private static final String[] TOOLTIP_KEYS_INSPECT = {
            "gui.csgobox.csgo_box.toolbar.inspect",
            "gui.csgobox.csgo_box.toolbar.sticker",
            "gui.csgobox.csgo_box.toolbar.more"
    };

    private static final ResourceLocation ICON_INSPECT =
            new ResourceLocation("csgobox:textures/gui/toolbar/inspect.png");
    private static final ResourceLocation ICON_INFO =
            new ResourceLocation("csgobox:textures/gui/toolbar/info.png");
    private static final ResourceLocation ICON_STICKER =
            new ResourceLocation("csgobox:textures/gui/toolbar/sticker.png");
    private static final ResourceLocation ICON_MORE =
            new ResourceLocation("csgobox:textures/gui/toolbar/more.png");

    private static final int OUTER_COLOR = 0xFF2B2B31;
    private static final int INNER_COLOR = 0xFF232328;
    private static final int INNER_ACTIVE_COLOR = 0xFF33333B;

    /** Toolbar hover state: index of hovered button (-1 = none) and 0..1 glow. */
    private int hoveredButton = -1;
    private float glow = 0F;

    /** Eases the hover glow toward its target (called once per client tick). */
    public void tickGlow() {
        float glowTarget = this.hoveredButton >= 0 ? 1F : 0F;
        this.glow += (glowTarget - this.glow) * 0.5F;
    }

    /** Draws the toolbar row and updates the hover state from the cursor.
     *  {@code specialInspect} lights the inspect button's green underline
     *  while a special inspect mode (mob animation / EF battle inspect /
     *  music) is active; {@code infoActive} lights the wear button while
     *  the info panel shows. */
    public void render(GuiGraphics guiGraphics, Font font, int screenW, int screenH,
                       int mouseX, int mouseY, int screenTicks,
                       boolean shipment, boolean specialInspect, boolean infoActive) {
        int size = LookLayout.toolbarButtonSize(screenH);
        int y = LookLayout.toolbarButtonY(screenH);
        // The wear (ⓘ) button ships only in shipment flows; the inspect
        // toolbar is compact [inspect, sticker, more] with no dead slot.
        ResourceLocation[] icons = shipment
                ? new ResourceLocation[]{ICON_INSPECT, ICON_INFO, ICON_STICKER, ICON_MORE}
                : new ResourceLocation[]{ICON_INSPECT, ICON_STICKER, ICON_MORE};
        String[] tooltips = shipment ? TOOLTIP_KEYS : TOOLTIP_KEYS_INSPECT;
        int[][] contents = shipment ? SHIPMENT_CONTENT : COMPACT_CONTENT;
        this.hoveredButton = -1;
        for (int i = 0; i < icons.length; i++) {
            int x = LookLayout.toolbarButtonX(i, screenH);
            int by = y + LookLayout.toolbarEnterRise(screenTicks, i);
            int alpha = (int) (0xFF * LookLayout.toolbarEnterEase(screenTicks, i));

            boolean hover = ButtonPalette.isInside(mouseX, mouseY, x, by, size, size);
            if (hover) {
                this.hoveredButton = i;
            }
            // Inspect (index 0) is the single inspect entry: always on as the
            // default mode (own 3D drag preview); its underline turns green
            // while a special inspect mode (mob animation / EF battle inspect)
            // is active.
            boolean active = i == 0 || (i == 1 && infoActive);
            int inner = active ? INNER_ACTIVE_COLOR : INNER_COLOR;
            guiGraphics.fill(x, by, x + size, by + size, (alpha << 24) | (OUTER_COLOR & 0xFFFFFF));
            guiGraphics.fill(x + 1, by + 1, x + size - 1, by + size - 1, (alpha << 24) | (inner & 0xFFFFFF));
            if (active) {
                guiGraphics.fill(x + 2, by + size - 2, x + size - 2, by + size,
                        (alpha << 24) | (i == 0 && specialInspect ? 0x54FF54 : 0xFFFFFF));
            }
            int iconSize = Math.max(12, Math.min(64, size * 3 / 4));
            int maxEdge = Math.max(contents[i][0], contents[i][1]);
            int screenIconW = iconSize * contents[i][0] / maxEdge;
            int screenIconH = iconSize * contents[i][1] / maxEdge;
            int iconX = x + (size - screenIconW) / 2;
            int iconY = by + (size - screenIconH) / 2;
            int u = (32 - contents[i][0]) / 2;
            int v = (32 - contents[i][1]) / 2;
            int iconColor;
            if (active) {
                iconColor = 0xFFFFFFFF;
            } else {
                float b = i == this.hoveredButton ? 0.55F + 0.45F * this.glow : 0.55F;
                iconColor = 0xFF000000 | ((int) (0xFF * b) << 16) | ((int) (0xFF * b) << 8) | (int) (0xFF * b);
            }
            iconColor = (alpha << 24) | (iconColor & 0xFFFFFF);
            AnimRenderOps.blitTextured(guiGraphics, icons[i],
                    iconX, iconY, screenIconW, screenIconH, u, v,
                    contents[i][0], contents[i][1], 32, 32, iconColor);
        }
        renderTooltip(guiGraphics, font, screenW, screenH, screenTicks, tooltips);
    }

    private void renderTooltip(GuiGraphics guiGraphics, Font font, int screenW, int screenH,
                               int screenTicks, String[] tooltips) {
        if (this.hoveredButton < 0 || this.glow < 0.05F) {
            return;
        }
        int size = LookLayout.toolbarButtonSize(screenH);
        int x = LookLayout.toolbarButtonX(this.hoveredButton, screenH);
        int y = LookLayout.toolbarButtonY(screenH)
                + LookLayout.toolbarEnterRise(screenTicks, this.hoveredButton);
        int textW = font.width(Component.translatable(tooltips[this.hoveredButton]).getVisualOrderText());
        int tooltipW = (int) (textW * 0.7F) + 8;
        int tooltipH = (int) (font.lineHeight * 0.7F) + 4;
        int tipX = x + (size - tooltipW) / 2;
        int tipY = y - tooltipH - 6;
        tipX = Math.max(2, Math.min(tipX, screenW - tooltipW - 2));
        tipY = Math.max(2, tipY);
        int alpha = (int) (0xCC * this.glow * LookLayout.toolbarEnterEase(screenTicks, this.hoveredButton));
        guiGraphics.fill(tipX, tipY, tipX + tooltipW, tipY + tooltipH, (alpha << 24) | 0x101014);
        int textAlpha = (int) (0xFF * this.glow * LookLayout.toolbarEnterEase(screenTicks, this.hoveredButton));
        RenderFontTool.drawString(guiGraphics, font,
                Component.translatable(tooltips[this.hoveredButton]).getVisualOrderText(),
                tipX + 4, tipY + 2, 0, 0, 0.7F, (textAlpha << 24) | 0xCCCCCC);
    }
}
