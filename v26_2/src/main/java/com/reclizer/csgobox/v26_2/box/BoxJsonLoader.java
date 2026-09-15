package com.reclizer.csgobox.v26_2.box;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxJsonSchemaValidator;
import com.reclizer.csgobox.box.PriceRange;
import com.reclizer.csgobox.box.PriceTable;
import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.box.LegacyPriceMigration;
import com.reclizer.csgobox.box.LegacyPriceMigration;
import com.reclizer.csgobox.v26_2.CsgoBox;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and writes box definitions under config/csbox. Item parsing is in
 *  {@link BoxItemCodec}, default config generation in {@link BoxDefaults};
 *  this class owns directory I/O, top-level JSON shape and registration. */
public final class BoxJsonLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path BOXES_DIR = FMLPaths.CONFIGDIR.get().resolve("csbox");

    private static final String[] GRADE_IDS = {"classified", "restricted", "mil_spec", "industrial", "consumer"};
    private static final String[] GRADE_NAMES = {"保密", "受限", "军规级", "工业级", "消费级"};
    private static final int[] GRADE_COLORS = {0xFFD32CE6, 0xFF8847FF, 0xFF4B69FF, 0xFF5E98D9, 0xFFB0C3D9};

    /**
     * Load diagnostics collected during loadAll/reloadPreserving. Written by
     * the file watcher thread (reload) and the server thread (/csbox reload),
     * so a CopyOnWriteArrayList keeps it safe under that cross-thread access.
     */
    private static final List<LoadError> LAST_LOAD_ERRORS = new CopyOnWriteArrayList<>();

    /**
     * Parse cache: file-name -> content-hash -> result/errors. Reloads skip
     * files whose SHA-256 is unchanged. Never persists across restarts — the
     * cached {@link BoxDefinition}/{@link ItemStack}s reference registry
     * objects rebuilt per launch.
     */
    private static final ConcurrentHashMap<String, CachedFile> PARSED_CACHE = new ConcurrentHashMap<>();

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    });

    /**
     * Background executor for the startup tutorial download: network timeouts
     * must not block world start. Failure-safe — a shutdown mid-download just
     * leaves the tutorial missing and the next launch retries.
     */
    private static final ExecutorService TUTORIAL_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "csgobox-tutorial");
        t.setDaemon(true);
        return t;
    });

    /**
     * Downloads the version-stamped tutorial markdown files into
     * {@code config/csbox/} on the background {@link #TUTORIAL_EXECUTOR}.
     * Called by the server startup path ({@link #loadAll()}) and by the
     * client setup path ({@code CsgoBox.ClientModEvents#onClientSetup}) so
     * dedicated-server players also get a local copy. Same-JVM duplicate
     * calls are safe: the executor is single-threaded and
     * {@link BoxDefaults#writeTutorialIfMissing(Path)} is synchronized and
     * idempotent.
     */
    public static void downloadTutorialsAsync() {
        TUTORIAL_EXECUTOR.execute(() -> BoxDefaults.writeTutorialIfMissing(BOXES_DIR));
    }

    /** Hash -> parse result; empty {@code definition} = failed parse with diagnostics.
     *  {@code tableHash} is the price-table hash that produced this result, so a
     *  change to {@code config/csbox/_prices.json} invalidates the cached box. */
    private record CachedFile(String hash, String tableHash, Optional<BoxDefinition> definition, List<LoadError> errors) {}

    /** Matches an optional leading hex color in the box "name" field, e.g.
     *  {@code "#FF5555 高级补给箱"}. Group 1 = 6 hex digits, group 2 = display text. */
    private static final Pattern NAME_COLOR_PREFIX =
            Pattern.compile("^#([0-9A-Fa-f]{6}) (.*)$");

    private BoxJsonLoader() {
    }

    /**
     * Lightweight read of a box JSON's {@code type} field without parsing the
     * full definition (registry lookups, DataComponent decode). Used by dynamic
     * item registration to pick {@code ItemTerminal} for terminal-type boxes
     * before definitions are loaded into {@link BoxRegistry}. Defaults to
     * {@code "csbox"} so a malformed file degrades to a regular box item.
     */
    public static String readType(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null && json.has("type") && json.get("type").isJsonPrimitive()) {
                return json.get("type").getAsString();
            }
        } catch (Exception e) {
            CsgoBox.LOGGER.warn("Failed to read type from {}: {}", file, e.getMessage());
        }
        return "csbox";
    }

    /** Parsed result of a box "name" value: display text + optional 0xRRGGBB color. */
    private record ParsedName(String text, OptionalInt color) {}

    /** Strips an optional {@code "#RRGGBB "} prefix; returns text + color, or the raw string without color. */
    private static ParsedName parseColoredName(String raw) {
        if (raw == null) return new ParsedName("", OptionalInt.empty());
        Matcher m = NAME_COLOR_PREFIX.matcher(raw);
        if (!m.matches()) return new ParsedName(raw, OptionalInt.empty());
        int rgb = Integer.parseInt(m.group(1), 16);
        String text = m.group(2);
        if (text.isEmpty()) {
            CsgoBox.LOGGER.warn("Box name has color prefix but empty text: '{}'", raw);
            return new ParsedName("", OptionalInt.empty());
        }
        return new ParsedName(text, OptionalInt.of(rgb));
    }

    public static void loadAll() {
        LAST_LOAD_ERRORS.clear();

        if (!Files.exists(BOXES_DIR)) {
            try {
                Files.createDirectories(BOXES_DIR);
            } catch (IOException e) {
                CsgoBox.LOGGER.error("Failed to create boxes config directory: {}", BOXES_DIR, e);
                return;
            }
            CsgoBox.LOGGER.info("Created boxes config directory: {}", BOXES_DIR);
        }

        // Pre-v2.0.0 terminal.json migration (no "type" field); must run before parsing.
        BoxDefaults.upgradeLegacyTerminalConfig(BOXES_DIR);

        // v2.1.0+: old-version adapter — transfer legacy per-item "price"
        // fields into _prices.json (conflict prices averaged), then strip
        // them from the box files so the removed field stops erroring.
        LegacyPriceMigration.migrateLegacyPrices(BOXES_DIR);

        // v2.1.0+: the central terminal price table is read once per scan and
        // baked into every box's GradeGroup prices.
        PriceTable priceTable = loadPriceTable();
        PriceTableRegistry.set(priceTable);
        if (!priceTable.isEmpty()) {
            CsgoBox.LOGGER.info("Loaded {} price(s) from {}",
                    priceTable.size(), PriceTable.FILE_NAME);
        }

        // Background download: network timeouts must not block the server thread.
        TUTORIAL_EXECUTOR.execute(() -> BoxDefaults.writeTutorialIfMissing(BOXES_DIR));

        List<Path> scannedFiles = new ArrayList<>();
        int[] loaded = {0};
        int[] skipped = {0};
        try {
            forEachBoxJson(file -> {
                String fileName = file.getFileName().toString();
                scannedFiles.add(file);
                try {
                    Optional<BoxDefinition> result = loadFromFile(file, priceTable);
                    if (result.isEmpty()) {
                        // loadFromFile already logged the per-grade or per-item reason.
                        skipped[0]++;
                        return;
                    }
                    BoxRegistry.register(result.get());
                    loaded[0]++;
                    CsgoBox.LOGGER.info("Loaded box from JSON: {} -> {}", file.getFileName(), result.get().id());
                } catch (Exception e) {
                    CsgoBox.LOGGER.error("Failed to load box JSON file: {}", file, e);
                    skipped[0]++;
                    recordLoadError(file, fileName, "Failed to load box JSON: " + e.getMessage());
                }
            });
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to list box JSON files in {}", BOXES_DIR, e);
        }

        CsgoBox.LOGGER.info(
                "Scanned {} JSON file(s) in {}; loaded {}, skipped {}",
                scannedFiles.size(), BOXES_DIR, loaded[0], skipped[0]);
    }

    /**
     * Re-scan the box directory and update {@link BoxRegistry} in place
     * without {@code clear()}: failed files keep their previous definition,
     * successful ones overwrite by id, files gone from disk are removed.
     * Used by {@code /csbox reload} and {@code BoxFileWatcher}. Never
     * resurrects the deleted sample tutorial, but re-runs the legacy
     * terminal migration (the terminal ships unconfigured since 2.0.0 —
     * no default terminal.json is ever written).
     */
    public static void reloadPreserving() {
        LAST_LOAD_ERRORS.clear();

        if (!Files.exists(BOXES_DIR)) {
            try {
                Files.createDirectories(BOXES_DIR);
            } catch (IOException e) {
                CsgoBox.LOGGER.error("Failed to create boxes config directory: {}", BOXES_DIR, e);
                return;
            }
            CsgoBox.LOGGER.info("Created boxes config directory: {}", BOXES_DIR);
        }
        BoxDefaults.upgradeLegacyTerminalConfig(BOXES_DIR);

        // v2.1.0+: old-version adapter — transfer legacy per-item "price"
        // fields into _prices.json (conflict prices averaged), then strip
        // them from the box files so the removed field stops erroring.
        LegacyPriceMigration.migrateLegacyPrices(BOXES_DIR);

        // v2.1.0+: central price table re-read on every reload, so editing
        // _prices.json picks up immediately (cache entries are keyed by hash).
        PriceTable priceTable = loadPriceTable();
        PriceTableRegistry.set(priceTable);

        Set<Identifier> previousIds = new HashSet<>(BoxRegistry.getIds());
        Set<Identifier> seenIds = new HashSet<>();
        int[] loaded = {0};
        int[] skipped = {0};

        try {
            forEachBoxJson(file -> {
                String fileName = file.getFileName().toString();
                String boxIdStr = fileName.substring(0, fileName.length() - 5);
                Identifier boxId;
                try {
                    boxId = Identifier.fromNamespaceAndPath(CsgoBox.MODID, boxIdStr);
                } catch (Exception e) {
                    CsgoBox.LOGGER.error("Invalid box id from filename {}: {}", file, e.getMessage());
                    recordLoadError(file, fileName, "Invalid identifier: " + e.getMessage());
                    skipped[0]++;
                    return;
                }
                seenIds.add(boxId);
                try {
                    Optional<BoxDefinition> result = loadFromFile(file, priceTable);
                    if (result.isPresent()) {
                        BoxRegistry.register(result.get());
                        loaded[0]++;
                        CsgoBox.LOGGER.info("Reloaded box from JSON: {} -> {}", fileName, result.get().id());
                    } else {
                        // loadFromFile already logged the per-grade or per-item reason.
                        skipped[0]++;
                    }
                } catch (Exception e) {
                    CsgoBox.LOGGER.error("Failed to load box JSON file: {}", file, e);
                    skipped[0]++;
                    recordLoadError(file, fileName, "Failed to load box JSON: " + e.getMessage());
                }
            });
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to list box JSON files in {}", BOXES_DIR, e);
            return;
        }

        Set<Identifier> toRemove = new HashSet<>(previousIds);
        toRemove.removeAll(seenIds);
        int removed = 0;
        for (Identifier id : toRemove) {
            BoxRegistry.remove(id);
            // v2.1.0-fix: drop the parse cache entry for the deleted file so a
            // later re-creation of the same file name cannot serve a stale hash.
            PARSED_CACHE.remove(id.getPath() + ".json");
            removed++;
            CsgoBox.LOGGER.info("Removed box no longer present in config: {}", id);
        }

        CsgoBox.LOGGER.info(
                "Reload preserving: scanned {} (of {} previously registered); loaded {}, skipped {}, removed {}",
                seenIds.size(), previousIds.size(), loaded[0], skipped[0], removed);
    }

    public static List<LoadError> getLastLoadErrors() {
        return Collections.unmodifiableList(LAST_LOAD_ERRORS);
    }

    /**
     * v2.1.0 dry-run validation (backing {@code /csbox validate}): parses a
     * box JSON file WITHOUT registering anything or touching the parse cache,
     * so an author can iterate on a config without changing live state.
     * Returns the produced diagnostics; {@code -1} for both line/column means
     * "no JSON position available". A result with {@code ok == false} must not
     * be loaded.
     */
    public static ValidateResult validateFile(Path file) {
        String fileName = file.getFileName().toString();
        int before = LAST_LOAD_ERRORS.size();
        Optional<BoxDefinition> result;
        try {
            byte[] bytes = Files.readAllBytes(file);
            result = parseFromBytes(bytes, file, loadPriceTable());
        } catch (IOException e) {
            recordLoadError(file, fileName, "Cannot read file: " + e.getMessage());
            result = Optional.empty();
        }
        List<LoadError> produced = new ArrayList<>(
                LAST_LOAD_ERRORS.subList(before, LAST_LOAD_ERRORS.size()));
        boolean ok = result.isPresent();
        // Roll back the diagnostics so a dry run never pollutes the live
        // error list shown by /csbox info error.
        for (int i = LAST_LOAD_ERRORS.size() - 1; i >= before; i--) {
            LAST_LOAD_ERRORS.remove(i);
        }
        return new ValidateResult(ok, result.orElse(null), produced);
    }

    /** Result of a dry-run validation: whether the file parsed, the definition
     *  (null on failure) and the diagnostics that would be reported. */
    public record ValidateResult(boolean ok, BoxDefinition definition, List<LoadError> errors) {
    }

    public static boolean hasLoadErrors() {
        return !LAST_LOAD_ERRORS.isEmpty();
    }

    private static void recordLoadError(Path file, String fileName, String reason, int line, int column) {
        String boxId = fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
        LAST_LOAD_ERRORS.add(new LoadError(file, boxId, reason, line, column));
    }

    /** No-JSON-position convenience overload. */
    private static void recordLoadError(Path file, String fileName, String reason) {
        recordLoadError(file, fileName, reason, -1, -1);
    }

    /** Non-fatal diagnostics (partially dropped components, migrated formats):
     *  kept items surface via the same error listing, marked as warnings. */
    private static void recordLoadWarning(Path file, String fileName, String reason) {
        String boxId = fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5) : fileName;
        LAST_LOAD_ERRORS.add(new LoadError(file, boxId, reason, -1, -1, true));
    }

    /** Extracts {@code at line N column M} from a Gson error message (Gson 2.13+
     *  removed {@code getLocation()}); {@code {-1,-1}} when not found. */
    private static final java.util.regex.Pattern GSON_LOCATION_PATTERN =
            java.util.regex.Pattern.compile("at line (\\d+) column (\\d+)");

    private static int[] parseLocationFromMessage(String message) {
        java.util.regex.Matcher m = GSON_LOCATION_PATTERN.matcher(message);
        if (m.find()) {
            return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        return new int[]{-1, -1};
    }

    /** Feeds every non-underscore {@code .json} in {@link #BOXES_DIR} to
     *  {@code action}; underscore-prefixed files are mod metadata, never boxes. */
    private static void forEachBoxJson(Consumer<Path> action) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(BOXES_DIR, "*.json")) {
            for (Path file : stream) {
                if (file.getFileName().toString().startsWith("_")) {
                    continue;
                }
                action.accept(file);
            }
        }
    }

    private static Optional<BoxDefinition> loadFromFile(Path file, PriceTable priceTable) throws IOException {
        String fileName = file.getFileName().toString();

        byte[] bytes = Files.readAllBytes(file);
        String hash = sha256Hex(bytes);

        CachedFile cached = PARSED_CACHE.get(fileName);
        if (cached != null && cached.hash().equals(hash)
                && cached.tableHash().equals(priceTable.hash())) {
            // Unchanged (box content and price table): reuse the cached parse
            // and its diagnostics.
            LAST_LOAD_ERRORS.addAll(cached.errors());
            return cached.definition();
        }

        int errorsBefore = LAST_LOAD_ERRORS.size();
        Optional<BoxDefinition> result = parseFromBytes(bytes, file, priceTable);
        List<LoadError> produced = new ArrayList<>(
                LAST_LOAD_ERRORS.subList(errorsBefore, LAST_LOAD_ERRORS.size()));
        PARSED_CACHE.put(fileName, new CachedFile(hash, priceTable.hash(), result, produced));
        return result;
    }

    private static String sha256Hex(byte[] bytes) {
        MessageDigest digest = SHA256.get();
        byte[] out = digest.digest(bytes);
        StringBuilder sb = new StringBuilder(out.length * 2);
        for (byte b : out) {
            sb.append(HEX_DIGITS[(b >> 4) & 0xF]).append(HEX_DIGITS[b & 0xF]);
        }
        return sb.toString();
    }

    private static Optional<BoxDefinition> parseFromBytes(byte[] bytes, Path file, PriceTable priceTable) {
        String fileName = file.getFileName().toString();
        String boxIdStr = fileName.substring(0, fileName.length() - 5);

        // v2.1.0: validate the file name before it becomes the box id — an
        // identifier must be lowercase [a-z0-9_./-]; anything else (spaces,
        // uppercase, CJK, #...) would either crash Identifier.parse or produce
        // a silently different id. Underscore-prefixed files are mod metadata
        // and never reach here.
        if (!boxIdStr.matches("[a-z0-9_./-]+")) {
            String msg = "Invalid box id from file name '" + boxIdStr
                    + "' — use only lowercase letters, digits, '_', '.', '/' or '-' "
                    + "(e.g. weapon_supply_box.json)";
            CsgoBox.LOGGER.error("Skipping {}: {}", file, msg);
            recordLoadError(file, fileName, msg);
            return Optional.empty();
        }

        JsonObject json;
        try {
            json = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
        } catch (JsonSyntaxException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "unknown syntax error";
            int[] lc = parseLocationFromMessage(msg);
            CsgoBox.LOGGER.error("Invalid JSON syntax in {}: {}", file, msg);
            recordLoadError(file, fileName, "Invalid JSON syntax: " + msg, lc[0], lc[1]);
            return Optional.empty();
        }
        if (json == null) return Optional.empty();

        // Schema issues surface as LoadError entries (diagnostic, not load-blocking); field fallback still runs below.
        for (BoxJsonSchemaValidator.SchemaIssue issue : BoxJsonSchemaValidator.validate(json)) {
            CsgoBox.LOGGER.warn("Schema issue in {} field {}: {}",
                    file, issue.field(), issue.reason());
            recordLoadError(file, fileName,
                    "Schema: " + issue.field() + " — " + issue.reason());
        }

        try {
            // v2.1.0 gating: "enabled":false skips cleanly (no error, the
            // author chose to disable it); "requires" skips with a clear
            // "missing dependency" error when any mod is absent. Only a real
            // boolean is honoured — a string "false" is not silently coerced.
            if (json.has("enabled") && json.get("enabled").isJsonPrimitive()
                    && json.get("enabled").getAsJsonPrimitive().isBoolean()
                    && !json.get("enabled").getAsBoolean()) {
                CsgoBox.LOGGER.info("Skipping disabled box config: {}", file);
                return Optional.empty();
            }
            if (json.has("requires")) {
                for (JsonElement req : json.get("requires").getAsJsonArray()) {
                    String modId = req.getAsString();
                    if (!CsgoBox.isModLoaded(modId)) {
                        String msg = "Skipped: required mod '" + modId
                                + "' is not installed (requires: [" + modId + "])";
                        CsgoBox.LOGGER.warn("{} for {}", msg, file);
                        recordLoadError(file, fileName, msg);
                        return Optional.empty();
                    }
                }
            }

            ParsedName parsedName = parseColoredName(getString(json, "name", boxIdStr));
            String type = getString(json, "type", "csbox");
            if (!"csbox".equals(type) && !"terminal".equals(type)) {
                type = "csbox";
            }
            // v2.0.0: "type" is the single source of truth; a terminal.json
            // without "type":"terminal" would silently become a keyless
            // crate, so refuse to load it.
            if (boxIdStr.equals("terminal") && !"terminal".equals(type)) {
                String msg = "terminal.json must declare \"type\": \"terminal\" "
                        + "(v2.0.0+: type is the single registration source; terminals have no key field)";
                CsgoBox.LOGGER.error("Skipping {}: {}", file, msg);
                recordLoadError(file, fileName, msg);
                return Optional.empty();
            }
            float dropRate = getFloat(json, "drop", 0.12F);

            int[] weights = parseWeights(json, file, fileName);

            List<Identifier> dropEntityIds = new ArrayList<>();
            Map<Identifier, Float> entityDropRates = new HashMap<>();
            parseEntities(json, dropEntityIds, entityDropRates, file, fileName);

            List<GradeGroup> grades = new ArrayList<>();
            boolean unpricedRejected = false;
            for (int i = 0; i < 5; i++) {
                String gradeKey = "grade" + (5 - i);
                if (json.has(gradeKey)) {
                    JsonArray itemsArr = json.getAsJsonArray(gradeKey);
                    List<ItemStack> items = new ArrayList<>();
                    List<PriceRange> prices = new ArrayList<>();
                    List<Integer> itemWeights = new ArrayList<>();
                    for (JsonElement elem : itemsArr) {
                        BoxItemCodec.ParseOutcome outcome = BoxItemCodec.parseItem(elem);
                        if (outcome.isSuccess()) {
                            for (String warning : outcome.warnings()) {
                                recordLoadWarning(file, fileName, "Item: " + warning);
                            }
                            items.addAll(outcome.stacks());
                            itemWeights.addAll(outcome.weights());
                            // v2.1.0+: prices come from the central price
                            // table (config/csbox/_prices.json), never from
                            // box JSON. UNPRICED = no price at all — the
                            // grade-default fallback was removed, so any
                            // unpriced id entry rejects the whole box.
                            boolean isLootTable = elem.isJsonObject()
                                    && elem.getAsJsonObject().has("loot_table");
                            List<PriceRange> resolved = resolvePrices(elem, outcome, priceTable);
                            if (!isLootTable) {
                                for (PriceRange r : resolved) {
                                    if (r == null || r.isUnpriced()) {
                                        unpricedRejected = true;
                                        recordLoadError(file, fileName,
                                                "物品未定价：该物品在 " + PriceTable.FILE_NAME
                                                        + " 中没有价格（表外默认价回退已移除，终端机不售卖、"
                                                        + "拆解回收为 0，请先在价格表补价再加载）");
                                        break;
                                    }
                                }
                            } else {
                                recordLoadWarning(file, fileName,
                                        "loot_table 条目没有固定物品 id，无法定价——不会出现在终端机报价中"
                                                + "（开箱掉落不受影响）");
                            }
                            prices.addAll(resolved);
                        } else {
                            recordLoadError(file, fileName,
                                    "Item: " + outcome.error());
                        }
                    }
                    if (!items.isEmpty()) {
                        grades.add(new GradeGroup(GRADE_IDS[i], GRADE_NAMES[i], GRADE_COLORS[i],
                                weights[4 - i], items, prices, itemWeights));
                    } else {
                        recordLoadWarning(file, fileName,
                                "Grade " + gradeKey + " has no valid items — the tier will fall back "
                                        + "to lower grades when rolled (empty tiers are invisible to players)");
                    }
                }
            }

            if (unpricedRejected) {
                return Optional.empty();
            }

            if (grades.isEmpty()) {
                CsgoBox.LOGGER.warn("Skipping box '{}': all items failed to parse (missing mods?)", boxIdStr);
                recordLoadError(file, fileName,
                        "All items failed to parse (missing mods?)");
                return Optional.empty();
            }

            BoxDefinition.Builder builder = BoxDefinition.builder(
                    Identifier.parse("csgobox:" + boxIdStr), parsedName.text());
            parsedName.color().ifPresent(builder::nameColor);
            builder.type(type);
            if (!"terminal".equals(type)) {
                builder.key(parseIdentifierSafe(getString(json, "key", "csgobox:csgo_key0"), "key"));
            }
            builder.dropRate(dropRate);
            for (Identifier entityId : dropEntityIds) {
                Float rate = entityDropRates.get(entityId);
                if (rate != null) {
                    builder.entityDropRate(entityId.toString(), rate);
                }
                builder.dropFrom(entityId.toString());
            }
            for (GradeGroup grade : grades) {
                builder.addGrade(grade);
            }
            // v2.1.0 config fields: gating (enabled/requires), icon, terminal
            // economy (discount/stock/restock), open constraints.
            if (json.has("icon")) {
                String icon = getString(json, "icon", "");
                if (!icon.isBlank()) {
                    builder.icon(icon);
                }
            }
            if (json.has("discount")) {
                builder.discount(getFloat(json, "discount", 0.0F));
            }
            if (json.has("stock")) {
                builder.stock(getInt(json, "stock", BoxDefinition.UNLIMITED));
            }
            if (json.has("restock_minutes")) {
                builder.restockMinutes(getInt(json, "restock_minutes", 0));
            }
            if (json.has("max_per_player")) {
                builder.maxPerPlayer(getInt(json, "max_per_player", BoxDefinition.UNLIMITED));
            }
            if (json.has("cooldown_seconds")) {
                builder.cooldownSeconds(getInt(json, "cooldown_seconds", 0));
            }
            if (json.has("permission")) {
                builder.permission(getString(json, "permission", ""));
            }

            return Optional.of(builder.build());
        } catch (IllegalArgumentException e) {
            CsgoBox.LOGGER.error("Invalid identifier in {}: {}", file, e.getMessage());
            recordLoadError(file, fileName,
                    "Invalid identifier: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Parses an {@link Identifier}, normalizing any exception to
     *  {@link IllegalArgumentException} (the MC exception type varies by version). */
    private static Identifier parseIdentifierSafe(String value, String fieldName) {
        try {
            return Identifier.parse(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "' = \"" + value + "\": " + e.getMessage(), e);
        }
    }

    /**
     * JSON "random" is ordered grade1 -> grade5.
     */
    private static int[] parseWeights(JsonObject json, Path file, String fileName) {
        int[] weights = BoxGrades.DEFAULT_WEIGHTS.clone();
        if (json.has("random")) {
            JsonArray randomArr = json.getAsJsonArray("random");
            for (int i = 0; i < Math.min(randomArr.size(), 5); i++) {
                weights[i] = randomArr.get(i).getAsInt();
            }
        }
        for (int i = 0; i < 5; i++) {
            String gradeKey = "grade" + (i + 1);
            if (weights[i] <= 0) {
                if (weights[i] < 0) {
                    CsgoBox.LOGGER.warn("Negative weight {} for {} in box config, using default: {}",
                            weights[i], gradeKey, BoxGrades.DEFAULT_WEIGHTS[i]);
                    recordLoadError(file, fileName,
                            "Random[" + (i + 1) + "]: negative weight " + weights[i]
                                    + ", using default " + BoxGrades.DEFAULT_WEIGHTS[i]);
                }
                weights[i] = BoxGrades.DEFAULT_WEIGHTS[i];
            } else if (weights[i] > 10000) {
                CsgoBox.LOGGER.warn("Weight {} for {} exceeds maximum, clamping to 10000", weights[i], gradeKey);
                recordLoadError(file, fileName,
                        "Random[" + (i + 1) + "]: weight " + weights[i]
                                + " exceeds 10000, clamped");
                weights[i] = 10000;
            }
        }
        return weights;
    }

    /** Parses a plain entity id list, or alternating id/drop-rate pairs. */
    private static void parseEntities(JsonObject json, List<Identifier> dropEntityIds,
                                       Map<Identifier, Float> entityDropRates,
                                       Path file, String fileName) {
        if (!json.has("entity")) return;
        JsonArray entityArr = json.getAsJsonArray("entity");
        if (entityArr.size() == 0) return;

        if (entityArr.size() == 1 || (entityArr.get(1).isJsonPrimitive()
                && entityArr.get(1).getAsJsonPrimitive().isString())) {
            for (JsonElement elem : entityArr) {
                Identifier entityId = parseIdentifierSafe(elem.getAsString(), "entity");
                dropEntityIds.add(entityId);
            }
            return;
        }

        if ((entityArr.size() & 1) != 0) {
            CsgoBox.LOGGER.warn("Ignoring trailing entity entry without drop rate: {}",
                    entityArr.get(entityArr.size() - 1));
            recordLoadError(file, fileName,
                    "Entity: odd number of entries (" + entityArr.size()
                            + "), trailing id without drop rate ignored");
        }
        for (int i = 0; i + 1 < entityArr.size(); i += 2) {
            String entityIdStr = entityArr.get(i).getAsString();
            float rate = entityArr.get(i + 1).getAsFloat();
            Identifier entityId = parseIdentifierSafe(entityIdStr, "entity");
            dropEntityIds.add(entityId);
            entityDropRates.put(entityId, rate);
        }
    }

    public static void deleteFile(Identifier boxId) {
        Path file = BOXES_DIR.resolve(boxId.getPath() + ".json").normalize();
        if (!file.startsWith(BOXES_DIR.normalize())) {
            CsgoBox.LOGGER.warn("Rejected path traversal attempt: {}", boxId.getPath());
            return;
        }
        try {
            if (Files.exists(file)) {
                Files.delete(file);
                PARSED_CACHE.remove(file.getFileName().toString());
                CsgoBox.LOGGER.info("Deleted box JSON: {}", file);
            }
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to delete box JSON: {}", file, e);
        }
    }

    private static String getString(JsonObject json, String key, String defaultValue) {
        return json.has(key) ? json.get(key).getAsString() : defaultValue;
    }

    private static float getFloat(JsonObject json, String key, float defaultValue) {
        return json.has(key) ? json.get(key).getAsFloat() : defaultValue;
    }

    private static int getInt(JsonObject json, String key, int defaultValue) {
        return json.has(key) ? json.get(key).getAsInt() : defaultValue;
    }

    /**
     * v2.1.0+: reads and parses the central price table
     * ({@code config/csbox/} {@link PriceTable#FILE_NAME}). A missing file is
     * a valid empty table (grade default prices); malformed entries are
     * recorded as LoadErrors (visible via {@code /csbox info error}) and the
     * remaining valid prices still apply.
     */
    private static PriceTable loadPriceTable() {
        Path file = BOXES_DIR.resolve(PriceTable.FILE_NAME);
        if (!Files.exists(file)) {
            return PriceTable.EMPTY;
        }
        try {
            JsonObject json = GSON.fromJson(
                    new String(Files.readAllBytes(file), StandardCharsets.UTF_8),
                    JsonObject.class);
            List<String> issues = new ArrayList<>();
            PriceTable table = PriceTable.parse(json, issues);
            for (String issue : issues) {
                CsgoBox.LOGGER.warn("Price table issue in {}: {}", file, issue);
                recordLoadError(file, PriceTable.FILE_NAME, "PriceTable: " + issue);
            }
            return table;
        } catch (Exception e) {
            CsgoBox.LOGGER.error("Failed to load price table {}: {}", file, e.getMessage());
            recordLoadError(file, PriceTable.FILE_NAME,
                    "Failed to load price table: " + e.getMessage());
            return PriceTable.EMPTY;
        }
    }

    /**
     * v2.1.0+: dry-run validation of the central price table (backing the
     * {@code _prices.json} part of {@code /csbox validate}). Diagnostics are
     * rolled back like {@link #validateFile}; ok == false only when at least
     * one entry is malformed (a missing table is always ok).
     */
    public static ValidateResult validatePriceTable() {
        Path file = BOXES_DIR.resolve(PriceTable.FILE_NAME);
        String fileName = PriceTable.FILE_NAME;
        int before = LAST_LOAD_ERRORS.size();
        boolean ok = true;
        if (Files.exists(file)) {
            try {
                JsonObject json = GSON.fromJson(
                        new String(Files.readAllBytes(file), StandardCharsets.UTF_8),
                        JsonObject.class);
                List<String> issues = new ArrayList<>();
                PriceTable.parse(json, issues);
                for (String issue : issues) {
                    recordLoadError(file, fileName, "PriceTable: " + issue);
                }
                ok = issues.isEmpty();
            } catch (Exception e) {
                recordLoadError(file, fileName,
                        "Failed to load price table: " + e.getMessage());
                ok = false;
            }
        }
        List<LoadError> produced = new ArrayList<>(
                LAST_LOAD_ERRORS.subList(before, LAST_LOAD_ERRORS.size()));
        for (int i = LAST_LOAD_ERRORS.size() - 1; i >= before; i--) {
            LAST_LOAD_ERRORS.remove(i);
        }
        return new ValidateResult(ok, null, produced);
    }

    /**
     * Resolve the terminal price of every parsed stack from the central price
     * table. A plain {@code id} entry is priced by that id — or by its
     * {@code id#variant} sub-key when the legacy NBT ({@code GunId} /
     * {@code AmmoId}, e.g. TACZ) spells a variant. A {@code #tag} expansion
     * prices each expanded member by its own registry id. Entries absent from
     * the table resolve to {@link PriceRange#UNPRICED}: loaders reject unpriced
     * id entries (no grade-default fallback); loot_table placeholders stay
     * unpriced and are simply never offered by the terminal.
     */
    private static List<PriceRange> resolvePrices(JsonElement elem,
                                                  BoxItemCodec.ParseOutcome outcome,
                                                  PriceTable priceTable) {
        List<PriceRange> prices = new ArrayList<>(outcome.stacks().size());
        if (elem.isJsonObject() && elem.getAsJsonObject().has("id")) {
            String id = elem.getAsJsonObject().get("id").getAsString();
            String variant = PriceTable.variantId(elem.getAsJsonObject()).orElse(null);
            PriceRange range = priceTable.lookupRange(id, variant);
            if (range == null) {
                range = PriceRange.UNPRICED;
            }
            for (int k = 0; k < outcome.stacks().size(); k++) {
                prices.add(range);
            }
        } else {
            for (ItemStack stack : outcome.stacks()) {
                PriceRange range = priceTable.lookupRange(itemIdOf(stack), null);
                prices.add(range != null ? range : PriceRange.UNPRICED);
            }
        }
        return prices;
    }

    /** Registry id string of an item stack ({@code ns:path}). */
    private static String itemIdOf(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
