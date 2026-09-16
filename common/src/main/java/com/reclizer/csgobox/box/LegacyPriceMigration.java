package com.reclizer.csgobox.box;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One-time legacy migration for the terminal price system (v2.0.1):
 * pre-price-table box configs still carry a per-item {@code price} field
 * inside the grade arrays. This migrator is the "old-version adapter" that
 * transfers those prices into {@code config/csbox/_prices.json} and strips
 * them from the box files, so the removed field stops producing schema
 * errors and every terminal keeps its old pricing.
 *
 * <p>Rules (mirror of {@link PriceTable} semantics):</p>
 * <ul>
 *   <li>Key = the entry's {@code id}, or {@code id#variant} when the legacy
 *       NBT {@code tag} spells a {@code GunId} / {@code AmmoId} (TACZ).</li>
 *   <li>Same key seen in several box files (or in several entries) with
 *       <b>different</b> prices is migrated as the <b>round-half-up
 *       average</b> — the mod is no longer able to keep per-box price
 *       differences, so the average is the least surprising single value.</li>
 *   <li>An existing {@code _prices.json} entry is never overwritten: the
 *       author's table wins, migration only fills missing keys.</li>
 *   <li>Entries without a single item id ({@code #tag} item-tag references,
 *       {@code loot_table}) or with an invalid price (negative / fractional /
 *       non-numeric) are <b>left in place</b> so the validator keeps
 *       reporting them; only valid, keyable prices are transferred.</li>
 *   <li>Malformed {@code _prices.json} aborts the whole migration (nothing is
 *       written, boxes are not stripped) so a broken table can never lose
 *       prices.</li>
 * </ul>
 *
 * <p>Idempotent: after a successful pass the box files no longer contain
 * {@code price}, so re-runs are no-ops. Atomic temp-file + move writes are
 * used for both the table and every rewritten box. Runs on the server load /
 * reload path (after the legacy terminal migration, before the price table is
 * read); the dry-run {@code /csbox validate} never invokes it.</p>
 */
public final class LegacyPriceMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyPriceMigration.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Outcome of one migration pass; all counters are 0 on a no-op. */
    public record Result(
            int migratedKeys,
            int averagedKeys,
            int boxesRewritten,
            List<String> warnings
    ) {
        public boolean didWork() {
            return migratedKeys > 0 || boxesRewritten > 0;
        }
    }

    private LegacyPriceMigration() {
    }

    /**
     * Scans {@code boxesDir} for legacy {@code price} fields and transfers
     * them into {@code config/csbox/_prices.json} (missing keys only, conflict
     * prices averaged). Box files that yielded valid migrated prices are
     * rewritten without those fields. Never throws; every failure is logged
     * and surfaced through the returned warnings.
     */
    public static synchronized Result migrateLegacyPrices(Path boxesDir) {
        List<String> warnings = new ArrayList<>();
        if (boxesDir == null || !Files.isDirectory(boxesDir)) {
            return new Result(0, 0, 0, warnings);
        }

        // 1) collect legacy prices and remember which item cells to strip.
        Map<String, List<Integer>> pricesByKey = new TreeMap<>();
        Map<Path, List<int[]>> stripRefsByFile = new LinkedHashMap<>();
        List<Path> scanned = scanBoxFiles(boxesDir);
        for (Path file : scanned) {
            collectLegacyPrices(file, pricesByKey, stripRefsByFile, warnings);
        }
        if (pricesByKey.isEmpty()) {
            return new Result(0, 0, 0, warnings);
        }

        // 2) existing table (authoritative for keys it already has).
        Path tablePath = boxesDir.resolve(PriceTable.FILE_NAME);
        Map<String, PriceRange> table = new TreeMap<>();
        if (Files.exists(tablePath)) {
            if (!loadExistingTable(tablePath, table, warnings)) {
                // Broken table: refuse to move prices out of the boxes they
                // still own. Nothing is written.
                return new Result(0, 0, 0, warnings);
            }
        }

        // 3) average conflicts and merge.
        int migratedKeys = 0;
        int averagedKeys = 0;
        for (Map.Entry<String, List<Integer>> e : pricesByKey.entrySet()) {
            String key = e.getKey();
            List<Integer> values = e.getValue();
            long sum = 0;
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int v : values) {
                sum += v;
                if (v < min) min = v;
                if (v > max) max = v;
            }
            int average = (int) Math.round(sum / (double) values.size());
            boolean conflicted = min != max;
            if (table.containsKey(key)) {
                warnings.add("保留 _prices.json 已有价格 '" + key + "' = " + table.get(key).toDisplayString()
                        + "（旧配置 " + values.size() + " 处价格"
                        + (conflicted ? "，平均 " + average : "") + " 未覆盖；"
                        + "箱子内的残留 price 字段仍会被移除）");
                continue;
            }
            table.put(key, PriceRange.fixed(average));
            migratedKeys++;
            if (conflicted) {
                averagedKeys++;
                warnings.add("'" + key + "' 在 " + values.size() + " 处价格不同（"
                        + min + "~" + max + "），已按平均值迁移为 " + average);
            }
        }

        // 4) persist the merged table first (when there is anything new); only
        // on success strip boxes so a failed table write can never orphan the
        // old prices. Keys already present in the table need no write — the
        // box fields are still stripped because the table now owns the price.
        if (migratedKeys > 0 && !writeTable(tablePath, table, warnings)) {
            return new Result(0, 0, 0, warnings);
        }

        // 5) rewrite the boxes that contributed valid prices.
        int boxesRewritten = 0;
        for (Map.Entry<Path, List<int[]>> e : stripRefsByFile.entrySet()) {
            if (stripLegacyPrices(e.getKey(), e.getValue(), warnings)) {
                boxesRewritten++;
            }
        }

        LOGGER.info("Legacy price migration: {} key(s) added to {} ({} with conflicting"
                        + " box prices averaged), {} box file(s) rewritten",
                migratedKeys, PriceTable.FILE_NAME, averagedKeys, boxesRewritten);
        return new Result(migratedKeys, averagedKeys, boxesRewritten, warnings);
    }

    /** Non-underscore {@code *.json} box files, in stable order. */
    private static List<Path> scanBoxFiles(Path boxesDir) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(boxesDir, "*.json")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if (name.startsWith("_")) {
                    continue;
                }
                files.add(file);
            }
        } catch (IOException e) {
            LOGGER.warn("Legacy price migration: failed to list {}: {}", boxesDir, e.getMessage());
        }
        files.sort(Path::compareTo);
        return files;
    }

    /**
     * Reads one box file; for every valid, keyable legacy {@code price} it
     * records the price under its table key and registers the item cell for
     * stripping. Invalid / unkeyable prices are left untouched.
     */
    private static void collectLegacyPrices(Path file,
                                            Map<String, List<Integer>> pricesByKey,
                                            Map<Path, List<int[]>> stripRefsByFile,
                                            List<String> warnings) {
        JsonObject json;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return;
            }
            json = GSON.fromJson(text, JsonObject.class);
        } catch (JsonSyntaxException e) {
            warnings.add(file + ": 语法错误，跳过旧价格迁移（保留原位）");
            return;
        } catch (IOException e) {
            warnings.add(file + ": 无法读取，跳过旧价格迁移: " + e.getMessage());
            return;
        }
        if (json == null) {
            return;
        }

        List<int[]> stripRefs = null;
        for (int g = 1; g <= 5; g++) {
            String gradeKey = "grade" + g;
            if (!json.has(gradeKey) || !json.get(gradeKey).isJsonArray()) {
                continue;
            }
            JsonArray items = json.getAsJsonArray(gradeKey);
            for (int i = 0; i < items.size(); i++) {
                JsonElement elem = items.get(i);
                if (!elem.isJsonObject() || !elem.getAsJsonObject().has("price")) {
                    continue;
                }
                JsonObject item = elem.getAsJsonObject();
                Integer price = parseValidPrice(item.get("price"), file, gradeKey, i, warnings);
                if (price == null) {
                    continue;
                }
                String key = tableKey(item, file, gradeKey, i, warnings);
                if (key == null) {
                    continue;
                }
                pricesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(price);
                if (stripRefs == null) {
                    stripRefs = new ArrayList<>();
                    stripRefsByFile.put(file, stripRefs);
                }
                stripRefs.add(new int[]{g, i});
            }
        }
    }

    /** Legacy price must be a non-negative integer; anything else stays put. */
    private static Integer parseValidPrice(JsonElement price, Path file,
                                           String gradeKey, int index,
                                           List<String> warnings) {
        if (!price.isJsonPrimitive() || !price.getAsJsonPrimitive().isNumber()) {
            warnings.add(file + ": " + gradeKey + "[" + index + "].price 非数字，"
                    + "无法自动迁移（保留原位，需手动处理）");
            return null;
        }
        double v = price.getAsDouble();
        if (v < 0 || v != Math.floor(v)) {
            warnings.add(file + ": " + gradeKey + "[" + index + "].price=" + v
                    + " 非法（需非负整数），无法自动迁移（保留原位）");
            return null;
        }
        return (int) v;
    }

    /**
     * Price-table key of a legacy item entry: its {@code id}, or
     * {@code id#variant} for legacy NBT variants (TACZ GunId/AmmoId).
     * Entries without a concrete item id ({@code #tag}, {@code loot_table})
     * cannot be keyed and are left in place.
     */
    private static String tableKey(JsonObject item, Path file,
                                   String gradeKey, int index,
                                   List<String> warnings) {
        JsonElement idElem = item.get("id");
        if (idElem == null || !idElem.isJsonPrimitive() || !idElem.getAsJsonPrimitive().isString()) {
            warnings.add(file + ": " + gradeKey + "[" + index
                    + "] 无唯一物品 id（#tag / loot_table / 非法 id），无法自动迁移其 price");
            return null;
        }
        String id = idElem.getAsString();
        String variant = PriceTable.variantId(item).orElse(null);
        return variant == null || variant.isEmpty() ? id : id + "#" + variant;
    }

    /** Reads an existing price table into {@code table}. Returns false (and a
     *  warning) when the file is missing-invalid, in which case the caller
     *  must abort so a broken table never causes price loss. */
    private static boolean loadExistingTable(Path tablePath, Map<String, PriceRange> table,
                                             List<String> warnings) {
        try {
            JsonObject json = GSON.fromJson(Files.readString(tablePath, StandardCharsets.UTF_8),
                    JsonObject.class);
            if (json == null) {
                warnings.add("无法自动迁移：_prices.json 顶层不是 JSON 对象，请先修复");
                return false;
            }
            List<String> issues = new ArrayList<>();
            PriceTable parsed = PriceTable.parse(json, issues);
            if (!issues.isEmpty()) {
                warnings.add("无法自动迁移：_prices.json 存在非法条目（"
                        + issues.get(0) + "），请先修复/删除后再重载");
                return false;
            }
            for (Map.Entry<String, PriceRange> e : parsed.entries().entrySet()) {
                table.put(e.getKey(), e.getValue());
            }
            return true;
        } catch (JsonSyntaxException e) {
            warnings.add("无法自动迁移：_prices.json 语法损坏，请先修复后再重载");
            return false;
        } catch (IOException e) {
            warnings.add("无法自动迁移：无法读取 _prices.json: " + e.getMessage());
            return false;
        }
    }

    private static boolean writeTable(Path tablePath, Map<String, PriceRange> table,
                                      List<String> warnings) {
        JsonObject out = new JsonObject();
        for (Map.Entry<String, PriceRange> e : table.entrySet()) {
            PriceRange r = e.getValue();
            if (r.isFixed()) {
                out.addProperty(e.getKey(), r.min());
            } else {
                JsonArray arr = new JsonArray();
                arr.add(r.min());
                arr.add(r.max());
                out.add(e.getKey(), arr);
            }
        }
        try {
            atomicWrite(tablePath, GSON.toJson(out) + "\n");
            LOGGER.info("Wrote {} entries to {}", table.size(), tablePath);
            return true;
        } catch (IOException e) {
            warnings.add("无法写入价格表 " + tablePath + "，旧价格未迁移: " + e.getMessage());
            return false;
        }
    }

    /** Re-reads the box file and removes the previously located price cells. */
    private static boolean stripLegacyPrices(Path file, List<int[]> stripRefs,
                                             List<String> warnings) {
        try {
            JsonObject json = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    JsonObject.class);
            if (json == null) {
                warnings.add(file + ": 重读为空，跳过改写");
                return false;
            }
            int removed = 0;
            for (int[] ref : stripRefs) {
                String gradeKey = "grade" + ref[0];
                if (!json.has(gradeKey) || !json.get(gradeKey).isJsonArray()) {
                    continue;
                }
                JsonArray items = json.getAsJsonArray(gradeKey);
                if (ref[1] >= items.size() || !items.get(ref[1]).isJsonObject()) {
                    continue;
                }
                if (items.get(ref[1]).getAsJsonObject().remove("price") != null) {
                    removed++;
                }
            }
            if (removed == 0) {
                return false;
            }
            atomicWrite(file, GSON.toJson(json));
            LOGGER.info("Rewrote {} (stripped {} legacy price field(s))", file, removed);
            return true;
        } catch (JsonSyntaxException e) {
            warnings.add(file + ": 重读时语法错误，未改写（价格已在表中生效）");
            return false;
        } catch (IOException e) {
            warnings.add(file + ": 改写失败（价格已在表中生效，残留 price 会继续报错）: "
                    + e.getMessage());
            return false;
        }
    }

    /** Temp-file + atomic (or fallback replace) write; never leaves a *.tmp. */
    private static void atomicWrite(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}