package com.reclizer.csgobox.forge_26_2.gui;

import com.reclizer.csgobox.utils.OverlayColor;

/** Full-screen backdrop fill for CS2-Box screens: translucent theme gray
 *  (fixed, not user-configurable) so the blurred world (vanilla blur or the
 *  Blur mod) shows through behind the screen. */
public final class UiBackdrop {
    private UiBackdrop() {
    }

    public static int fill() {
        return OverlayColor.getBackgroundTranslucent();
    }
}
