package com.reclizer.csgobox.box;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorPackTest {

    private static final String BOX_JSON = """
            {"name":"Test Box","type":"csbox","key":"minecraft:iron_ingot",
             "random":[625,125,25,5,2],
             "grade1":[{"id":"minecraft:diamond","weight":1}]}""";
    private static final String PRICES_JSON = """
            {"minecraft:diamond":1500,"minecraft:emerald":[100,200]}""";

    @TempDir
    Path tempDir;

    private void write(String name, String content) throws Exception {
        Files.writeString(tempDir.resolve(name), content, StandardCharsets.UTF_8);
    }

    private JsonObject roundtrip(String url) throws Exception {
        String enc = url.substring(url.indexOf("#pack=") + "#pack=".length());
        byte[] deflated = Base64.getUrlDecoder().decode(enc);
        String json;
        try (InflaterInputStream in = new InflaterInputStream(
                new java.io.ByteArrayInputStream(deflated), new Inflater())) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void packsBoxAndPricesIntoFragment() throws Exception {
        write("weapon_box.json", BOX_JSON);
        write("_prices.json", PRICES_JSON);
        write("_tutorial_v2.0.2.md", "not json, must be ignored");

        String url = EditorPack.buildUrl(tempDir, "https://example.test/ed/", "26.2");
        assertNotNull(url);
        assertTrue(url.startsWith("https://example.test/ed/#pack="));

        JsonObject manifest = roundtrip(url);
        assertEquals(1, manifest.get("v").getAsInt());
        assertEquals("26.2", manifest.get("mc").getAsString());
        assertTrue(manifest.getAsJsonObject("files").has("weapon_box.json"));
        assertEquals("Test Box",
                manifest.getAsJsonObject("files").getAsJsonObject("weapon_box.json")
                        .get("name").getAsString());
        assertEquals(1500, manifest.getAsJsonObject("prices").get("minecraft:diamond").getAsInt());
    }

    @Test
    void brokenFileIsCarriedAsRawText() throws Exception {
        // Unparseable files ride along verbatim; the website opens them in its
        // import dialog so the player can fix and re-import them.
        String raw = "{\"name\":\"Half typed box\",\"grade1\": [";
        write("good.json", BOX_JSON);
        write("broken.json", raw);
        write("prices_broken.json", PRICES_JSON); // control: valid sidecar file stays packed normally

        String url = EditorPack.buildUrl(tempDir, "https://e/", "1.21.1");
        assertNotNull(url);
        JsonObject manifest = roundtrip(url);
        assertTrue(manifest.getAsJsonObject("files").has("good.json"));
        assertTrue(!manifest.getAsJsonObject("files").has("broken.json"));
        assertTrue(!manifest.getAsJsonObject("broken").has("prices_broken.json"));
        assertEquals(raw, manifest.getAsJsonObject("broken").get("broken.json").getAsString());
    }

    @Test
    void brokenPricesFileLandsInBrokenSlot() throws Exception {
        write("box.json", BOX_JSON);
        write("_prices.json", "{already edited mid-save");

        String url = EditorPack.buildUrl(tempDir, "https://e/", "26.2");
        assertNotNull(url);
        JsonObject manifest = roundtrip(url);
        assertTrue(manifest.getAsJsonObject("broken").has("_prices.json"));
        assertTrue(manifest.get("prices") == null || manifest.get("prices").isJsonNull());
    }

    @Test
    void brokenOnlyPackStillBuilds() throws Exception {
        write("only_broken.json", "{\"oops\":");
        String url = EditorPack.buildUrl(tempDir, "https://e/", "1.21.1");
        assertNotNull(url);
        JsonObject manifest = roundtrip(url);
        assertTrue(manifest.getAsJsonObject("files").size() == 0);
        assertEquals("{\"oops\":", manifest.getAsJsonObject("broken").get("only_broken.json").getAsString());
    }

    @Test
    void oversizedBrokenFileIsDroppedNotCarried() throws Exception {
        StringBuilder sb = new StringBuilder("{\"name\":\"");
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < 120_000; i++) {
            sb.append(Integer.toHexString(rnd.nextInt(16)));
        }
        sb.append("\""); // unterminated -> always unparseable, and > BROKEN_MAX_CHARS
        write("huge_broken.json", sb.toString());

        String url = EditorPack.buildUrl(tempDir, "https://e/", "1.21.1");
        // No valid files at all: only the dropped broken one existed -> nothing
        // usable to pack.
        assertNull(url);
    }

    @Test
    void missingOrEmptyDirReturnsNull() throws Exception {
        assertNull(EditorPack.buildUrl(tempDir, "https://e/", "1.21.1"));
        assertNull(EditorPack.buildUrl(tempDir.resolve("nope"), "https://e/", "1.21.1"));
        assertNull(EditorPack.buildUrl(null, "https://e/", "1.21.1"));
    }

    @Test
    void pricesOnlyPackIsAllowed() throws Exception {
        write("_prices.json", PRICES_JSON);
        String url = EditorPack.buildUrl(tempDir, "https://e/", "1.20.1");
        assertNotNull(url);
        JsonObject manifest = roundtrip(url);
        assertTrue(manifest.getAsJsonObject("files").size() == 0);
        assertEquals(100, manifest.getAsJsonObject("prices")
                .getAsJsonArray("minecraft:emerald").get(0).getAsInt());
    }

    @Test
    void oversizedPackFallsBackToNull() throws Exception {
        // Incompressible-ish payload: random hex never compresses below 1:1,
        // so ~400 KB of it exceeds the 200k-char encoded cap.
        StringBuilder sb = new StringBuilder("{\"name\":\"");
        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < 400_000; i++) {
            sb.append(Integer.toHexString(rnd.nextInt(16)));
        }
        sb.append("\"}");
        write("big.json", sb.toString());

        assertNull(EditorPack.buildUrl(tempDir, "https://e/", "1.21.1"));
    }

    @Test
    void gameRegistryListsRideAlong() throws Exception {
        // Sorted + deduped newline text; the website splits it for autocomplete.
        write("weapon_box.json", BOX_JSON);
        String url = EditorPack.buildUrl(tempDir, "https://e/", "26.2",
                java.util.List.of("zzmod:gun", "minecraft:diamond", "minecraft:diamond", "  ", "zzmod:gun"),
                java.util.List.of("minecraft:zombie", "zzmod:goblin"));
        assertNotNull(url);
        JsonObject manifest = roundtrip(url);
        assertEquals("minecraft:diamond\nzzmod:gun", manifest.get("items").getAsString());
        assertEquals("minecraft:zombie\nzzmod:goblin", manifest.get("entities").getAsString());
        assertTrue(manifest.getAsJsonObject("files").has("weapon_box.json"));
    }

    @Test
    void listsOnlyPackBuildsOnEmptyDir() throws Exception {
        // No configs at all, but a registry payload: still worth a pack (the
        // player starts from scratch with full autocomplete).
        String url = EditorPack.buildUrl(tempDir, "https://e/", "1.21.1",
                java.util.List.of("minecraft:diamond"), null);
        assertNotNull(url);
        JsonObject manifest = roundtrip(url);
        assertTrue(manifest.getAsJsonObject("files").size() == 0);
        assertEquals("minecraft:diamond", manifest.get("items").getAsString());
        assertTrue(!manifest.has("entities"));
    }

    @Test
    void overCapDropsListsButKeepsConfigs() throws Exception {
        write("weapon_box.json", BOX_JSON);
        // Config-only encoded length, measured through the same code path.
        String baseUrl = EditorPack.buildUrl(tempDir, "https://e/", "26.2",
                null, null, Integer.MAX_VALUE);
        assertNotNull(baseUrl);
        int baseLen = baseUrl.substring(baseUrl.indexOf("#pack=") + 6).length();

        java.util.List<String> many = new java.util.ArrayList<>();
        java.util.Random rnd = new java.util.Random(11);
        for (int i = 0; i < 2000; i++) {
            StringBuilder id = new StringBuilder("zzmod").append(i).append(':');
            for (int j = 0; j < 16; j++) {
                id.append(Integer.toHexString(rnd.nextInt(16)));
            }
            many.add(id.toString());
        }

        // Registry lists fit when the cap allows them...
        String full = EditorPack.buildUrl(tempDir, "https://e/", "26.2", many, null, Integer.MAX_VALUE);
        assertNotNull(full);
        assertTrue(roundtrip(full).has("items"));

        // ...and are dropped (configs survive) when they blow the cap.
        String dropped = EditorPack.buildUrl(tempDir, "https://e/", "26.2", many, null, baseLen + 50);
        assertNotNull(dropped);
        JsonObject manifest = roundtrip(dropped);
        assertTrue(!manifest.has("items"));
        assertTrue(manifest.getAsJsonObject("files").has("weapon_box.json"));
    }

    @Test
    void deflateRoundtripMatchesZlibFormat() {
        String enc = EditorPack.deflateBase64Url("{\"ok\":true}");
        byte[] bytes = Base64.getUrlDecoder().decode(enc);
        // zlib header magic: 0x78 (CM=8 deflate)
        assertEquals((byte) 0x78, bytes[0]);
    }
}
