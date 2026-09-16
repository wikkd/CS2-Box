package com.reclizer.csgobox.villager;

import com.reclizer.csgobox.box.PriceRange;
import com.reclizer.csgobox.box.PriceTable;

import java.util.function.IntUnaryOperator;

/**
 * Price-table-anchored dynamic pricing for the arms-dealer villager.
 *
 * <p>Pure calculation model (no Minecraft imports): given the active
 * {@link PriceTable}, the {@link VillagerPricingConfig} and a random source
 * ({@code nextBounded}, the same shape {@link PriceRange#sample} takes), it
 * produces one fresh {@link Quote} per villager trade refresh.</p>
 *
 * <p>Anchoring rules (all numbers re-sampled per refresh):</p>
 *
 * <ul>
 *   <li><b>Buy (minerals → points)</b>: {@code tableBase × buyRate × factor}.
 *       Table base is a sampled range when the table stores {@code [min,max]},
 *       otherwise the fixed price; falls back to the config's {@code fallbacks}
 *       when the table has no entry. With the default price table this keeps
 *       3 iron below one key0, so "mine → points → key0" cannot arbitrage.</li>
 *   <li><b>key0</b>: the config's {@code key0Price} (the mod's 9-point
 *       currency anchor), with a small separate fluctuation.</li>
 *   <li><b>key1 / key2</b>: crafting-cost anchors {@code 3 × mineral base ×
 *       sellMarkup}. key2 is split into points + diamonds because a single
 *       Armory Point stack caps at 64: points are clamped to 64 and the
 *       remaining value is paid in whole diamonds.</li>
 *   <li><b>box / terminal</b>: derived from key0 ({@code key0 × boxKeyRatio}
 *       and {@code key0 × terminalKeyRatio}).</li>
 * </ul>
 *
 * <p>When {@code enabled} is false the model returns {@code null} and the
 * platform keeps its static trade tables.</p>
 */
public final class VillagerPricing {

    /** Maximum Armory Points payable in one {@code MerchantOffer} input. */
    public static final int POINT_STACK_LIMIT = 64;

    /** A complete fresh price quote for every arms-dealer trade. */
    public record Quote(
            int iron,       // 1 iron_ingot → N points
            int emerald,    // 1 emerald → N points
            int gold,       // 1 gold_ingot → N points
            int diamond,    // 1 diamond → N points
            int key0,       // N points → 1 csgo_key0
            int key1,       // N points → 1 csgo_key1
            int key2Points, // N points (≤ 64) → 1 csgo_key2
            int key2Diamonds, // + M diamonds (0 when the points already cover it)
            int box,        // N points → 1 csgo_box
            int terminal) { // N points → 1 terminal
    }

    private static final int UNIT = 1_000_000;

    private VillagerPricing() {
    }

    /**
     * Computes one fresh quote, or {@code null} when dynamic pricing is
     * disabled in the config.
     */
    public static Quote quote(PriceTable table, VillagerPricingConfig cfg,
                              IntUnaryOperator nextBounded) {
        if (cfg == null || !cfg.enabled()) {
            return null;
        }
        PriceTable t = table != null ? table : PriceTable.EMPTY;

        double factor = factor(cfg.fluctuation(), nextBounded);
        double key0Factor = factor(cfg.key0Fluctuation(), nextBounded);

        int ironBase = base(t, cfg, "minecraft:iron_ingot", nextBounded);
        int goldBase = base(t, cfg, "minecraft:gold_ingot", nextBounded);
        int diamondBase = base(t, cfg, "minecraft:diamond", nextBounded);
        int emeraldBase = emeraldBase(t, cfg, nextBounded);

        int iron = point(ironBase * cfg.buyRate() * factor);
        int emerald = point(emeraldBase * cfg.buyRate() * factor);
        int gold = point(goldBase * cfg.buyRate() * factor);
        int diamond = point(diamondBase * cfg.buyRate() * factor);

        int key0 = point(cfg.key0Price() * key0Factor);

        int key1 = point(goldBase * 3.0 * cfg.sellMarkup() * factor);

        double key2Value = diamondBase * 3.0 * cfg.sellMarkup() * factor;
        int key2Points = (int) Math.min(POINT_STACK_LIMIT, Math.max(1, Math.round(key2Value)));
        int key2Diamonds = diamondBase > 0
                ? (int) Math.ceil(Math.max(0.0, key2Value - key2Points) / diamondBase)
                : 0;

        int box = point(key0 * cfg.boxKeyRatio() * factor);
        int terminal = point(key0 * cfg.terminalKeyRatio() * factor);

        return new Quote(iron, emerald, gold, diamond,
                key0, key1, key2Points, key2Diamonds, box, terminal);
    }

    /** Base price for a mineral: sampled table range, else fallback, else 0. */
    private static int base(PriceTable table, VillagerPricingConfig cfg,
                            String itemId, IntUnaryOperator nextBounded) {
        PriceRange range = table.lookupRange(itemId);
        if (range != null) {
            return range.sample(nextBounded);
        }
        return cfg.fallbackFor(itemId);
    }

    /**
     * Emerald base: a single emerald has no own table entry in the bundled
     * tables (only {@code emerald_block} does), so the base is
     * {@code emerald_block price / 9} with a config fallback when missing.
     */
    private static int emeraldBase(PriceTable table, VillagerPricingConfig cfg,
                                   IntUnaryOperator nextBounded) {
        PriceRange block = table.lookupRange("minecraft:emerald_block");
        if (block != null) {
            int base = block.sample(nextBounded);
            return Math.max(1, base / 9);
        }
        return cfg.fallbackFor("minecraft:emerald");
    }

    /** Random factor in {@code [1 - f, 1 + f]} from a [0,1) unit draw. */
    private static double factor(double fluctuation, IntUnaryOperator nextBounded) {
        if (fluctuation <= 0) {
            return 1.0;
        }
        double u = nextBounded.applyAsInt(UNIT) / (double) UNIT;
        return 1.0 + fluctuation * (2.0 * u - 1.0);
    }

    /** Whole points, at least 1. */
    private static int point(double value) {
        return (int) Math.max(1, Math.round(value));
    }
}
