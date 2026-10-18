package com.reclizer.csgobox.box;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorPackStatsTest {

    @TempDir
    Path dir;

    private void write(String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
    }

    @Test
    void countsBoxesItemsAndUnpricedIds() throws Exception {
        write("alpha.json", """
                {"name":"Alpha","type":"csbox","key":"minecraft:iron_ingot",
                 "random":[625,125,25,5,2],
                 "grade1":[{"id":"minecraft:diamond","weight":1},"minecraft:stone"],
                 "grade2":[{"id":"minecraft:dirt","weight":2}]}""");
        write("beta.json", """
                {"name":"Beta","type":"csbox","key":"minecraft:iron_ingot",
                 "random":[625,125,25,5,2],
                 "grade1":["#minecraft:logs","minecraft:diamond"]}""");
        write("_prices.json", """
                {"minecraft:diamond":1500}""");
        EditorPackStats.Summary s = EditorPackStats.summarize(dir);
        assertEquals(2, s.boxes());
        assertEquals(5, s.items());
        // stone + dirt unpriced; diamond priced; the #tag entry is not an id.
        assertEquals(2, s.unpriced());
    }

    @Test
    void variantPriceKeySatisfiesBaseId() throws Exception {
        write("gun.json", """
                {"name":"Gun","type":"csbox","key":"minecraft:iron_ingot",
                 "random":[625,125,25,5,2],
                 "grade1":[{"id":"tacz:modern_kinetic_gun","variant":"tacz:ak47"}]}""");
        write("_prices.json", """
                {"tacz:modern_kinetic_gun#tacz:ak47":800}""");
        EditorPackStats.Summary s = EditorPackStats.summarize(dir);
        assertEquals(1, s.boxes());
        assertEquals(0, s.unpriced());
    }

    @Test
    void underscoreHelpersAndNonJsonFilesAreIgnored() throws Exception {
        write("box.json", """
                {"name":"Box","type":"csbox","key":"minecraft:iron_ingot",
                 "random":[625,125,25,5,2],"grade1":[{"id":"minecraft:diamond"}]}""");
        write("_tutorial_v2.0.1.md", "not json");
        write("notes.txt", "not a box");
        write("_prices.json", "{\"minecraft:diamond\":1}");
        EditorPackStats.Summary s = EditorPackStats.summarize(dir);
        assertEquals(1, s.boxes());
        assertEquals(1, s.items());
        assertEquals(0, s.unpriced());
    }

    @Test
    void brokenFilesAreSkippedNotFatal() throws Exception {
        write("good.json", """
                {"name":"Good","type":"csbox","key":"minecraft:iron_ingot",
                 "random":[625,125,25,5,2],"grade1":[{"id":"minecraft:diamond"}]}""");
        write("broken.json", "{\"name\":\"Half typed\",\"grade1\": [");
        write("_prices.json", "{\"minecraft:diamond\":1}");
        EditorPackStats.Summary s = EditorPackStats.summarize(dir);
        assertEquals(1, s.boxes());
        assertEquals(1, s.items());
        assertEquals(0, s.unpriced());
    }

    @Test
    void missingFolderYieldsEmptySummary() {
        EditorPackStats.Summary s = EditorPackStats.summarize(dir.resolve("nope"));
        assertTrue(s.empty());
        assertEquals(0, s.items());
        assertEquals(0, s.unpriced());
    }

    @Test
    void missingPriceTableCountsAllIdsUnpriced() throws Exception {
        write("box.json", """
                {"name":"Box","type":"csbox","key":"minecraft:iron_ingot",
                 "random":[625,125,25,5,2],
                 "grade1":["minecraft:diamond","minecraft:stone"]}""");
        EditorPackStats.Summary s = EditorPackStats.summarize(dir);
        assertEquals(2, s.unpriced());
    }
}
