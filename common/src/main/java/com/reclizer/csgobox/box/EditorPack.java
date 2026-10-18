package com.reclizer.csgobox.box;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.Deflater;

/**
 * Packs the raw {@code config/csbox/*.json} files into a URL fragment for the
 * web box editor (issue: {@code /csbox editor} should auto-import the configs).
 *
 * <p>The manifest is compact JSON — {@code {"v":1,"mc":"<version key>",<br>
 * "files":{name: <parsed json>, ...},"broken":{name: <raw text>, ...},<br>
 * "prices": <parsed _prices.json> | null, "items": "id\nid\n...",<br>
 * "entities": "id\n..."}} — deflated (zlib, RFC 1950) and Base64-URL encoded
 * (no padding). Valid files go in parsed (smaller than escaped text); files
 * the JSON parser rejects are carried verbatim under {@code broken} — the
 * website parses each again, and a still-broken file opens pre-filled in its
 * import dialog so the player can fix it in place and re-import. The platform
 * command also supplies the instance's item/entity registry ids (newline
 * joined, sorted, deduped) so the website's autocomplete covers every loaded
 * item — box items can then be picked in the browser without copying ids or
 * NBT in-game. Fragments are never sent to any server, so the configs stay
 * between the player's game and their browser.
 *
 * <p>Only outright failures (missing dir, unreadable/oversized files,
 * oversized encoded pack) return {@code null} — the caller falls back to the
 * plain editor URL. The registry lists are a nice-to-have: when they push the
 * fragment over {@link #MAX_ENCODED_CHARS} the configs ship without them.
 * Pure Java (CONSTRAINT-001); the platform command supplies the config path,
 * the editor version key and the registry ids.
 */
public final class EditorPack {

    /** Hard cap on the encoded fragment; above this the plain link is used. */
    public static final int MAX_ENCODED_CHARS = 200_000;
    /** Per-file read cap so one pathological file cannot exhaust the pack. */
    public static final int MAX_FILE_BYTES = 512 * 1024;
    /** Raw text of a broken file is only carried up to this length; larger
     *  pathological files are dropped instead of choking the shared cap. */
    public static final int BROKEN_MAX_CHARS = 100_000;

    public static final String PRICES_FILE = "_prices.json";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private EditorPack() {
    }

    /** Builds the pack URL, or {@code null} if nothing usable was packed. */
    public static String buildUrl(Path csboxDir, String baseUrl, String mcVersion) {
        return buildUrl(csboxDir, baseUrl, mcVersion, null, null);
    }

    /** Builds the pack URL including the instance's item/entity registry ids
     *  (may be {@code null}; over the cap they are dropped before failing). */
    public static String buildUrl(Path csboxDir, String baseUrl, String mcVersion,
                                  Iterable<String> gameItems, Iterable<String> gameEntities) {
        return buildUrl(csboxDir, baseUrl, mcVersion, gameItems, gameEntities, MAX_ENCODED_CHARS);
    }

    static String buildUrl(Path csboxDir, String baseUrl, String mcVersion, int maxEncodedChars) {
        return buildUrl(csboxDir, baseUrl, mcVersion, null, null, maxEncodedChars);
    }

    static String buildUrl(Path csboxDir, String baseUrl, String mcVersion,
                           Iterable<String> gameItems, Iterable<String> gameEntities,
                           int maxEncodedChars) {
        try {
            if (csboxDir == null || !Files.isDirectory(csboxDir)) {
                return null;
            }
            List<Path> jsons = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(csboxDir, "*.json")) {
                for (Path p : stream) {
                    String name = p.getFileName().toString();
                    // Underscore files are not box configs; _prices.json is
                    // packed into its own manifest slot below.
                    if (name.startsWith("_") && !PRICES_FILE.equals(name)) {
                        continue;
                    }
                    jsons.add(p);
                }
            }
            jsons.sort(Comparator.comparing(p -> p.getFileName().toString()));

            JsonObject files = new JsonObject();
            JsonObject broken = new JsonObject();
            JsonElement prices = null;
            for (Path p : jsons) {
                try {
                    if (Files.size(p) > MAX_FILE_BYTES) {
                        continue;
                    }
                    String text = Files.readString(p, StandardCharsets.UTF_8).trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    String name = p.getFileName().toString();
                    try {
                        JsonElement doc = JsonParser.parseString(text);
                        if (!doc.isJsonObject()) {
                            throw new IllegalStateException("not a JSON object");
                        }
                        if (PRICES_FILE.equals(name)) {
                            prices = doc;
                        } else {
                            files.add(name, doc);
                        }
                    } catch (RuntimeException e) {
                        // Carried verbatim so the player can fix it in the web
                        // editor's import dialog (the `_prices.json` slot only
                        // takes valid tables); oversized raw text is dropped
                        // to protect the shared fragment cap.
                        if (text.length() <= BROKEN_MAX_CHARS) {
                            broken.addProperty(name, text);
                        }
                    }
                } catch (IOException io) {
                    // Unreadable file: skip, the pack stays usable without it.
                }
            }
            String itemsText = joinIds(gameItems);
            String entitiesText = joinIds(gameEntities);
            if (files.size() == 0 && prices == null && broken.size() == 0
                    && itemsText.isEmpty() && entitiesText.isEmpty()) {
                return null;
            }

            JsonObject manifest = new JsonObject();
            manifest.addProperty("v", 1);
            if (mcVersion != null && !mcVersion.isEmpty()) {
                manifest.addProperty("mc", mcVersion);
            }
            manifest.add("files", files);
            if (broken.size() > 0) {
                manifest.add("broken", broken);
            }
            manifest.add("prices", prices);

            if (!itemsText.isEmpty() || !entitiesText.isEmpty()) {
                JsonObject withLists = manifest.deepCopy();
                if (!itemsText.isEmpty()) {
                    withLists.addProperty("items", itemsText);
                }
                if (!entitiesText.isEmpty()) {
                    withLists.addProperty("entities", entitiesText);
                }
                String enc = deflateBase64Url(GSON.toJson(withLists));
                if (!enc.isEmpty() && enc.length() <= maxEncodedChars) {
                    return baseUrl + "#pack=" + enc;
                }
                // Registry lists are a nice-to-have; over the cap (huge item
                // registries on big modpacks) the configs ship without them.
            }
            String enc = deflateBase64Url(GSON.toJson(manifest));
            if (enc.isEmpty() || enc.length() > maxEncodedChars) {
                return null;
            }
            return baseUrl + "#pack=" + enc;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Newline-joined registry ids: sorted, deduped, blank/embedded-newline
     *  entries dropped (the website splits on newline). */
    private static String joinIds(Iterable<String> ids) {
        if (ids == null) {
            return "";
        }
        TreeSet<String> sorted = new TreeSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank() && id.indexOf('\n') < 0) {
                sorted.add(id);
            }
        }
        return String.join("\n", sorted);
    }

    /** zlib-wrapped deflate (RFC 1950) + unpadded URL-safe Base64. */
    static String deflateBase64Url(String json) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            deflater.setInput(json.getBytes(StandardCharsets.UTF_8));
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                if (n <= 0) {
                    break;
                }
                out.write(buf, 0, n);
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
        } finally {
            deflater.end();
        }
    }
}
