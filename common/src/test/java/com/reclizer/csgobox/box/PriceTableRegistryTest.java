package com.reclizer.csgobox.box;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for {@link PriceTableRegistry} — the cross-platform holder that
 * exposes the active price table to read-only economy code (recycler).
 */
final class PriceTableRegistryTest {

    @Test
    @DisplayName("defaults to the empty table and never returns null")
    void defaultsToEmpty() {
        assertSame(PriceTable.EMPTY, PriceTableRegistry.get());
    }

    @Test
    @DisplayName("set publishes the table; null degrades to empty")
    void setPublishes() {
        List<String> issues = new ArrayList<>();
        PriceTable table = PriceTable.parse(
                JsonParser.parseString("{\"minecraft:diamond\": 500}").getAsJsonObject(),
                issues);
        PriceTableRegistry.set(table);
        try {
            assertSame(table, PriceTableRegistry.get());
            assertEquals(500, PriceTableRegistry.get().lookup("minecraft:diamond"));
            PriceTableRegistry.set(null);
            assertSame(PriceTable.EMPTY, PriceTableRegistry.get());
        } finally {
            PriceTableRegistry.set(PriceTable.EMPTY);
        }
    }
}