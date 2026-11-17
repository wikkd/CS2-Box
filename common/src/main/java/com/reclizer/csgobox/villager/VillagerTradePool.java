package com.reclizer.csgobox.villager;

import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;

/**
 * The arms-dealer's random mineral-buy pool (v2.x).
 *
 * <p>Sell offers (box / key0 / key1 / terminal / key2) stay fixed on every
 * villager, but the mineral-buy offers are drawn from per-level pools: each
 * villager deterministically activates {@code draws} minerals out of its
 * level's pool, keyed by the villager's UUID + level. Inactive buy offers are
 * gated off by the platform glue (legacy listings return {@code null}; 26.x
 * trades fail their {@code merchant_predicate}), and vanilla's random
 * draw fills the offer slots with the active ones — null offers never
 * consume a slot on either era (verified against decompiled
 * {@code addOffersFromItemListings} / {@code addOffersFromItemListingsWithoutDuplicates}).
 * The selection is stable for a villager at a given level and re-rolls on
 * level-up, matching vanilla's build-once-at-level-up offer semantics.</p>
 *
 * <p>Level composition (defaults, {@code pool_enabled=true}):</p>
 * <ul>
 *   <li>Level 1: draw 2 of {coal, copper, iron, emerald, gold}</li>
 *   <li>Level 2: draw 1 of {iron, emerald, gold} + points → box</li>
 *   <li>Level 3: draw 1 of {gold, diamond} + points → key0</li>
 *   <li>Level 4: points+gold → key1, points → terminal (no buys)</li>
 *   <li>Level 5: draw 1 of {iron, emerald, gold, diamond} + points+diamond → key2</li>
 * </ul>
 *
 * <p>With {@code pool_enabled=false} the pre-pool fixed lists are restored
 * exactly (iron+emerald at level 1, gold at level 2, diamond at level 3,
 * none at levels 4/5) on every platform — the same {@link #isActive} gate
 * simply reports the original minerals as the active set.</p>
 *
 * <p>Pure Java (no Minecraft imports) so it compiles in the common module
 * and behaves identically on all platforms. Determinism relies on
 * {@link Random}'s spec-guaranteed LCG: the same seed always yields the same
 * selection.</p>
 */
public final class VillagerTradePool {

    private VillagerTradePool() {
    }

    /** Mineral item ids usable as {@code mineral} keys (and 26.x JSON values). */
    public static final String COAL = "minecraft:coal";
    public static final String COPPER = "minecraft:copper_ingot";
    public static final String IRON = "minecraft:iron_ingot";
    public static final String EMERALD = "minecraft:emerald";
    public static final String GOLD = "minecraft:gold_ingot";
    public static final String DIAMOND = "minecraft:diamond";

    /** How many mineral buys are active per level (0 = no buy slot). */
    private static final Map<Integer, Integer> DRAWS = new TreeMap<>();
    /** Per-level mineral-buy pools. */
    private static final Map<Integer, String[]> POOLS = new TreeMap<>();
    /** Pre-pool fixed minerals per level, restored when pool_enabled=false. */
    private static final Map<Integer, Set<String>> ORIGINAL = new TreeMap<>();

    static {
        POOLS.put(1, new String[]{COAL, COPPER, IRON, EMERALD, GOLD});
        POOLS.put(2, new String[]{IRON, EMERALD, GOLD});
        POOLS.put(3, new String[]{GOLD, DIAMOND});
        POOLS.put(5, new String[]{IRON, EMERALD, GOLD, DIAMOND});

        DRAWS.put(1, 2);
        DRAWS.put(2, 1);
        DRAWS.put(3, 1);
        DRAWS.put(5, 1);

        ORIGINAL.put(1, Set.of(IRON, EMERALD));
        ORIGINAL.put(2, Set.of(GOLD));
        ORIGINAL.put(3, Set.of(DIAMOND));
    }

    /**
     * Reports whether the given mineral buy is active for a villager.
     *
     * @param villagerSeed the villager's UUID least-significant bits
     * @param level        the villager level (1..5) whose offers are built
     * @param mineralItemId one of the {@code minecraft:*} ids above
     * @param poolEnabled  the {@code pool_enabled} config knob
     */
    public static boolean isActive(long villagerSeed, int level, String mineralItemId, boolean poolEnabled) {
        if (!poolEnabled) {
            Set<String> fixed = ORIGINAL.get(level);
            return fixed != null && fixed.contains(mineralItemId);
        }
        String[] pool = POOLS.get(level);
        Integer draws = DRAWS.get(level);
        if (pool == null || draws == null || draws <= 0) {
            return false;
        }
        String[] arr = pool.clone();
        Random rng = new Random(mix(villagerSeed, level));
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            String tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
        for (int i = 0; i < Math.min(draws, arr.length); i++) {
            if (arr[i].equals(mineralItemId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Level-scoped seed: the same villager re-rolls its selection per level.
     * Splitmix-style avalanche before feeding {@link Random} — the LCG only
     * consumes the high bits of its 48-bit state, so a bare XOR would leave
     * low-bit-similar UUIDs (sequential spawns) with identical pools.
     */
    private static long mix(long seed, int level) {
        long h = seed ^ (level * 0x9E3779B97F4A7C15L);
        h ^= h >>> 32;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }
}
