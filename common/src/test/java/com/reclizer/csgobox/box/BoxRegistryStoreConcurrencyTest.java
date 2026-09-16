package com.reclizer.csgobox.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency regression for {@link BoxRegistryStore} (v2.0.1-fix): the store
 * must survive simultaneous mutations (the BoxFileWatcher reload thread) and
 * reads (main server thread opening boxes / running commands) without
 * {@code ConcurrentModificationException} or lost updates. Insertion-order and
 * unmodifiable-view semantics are pinned in {@link BoxRegistryStoreTest};
 * this test only checks no-exception + final internal consistency.
 */
final class BoxRegistryStoreConcurrencyTest {

    private static final int KEYS = 200;
    private static final int WRITERS = 3;
    private static final int READERS = 3;
    private static final int OPS_PER_THREAD = 5_000;

    @Test
    @DisplayName("concurrent register/remove + read snapshots never throw and stay consistent")
    void concurrentMutationsAndReads() throws Exception {
        BoxRegistryStore<Integer, Integer> store = new BoxRegistryStore<>(k -> { }, () -> { });
        for (int i = 0; i < KEYS; i++) {
            store.register(i, i);
        }

        CyclicBarrier barrier = new CyclicBarrier(WRITERS + READERS);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(WRITERS + READERS);
        try {
            for (int w = 0; w < WRITERS; w++) {
                final long seed = w;
                pool.submit(() -> {
                    try {
                        barrier.await();
                        Random rnd = new Random(seed);
                        for (int op = 0; op < OPS_PER_THREAD; op++) {
                            int k = rnd.nextInt(KEYS);
                            if ((op & 1) == 0) {
                                store.register(k, k);
                            } else {
                                store.remove(k);
                            }
                        }
                    } catch (Throwable t) {
                        firstFailure.compareAndSet(null, t);
                    }
                });
            }
            for (int r = 0; r < READERS; r++) {
                final long seed = 100 + r;
                pool.submit(() -> {
                    try {
                        barrier.await();
                        Random rnd = new Random(seed);
                        long checksum = 0L;
                        for (int op = 0; op < OPS_PER_THREAD; op++) {
                            int k = rnd.nextInt(KEYS);
                            if ((op & 1) == 0) {
                                // Live read: must never throw or return a torn state.
                                checksum += store.size();
                            } else {
                                // Snapshot read: iterating a snapshot must be safe.
                                checksum += store.getAll().size();
                            }
                            if ((op & 3) == 0) {
                                checksum += store.getIds().size();
                                checksum += store.get(k) == null ? 0 : 1;
                            }
                        }
                        if (checksum < 0) {
                            throw new AssertionError("impossible checksum");
                        }
                    } catch (Throwable t) {
                        firstFailure.compareAndSet(null, t);
                    }
                });
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS),
                    "concurrency test workers did not finish");
        }

        assertNull(firstFailure.get(), () -> "concurrent access failed: " + firstFailure.get());
        int finalSize = store.size();
        assertTrue(finalSize >= 0 && finalSize <= KEYS, "size out of range: " + finalSize);
        // After all workers stop, the store must be internally consistent.
        assertEquals(finalSize, store.getIds().size());
        assertEquals(finalSize, store.getAll().size());
    }
}