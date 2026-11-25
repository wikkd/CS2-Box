package com.reclizer.csgobox.forge_1_20_1.gui;

import com.reclizer.csgobox.utils.Easing;

/**
 * Pure layout math for {@link CsLookItemScreen}: every rect and easing
 * curve the screen draws or hit-tests, as static functions of the screen
 * size. No state, no Minecraft types beyond ints — the render path and the
 * mouse hit tests both call these, so what the user sees and what the
 * cursor lands on can never drift apart.
 */
public final class LookLayout {
    private LookLayout() {
    }

    /** Enter transition progress: 0 at screen open, 1 after 300ms (6 ticks). */
    public static float enterEase(int screenTicks) {
        return Easing.easeOutCubic(Math.min(1F, screenTicks / 6F));
    }

    // Back button (danger-red 关闭, bottom-right).

    public static int backButtonWidth(int screenW) {
        return Math.max(64, screenW * 7 / 100);
    }

    public static int backButtonX(int screenW) {
        return Math.max(8, screenW - backButtonWidth(screenW) - 16);
    }

    public static int backButtonY(int screenH) {
        return screenH - 8 - screenH * 5 / 100;
    }

    public static int backButtonHeight(int screenH) {
        return screenH * 5 / 100;
    }

    // Toolbar (bottom-left icon row).

    public static int toolbarButtonSize(int screenH) {
        return Math.max(17, screenH * 35 / 1000);
    }

    public static int toolbarButtonX(int index, int screenH) {
        return Math.max(8, 8 + index * (toolbarButtonSize(screenH) + 6));
    }

    public static int toolbarButtonY(int screenH) {
        return screenH - 8 - toolbarButtonSize(screenH);
    }

    /** Toolbar buttons slide up into place one after another on open. */
    public static float toolbarEnterEase(int screenTicks, int index) {
        float enterT = Math.max(0F, Math.min(1F, (screenTicks - index * 0.8F) / 3F));
        return 1F - (1F - enterT) * (1F - enterT);
    }

    public static int toolbarEnterRise(int screenTicks, int index) {
        return (int) (8F * (1F - toolbarEnterEase(screenTicks, index)));
    }

    // Preview area (the centred square the item renders into).

    public static int previewTextureSize(int screenW, int screenH) {
        int containerTop = screenH * 22 / 100;
        int containerBottom = screenH * 88 / 100;
        int containerHeight = containerBottom - containerTop;
        return Math.max(144, Math.min(screenW * 28 / 100, containerHeight * 72 / 100));
    }

    public static int previewPixelX(int screenW, int screenH) {
        return (screenW - previewTextureSize(screenW, screenH)) / 2;
    }

    public static int previewPixelY(int screenW, int screenH) {
        // Container = vertical band between the grade bar and the divider
        // above the button. Centre the crate within it so the result focus
        // sits in the visual middle of the screen.
        int containerTop = screenH * 22 / 100;
        int containerBottom = screenH * 88 / 100;
        return (containerTop + containerBottom - previewTextureSize(screenW, screenH)) / 2;
    }

    // Battle-inspect viewport (the Epic Fight figure replaces the item square;
    // the caller draws it across the full screen width).

    /** Height of the Epic Fight battle inspect's viewport: from the top of
     *  the screen down to just above the toolbar and the back button. The
     *  figure there is framed by its weapon's whole swing (see
     *  EpicFightInspect), so the height is what caps the figure's size, and
     *  the full width keeps a drag-rotated view from needing more distance.
     *  It stops short of the buttons because the figure's widget owns every
     *  click inside its rect. */
    public static int battleInspectHeight(int screenH) {
        int buttonsTop = Math.min(toolbarButtonY(screenH), backButtonY(screenH));
        return Math.max(1, buttonsTop - 2);
    }

    /** Hairline above the toolbar separating the preview from the buttons. */
    public static int dividerY(int screenH) {
        return screenH - 18 - toolbarButtonSize(screenH);
    }

    /** Thin grade-coloured bar under the title. */
    public static int gradeBarY(int screenH) {
        return screenH * 17 / 100;
    }
}
