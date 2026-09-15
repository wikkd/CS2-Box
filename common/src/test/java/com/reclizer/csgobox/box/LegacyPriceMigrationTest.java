package com.reclizer.csgobox.box;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Legacy-version adapter tests: per-item {@code price} fields inside box
 * JSONs are transferred into {@code config/csbox/_prices.json}; conflicting
 * prices for the same key are migrated as the round-half-up average.
 */
final class LegacyPriceMigrationTest {

    @TempDir
    Path tmp;

    private Path box(String name, String body) throws Exception {
        Path f = tmp.resolve(name);
        Files.writeString(f, body, StandardCharsets.UTF_8);
        return f;
    }

    private JsonObject readJson(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private JsonObject readTable() throws Exception {
        Path table = tmp.resolve(PriceTable.FILE_NAME);
        assertTrue(Files.exists(table), "_prices.json must exist");
        return readJson(table);
    }

    private Integer priceInTable(JsonObject table, String key) {
        return table.has(key) ? table.get(key).getAsInt() : null;
    }

    @Test
    @DisplayName("same key with different box prices migrates as the average and strips the boxes")
    void conflictingPricesAveraged() throws Exception {
        box("box_a.json", """
                {"name": "A", "grade5": [{"id": "minecraft:netherite_sword", "price": 4000}]}
                """);
        box("box_b.json", """
                {"name": "B", "grade5": [{"id": "minecraft:netherite_sword", "price": 200}]}
                """);

        LegacyPriceMigration.Result r = LegacyPriceMigration.migrateLegacyPrices(tmp);

        assertEquals(1, r.migratedKeys(), "one new key");
        assertEquals(1, r.averagedKeys(), "conflicting prices averaged");
        assertEquals(2, r.boxesRewritten(), "both boxes stripped");
        assertEquals(2100, priceInTable(readTable(), "minecraft:netherite_sword").intValue());

        // box files no longer carry the price field
        JsonObject a = readJson(tmp.resolve("box_a.json"));
        assertFalse(a.getAsJsonArray("grade5").get(0).getAsJsonObject().has("price"));
        JsonObject b = readJson(tmp.resolve("box_b.json"));
        assertFalse(b.getAsJsonArray("grade5").get(0).getAsJsonObject().has("price"));
    }

    @Test
    @DisplayName("average rounds half up (1.5 -> 2)")
    void averageRoundsHalfUp() throws Exception {
        box("x.json", """
                {"name": "x", "grade1": [{"id": "minecraft:diamond", "price": 1},
                                         {"id": "minecraft:diamond", "price": 2}]}
                """);
        LegacyPriceMigration.Result r = LegacyPriceMigration.migrateLegacyPrices(tmp);
        assertEquals(1, r.migratedKeys());
        assertEquals(2, priceInTable(readTable(), "minecraft:diamond").intValue());
    }

    @Test
    @DisplayName("an existing table entry wins; the leftover box field is still stripped")
    void existingTableEntryWins() throws Exception {
        Files.writeString(tmp.resolve(PriceTable.FILE_NAME), """
                {"minecraft:diamond": 999}
                """, StandardCharsets.UTF_8);
        box("x.json", """
                {"name": "x", "grade1": [{"id": "minecraft:diamond", "price": 500}]}
                """);

        LegacyPriceMigration.Result r = LegacyPriceMigration.migrateLegacyPrices(tmp);

        assertEquals(0, r.migratedKeys(), "no new key (already priced)");
        assertEquals(1, r.boxesRewritten(), "box field still stripped");
        assertEquals(999, priceInTable(readTable(), "minecraft:diamond").intValue());
        assertFalse(readJson(tmp.resolve("x.json"))
                .getAsJsonArray("grade1").get(0).getAsJsonObject().has("price"));
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("保留 _prices.json 已有价格")));
    }

    @Test
    @DisplayName("malformed _prices.json aborts: nothing is written or stripped")
    void malformedTableAborts() throws Exception {
        Files.writeString(tmp.resolve(PriceTable.FILE_NAME), "{not json",
                StandardCharsets.UTF_8);
        Path f = box("x.json", """
                {"name": "x", "grade1": [{"id": "minecraft:diamond", "price": 500}]}
                """);

        LegacyPriceMigration.Result r = LegacyPriceMigration.migrateLegacyPrices(tmp);

        assertEquals(0, r.migratedKeys());
        assertEquals(0, r.boxesRewritten());
        assertFalse(r.warnings().isEmpty());
        // box untouched: still has the price field, and the broken table is intact
        assertTrue(readJson(f).getAsJsonArray("grade1").get(0).getAsJsonObject().has("price"));
        assertEquals("{not json", Files.readString(tmp.resolve(PriceTable.FILE_NAME)));
    }

    @Test
    @DisplayName("tag/loot entries and invalid prices are left in place")
    void unkeyableEntriesStay() throws Exception {
        Path f = box("x.json", """
                {"name": "x",
                 "grade1": [{"id": "minecraft:diamond", "price": 100},
                            {"tag": "#minecraft:swords", "price": 7},
                            {"loot_table": "minecraft:chests/simple_dungeon", "price": 9},
                            {"id": "minecraft:iron_ingot", "price": -3},
                            {"id": "minecraft:gold_ingot", "price": 2.5}]}
                """);

        LegacyPriceMigration.Result r = LegacyPriceMigration.migrateLegacyPrices(tmp);

        assertEquals(1, r.migratedKeys(), "only the plain id entry migrates");
        assertEquals(100, priceInTable(readTable(), "minecraft:diamond").intValue());

        JsonArray items = readJson(f).getAsJsonArray("grade1");
        JsonObject diamond = items.get(0).getAsJsonObject();
        assertFalse(diamond.has("price"), "valid migrated price is stripped");
        assertTrue(items.get(1).getAsJsonObject().has("price"), "tag entry kept");
        assertTrue(items.get(2).getAsJsonObject().has("price"), "loot_table entry kept");
        assertTrue(items.get(3).getAsJsonObject().has("price"), "negative price kept");
        assertTrue(items.get(4).getAsJsonObject().has("price"), "fractional price kept");
        assertFalse(priceInTable(readTable(), "minecraft:iron_ingot") != null);
        assertFalse(priceInTable(readTable(), "minecraft:gold_ingot") != null);
    }

    @Test
    @DisplayName("legacy TACZ NBT variants migrate under id#variant keys")
    void variantKeyMigrated() throws Exception {
        box("tacz.json", """
                {"name": "tacz", "type": "terminal",
                 "grade3": [{"id": "tacz:modern_kinetic_gun",
                             "tag": "{GunId:\\"tacz:ak47\\"}",
                             "count": 1, "price": 2000}]}
                """);

        LegacyPriceMigration.Result r = LegacyPriceMigration.migrateLegacyPrices(tmp);

        assertEquals(1, r.migratedKeys());
        JsonObject table = readTable();
        assertEquals(2000, table.get("tacz:modern_kinetic_gun#tacz:ak47").getAsInt());
        assertNull(priceInTable(table, "tacz:modern_kinetic_gun"), "plain gun id stays unpriced");
        assertFalse(readJson(tmp.resolve("tacz.json"))
                .getAsJsonArray("grade3").get(0).getAsJsonObject().has("price"));
    }

    @Test
    @DisplayName("a second run is a no-op (idempotent)")
    void idempotent() throws Exception {
        box("x.json", """
                {"name": "x", "grade1": [{"id": "minecraft:diamond", "price": 100}]}
                """);
        LegacyPriceMigration.Result first = LegacyPriceMigration.migrateLegacyPrices(tmp);
        assertTrue(first.didWork());

        LegacyPriceMigration.Result second = LegacyPriceMigration.migrateLegacyPrices(tmp);
        assertEquals(0, second.migratedKeys());
        assertEquals(0, second.boxesRewritten());
        assertFalse(second.didWork());
        assertEquals(100, priceInTable(readTable(), "minecraft:diamond").intValue());
    }

    @Test
    @DisplayName("empty or missing directory is a harmless no-op")
    void noDir() {
        LegacyPriceMigration.Result r = LegacyPriceMigration.migrateLegacyPrices(tmp.resolve("nope"));
        assertEquals(0, r.migratedKeys());
        assertEquals(0, r.boxesRewritten());
        assertFalse(r.didWork());
    }
}