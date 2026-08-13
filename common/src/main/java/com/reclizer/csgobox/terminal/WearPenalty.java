package com.reclizer.csgobox.terminal;

/**
 * Wear surcharge for terminal purchases of items WITHOUT a durability bar:
 * wear has no durability to consume, so it becomes an Armory Point penalty —
 * the more worn the item, the more points it costs. Durable items keep the
 * base grade price and take wear damage instead (see {@code
 * PacketCsgoProgress#applyWearDamage}).
 */
public final class WearPenalty {

    /** Whole Armory Points per 5% of wear value, rounded up (0 at FN edge .. 20 at BS). */
    private static final float POINTS_PER_UNIT_WEAR = 20F;

    /**
     * Surcharge in whole Armory Points for a wear value (0..1). Monotonic:
     * every 5% of wear costs one extra point, so a Battle-Scarred item adds
     * up to 20 points over the base grade price.
     */
    public static int surcharge(float wearVal) {
        return (int) Math.ceil(Math.max(0F, wearVal) * POINTS_PER_UNIT_WEAR);
    }

    private WearPenalty() {
    }
}
