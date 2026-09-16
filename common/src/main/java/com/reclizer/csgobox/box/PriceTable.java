package com.reclizer.csgobox.box;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central terminal price table ({@code config/csbox/_prices.json}).
 *
 * <p>v2.0.1+: per-item terminal prices are no longer written inside box
 * JSONs — every grade entry's price is resolved from this single table so
 * all terminals share one economy. Keys are item registry ids
 * ({@code "minecraft:diamond_sword"}) or {@code id#variant} sub-keys for
 * items that differ by NBT variant (TACZ guns/ammo: {@code
 * "tacz:modern_kinetic_gun#tacz:deagle_golden"}). There is NO grade-default
 * fallback anymore: a missing entry ({@link PriceRange#UNPRICED} / null)
 * means the item has no price — the terminal never offers it and the
 * recycler pays 0, and the loaders reject box files whose id entries are
 * unpriced (loot_table entries are the only allowed exception, they cannot
 * be keyed and are simply never offered).</p>
 *
 * <p>Values are either a fixed non-negative int ({@code 1500}) or an
 * inclusive random range ({@code [1500, 3000]}, written as a two-element
 * array — same style as the {@code count} interval). When a range is priced,
 * the terminal samples a fresh price per offer and the recycler samples per
 * recycle; fixed values are just ranges with {@code min == max}.</p>
 *
 * <p>Pure {@link JsonElement} functions — no Minecraft or platform imports —
 * so the same source compiles in the common module and is reused by every
 * platform loader. The underscore prefix keeps the file out of the box scan
 * ({@code forEachBoxJson} skips {@code _}*).</p>
 */
public final class PriceTable {

    /** File name inside {@code config/csbox/}; underscore = never a box. */
    public static final String FILE_NAME = "_prices.json";

    /** Sentinel returned by {@link #lookup} when no entry exists: the item has
     *  no price at all — the terminal never offers it and the recycler pays
     *  0 (no grade-default fallback). */
    public static final int UNPRICED = -1;

    /** Key shape: {@code ns:path} with an optional {@code #variant} suffix. */
    private static final Pattern KEY_RE = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$");

    /** NBT variant field in a legacy {@code "tag"} string: TACZ guns carry
     *  {@code GunId}, ammo carries {@code AmmoId}. Group 1 = the variant id. */
    private static final Pattern VARIANT_FIELD_RE = Pattern.compile(
            "\\b(?:GunId|AmmoId)\\b\\s*[:=]\\s*\"?([A-Za-z0-9_.:+-]+)\"?");

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /** Empty table: every item has no price (terminals offer nothing,
     *  recycler pays 0). */
    public static final PriceTable EMPTY =
            new PriceTable(Collections.emptyMap(), hashOf(Collections.emptyMap()));

    private final Map<String, PriceRange> prices;
    private final String hash;

    private PriceTable(Map<String, PriceRange> prices, String hash) {
        this.prices = prices;
        this.hash = hash;
    }

    /**
     * Parses a price table object. Every non-conforming key/value is reported
     * through {@code issues} (as a human-readable message) and skipped; the
     * returned table always contains only valid entries, so a malformed file
     * degrades to the remaining valid prices instead of failing everything.
     * A null root (syntax error produced no object) reports one issue and
     * returns {@link #EMPTY}.
     */
    public static PriceTable parse(JsonObject json, List<String> issues) {
        if (json == null) {
            issues.add(FILE_NAME + " root must be a JSON object");
            return EMPTY;
        }
        Map<String, PriceRange> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            String key = e.getKey();
            JsonElement value = e.getValue();
            String where = "entry '" + key + "'";
            if (!KEY_RE.matcher(key).matches()) {
                issues.add(where + " is not a valid key — expected ns:path "
                        + "(optionally '#'variant for NBT-distinguished items)");
                continue;
            }
            PriceRange range = parsePrice(value, where, issues);
            if (range != null) {
                map.put(key, range);
            }
        }
        return new PriceTable(Collections.unmodifiableMap(map), hashOf(map));
    }

    /**
     * Parses one table value: a non-negative integer (fixed price) or a
     * two-element array {@code [min, max]} (random range, min &le; max).
     * Invalid values are reported through {@code issues} and return null.
     */
    private static PriceRange parsePrice(JsonElement value, String where,
                                         List<String> issues) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            double v = value.getAsDouble();
            if (v < 0 || v != Math.floor(v)) {
                issues.add(where + " must be a non-negative integer or a "
                        + "[min, max] range (Armory Points), got " + v);
                return null;
            }
            return PriceRange.fixed((int) v);
        }
        if (value.isJsonArray()) {
            JsonArray arr = value.getAsJsonArray();
            if (arr.size() != 2) {
                issues.add(where + " range must be exactly [min, max],"
                        + " got " + arr.size() + " element(s)");
                return null;
            }
            int[] bounds = new int[2];
            for (int i = 0; i < 2; i++) {
                JsonElement b = arr.get(i);
                if (!b.isJsonPrimitive() || !b.getAsJsonPrimitive().isNumber()) {
                    issues.add(where + " range bounds must be non-negative "
                            + "integers, got " + b);
                    return null;
                }
                double v = b.getAsDouble();
                if (v < 0 || v != Math.floor(v)) {
                    issues.add(where + " range bounds must be non-negative "
                            + "integers, got " + v);
                    return null;
                }
                bounds[i] = (int) v;
            }
            if (bounds[0] > bounds[1]) {
                issues.add(where + " range min (" + bounds[0]
                        + ") must not exceed max (" + bounds[1] + ")");
                return null;
            }
            return new PriceRange(bounds[0], bounds[1]);
        }
        issues.add(where + " must be a non-negative integer or a "
                + "[min, max] range (Armory Points)");
        return null;
    }

    /**
     * Terminal price range for an item id, or {@code null} when the table has
     * no entry. A variant lookup prefers {@code id#variant} and falls back to
     * the plain {@code id} price, so an author can price one variant
     * differently while everyone else keeps the shared price. The returned
     * range may be fixed ({@code min == max}) or random.
     */
    public PriceRange lookupRange(String itemId, String variant) {
        if (itemId == null) {
            return null;
        }
        if (variant != null && !variant.isEmpty()) {
            PriceRange variantPrice = prices.get(itemId + "#" + variant);
            if (variantPrice != null) {
                return variantPrice;
            }
        }
        return prices.get(itemId);
    }

    /** Plain-id range lookup without a variant. */
    public PriceRange lookupRange(String itemId) {
        return lookupRange(itemId, null);
    }

    /**
     * v2.0.1 compatibility view: terminal price for an item id as an int —
     * a random range resolves to its minimum, {@link #UNPRICED} when the
     * table has no entry. Consumers that need the actual sampled price
     * should use {@link #lookupRange} + {@link PriceRange#sample}.
     */
    public int lookup(String itemId, String variant) {
        PriceRange range = lookupRange(itemId, variant);
        return range != null ? range.min() : UNPRICED;
    }

    /** Plain-id lookup without a variant. */
    public int lookup(String itemId) {
        return lookup(itemId, null);
    }

    /**
     * Extracts the NBT variant id of a box item entry from its legacy
     * {@code "tag"} string ({@code GunId} / {@code AmmoId}, see TACZ).
     * Returns empty for entries without a variant field — the caller then
     * prices by the plain item id. Does not touch the Minecraft NBT API, so
     * it works identically on every platform.
     */
    public static Optional<String> variantId(JsonObject entry) {
        if (entry == null || !entry.has("tag")) {
            return Optional.empty();
        }
        JsonElement tag = entry.get("tag");
        if (!tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }
        Matcher m = VARIANT_FIELD_RE.matcher(tag.getAsString());
        if (m.find()) {
            String variant = m.group(1).trim();
            if (!variant.isEmpty()) {
                return Optional.of(variant);
            }
        }
        return Optional.empty();
    }

    /** Number of priced entries. */
    public int size() {
        return prices.size();
    }

    /** Unmodifiable view of key → price range entries (used by the legacy
     *  migrator and inspection code). */
    public Map<String, PriceRange> entries() {
        return prices;
    }

    public boolean isEmpty() {
        return prices.isEmpty();
    }

    /** SHA-256 of the sorted {@code key=min..max} lines; changes whenever any
     *  entry changes, used to invalidate the platform loaders' parse cache. */
    public String hash() {
        return hash;
    }

    /**
     * v2.0.1 economy: Armory Recycler payout for a table price — 90% of the
     * price, rounded UP. A 4500-point item recycles for 4050, a 10-point item
     * for 9, an 11-point item for 10; a 1-point item for 1. Non-positive
     * prices (including {@link #UNPRICED}) pay 0. Integer math
     * ({@code ceil(p * 0.9) = (p*9 + 9) / 10}) is exact for every int price.
     */
    public static int recycleYield(int price) {
        if (price <= 0) {
            return 0;
        }
        return (int) ((price * 9L + 9L) / 10L);
    }

    /**
     * Recycler payout for a random range: samples one price from the range
     * through {@code nextBounded} (per recycle) and applies the 90% rule.
     * {@link PriceRange#UNPRICED} (or null) pays 0.
     */
    public static int recycleYield(PriceRange range, IntUnaryOperator nextBounded) {
        if (range == null || range.isUnpriced()) {
            return 0;
        }
        return recycleYield(range.sample(nextBounded));
    }

    private static String hashOf(Map<String, PriceRange> map) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            List<String> keys = new ArrayList<>(map.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                PriceRange r = map.get(key);
                md.update(key.getBytes(StandardCharsets.UTF_8));
                md.update((byte) '=');
                md.update((r.min() + ".." + r.max()).getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
            }
            byte[] out = md.digest();
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(HEX_DIGITS[(b >> 4) & 0xF]).append(HEX_DIGITS[b & 0xF]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}