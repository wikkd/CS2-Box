package com.reclizer.csgobox.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * A grade-indexed item collection supporting random selection (optionally
 * per-item weighted) and fallback search.
 *
 * <p>Generic replacement for per-platform {@code RandomItem.precomputeGradeMap /
 * randomItemsFromGradeMap / findFallbackFromGradeMap}. Uses a
 * {@code Predicate<T>} for validity (replacing {@code ItemStack.isEmpty()}) and
 * a {@code Function<T,T>} copier (replacing {@code ItemStack.copy()}).</p>
 *
 * <p>Per-item weights are additive: the classic {@link #build(Map, Predicate,
 * Function)} overload keeps uniform selection, while
 * {@link #build(Map, ToIntFunction, Predicate, Function)} weights each item
 * inside its grade (a weight {@code <= 0} removes the item from the pool, so
 * authors can temporarily disable single entries without deleting them).</p>
 *
 * @param <T> item type
 */
public final class GradeMap<T> {

    /** One pool entry: the item plus its intra-grade weight. */
    public record Weighted<T>(T item, int weight) {
    }

    private final Map<Integer, List<Weighted<T>>> map;
    private final Predicate<T> valid;
    private final Function<T, T> copier;

    /**
     * Lazy per-target-grade fallback cache. {@code fallbackCache} stores the
     * cached source item (never a copy); {@code noFallback} marks grades that
     * resolve to nothing. Since the map is immutable this is safe to share
     * across threads — concurrent misses just compute the same value and
     * publish one winner, matching {@code GradeMapCache} semantics.
     */
    private final Map<Integer, T> fallbackCache = new ConcurrentHashMap<>();
    private final Set<Integer> noFallback = ConcurrentHashMap.newKeySet();

    /**
     * Precomputed per-grade positive-weight cumulative table, built once in
     * the constructor. The roll path draws ~50 slots per animation strip plus
     * one pick per bulk follow-up box, so hoisting the per-pick weight re-sum
     * to build time removes two linear scans per roll (mirrors
     * {@link OddsCalculator#precomputeWeights}, which already does this for
     * the grade-level weights). Immutable and thread-safe like the map.
     */
    private record Pool(long[] cumulative, long total) {
    }

    private final Map<Integer, Pool> pools;

    /**
     * @param map    grade → weighted items (defensively copied)
     * @param valid  predicate returning true for valid (non-empty) items
     * @param copier copy function applied to items before returning
     */
    private GradeMap(Map<Integer, List<Weighted<T>>> map, Predicate<T> valid, Function<T, T> copier) {
        this.valid = valid;
        this.copier = copier;
        if (map == null || map.isEmpty()) {
            this.map = Map.of();
        } else {
            LinkedHashMap<Integer, List<Weighted<T>>> copy = new LinkedHashMap<>();
            for (Map.Entry<Integer, List<Weighted<T>>> entry : map.entrySet()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            this.map = Collections.unmodifiableMap(copy);
        }
        Map<Integer, Pool> poolBuilder = new HashMap<>();
        for (Map.Entry<Integer, List<Weighted<T>>> entry : this.map.entrySet()) {
            List<Weighted<T>> list = entry.getValue();
            long[] cumulative = new long[list.size()];
            long running = 0L;
            for (int i = 0; i < list.size(); i++) {
                running += Math.max(0, list.get(i).weight());
                cumulative[i] = running;
            }
            poolBuilder.put(entry.getKey(), new Pool(cumulative, running));
        }
        this.pools = poolBuilder.isEmpty() ? Map.of() : Map.copyOf(poolBuilder);
    }

    /**
     * Builds a GradeMap directly from per-grade weighted entries (used by the
     * platform item code when the weights live in the box definition, so the
     * intermediate item→grade map is skipped entirely). Entries with a weight
     * {@code <= 0} are dropped.
     */
    public static <T> GradeMap<T> fromWeighted(Map<Integer, List<Weighted<T>>> weighted,
                                               Predicate<T> valid, Function<T, T> copier) {
        Map<Integer, List<Weighted<T>>> gradeMap = new LinkedHashMap<>();
        if (weighted == null || weighted.isEmpty()) {
            return new GradeMap<>(gradeMap, valid, copier);
        }
        for (Map.Entry<Integer, List<Weighted<T>>> entry : weighted.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            List<Weighted<T>> cleaned = new ArrayList<>();
            for (Weighted<T> w : entry.getValue()) {
                if (w == null || w.item() == null || !valid.test(w.item()) || w.weight() <= 0) continue;
                cleaned.add(w);
            }
            if (!cleaned.isEmpty()) {
                gradeMap.put(entry.getKey(), cleaned);
            }
        }
        return new GradeMap<>(gradeMap, valid, copier);
    }

    /**
     * An empty GradeMap (used as a null-safe placeholder before a real pool
     * is built).
     */
    public static <T> GradeMap<T> empty(Predicate<T> valid, Function<T, T> copier) {
        return new GradeMap<>(Map.of(), valid, copier);
    }

    /**
     * Builds a GradeMap with uniform intra-grade selection (every item weight
     * 1). Mirrors {@code RandomItem.precomputeGradeMap}.
     *
     * @param itemMap item → grade mapping
     * @param valid   validity predicate (entries failing this are skipped)
     * @param copier  copy function for returned items
     * @param <T>     item type
     * @return a new GradeMap
     */
    public static <T> GradeMap<T> build(Map<T, Integer> itemMap, Predicate<T> valid, Function<T, T> copier) {
        return build(itemMap, item -> 1, valid, copier);
    }

    /**
     * Builds a GradeMap with per-item intra-grade weights.
     *
     * @param itemMap  item → grade mapping
     * @param weightOf weight resolver (a weight {@code <= 0} skips the item)
     * @param valid    validity predicate (entries failing this are skipped)
     * @param copier   copy function for returned items
     * @param <T>      item type
     * @return a new GradeMap
     */
    public static <T> GradeMap<T> build(Map<T, Integer> itemMap, ToIntFunction<T> weightOf,
                                        Predicate<T> valid, Function<T, T> copier) {
        Map<Integer, List<Weighted<T>>> gradeMap = new LinkedHashMap<>();
        if (itemMap == null || itemMap.isEmpty()) {
            return new GradeMap<>(gradeMap, valid, copier);
        }
        for (Map.Entry<T, Integer> entry : itemMap.entrySet()) {
            T item = entry.getKey();
            Integer grade = entry.getValue();
            if (item == null || !valid.test(item) || grade == null) continue;
            int weight = weightOf.applyAsInt(item);
            if (weight <= 0) continue;
            gradeMap.computeIfAbsent(grade, k -> new ArrayList<>())
                    .add(new Weighted<>(item, weight));
        }
        return new GradeMap<>(gradeMap, valid, copier);
    }

    /**
     * Picks a random item of the given grade using intra-grade weights.
     * Returns null if no candidates exist. Mirrors
     * {@code RandomItem.randomItemsFromGradeMap}.
     */
    public T pickRandom(Random rng, int grade) {
        List<Weighted<T>> candidates = map.get(grade);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return copier.apply(pickWeighted(rng, candidates, pools.get(grade)));
    }

    /**
     * Weighted pick over one grade's pool via the precomputed cumulative
     * table (single linear scan per roll; the sum step moved to build time).
     * Selection order and the {@code roll < cumulative[i]} condition match
     * the historical re-summing scan exactly, so a given RNG stream yields
     * the same item as before.
     */
    private T pickWeighted(Random rng, List<Weighted<T>> candidates, Pool pool) {
        if (pool == null || pool.total() <= 0) {
            // Unreachable through the public factories (both filter weight
            // <= 0); kept for parity with the historical uniform fallback.
            return candidates.get(rng.nextInt(candidates.size())).item();
        }
        long roll = OddsCalculator.nextBoundedLong(rng, pool.total());
        long[] cumulative = pool.cumulative();
        for (int i = 0; i < cumulative.length; i++) {
            if (roll < cumulative[i]) {
                return candidates.get(i).item();
            }
        }
        return candidates.get(candidates.size() - 1).item();
    }

    /**
     * Finds a fallback item: same grade first, then descending grades, then any
     * valid item. Returns null if nothing is found. Mirrors
     * {@code RandomItem.findFallbackFromGradeMap}.
     */
    public T findFallback(int targetGrade) {
        // Cached miss (this grade resolves to nothing) — cheap check.
        if (noFallback.contains(targetGrade)) {
            return null;
        }
        T cached = fallbackCache.get(targetGrade);
        if (cached != null) {
            return copier.apply(cached);
        }
        T source = computeFallback(targetGrade);
        if (source != null) {
            fallbackCache.put(targetGrade, source);
            return copier.apply(source);
        }
        noFallback.add(targetGrade);
        return null;
    }

    /** Un-cached fallback search; returns the source item (not a copy). */
    private T computeFallback(int targetGrade) {
        List<Weighted<T>> sameGrade = map.get(targetGrade);
        if (sameGrade != null) {
            for (Weighted<T> wi : sameGrade) {
                if (valid.test(wi.item())) return wi.item();
            }
        }
        for (int g = targetGrade - 1; g >= 1; g--) {
            List<Weighted<T>> lower = map.get(g);
            if (lower != null) {
                for (Weighted<T> wi : lower) {
                    if (valid.test(wi.item())) return wi.item();
                }
            }
        }
        for (List<Weighted<T>> list : map.values()) {
            for (Weighted<T> wi : list) {
                if (valid.test(wi.item())) return wi.item();
            }
        }
        return null;
    }

    /** Returns true when {@code item} passes this map's validity predicate. */
    public boolean isValid(T item) {
        return item != null && valid.test(item);
    }

    /** Returns true if this grade map contains no items. */
    public boolean isEmpty() {
        return map.isEmpty();
    }
}
