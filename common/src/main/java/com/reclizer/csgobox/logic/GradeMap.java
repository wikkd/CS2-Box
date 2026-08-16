package com.reclizer.csgobox.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A grade-indexed item collection supporting random selection and fallback
 * search.
 *
 * <p>Generic replacement for per-platform {@code RandomItem.precomputeGradeMap /
 * randomItemsFromGradeMap / findFallbackFromGradeMap}. Uses a
 * {@code Predicate<T>} for validity (replacing {@code ItemStack.isEmpty()}) and
 * a {@code Function<T,T>} copier (replacing {@code ItemStack.copy()}).</p>
 *
 * @param <T> item type
 */
public final class GradeMap<T> {

    private final Map<Integer, List<T>> map;
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
     * @param map    grade → items (defensively copied)
     * @param valid  predicate returning true for valid (non-empty) items
     * @param copier copy function applied to items before returning
     */
    public GradeMap(Map<Integer, List<T>> map, Predicate<T> valid, Function<T, T> copier) {
        this.valid = valid;
        this.copier = copier;
        if (map == null || map.isEmpty()) {
            this.map = Map.of();
        } else {
            LinkedHashMap<Integer, List<T>> copy = new LinkedHashMap<>();
            for (Map.Entry<Integer, List<T>> entry : map.entrySet()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            this.map = Collections.unmodifiableMap(copy);
        }
    }

    /**
     * Builds a GradeMap from a raw item→grade mapping, filtering out invalid
     * entries. Mirrors {@code RandomItem.precomputeGradeMap}.
     *
     * @param itemMap item → grade mapping
     * @param valid   validity predicate (entries failing this are skipped)
     * @param copier  copy function for returned items
     * @param <T>     item type
     * @return a new GradeMap
     */
    public static <T> GradeMap<T> build(Map<T, Integer> itemMap, Predicate<T> valid, Function<T, T> copier) {
        Map<Integer, List<T>> gradeMap = new LinkedHashMap<>();
        if (itemMap == null || itemMap.isEmpty()) {
            return new GradeMap<>(gradeMap, valid, copier);
        }
        for (Map.Entry<T, Integer> entry : itemMap.entrySet()) {
            T item = entry.getKey();
            Integer grade = entry.getValue();
            if (item == null || !valid.test(item) || grade == null) continue;
            gradeMap.computeIfAbsent(grade, k -> new ArrayList<>()).add(item);
        }
        return new GradeMap<>(gradeMap, valid, copier);
    }

    /**
     * Picks a random item of the given grade. Returns null if no candidates
     * exist. Mirrors {@code RandomItem.randomItemsFromGradeMap}.
     */
    public T pickRandom(Random rng, int grade) {
        List<T> candidates = map.get(grade);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return copier.apply(candidates.get(rng.nextInt(candidates.size())));
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
        List<T> sameGrade = map.get(targetGrade);
        if (sameGrade != null) {
            for (T item : sameGrade) {
                if (valid.test(item)) return item;
            }
        }
        for (int g = targetGrade - 1; g >= 1; g--) {
            List<T> lower = map.get(g);
            if (lower != null) {
                for (T item : lower) {
                    if (valid.test(item)) return item;
                }
            }
        }
        for (List<T> list : map.values()) {
            for (T item : list) {
                if (valid.test(item)) return item;
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
