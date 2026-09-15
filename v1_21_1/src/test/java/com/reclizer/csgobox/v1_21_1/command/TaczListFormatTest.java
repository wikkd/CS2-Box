package com.reclizer.csgobox.v1_21_1.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.reclizer.csgobox.v1_21_1.command.TaczList.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure helpers of {@link TaczList} (sort / filter /
 * paginate / JSON). TACZ classes are never touched; rows are constructed
 * directly so the tests run without any TACZ registry data.
 */
class TaczListFormatTest {

    private static List<Row> rows(int n) {
        List<Row> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            out.add(new Row(String.format("tacz:gun_%02d", i), "Gun " + i,
                    Map.of("type", "rifle", "ammo", "tacz:762x39", "magazine", "30")));
        }
        return out;
    }

    @Test
    void pageCount_rounding() {
        assertEquals(1, TaczList.pageCount(0, 24));
        assertEquals(1, TaczList.pageCount(24, 24));
        assertEquals(2, TaczList.pageCount(25, 24));
        assertEquals(3, TaczList.pageCount(49, 24));
    }

    @Test
    void clampPage_edges() {
        assertEquals(1, TaczList.clampPage(25, 0, 24));
        assertEquals(1, TaczList.clampPage(25, 1, 24));
        assertEquals(2, TaczList.clampPage(25, 2, 24));
        assertEquals(2, TaczList.clampPage(25, 99, 24)); // clamp to last page
        assertEquals(1, TaczList.clampPage(0, 5, 24));
    }

    @Test
    void pageRows_slicesByPage() {
        List<Row> all = rows(25);
        assertEquals(24, TaczList.pageRows(all, 1, 24).size());
        assertEquals(1, TaczList.pageRows(all, 2, 24).size());
        assertEquals("tacz:gun_25", TaczList.pageRows(all, 2, 24).get(0).id());
        assertEquals(0, TaczList.pageRows(List.of(), 1, 24).size());
    }

    @Test
    void filterNs_matchesNamespacePrefixOnly() {
        List<Row> mixed = List.of(
                new Row("tacz:ak47", "", Map.of()),
                new Row("tacz:m4a1", "", Map.of()),
                new Row("my_gunpack:ak47", "", Map.of()));
        List<Row> tacz = TaczList.filterNs(mixed, "tacz");
        assertEquals(2, tacz.size());
        assertTrue(tacz.stream().allMatch(r -> r.id().startsWith("tacz:")));
        assertEquals(3, TaczList.filterNs(mixed, null).size());
        assertEquals(1, TaczList.filterNs(mixed, "MY_GUNPACK").size()); // case-insensitive
    }

    @Test
    void filterField_exactMatch() {
        List<Row> guns = rows(3);
        assertEquals(3, TaczList.filterField(guns, "ammo", "tacz:762x39").size());
        assertEquals(0, TaczList.filterField(guns, "ammo", "tacz:9mm").size());
        assertEquals(3, TaczList.filterField(guns, "ammo", null).size());
    }

    @Test
    void filterContains_tokenInCsv() {
        List<Row> ammo = List.of(
                new Row("tacz:9mm", "", Map.of("guns", "tacz:glock_17,tacz:m1911")));
        assertEquals(1, TaczList.filterContains(ammo, "guns", "tacz:glock_17").size());
        assertEquals(0, TaczList.filterContains(ammo, "guns", "tacz:ak47").size());
        assertEquals(1, TaczList.filterContains(ammo, "guns", " tacz:glock_17 ").size());
    }

    @Test
    void sortById_ordersByFullId() {
        List<Row> unsorted = List.of(
                new Row("z:last", "", Map.of()),
                new Row("a:first", "", Map.of()),
                new Row("a:second", "", Map.of()));
        List<Row> sorted = TaczList.sortById(unsorted);
        assertEquals(List.of("a:first", "a:second", "z:last"),
                sorted.stream().map(Row::id).toList());
    }

    @Test
    void toJson_includesIdNameAndFields() {
        List<Row> rows = List.of(new Row("tacz:ak47", "AK-47",
                Map.of("type", "rifle", "ammo", "tacz:762x39", "magazine", "30")));
        JsonArray arr = JsonParser.parseString(TaczList.toJson(rows)).getAsJsonArray();
        assertEquals(1, arr.size());
        assertEquals("tacz:ak47", arr.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("AK-47", arr.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("tacz:762x39", arr.get(0).getAsJsonObject().get("ammo").getAsString());
        assertEquals("30", arr.get(0).getAsJsonObject().get("magazine").getAsString());
    }

    @Test
    void toJson_empty_returnsEmptyArray() {
        assertEquals("[]", TaczList.toJson(List.of()));
    }
}