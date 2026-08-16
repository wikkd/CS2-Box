package com.reclizer.csgobox.logic;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GradeMapFallbackConcurrencyTest {

    private static final Predicate<String> NOT_EMPTY = s -> s.length() > 0;
    private static final Function<String, String> IDENTITY = Function.identity();

    private static GradeMap<String> pool(boolean withItem) {
        Map<String, Integer> items = new LinkedHashMap<>();
        if (withItem) {
            items.put("common", 1);
        }
        return GradeMap.build(items, NOT_EMPTY, IDENTITY);
    }

    @Test
    void concurrentHitAgrees() throws Exception {
        GradeMap<String> gm = pool(true);
        int threads = 8;
        int callsPerThread = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        String[] results = new String[threads];
        for (int t = 0; t < threads; t++) {
            final int ti = t;
            Thread th = new Thread(() -> {
                try {
                    start.await();
                    String last = null;
                    for (int i = 0; i < callsPerThread; i++) {
                        last = gm.findFallback(5);
                    }
                    results[ti] = last;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            th.start();
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "workers did not finish");
        for (String r : results) {
            assertEquals("common", r);
        }
    }

    @Test
    void concurrentMissAgrees() throws Exception {
        GradeMap<String> gm = pool(false);
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        boolean[] sawNonNull = new boolean[threads];
        for (int t = 0; t < threads; t++) {
            final int ti = t;
            Thread th = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 200; i++) {
                        if (gm.findFallback(1) != null) {
                            sawNonNull[ti] = true;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            th.start();
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "workers did not finish");
        for (boolean b : sawNonNull) {
            assertEquals(false, b);
        }
    }

    @Test
    void stability() {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("a", 1);
        items.put("b", 3);
        GradeMap<String> gm = GradeMap.build(items, NOT_EMPTY, IDENTITY);
        assertEquals("b", gm.findFallback(5));
        assertEquals("b", gm.findFallback(5));
        assertEquals("b", gm.findFallback(4));
        assertEquals("a", gm.findFallback(2));
        // Grade 0 has no same-grade or descending buckets; last-resort scans all -> "a"
        assertEquals("a", gm.findFallback(0));
        assertEquals("a", gm.findFallback(0));
    }
}
