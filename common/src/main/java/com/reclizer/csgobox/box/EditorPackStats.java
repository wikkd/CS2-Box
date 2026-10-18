package com.reclizer.csgobox.box;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lightweight summary of {@code config/csbox} for the {@code /csbox editor}
 * chat message: how many box files exist, how many item entries they hold and
 * how many referenced ids lack a price-table entry.
 *
 * <p>Pure Java + Gson — no Minecraft imports — so the identical source serves
 * all six platform commands (CONSTRAINT-001). Parsing is tolerant: unreadable
 * or broken files are skipped rather than failing the whole summary, because
 * this only feeds an informational chat line (the loaders do the strict
 * validation elsewhere).</p>
 */
public final class EditorPackStats {

    private EditorPackStats() {
    }

    /**
     * @param boxes    box JSON files found (underscore-prefixed helpers such as
     *                 {@code _prices.json} and tutorial files are excluded)
     * @param items    total grade entries across every box file
     * @param unpriced distinct item ids referenced with no price-table entry
     */
    public record Summary(int boxes, int items, int unpriced) {
        public boolean empty() {
            return boxes == 0;
        }
    }

    /** Scans {@code csboxDir}; a missing/unreadable folder yields an empty zero summary. */
    public static Summary summarize(Path csboxDir) {
        Set<String> priceKeys = loadPriceKeys(csboxDir);
        int boxes = 0;
        int items = 0;
        Set<String> ids = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.list(csboxDir)) {
            for (Path file : stream.sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".json") || name.startsWith("_")) {
                    continue;
                }
                try {
                    JsonElement root = JsonParser.parseString(Files.readString(file));
                    if (!root.isJsonObject()) {
                        continue;
                    }
                    boxes++;
                    JsonObject obj = root.getAsJsonObject();
                    for (int grade = 1; grade <= BoxGrades.GRADE_COUNT; grade++) {
                        JsonElement arr = obj.get("grade" + grade);
                        if (arr == null || !arr.isJsonArray()) {
                            continue;
                        }
                        for (JsonElement entry : arr.getAsJsonArray()) {
                            items++;
                            String id = itemId(entry);
                            if (id != null) {
                                ids.add(id);
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Broken file: the loaders report it; the stats line skips it.
                }
            }
        } catch (IOException e) {
            return new Summary(0, 0, 0);
        }
        int unpriced = 0;
        for (String id : ids) {
            if (!hasPrice(priceKeys, id)) {
                unpriced++;
            }
        }
        return new Summary(boxes, items, unpriced);
    }

    private static Set<String> loadPriceKeys(Path csboxDir) {
        Set<String> keys = new LinkedHashSet<>();
        Path prices = csboxDir.resolve(PriceTable.FILE_NAME);
        try {
            JsonElement root = JsonParser.parseString(Files.readString(prices));
            if (root.isJsonObject()) {
                keys.addAll(PriceTable.parse(root.getAsJsonObject(), new ArrayList<>()).entries().keySet());
            }
        } catch (Exception ignored) {
            // Missing/corrupt table: every id counts as unpriced (matches the
            // loaders' "no fallback price" rule).
        }
        return keys;
    }

    /** Priced when the table has the exact id or any {@code id#variant} sub-key. */
    private static boolean hasPrice(Set<String> priceKeys, String id) {
        if (priceKeys.contains(id)) {
            return true;
        }
        String prefix = id + "#";
        for (String key : priceKeys) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Extracts the item id from a grade entry: {@code "ns:id"} string or
     *  {@code {"id": "ns:id"}} object. Tags ({@code "#..."}), loot tables and
     *  malformed entries return null (never priced, never counted unpriced). */
    private static String itemId(JsonElement entry) {
        if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
            String s = entry.getAsString();
            return (!s.startsWith("#") && s.contains(":")) ? s : null;
        }
        if (entry.isJsonObject()) {
            JsonElement id = entry.getAsJsonObject().get("id");
            if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) {
                return id.getAsString();
            }
        }
        return null;
    }
}
