package com.reclizer.csgobox.villager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Config for the arms-dealer's dynamic pricing
 * ({@code config/csbox/_villager_prices.json}).
 *
 * <p>The villager's prices are anchored to the central price table
 * ({@code config/csbox/_prices.json}) instead of hard-coded constants:
 * mineral buy prices are {@code table base × buyRate} (iron additionally
 * capped at {@code key0Price / 3}, the 3-iron-craft-one-key0 parity, so
 * selling iron for points can never beat crafting a key0), key1/key2 sell prices
 * are their crafting-cost anchors ({@code 3 × mineral base × sellMarkup}),
 * key0 keeps the mod's currency anchor (9 points = 1 key0), and the terminal
 * sell price is derived from key0. Box sales are priced by
 * {@link VillagerBoxPool} from the contents' table-price expectation ×
 * {@code boxSellRatio}. Every price is re-sampled on
 * each villager trade refresh within {@code ±fluctuation}, so prices follow
 * the live economy and never drift out of line with the recycler / terminal.
 *
 * <p>When the price table has no entry for a mineral, the {@code fallbacks}
 * map is used, so a server without {@code _prices.json} (e.g. a fresh 26.x
 * world) still gets sane trades instead of zero-point offers.</p>
 *
 * <p>{@code pool_enabled} (default true) additionally draws the mineral-buy
 * offers from per-level random pools via {@link VillagerTradePool} — each
 * villager offers a random subset of its level's minerals while every sell
 * offer (key0 / key1 / terminal / key2) stays present;
 * {@code false} restores the pre-pool fixed lists. The box / terminal sale
 * pool ({@link VillagerBoxPool}) is independent of this knob — it is driven
 * by the live BoxRegistry and {@code box_sell_ratio} (0 disables box
 * sales).</p>
 *
 * <p>Pure value type — no Minecraft imports — so it compiles in the common
 * module and parses identically on every platform. Malformed fields are
 * reported through {@code issues} and fall back to defaults (same tolerant
 * style as {@code PriceTable.parse}).</p>
 */
