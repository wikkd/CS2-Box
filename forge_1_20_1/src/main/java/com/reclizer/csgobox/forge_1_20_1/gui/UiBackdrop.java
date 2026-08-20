package com.reclizer.csgobox.forge_1_20_1.gui;

import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.config.CsboxConfig;

public final class UiBackdrop {
    private UiBackdrop() {
    }

    public static int fill() {
        return CsgoBox.CONFIG.backgroundStyle() == CsboxConfig.BackgroundStyle.TRANSLUCENT
                ? OverlayColor.getBackgroundTranslucent()
                : OverlayColor.getBackgroundColor();
    }
}
