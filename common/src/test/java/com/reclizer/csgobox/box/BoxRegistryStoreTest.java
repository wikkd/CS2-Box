package com.reclizer.csgobox.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BoxRegistryStore}, pinning the historical
 * per-platform BoxRegistry container semantics: insertion order, replacement,
 * unconditional invalidation callbacks, and unmodifiable views.
 */
final class BoxRegistryStoreTest {

    private final List<String> changed = new ArrayList<>();
    private int cleared = 0;

    private BoxRegistryStore<String, String> newStore() {
        return new BoxRegistryStore<>(changed::add, () -> cleared++);
    }

    @Test
    @DisplayName("register stores values and fires the change callback")
    void registerStoresAndInvalidates() {
        BoxRegistryStore<String, String> store = newStore();
        store.register("a", "valueA");
        store.register("b", "valueB");

        assertEquals("valueA", store.get("a"));
        assertEquals(2, store.size());
        assertEquals(List.of("a", "b"), new ArrayList<>(store.getIds()));
        assertEquals(List.of("a", "b"), changed);
    }

    @Test
    @DisplayName("register replaces the value under an existing key")
    void registerReplaces() {
        BoxRegistryStore<String, String> store = newStore();
        store.register("a", "old");
        store.register("a", "new");

        assertEquals("new", store.get("a"));
        assertEquals(1, store.size());
        // Both mutations must invalidate (parity with the platform shell).
        assertEquals(List.of("a", "a"), changed);
    }

    @Test
    @DisplayName("remove drops the entry and invalidates even for absent keys")
    void removeInvalidatesUnconditionally() {
        BoxRegistryStore<String, String> store = newStore();
        store.register("a", "valueA");
        changed.clear();

        store.remove("a");
        store.remove("missing");

        assertNull(store.get("a"));
        assertEquals(0, store.size());
        assertEquals(List.of("a", "missing"), changed);
    }

    @Test
    @DisplayName("clear empties the store and fires the clear callback")
    void clearFiresCallback() {
        BoxRegistryStore<String, String> store = newStore();
        store.register("a", "valueA");

        store.clear();

        assertEquals(0, store.size());
        assertTrue(store.getIds().isEmpty());
        assertEquals(1, cleared);
        // clear() must not emit per-entry callbacks (parity with platform shell).
        assertEquals(List.of("a"), changed);
    }

    @Test
    @DisplayName("getAll and getIds return unmodifiable views")
    void viewsAreUnmodifiable() {
        BoxRegistryStore<String, String> store = newStore();
        store.register("a", "valueA");

        assertThrows(UnsupportedOperationException.class, () -> store.getAll().clear());
        assertThrows(UnsupportedOperationException.class, () -> store.getIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> store.getAll().add("x"));
    }

    @Test
    @DisplayName("constructor rejects null callbacks")
    void nullCallbacksRejected() {
        assertThrows(NullPointerException.class, () -> new BoxRegistryStore<>(null, () -> { }));
        assertThrows(NullPointerException.class, () -> new BoxRegistryStore<>(k -> { }, null));
    }
}
