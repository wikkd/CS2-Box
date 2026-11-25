package com.reclizer.csgobox.forge_1_20_1.gui;

import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The shipment wear card: the drop's own generated metadata (float wear,
 * pattern seed, skin id, finish style). Rolled once when the screen opens;
 * the info (ⓘ) panel renders it. Only shipment flows show the card — the
 * right-click inspect flow confirms nothing about an item's wear, so it
 * rolls the same way but stays hidden there.
 */
public final class LookWearCard {
    /** Decorative finish styles, mirrored in lang (style.custom_paint etc). */
    public static final String[] SKIN_STYLES = {
            "custom_paint", "gunsmith", "patina", "hydrographic", "spray_paint", "anodized"
    };

    private static final String[] TIER_KEYS = {
            "gui.csgobox.csgo_box.wear_fn", "gui.csgobox.csgo_box.wear_mw",
            "gui.csgobox.csgo_box.wear_ft", "gui.csgobox.csgo_box.wear_ww",
            "gui.csgobox.csgo_box.wear_bs"
    };
    private static final float[] TIER_CEILINGS = {0.07F, 0.15F, 0.38F, 0.45F};

    private final float wearValue;
    private final int patternSeed;
    private final int skinId;
    private final int skinStyleIndex;

    public LookWearCard(ItemStack item, ThreadLocalRandom rnd) {
        if (!item.isEmpty() && item.isDamageableItem() && item.getDamageValue() > 0) {
            int maxDamage = item.getMaxDamage();
            this.wearValue = maxDamage > 0 ? (float) item.getDamageValue() / maxDamage : rnd.nextFloat();
        } else {
            this.wearValue = rnd.nextFloat();
        }
        this.patternSeed = rnd.nextInt(1000);
        this.skinId = rnd.nextInt(100, 1301);
        this.skinStyleIndex = rnd.nextInt(SKIN_STYLES.length);
    }

    public float wearValue() {
        return this.wearValue;
    }

    public int patternSeed() {
        return this.patternSeed;
    }

    public int skinId() {
        return this.skinId;
    }

    public String skinStyleKey() {
        return "gui.csgobox.csgo_box.style." + SKIN_STYLES[this.skinStyleIndex];
    }

    public String wearTierKey() {
        for (int i = 0; i < TIER_CEILINGS.length; i++) {
            if (this.wearValue < TIER_CEILINGS[i]) {
                return TIER_KEYS[i];
            }
        }
        return TIER_KEYS[TIER_KEYS.length - 1];
    }

    public String formatWear() {
        return String.format(Locale.ROOT, "%.9f", this.wearValue);
    }
}