public record VillagerPricingConfig(
        boolean enabled,
        double buyRate,
        double sellMarkup,
        double fluctuation,
        int key0Price,
        double key0Fluctuation,
        double boxSellRatio,
        double terminalKeyRatio,
        boolean poolEnabled,
        Map<String, Integer> fallbacks) {

    public static final String FILE_NAME = "_villager_prices.json";

    /** Default mineral base prices used when the price table lacks an entry. */
    public static final Map<String, Integer> DEFAULT_FALLBACKS = Map.of(
            "minecraft:iron_ingot", 50,
            "minecraft:gold_ingot", 50,
            "minecraft:diamond", 200,
            "minecraft:emerald", 55,
            "minecraft:coal", 15,
            "minecraft:copper_ingot", 25);

    /**
     * Defaults mirror the pre-dynamic economy so enabling the feature does
     * not shock existing servers: buyRate 0.06 reproduces the old
     * iron=3 / gold=3 / diamond=12 ballpark, key0 stays at the 9-point
     * currency anchor, terminal = 2 key0 (18 points). Box sales price at the
     * contents' table-price expectation ({@code box_sell_ratio} 1.0 = zero
     * expected margin). The random mineral-buy pool ({@link VillagerTradePool})
     * and the box/terminal sale pool ({@link VillagerBoxPool}) are on by
     * default; {@code pool_enabled=false} restores the pre-pool fixed mineral
     * buys (the box sale pool has no legacy equivalent).
     */
    public static final VillagerPricingConfig DEFAULT = new VillagerPricingConfig(
            true,
            0.06,
            1.0,
            0.10,
            9,
            0.05,
            1.0,
            2.0,
            true,
            DEFAULT_FALLBACKS);

    private static final Logger LOGGER = LoggerFactory.getLogger(VillagerPricingConfig.class);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public VillagerPricingConfig {
        fallbacks = fallbacks == null ? Map.of() : Map.copyOf(fallbacks);
    }

    /** Fallback base price for a mineral key, or 0 when not configured. */
    public int fallbackFor(String itemId) {
        Integer v = fallbacks.get(itemId);
        return v == null ? 0 : v;
    }

    /**
     * Parses a config object (tolerant): unknown fields are ignored, invalid
     * known fields are reported through {@code issues} and replaced with the
     * default value. A null root reports one issue and returns
     * {@link #DEFAULT}.
     */
    public static VillagerPricingConfig parse(JsonObject json, List<String> issues) {
        if (json == null) {
            issues.add(FILE_NAME + " root must be a JSON object");
            return DEFAULT;
        }
        boolean enabled = bool(json, "enabled", DEFAULT.enabled(), issues);
        double buyRate = ratio(json, "buy_rate", DEFAULT.buyRate(), issues);
        double sellMarkup = ratio(json, "sell_markup", DEFAULT.sellMarkup(), issues);
        double fluctuation = ratio(json, "fluctuation", DEFAULT.fluctuation(), issues);
        int key0Price = nonNegativeInt(json, "key0_price", DEFAULT.key0Price(), issues);
        double key0Fluctuation = ratio(json, "key0_fluctuation", DEFAULT.key0Fluctuation(), issues);
        double boxSellRatio = ratio(json, "box_sell_ratio", DEFAULT.boxSellRatio(), issues);
        double terminalKeyRatio = ratio(json, "terminal_key_ratio", DEFAULT.terminalKeyRatio(), issues);
        boolean poolEnabled = bool(json, "pool_enabled", DEFAULT.poolEnabled(), issues);

        Map<String, Integer> fallbacks = new LinkedHashMap<>(DEFAULT_FALLBACKS);
        if (json.has("fallbacks") && json.get("fallbacks").isJsonObject()) {
            JsonObject fb = json.getAsJsonObject("fallbacks");
            for (Map.Entry<String, com.google.gson.JsonElement> e : fb.entrySet()) {
                String key = e.getKey();
                if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) {
                    issues.add(FILE_NAME + " fallback '" + key + "' must be a non-negative integer");
                    continue;
                }
                int v = e.getValue().getAsInt();
                if (v < 0) {
                    issues.add(FILE_NAME + " fallback '" + key + "' must be non-negative");
                    continue;
                }
                fallbacks.put(key, v);
            }
        }
        return new VillagerPricingConfig(enabled, buyRate, sellMarkup, fluctuation,
                key0Price, key0Fluctuation, boxSellRatio, terminalKeyRatio, poolEnabled, fallbacks);
    }

    /**
     * Loads the config from {@code dir/_villager_prices.json}. A missing or
     * unreadable file degrades to {@link #DEFAULT} (the caller decides
     * whether to write a default file); parse issues are logged.
     */
    public static VillagerPricingConfig load(Path dir) {
        Path file = dir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return DEFAULT;
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject json = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
            List<String> issues = new ArrayList<>();
            VillagerPricingConfig cfg = parse(json, issues);
            for (String issue : issues) {
                LOGGER.warn("[csgo-villager] {} — using default for that field", issue);
            }
            return cfg;
        } catch (IOException | com.google.gson.JsonParseException e) {
            LOGGER.warn("[csgo-villager] cannot read {}: {} — using defaults",
                    file, e.getMessage());
            return DEFAULT;
        }
    }

    /** Serialises the config to the on-disk JSON shape (for default writing). */
    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("_doc", "军火商动态定价配置 / Arms-dealer dynamic pricing. " +
                "Prices are anchored to config/csbox/_prices.json: buy = table base × buy_rate " +
                "(iron additionally capped at floor(key0_price / 3) — 3 iron craft one key0, " +
                "so selling iron for points can never beat direct crafting), " +
                "key1/key2 = crafting cost (3 × mineral base) × sell_markup, paid as ≤ 64 Armory " +
                "Points plus gold-ingot / diamond change for the remainder (a single trade input " +
                "holds one 64-point stack), key0 = key0_price (9-point currency anchor), " +
                "terminal = key0 × terminal_key_ratio. " +
                "Box sales price at the contents' table-price expectation (grade odds × item " +
                "weights × mean count × mean table price) × box_sell_ratio (1.0 = zero expected " +
                "margin, so buying a box can never arbitrage the villager); prices beyond one " +
                "points stack are paid as ≤ 64 points + gold-ingot change. Boxes whose contents " +
                "can't be priced faithfully (loot_table entries, random-enchant specs) stay out " +
                "of the sale pool. " +
                "Each refresh re-samples within ±fluctuation. fallbacks are used when the price " +
                "table lacks a mineral entry. pool_enabled draws the mineral-buy offers from " +
                "per-level pools (each villager picks a random subset; sell offers for keys / " +
                "terminal are always present); false restores the pre-pool fixed list " +
                "(iron+emerald / gold / diamond / — / —). Requires a restart to reload.");
        root.addProperty("enabled", enabled);
        root.addProperty("buy_rate", buyRate);
        root.addProperty("sell_markup", sellMarkup);
        root.addProperty("fluctuation", fluctuation);
        root.addProperty("key0_price", key0Price);
        root.addProperty("key0_fluctuation", key0Fluctuation);
        root.addProperty("box_sell_ratio", boxSellRatio);
        root.addProperty("terminal_key_ratio", terminalKeyRatio);
        root.addProperty("pool_enabled", poolEnabled);
        JsonObject fb = new JsonObject();
        fallbacks.forEach(fb::addProperty);
        root.add("fallbacks", fb);
        return GSON.toJson(root);
    }

    private static boolean bool(JsonObject json, String field, boolean dflt, List<String> issues) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive()) {
            return dflt;
        }
        try {
            return json.get(field).getAsBoolean();
        } catch (RuntimeException e) {
            issues.add(FILE_NAME + " '" + field + "' must be true/false");
            return dflt;
        }
    }

    private static double ratio(JsonObject json, String field, double dflt, List<String> issues) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive()) {
            return dflt;
        }
        try {
            double v = json.get(field).getAsDouble();
            if (v < 0 || v > 5) {
                issues.add(FILE_NAME + " '" + field + "' must be in [0, 5], got " + v);
                return dflt;
            }
            return v;
        } catch (RuntimeException e) {
            issues.add(FILE_NAME + " '" + field + "' must be a number");
            return dflt;
        }
    }

    private static int nonNegativeInt(JsonObject json, String field, int dflt, List<String> issues) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive()) {
            return dflt;
        }
        try {
            int v = json.get(field).getAsInt();
            if (v < 0) {
                issues.add(FILE_NAME + " '" + field + "' must be non-negative");
                return dflt;
            }
            return v;
        } catch (RuntimeException e) {
            issues.add(FILE_NAME + " '" + field + "' must be an integer");
            return dflt;
        }
    }
}
