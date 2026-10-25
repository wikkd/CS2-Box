package com.reclizer.csgobox.forge_1_18_2.event;

/**
 * 1.18.2 edition of the menu-blur boost: MC 1.18.2 has no menu background
 * blur at all (the {@code menuBackgroundBlurriness} option and the blur
 * shader arrived in 1.20.2), so there is nothing to boost — the class is
 * kept as a no-op so the client wiring ports unchanged.
 */
public final class ScreenBlurBoost {

    private ScreenBlurBoost() {
    }

    public static void register() {
        // no-op on 1.18.2
    }
}
