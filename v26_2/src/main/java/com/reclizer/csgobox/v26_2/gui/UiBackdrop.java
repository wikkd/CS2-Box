package com.reclizer.csgobox.v26_2.gui;

import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.v26_2.config.CsboxConfig;

/** Full-screen backdrop fill for CS2-Box screens: opaque or translucent
 *  theme gray, driven by the backgroundStyle config. Translucent lets the
 *  blurred world (vanilla blur or the Blur mod) show through behind the
 *  screen; opaque keeps the solid dark panels look. */
public final class UiBackdrop {
    private UiBackdrop() {
    }

    public static int fill() {
        return CsgoBox.CONFIG.backgroundStyle() == CsboxConfig.BackgroundStyle.TRANSLUCENT
                ? OverlayColor.getBackgroundTranslucent()
                : OverlayColor.getBackgroundColor();
    }
}
