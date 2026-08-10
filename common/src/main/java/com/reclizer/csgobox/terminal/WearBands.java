package com.reclizer.csgobox.terminal;

/**
 * CS2 wear tiers — the five-band wear bar (HTML region 8 / F) as pure data.
 * Band boundaries (fractions of wear value): 7% / 15% / 38% / 45% / 100%.
 * Tier names come from the shared lang file via {@link #NAME_KEYS}.
 */
public final class WearBands {

    /** Band boundaries: FN [0,0.07) MW [0.07,0.15) FT [0.15,0.38) WW [0.38,0.45) BS [0.45,1]. */
    public static final float[] BAND_STOPS = {0.07F, 0.15F, 0.38F, 0.45F, 1.0F};

    /** Five band colours (HTML wear-bar gradient stops). */
    public static final int[] BAND_COLORS = {
            0xFFE8ECEF, 0xFFC3CCD3, 0xFF9FB2BF, 0xFF7E97A6, 0xFF64798A
    };

    /** Corner-tab abbreviations. */
    public static final String[] ABBR = {"FN", "MW", "FT", "WW", "BS"};

    /** Lang keys for tier display names (already present in lang files). */
    public static final String[] NAME_KEYS = {
            "gui.csgobox.csgo_box.wear_fn",
            "gui.csgobox.csgo_box.wear_mw",
            "gui.csgobox.csgo_box.wear_ft",
            "gui.csgobox.csgo_box.wear_ww",
            "gui.csgobox.csgo_box.wear_bs",
    };

    public static final int COUNT = BAND_STOPS.length;

    private WearBands() {
    }

    /** Tier index (0..4) containing a wear value; edges clamp to FN/BS. */
    public static int tierIndex(float wearVal) {
        if (wearVal <= 0F) {
            return 0;
        }
        for (int i = 0; i < BAND_STOPS.length; i++) {
            if (wearVal < BAND_STOPS[i]) {
                return i;
            }
        }
        return BAND_STOPS.length - 1;
    }

    /** Band colour for a tier index. */
    public static int tierColor(int idx) {
        return BAND_COLORS[Math.max(0, Math.min(idx, COUNT - 1))];
    }

    /** Band abbreviation for a tier index ("FN".."BS"). */
    public static String tierAbbr(int idx) {
        return ABBR[Math.max(0, Math.min(idx, COUNT - 1))];
    }

    /** Lang key of the tier display name. */
    public static String tierNameKey(int idx) {
        return NAME_KEYS[Math.max(0, Math.min(idx, COUNT - 1))];
    }

    /** Lower bound (inclusive) of a tier as a fraction of the wear bar. */
    public static float tierLo(int idx) {
        return idx <= 0 ? 0F : BAND_STOPS[idx - 1];
    }

    /** Upper bound (exclusive) of a tier as a fraction of the wear bar. */
    public static float tierHi(int idx) {
        return BAND_STOPS[Math.max(0, Math.min(idx, COUNT - 1))];
    }
}
