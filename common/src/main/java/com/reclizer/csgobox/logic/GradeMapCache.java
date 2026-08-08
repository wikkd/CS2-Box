package com.reclizer.csgobox.logic;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cache of immutable {@link GradeMap} instances keyed by box id, so the
 * per-box item pool is built only once per definition instead of once per
 * bulk-open request.
 *
 * <p>Values are treated as immutable: callers must never mutate a cached
 * grade map. Platform code invalidates entries whenever the box registry
 * changes (register/remove/clear), so a reload can never serve a stale pool.</p>
 */
public final class GradeMapCache {

    private static final ConcurrentHashMap<String, GradeMap<?>> CACHE = new ConcurrentHashMap<>();

    private GradeMapCache() {
    }

    /**
     * Returns the cached grade map for {@code boxId}, building it with
     * {@code builder} on a miss. The builder may run more than once under
     * concurrency, but only one result is ever published.
     */
    @SuppressWarnings("unchecked")
    public static <T> GradeMap<T> get(String boxId, Supplier<GradeMap<T>> builder) {
        return (GradeMap<T>) CACHE.computeIfAbsent(boxId, key -> builder.get());
    }

    /** Drops the entry for one box id (used on register/remove). */
    public static void invalidate(String boxId) {
        CACHE.remove(boxId);
    }

    /** Drops every entry (used on registry clear). */
    public static void invalidateAll() {
        CACHE.clear();
    }
}
