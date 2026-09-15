package com.reclizer.csgobox.terminal;

/**
 * Wear surcharge for terminal purchases of items WITHOUT a durability bar:
 * wear has no durability to consume, so it becomes an Armory Point penalty —
 * the more worn the item, the larger the share of the base price it costs.
 * Durable items keep the base grade price and take wear damage instead (see
 * {@code PacketCsgoProgress#applyWearDamage}).
 */
public final class WearPenalty {

    /**
     * Markup at 100% wear, as a fraction of the base price: a Battle-Scarred
     * (full-wear) item costs 20% more, i.e. +0.2% per 1% of wear. Applied on
     * top of the per-item / grade price, so a pricier item pays more points
     * for the same wear than a cheap one.
     */
    public static final float MARKUP_AT_FULL_WEAR = 0.20F;

    /** Wear is sampled in [0,1]; anything above full wear clamps to 100%. */
    private static final int FULL_WEAR_PERCENT = 100;

    /**
     * Divisor of {@code ceil(basePrice × wearPercent / MARKUP_DIVISOR)}:
     * derived from the markup so the 0.2%-per-percent rate has one source of
     * truth (100% / 20% = 500).
     */
    private static final int MARKUP_DIVISOR = Math.round(FULL_WEAR_PERCENT / MARKUP_AT_FULL_WEAR);

    /**
     * Surcharge in whole Armory Points for a base price and a wear value
     * (0..1), rounded up: {@code ceil(basePrice × 0.2 × wear)}. An item at
     * full wear adds at most 20% of its base price (grade 1 = 6 → +2,
     * grade 5 = 30 → +6). Monotonic in both arguments; wear &le; 0 (or
     * non-finite wear) or a non-positive base price yields no surcharge.
     *
     * <p>Wear is quantised to whole percent (round to nearest) and the markup
     * is integer math, so exact boundaries (10 points at 50% wear = 1) cannot
     * drift by a floating-point ulp; wear below 0.5% therefore rounds to a
     * free purchase at the base price.
     */
    public static int surcharge(int basePrice, float wearVal) {
        if (basePrice <= 0 || !(wearVal > 0F)) {
            return 0;
        }
        int wearPercent = Math.min(FULL_WEAR_PERCENT, Math.round(wearVal * 100F));
        return (int) ((basePrice * (long) wearPercent + (MARKUP_DIVISOR - 1)) / MARKUP_DIVISOR);
    }

    private WearPenalty() {
    }
}
