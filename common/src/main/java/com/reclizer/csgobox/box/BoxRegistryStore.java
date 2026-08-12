package com.reclizer.csgobox.box;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Generic insertion-ordered registry container shared by every platform's
 * {@code BoxRegistry}.
 *
 * <p>Extracted from the per-platform {@code BoxRegistry} container logic.
 * Platforms keep a thin shell that supplies the key/value types, the
 * invalidation callbacks (e.g. grade-pool cache eviction), and logging.</p>
 *
 * <p>Callback contract (mirrors the historical platform behavior exactly):
 * <ul>
 *   <li>{@code onEntryChanged} fires on every {@link #register} and
 *       {@link #remove}, even when the key was not present — a stale
 *       derived cache must never survive a registry mutation.</li>
 *   <li>{@code onCleared} fires on every {@link #clear}, even when the
 *       registry was already empty.</li>
 * </ul>
 *
 * @param <K> key type (platform identifier type)
 * @param <V> value type (platform box definition)
 */
public final class BoxRegistryStore<K, V> {

    private final Map<K, V> registry = new LinkedHashMap<>();
    private final Consumer<K> onEntryChanged;
    private final Runnable onCleared;

    /**
     * @param onEntryChanged invoked with the key after every register/remove
     * @param onCleared        invoked after every clear
     */
    public BoxRegistryStore(Consumer<K> onEntryChanged, Runnable onCleared) {
        this.onEntryChanged = Objects.requireNonNull(onEntryChanged, "onEntryChanged");
        this.onCleared = Objects.requireNonNull(onCleared, "onCleared");
    }

    /** Registers (or replaces) the value under its key, then invalidates. */
    public void register(K key, V value) {
        registry.put(key, value);
        onEntryChanged.accept(key);
    }

    public V get(K key) {
        return registry.get(key);
    }

    public Collection<V> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    public Set<K> getIds() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    public int size() {
        return registry.size();
    }

    /** Removes the entry for the key (if any), then invalidates. */
    public void remove(K key) {
        registry.remove(key);
        onEntryChanged.accept(key);
    }

    /** Removes every entry, then fires the clear callback. */
    public void clear() {
        registry.clear();
        onCleared.run();
    }
}
