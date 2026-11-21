package com.reclizer.csgobox.box.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxJsonSchemaValidator;
import com.reclizer.csgobox.box.LegacyPriceMigration;
import com.reclizer.csgobox.box.PriceRange;
import com.reclizer.csgobox.box.PriceTable;
import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.logic.PityPolicy;
import com.reclizer.csgobox.terminal.TierStockManager;
import org.slf4j.Logger;

import java.io.IOException;
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
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Era-neutral core of the box JSON loader (2026-11-21 轮51 sink). Owns
 * directory I/O, the parse cache, load diagnostics, the price table, and the
 * full top-level JSON shape + v2.x gameplay field parsing; each platform
 * module keeps only a thin {@code BoxJsonLoader} adapter that supplies
 *
 * <ul>
 *   <li>the config directory and mod-presence checks ({@link Era}),</li>
 *   <li>resource-id parsing ({@link Era#requireId}),</li>
 *   <li>item resolution through its era codec ({@link Era#resolveItems}),</li>
 *   <li>the mapping from {@link ParsedBox} to its definition type and the
 *       registry calls ({@link RegistryOps}),</li>
 * </ul>
 *
 * <p>The forge line additionally keeps {@code saveToFile} platform-side: it
 * serializes live {@code BoxDefinition} objects (Component color round-trip),
 * which does not fit this parse-side core yet.</p>
 *
 * <p>Every method body here is moved verbatim from the platform loaders —
 * only the era touchpoints became hooks. {@code T} is the platform item token
 * (ItemStack on all platforms), {@code D} the platform definition type.</p>
 */
public final class BoxJsonCore<T, D> {

    /** Platform hooks: the only MC/loader-facing surface of the loader. */
    public interface Era<T> {
        /** The {@code config/csbox} directory (FMLPaths / FabricLoader per platform). */
        Path boxesDir();

        /** Whether a required (optional-dependency) mod id is present. */
        boolean isModLoaded(String modId);

        /**
         * Validates a resource id string ("ns:path") and returns its canonical
         * form; throws {@link RuntimeException} when invalid (the MC exception
         * type varies by version — normalized by the caller).
         */
        String requireId(String value);

        /**
         * Runs the platform item codec on one JSON item entry. On success the
         * parallel lists carry one entry per produced stack: the platform
         * token, its registry id string ("ns:path"), an optional price-variant
         * sub-key ("GunId|AmmoId", null when the era has none), and the
         * intra-grade weight. On failure {@code error} is non-null.
         */
        ItemResolution<T> resolveItems(JsonElement elem);
    }

    /** Registry-facing hooks: how parsed boxes enter the platform registry. */
    public interface RegistryOps<T, D> {
        /**
         * Maps a parsed box to the platform definition type (parses ids, runs
         * the builder). The ids inside {@code parsed} are already validated.
         */
        D toDefinition(ParsedBox<T> parsed);

        void register(D definition);

        /** Currently registered box ids as file-stem strings (path part of the box id). */
        Set<String> currentBoxIds();

        /** Removes the box whose config file was {@code boxId + ".json"}. */
        void removeBox(String boxId);
    }

    /**
     * One JSON load diagnostic (error or warning). Pure data twin of the
     * platform {@code LoadError} records, which add only the chat rendering.
     */
    public record LoadDiagnostic(
            Path file,
            String boxId,
            String reason,
            int line,
            int column,
            boolean warning
    ) {
        /** Backwards-compatible overload: an entry is an error unless warning=true. */
        public LoadDiagnostic(Path file, String boxId, String reason, int line, int column) {
            this(file, boxId, reason, line, column, false);
        }
    }

    /** One resolved item entry of a parsed tier: platform stack + its price identity. */
    public record ItemResolution<T>(
            String error,
            List<String> warnings,
            List<T> stacks,
            List<String> ids,
            List<String> variants,
            List<Integer> weights
    ) {
        public static <T> ItemResolution<T> fail(String message) {
            return new ItemResolution<>(message, List.of(), List.of(), List.of(), List.of(), List.of());
        }

        public static <T> ItemResolution<T> ok(List<T> stacks, List<String> ids,
                                               List<String> variants, List<Integer> weights,
                                               List<String> warnings) {
            return new ItemResolution<>(null, List.copyOf(warnings), List.copyOf(stacks),
                    List.copyOf(ids), variants == null ? null : List.copyOf(variants),
                    List.copyOf(weights));
        }
    }

    /** Parsed grade tier: tier meta + parallel stack/price/weight lists. */
    public record ParsedGrade<T>(
            String id,
            String displayName,
            int color,
            int weight,
            List<T> stacks,
            List<PriceRange> prices,
            List<Integer> itemWeights
    ) {
    }

    /** v2.x upgrade rule (config "upgrade": {target, consume}). */
    public record UpgradeRule(String target, int consume) {
    }

    /**
     * Era-neutral parse result of one box JSON file: every field the platform
     * builder consumes, with ids as validated strings. The platform adapter
     * maps this onto its {@code BoxDefinition} builder verbatim.
     */
    public record ParsedBox<T>(
            String boxId,
            String name,
            OptionalInt nameColor,
            String type,
            String keyId,
            float dropRate,
            List<String> dropEntities,
            Map<String, Float> entityDropRates,
            List<ParsedGrade<T>> grades,
            Optional<String> icon,
            float discount,
            int stock,
            int maxPerPlayer,
            int cooldownSeconds,
            String permission,
            String soundId,
            boolean hideOdds,
            boolean allowBulk,
            List<String> extraKeys,
            float wearMin,
            float wearMax,
            float bonusChance,
            int bonusMode,
            int announceLevel,
            long availableFromMs,
            long availableUntilMs,
            int openCost,
            UpgradeRule upgrade,
            int negotiationRounds,
            int negotiationTimeoutMinutes,
            float priceBias,
            PityPolicy pity
    ) {
    }

    /** Dry-run validation result ({@code /csbox validate}); definition null on failure. */
    public record ValidateResult<D>(
            boolean ok,
            D definition,
            List<LoadDiagnostic> errors
    ) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String[] GRADE_IDS = {"classified", "restricted", "mil_spec", "industrial", "consumer"};
    private static final String[] GRADE_NAMES = {"保密", "受限", "军规级", "工业级", "消费级"};
    private static final int[] GRADE_COLORS = {0xFFD32CE6, 0xFF8847FF, 0xFF4B69FF, 0xFF5E98D9, 0xFFB0C3D9};

    /** Sentinel values: -1 = unlimited (stock / maxPerPlayer). */
    public static final int UNLIMITED = -1;

    /** v2.x bonus modes (config "bonus.mode": "double" | "box"). */
    public static final int BONUS_MODE_NONE = 0;
    public static final int BONUS_MODE_DOUBLE = 1;
    public static final int BONUS_MODE_BOX = 2;

    /**
     * Load diagnostics collected during loadAll/reloadPreserving. Written by
     * the file watcher thread (reload) and the server thread (/csbox reload),
     * so a CopyOnWriteArrayList keeps it safe under that cross-thread access.
     */
    private final List<LoadDiagnostic> diagnostics = new CopyOnWriteArrayList<>();

    /**
     * Parse cache: file-name -> content-hash -> result/errors. Reloads skip
     * files whose SHA-256 is unchanged. Never persists across restarts — the
     * cached parse results reference registry objects rebuilt per launch.
     */
    private final ConcurrentHashMap<String, CachedFile<T>> parseCache = new ConcurrentHashMap<>();

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    });

    /** Hash -> parse result; empty {@code definition} = failed parse with diagnostics.
     *  {@code tableHash} is the price-table hash that produced this result, so a
     *  change to {@code config/csbox/_prices.json} invalidates the cached box. */
    private record CachedFile<T>(String hash, String tableHash, Optional<ParsedBox<T>> definition,
                                 List<LoadDiagnostic> errors) {
    }

    /** Matches an optional leading hex color in the box "name" field, e.g.
     *  {@code "#FF5555 高级补给箱"}. Group 1 = 6 hex digits, group 2 = display text. */
    private static final Pattern NAME_COLOR_PREFIX =
            Pattern.compile("^#([0-9A-Fa-f]{6}) (.*)$");

    /** Parsed result of a box "name" value: display text + optional 0xRRGGBB color. */
    private record ParsedName(String text, OptionalInt color) {
    }

    private final Era<T> era;
    private final RegistryOps<T, D> ops;
    private final Logger logger;

    public BoxJsonCore(Era<T> era, RegistryOps<T, D> ops, Logger logger) {
        this.era = era;
        this.ops = ops;
        this.logger = logger;
    }

    /** Strips an optional {@code "#RRGGBB "} prefix; returns text + color, or the raw string without color. */
    private static ParsedName parseColoredName(Logger log, String raw) {
        if (raw == null) return new ParsedName("", OptionalInt.empty());
        Matcher m = NAME_COLOR_PREFIX.matcher(raw);
        if (!m.matches()) return new ParsedName(raw, OptionalInt.empty());
        int rgb = Integer.parseInt(m.group(1), 16);
        String text = m.group(2);
        if (text.isEmpty()) {
            log.warn("Box name has color prefix but empty text: '{}'", raw);
            return new ParsedName("", OptionalInt.empty());
        }
        return new ParsedName(text, OptionalInt.of(rgb));
    }

    public void loadAll() {
        diagnostics.clear();
        loadAllInto(era.boxesDir());
    }

    private void loadAllInto(Path boxesDir) {
        if (!Files.exists(boxesDir)) {
            try {
                Files.createDirectories(boxesDir);
            } catch (IOException e) {
                logger.error("Failed to create boxes config directory: {}", boxesDir, e);
                return;
            }
            logger.info("Created boxes config directory: {}", boxesDir);
        }

        // Pre-v2.0.0 terminal.json migration (no "type" field); must run before parsing.
        BoxDefaults.upgradeLegacyTerminalConfig(boxesDir);

        // v2.0.1+: old-version adapter — transfer legacy per-item "price"
        // fields into _prices.json (conflict prices averaged), then strip
        // them from the box files so the removed field stops erroring.
        LegacyPriceMigration.migrateLegacyPrices(boxesDir);

        // v2.0.1+: the central terminal price table is read once per scan and
        // baked into every box's GradeGroup prices.
        PriceTable priceTable = loadPriceTable(boxesDir);
        PriceTableRegistry.set(priceTable);
        if (!priceTable.isEmpty()) {
            logger.info("Loaded {} price(s) from {}",
                    priceTable.size(), PriceTable.FILE_NAME);
        }

        List<Path> scannedFiles = new ArrayList<>();
        int[] loaded = {0};
        int[] skipped = {0};
        try {
            forEachBoxJson(boxesDir, file -> {
                String fileName = file.getFileName().toString();
                scannedFiles.add(file);
                try {
                    Optional<ParsedBox<T>> result = loadFromFile(boxesDir, file, priceTable);
                    if (result.isEmpty()) {
                        // loadFromFile already logged the per-grade or per-item reason.
                        skipped[0]++;
                        return;
                    }
                    ops.register(ops.toDefinition(result.get()));
                    loaded[0]++;
                    logger.info("Loaded box from JSON: {} -> csgobox:{}", file.getFileName(), result.get().boxId());
                } catch (Exception e) {
                    logger.error("Failed to load box JSON file: {}", file, e);
                    skipped[0]++;
                    recordLoadError(file, fileName, "Failed to load box JSON: " + e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.error("Failed to list box JSON files in {}", boxesDir, e);
        }

        logger.info(
                "Scanned {} JSON file(s) in {}; loaded {}, skipped {}",
                scannedFiles.size(), boxesDir, loaded[0], skipped[0]);
    }

    /**
     * Re-scan the box directory and update the registry in place without
     * clearing it: failed files keep their previous definition, successful
     * ones overwrite by id, files gone from disk are removed. Used by
     * {@code /csbox reload} and the file watcher. Re-runs the legacy terminal
     * migration (the terminal ships unconfigured since 2.0.0 — no default
     * terminal.json is ever written).
     */
    public void reloadPreserving() {
        diagnostics.clear();
        Path boxesDir = era.boxesDir();
        if (!Files.exists(boxesDir)) {
            try {
                Files.createDirectories(boxesDir);
            } catch (IOException e) {
                logger.error("Failed to create boxes config directory: {}", boxesDir, e);
                return;
            }
            logger.info("Created boxes config directory: {}", boxesDir);
        }
        BoxDefaults.upgradeLegacyTerminalConfig(boxesDir);

        // v2.0.1+: old-version adapter — transfer legacy per-item "price"
        // fields into _prices.json (conflict prices averaged), then strip
        // them from the box files so the removed field stops erroring.
        LegacyPriceMigration.migrateLegacyPrices(boxesDir);

        // v2.0.1+: central price table re-read on every reload, so editing
        // _prices.json picks up immediately (cache entries are keyed by hash).
        PriceTable priceTable = loadPriceTable(boxesDir);
        PriceTableRegistry.set(priceTable);

        Set<String> previousIds = new HashSet<>(ops.currentBoxIds());
        Set<String> seenIds = new HashSet<>();
        int[] loaded = {0};
        int[] skipped = {0};

        try {
            forEachBoxJson(boxesDir, file -> {
                String fileName = file.getFileName().toString();
                String boxIdStr = fileName.substring(0, fileName.length() - 5);
                try {
                    era.requireId("csgobox:" + boxIdStr);
                } catch (Exception e) {
                    logger.error("Invalid box id from filename {}: {}", file, e.getMessage());
                    recordLoadError(file, fileName, "Invalid identifier: " + e.getMessage());
                    skipped[0]++;
                    return;
                }
                seenIds.add(boxIdStr);
                try {
                    Optional<ParsedBox<T>> result = loadFromFile(boxesDir, file, priceTable);
                    if (result.isPresent()) {
                        ops.register(ops.toDefinition(result.get()));
                        loaded[0]++;
                        logger.info("Reloaded box from JSON: {} -> csgobox:{}", fileName, result.get().boxId());
                    } else {
                        // loadFromFile already logged the per-grade or per-item reason.
                        skipped[0]++;
                    }
                } catch (Exception e) {
                    logger.error("Failed to load box JSON file: {}", file, e);
                    skipped[0]++;
                    recordLoadError(file, fileName, "Failed to load box JSON: " + e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.error("Failed to list box JSON files in {}", boxesDir, e);
            return;
        }

        Set<String> toRemove = new HashSet<>(previousIds);
        toRemove.removeAll(seenIds);
        int removed = 0;
        for (String boxId : toRemove) {
            ops.removeBox(boxId);
            // v2.0.1-fix: drop the parse cache entry for the deleted file so a
            // later re-creation of the same file name cannot serve a stale hash.
            parseCache.remove(boxId + ".json");
            removed++;
            logger.info("Removed box no longer present in config: csgobox:{}", boxId);
        }

        logger.info(
                "Reload preserving: scanned {} (of {} previously registered); loaded {}, skipped {}, removed {}",
                seenIds.size(), previousIds.size(), loaded[0], skipped[0], removed);
    }

    public List<LoadDiagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    /**
     * v2.0.1 dry-run validation (backing {@code /csbox validate}): parses a
     * box JSON file WITHOUT registering anything or touching the parse cache,
     * so an author can iterate on a config without changing live state.
     * Returns the produced diagnostics; {@code -1} for both line/column means
     * "no JSON position available". A result with {@code ok == false} must not
     * be loaded.
     */
    public ValidateResult<D> validateFile(Path file) {
        String fileName = file.getFileName().toString();
        int before = diagnostics.size();
        Optional<ParsedBox<T>> result;
        try {
            byte[] bytes = Files.readAllBytes(file);
            result = parseFromBytes(bytes, file, loadPriceTable(era.boxesDir()));
        } catch (IOException e) {
            recordLoadError(file, fileName, "Cannot read file: " + e.getMessage());
            result = Optional.empty();
        }
        List<LoadDiagnostic> produced = new ArrayList<>(
                diagnostics.subList(before, diagnostics.size()));
        boolean ok = result.isPresent();
        // Roll back the diagnostics so a dry run never pollutes the live
        // error list shown by /csbox info error.
        for (int i = diagnostics.size() - 1; i >= before; i--) {
            diagnostics.remove(i);
        }
        return new ValidateResult<>(ok, result.map(ops::toDefinition).orElse(null), produced);
    }

    public boolean hasLoadErrors() {
        return !diagnostics.isEmpty();
    }

    /**
     * v2.0.1+: dry-run validation of the central price table (backing the
     * {@code _prices.json} part of {@code /csbox validate}). Diagnostics are
     * rolled back like {@link #validateFile}; ok == false only when at least
     * one entry is malformed (a missing table is always ok).
     */
    public ValidateResult<D> validatePriceTable() {
        Path file = era.boxesDir().resolve(PriceTable.FILE_NAME);
        String fileName = PriceTable.FILE_NAME;
        int before = diagnostics.size();
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
        List<LoadDiagnostic> produced = new ArrayList<>(
                diagnostics.subList(before, diagnostics.size()));
        for (int i = diagnostics.size() - 1; i >= before; i--) {
            diagnostics.remove(i);
        }
        return new ValidateResult<>(ok, null, produced);
    }

    private void recordLoadError(Path file, String fileName, String reason, int line, int column) {
        String boxId = fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
        diagnostics.add(new LoadDiagnostic(file, boxId, reason, line, column));
    }

    /** No-JSON-position convenience overload. */
    private void recordLoadError(Path file, String fileName, String reason) {
        recordLoadError(file, fileName, reason, -1, -1);
    }

    /** Non-fatal diagnostics (partially dropped components, migrated formats):
     *  kept items surface via the same error listing, marked as warnings. */
    private void recordLoadWarning(Path file, String fileName, String reason) {
        String boxId = fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5) : fileName;
        diagnostics.add(new LoadDiagnostic(file, boxId, reason, -1, -1, true));
    }

    /** Extracts {@code at line N column M} from a Gson error message (Gson 2.13+
     *  removed {@code getLocation()}); {@code {-1,-1}} when not found. */
    private static final Pattern GSON_LOCATION_PATTERN =
            Pattern.compile("at line (\\d+) column (\\d+)");

    private static int[] parseLocationFromMessage(String message) {
        Matcher m = GSON_LOCATION_PATTERN.matcher(message);
        if (m.find()) {
            return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        return new int[]{-1, -1};
    }

    /** Feeds every non-underscore {@code .json} in the boxes dir to
     *  {@code action}; underscore-prefixed files are mod metadata, never boxes. */
    private static void forEachBoxJson(Path boxesDir, Consumer<Path> action) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(boxesDir, "*.json")) {
            for (Path file : stream) {
                if (file.getFileName().toString().startsWith("_")) {
                    continue;
                }
                action.accept(file);
            }
        }
    }

    private Optional<ParsedBox<T>> loadFromFile(Path boxesDir, Path file, PriceTable priceTable) throws IOException {
        String fileName = file.getFileName().toString();

        byte[] bytes = Files.readAllBytes(file);
        String hash = sha256Hex(bytes);

        CachedFile<T> cached = parseCache.get(fileName);
        if (cached != null && cached.hash().equals(hash)
                && cached.tableHash().equals(priceTable.hash())
                // Only error-free parses are cacheable: a parse that produced
                // errors may be timing-dependent (e.g. '#tag' entries scanned
                // before item tags are bound expand to nothing), and reusing
                // it would freeze the error in place forever. Re-parse so the
                // next scan (server start / reload) self-heals.
                && cached.errors().isEmpty()) {
            // Unchanged (box content and price table): reuse the cached parse
            // and its diagnostics.
            diagnostics.addAll(cached.errors());
            return cached.definition();
        }

        int errorsBefore = diagnostics.size();
        Optional<ParsedBox<T>> result = parseFromBytes(bytes, file, priceTable);
        List<LoadDiagnostic> produced = new ArrayList<>(
                diagnostics.subList(errorsBefore, diagnostics.size()));
        parseCache.put(fileName, new CachedFile<>(hash, priceTable.hash(), result, produced));
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

    private Optional<ParsedBox<T>> parseFromBytes(byte[] bytes, Path file, PriceTable priceTable) {
        String fileName = file.getFileName().toString();
        String boxIdStr = fileName.substring(0, fileName.length() - 5);

        // v2.0.1: validate the file name before it becomes the box id — an
        // identifier must be lowercase [a-z0-9_./-]; anything else (spaces,
        // uppercase, CJK, #...) would either crash the id parse or produce
        // a silently different id. Underscore-prefixed files are mod metadata
        // and never reach here.
        if (!boxIdStr.matches("[a-z0-9_./-]+")) {
            String msg = "Invalid box id from file name '" + boxIdStr
                    + "' — use only lowercase letters, digits, '_', '.', '/' or '-' "
                    + "(e.g. weapon_supply_box.json)";
            logger.error("Skipping {}: {}", file, msg);
            recordLoadError(file, fileName, msg);
            return Optional.empty();
        }

        JsonObject json;
        try {
            json = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
        } catch (JsonSyntaxException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "unknown syntax error";
            int[] lc = parseLocationFromMessage(msg);
            logger.error("Invalid JSON syntax in {}: {}", file, msg);
            recordLoadError(file, fileName, "Invalid JSON syntax: " + msg, lc[0], lc[1]);
            return Optional.empty();
        }
        if (json == null) return Optional.empty();

        // Schema issues surface as LoadError entries (diagnostic, not load-blocking); field fallback still runs below.
        for (BoxJsonSchemaValidator.SchemaIssue issue : BoxJsonSchemaValidator.validate(json)) {
            logger.warn("Schema issue in {} field {}: {}",
                    file, issue.field(), issue.reason());
            recordLoadError(file, fileName,
                    "Schema: " + issue.field() + " — " + issue.reason());
        }

        try {
            // v2.0.1 gating: "enabled":false skips cleanly (no error, the
            // author chose to disable it); "requires" skips with a clear
            // "missing dependency" error when any mod is absent. Only a real
            // boolean is honoured — a string "false" is not silently coerced.
            if (json.has("enabled") && json.get("enabled").isJsonPrimitive()
                    && json.get("enabled").getAsJsonPrimitive().isBoolean()
                    && !json.get("enabled").getAsBoolean()) {
                logger.info("Skipping disabled box config: {}", file);
                return Optional.empty();
            }
            if (json.has("requires")) {
                for (JsonElement req : json.get("requires").getAsJsonArray()) {
                    String modId = req.getAsString();
                    if (!era.isModLoaded(modId)) {
                        String msg = "Skipped: required mod '" + modId
                                + "' is not installed (requires: [" + modId + "])";
                        logger.warn("{} for {}", msg, file);
                        recordLoadError(file, fileName, msg);
                        return Optional.empty();
                    }
                }
            }

            ParsedName parsedName = parseColoredName(logger, getString(json, "name", boxIdStr));
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
                logger.error("Skipping {}: {}", file, msg);
                recordLoadError(file, fileName, msg);
                return Optional.empty();
            }
            float dropRate = getFloat(json, "drop", 0.12F);

            // v2.x: a "grades" array replaces the legacy random/grade1..5
            // format entirely; the legacy weights array is then unused.
            boolean customTiers = json.has("grades") && json.get("grades").isJsonArray();
            int[] weights = customTiers ? null : parseWeights(json, file, fileName);

            List<String> dropEntityIds = new ArrayList<>();
            Map<String, Float> entityDropRates = new HashMap<>();
            parseEntities(json, dropEntityIds, entityDropRates, file, fileName);

            List<ParsedGrade<T>> grades;
            boolean unpricedRejected;
            if (customTiers) {
                // v2.x custom tier array ({id, name?, color?, weight?, items},
                // lowest tier first) replaces the legacy format entirely.
                ParsedGrades<T> parsed = parseCustomGrades(json, priceTable, boxIdStr, file, fileName);
                grades = parsed.grades();
                unpricedRejected = parsed.unpricedRejected();
            } else {
                ParsedGrades<T> parsed = parseLegacyGrades(json, weights, priceTable, file, fileName);
                grades = parsed.grades();
                unpricedRejected = parsed.unpricedRejected();
            }

            if (unpricedRejected) {
                return Optional.empty();
            }

            if (grades.isEmpty()) {
                logger.warn("Skipping box '{}': all items failed to parse (missing mods?)", boxIdStr);
                recordLoadError(file, fileName,
                        "All items failed to parse (missing mods?)");
                return Optional.empty();
            }

            String keyId = null;
            if (!"terminal".equals(type)) {
                keyId = requireIdSafe(getString(json, "key", "csgobox:csgo_key0"), "key");
            }
            String soundId = null;
            if (json.has("sound") && json.get("sound").isJsonPrimitive()) {
                soundId = requireIdSafe(json.get("sound").getAsString(), "sound");
            }

            Optional<String> icon = Optional.empty();
            if (json.has("icon")) {
                String iconStr = getString(json, "icon", "");
                if (!iconStr.isBlank()) {
                    icon = Optional.of(iconStr);
                }
            }
            float discount = getFloat(json, "discount", 0.0F);
            int stock = getInt(json, "stock", UNLIMITED);
            int maxPerPlayer = getInt(json, "max_per_player", UNLIMITED);
            int cooldownSeconds = getInt(json, "cooldown_seconds", 0);
            String permission = getString(json, "permission", "");

            // v2.x gameplay fields.
            boolean hideOdds = false;
            if (json.has("hide_odds")) {
                hideOdds = json.get("hide_odds").getAsBoolean();
            }
            boolean allowBulk = true;
            if (json.has("allow_bulk")) {
                allowBulk = json.get("allow_bulk").getAsBoolean();
            }
            List<String> extraKeys = new ArrayList<>();
            if (json.has("keys") && json.get("keys").isJsonArray()) {
                for (JsonElement ke : json.getAsJsonArray("keys")) {
                    if (ke.isJsonPrimitive() && ke.getAsJsonPrimitive().isString()) {
                        String keyIdStr = ke.getAsString();
                        if (keyIdStr != null && !keyIdStr.isBlank()) {
                            extraKeys.add(keyIdStr.trim());
                        }
                    }
                }
            }
            float wearMin = 0.0F;
            float wearMax = 1.0F;
            if (json.has("wear") && json.get("wear").isJsonArray() && json.getAsJsonArray("wear").size() == 2) {
                wearMin = json.getAsJsonArray("wear").get(0).getAsFloat();
                wearMax = json.getAsJsonArray("wear").get(1).getAsFloat();
            }
            float bonusChance = 0.0F;
            int bonusMode = BONUS_MODE_NONE;
            if (json.has("bonus") && json.get("bonus").isJsonObject()) {
                JsonObject bonus = json.getAsJsonObject("bonus");
                bonusChance = bonus.has("chance") ? bonus.get("chance").getAsFloat() : 0.0F;
                String mode = getString(bonus, "mode", "double");
                bonusMode = "box".equalsIgnoreCase(mode) ? BONUS_MODE_BOX : BONUS_MODE_DOUBLE;
            }
            int announceLevel = 0;
            if (json.has("announce_above")) {
                JsonElement ae = json.get("announce_above");
                if (ae.isJsonPrimitive() && ae.getAsJsonPrimitive().isNumber()) {
                    announceLevel = ae.getAsInt();
                } else if (ae.isJsonPrimitive() && ae.getAsJsonPrimitive().isString()) {
                    announceLevel = tierLevelOf(grades, ae.getAsString());
                    if (announceLevel == 0) {
                        announceLevel = BoxGrades.gradeLevel(ae.getAsString());
                    }
                }
            }
            long availableFromMs = 0L;
            if (json.has("available_from")) {
                Long fromMs = parseIsoMillis(json.get("available_from").getAsString());
                if (fromMs == null) {
                    recordLoadError(file, fileName, "available_from: expected ISO-8601 date-time, got \""
                            + json.get("available_from").getAsString() + "\"");
                } else {
                    availableFromMs = fromMs;
                }
            }
            long availableUntilMs = 0L;
            if (json.has("available_until")) {
                Long untilMs = parseIsoMillis(json.get("available_until").getAsString());
                if (untilMs == null) {
                    recordLoadError(file, fileName, "available_until: expected ISO-8601 date-time, got \""
                            + json.get("available_until").getAsString() + "\"");
                } else {
                    availableUntilMs = untilMs;
                }
            }
            int openCost = getInt(json, "open_cost", 0);
            int negotiationRounds = 0;
            int negotiationTimeoutMinutes = 0;
            float priceBias = 0.0F;
            if (json.has("negotiation") && json.get("negotiation").isJsonObject()) {
                JsonObject neg = json.getAsJsonObject("negotiation");
                negotiationRounds = neg.has("rounds") ? neg.get("rounds").getAsInt() : 0;
                negotiationTimeoutMinutes = neg.has("timeout_minutes") ? neg.get("timeout_minutes").getAsInt() : 0;
                priceBias = neg.has("price_bias") ? neg.get("price_bias").getAsFloat() : 0.0F;
            }
            UpgradeRule upgrade = null;
            if (json.has("upgrade") && json.get("upgrade").isJsonObject()) {
                JsonObject up = json.getAsJsonObject("upgrade");
                String target = getString(up, "target", "");
                int consume = getInt(up, "consume", 1);
                if (!target.isBlank() && consume >= 1) {
                    upgrade = new UpgradeRule(target, consume);
                }
            }
            // v2.0.1 pity (保底): { "grade": "classified", "every": 20 } —
            // an invalid config degrades to no pity (the schema validator
            // reports the mistake at load time).
            PityPolicy pity = null;
            if (json.has("pity") && json.get("pity").isJsonObject()) {
                JsonObject pityJson = json.getAsJsonObject("pity");
                String gradeId = getString(pityJson, "grade", "");
                int every = getInt(pityJson, "every", 0);
                boolean firstOpen = pityJson.has("first_open")
                        && pityJson.get("first_open").isJsonPrimitive()
                        && pityJson.get("first_open").getAsBoolean();
                // Custom tiers resolve the level from the configured tier ids;
                // the legacy format keeps the canonical five-id mapping.
                pity = customTiers
                        ? PityPolicy.ofLevel(tierLevelOf(grades, gradeId), every, firstOpen)
                        : PityPolicy.of(gradeId, every, firstOpen);
                // v2.0.1-fix(A): warn when the guaranteed tier (target and up)
                // has zero total weight — the forced roll could never fire
                // and players would silently lose their pity.
                if (pity != null) {
                    int target = pity.targetLevel();
                    int pityWeight = 0;
                    for (int i = 0; i < grades.size(); i++) {
                        if (BoxGrades.levelAt(grades.size(), i) >= target) {
                            pityWeight += Math.max(0, grades.get(i).weight());
                        }
                    }
                    if (pityWeight <= 0) {
                        recordLoadWarning(file, fileName,
                                "Pity: target grade '" + gradeId + "' (level " + target
                                        + ") and above have zero weight — guaranteed roll can never trigger, "
                                        + "fix the 'random' weights");
                    }
                }
            }

            return Optional.of(new ParsedBox<>(boxIdStr, parsedName.text(), parsedName.color(),
                    type, keyId, dropRate, dropEntityIds, entityDropRates, grades,
                    icon, discount, stock, maxPerPlayer, cooldownSeconds, permission,
                    soundId, hideOdds, allowBulk, extraKeys, wearMin, wearMax,
                    bonusChance, bonusMode, announceLevel, availableFromMs, availableUntilMs,
                    openCost, upgrade, negotiationRounds, negotiationTimeoutMinutes,
                    priceBias, pity));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid identifier in {}: {}", file, e.getMessage());
            recordLoadError(file, fileName,
                    "Invalid identifier: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Validates a resource id via {@link Era#requireId}, normalizing any
     *  exception to {@link IllegalArgumentException} with the offending field
     *  name (the MC exception type varies by version). */
    private String requireIdSafe(String value, String fieldName) {
        try {
            return era.requireId(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "' = \"" + value + "\": " + e.getMessage(), e);
        }
    }

    /** Parsed result of a tier-format grade list. */
    private record ParsedGrades<T>(List<ParsedGrade<T>> grades, boolean unpricedRejected) {
    }

    /** Legacy tier format: "grade5".."grade1" item arrays weighted by the
     *  "random" array (grade1 → grade5 order); tiers are created highest →
     *  lowest and empty ones are skipped with a warning. */
    private ParsedGrades<T> parseLegacyGrades(JsonObject json, int[] weights, PriceTable priceTable,
                                              Path file, String fileName) {
        List<ParsedGrade<T>> grades = new ArrayList<>();
        boolean unpricedRejected = false;
        for (int i = 0; i < 5; i++) {
            String gradeKey = "grade" + (5 - i);
            if (json.has(gradeKey)) {
                ParsedTier<T> tier = parseTierItems(json.getAsJsonArray(gradeKey), file, fileName, priceTable);
                if (tier.unpricedRejected()) {
                    unpricedRejected = true;
                }
                if (!tier.stacks().isEmpty()) {
                    grades.add(new ParsedGrade<>(GRADE_IDS[i], GRADE_NAMES[i], GRADE_COLORS[i],
                            weights[4 - i], tier.stacks(), tier.prices(), tier.itemWeights()));
                } else {
                    recordLoadWarning(file, fileName,
                            "Grade " + gradeKey + " has no valid items — the tier will fall back "
                                    + "to lower grades when rolled (empty tiers are invisible to players)");
                }
            }
        }
        return new ParsedGrades<>(grades, unpricedRejected);
    }

    /** v2.x custom tier format: "grades" = tier objects lowest tier first,
     *  each {id, name?, color?, weight?, items}. The internal tier list
     *  stays highest → lowest (the network/display invariant), so the array
     *  is consumed in reverse. */
    private ParsedGrades<T> parseCustomGrades(JsonObject json, PriceTable priceTable, String boxId,
                                              Path file, String fileName) {
        List<ParsedGrade<T>> grades = new ArrayList<>();
        boolean unpricedRejected = false;
        JsonArray tiersArr = json.getAsJsonArray("grades");
        if (tiersArr.size() > BoxGrades.MAX_GRADES) {
            recordLoadError(file, fileName,
                    "Grades: too many tiers (" + tiersArr.size() + ", max " + BoxGrades.MAX_GRADES + ")");
            return new ParsedGrades<>(grades, unpricedRejected);
        }
        for (int t = tiersArr.size() - 1; t >= 0; t--) {
            JsonElement te = tiersArr.get(t);
            if (!te.isJsonObject()) {
                recordLoadError(file, fileName, "Grades[" + t + "]: expected a tier object");
                continue;
            }
            JsonObject tier = te.getAsJsonObject();
            String tierId = getString(tier, "id", "");
            if (tierId.isBlank()) {
                recordLoadError(file, fileName, "Grades[" + t + "]: missing required 'id'");
                continue;
            }
            boolean duplicate = false;
            for (ParsedGrade<T> existing : grades) {
                if (existing.id().equals(tierId)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) {
                recordLoadError(file, fileName, "Grades[" + t + "]: duplicate tier id '" + tierId + "'");
                continue;
            }
            String display = getString(tier, "name", tierId);
            int color = BoxGrades.DEFAULT_TIER_COLOR;
            String rawColor = getString(tier, "color", "");
            if (!rawColor.isBlank()) {
                Integer parsedColor = BoxGrades.parseTierColor(rawColor);
                if (parsedColor == null) {
                    recordLoadError(file, fileName,
                            "Grades[" + t + "].color: expected \"#RRGGBB\", got \"" + rawColor + "\"");
                } else {
                    color = parsedColor;
                }
            }
            int weight = 1;
            if (tier.has("weight") && tier.get("weight").isJsonPrimitive()) {
                weight = tier.get("weight").getAsInt();
                if (weight < 0) {
                    recordLoadError(file, fileName,
                            "Grades[" + t + "].weight: negative weight " + weight + " treated as 0 (tier never rolls)");
                    weight = 0;
                } else if (weight > 10000) {
                    logger.warn("Weight {} for grades[{}] exceeds maximum, clamping to 10000", weight, t);
                    weight = 10000;
                }
            }
            JsonArray itemsArr = tier.has("items") && tier.get("items").isJsonArray()
                    ? tier.getAsJsonArray("items") : new JsonArray();
            ParsedTier<T> parsedTier = parseTierItems(itemsArr, file, fileName, priceTable);
            if (parsedTier.unpricedRejected()) {
                unpricedRejected = true;
            }
            if (!parsedTier.stacks().isEmpty()) {
                int tierStock = tier.has("stock") && tier.get("stock").isJsonPrimitive()
                        ? tier.get("stock").getAsInt() : -1;
                if (tierStock >= 0) {
                    TierStockManager.configure(boxId, tierId, tierStock);
                }
                grades.add(new ParsedGrade<>(tierId, display, color, weight,
                        parsedTier.stacks(), parsedTier.prices(), parsedTier.itemWeights()));
            } else {
                recordLoadWarning(file, fileName,
                        "Grades[" + t + "] ('" + tierId + "') has no valid items — the tier will fall back "
                                + "to lower grades when rolled (empty tiers are invisible to players)");
            }
        }
        return new ParsedGrades<>(grades, unpricedRejected);
    }

    /** One parsed tier: parallel item/price/weight lists; the
     *  {@code unpricedRejected} flag is true when an unpriced entry rejects
     *  the whole box. */
    private record ParsedTier<T>(List<T> stacks, List<PriceRange> prices,
                                 List<Integer> itemWeights, boolean unpricedRejected) {
    }

    /** One tier's item array → parallel item/price/weight lists via the era
     *  item codec; prices come from the central price table. */
    private ParsedTier<T> parseTierItems(JsonArray itemsArr, Path file, String fileName,
                                         PriceTable priceTable) {
        List<T> stacks = new ArrayList<>();
        List<PriceRange> prices = new ArrayList<>();
        List<Integer> itemWeights = new ArrayList<>();
        boolean unpricedRejected = false;
        for (JsonElement elem : itemsArr) {
            ItemResolution<T> outcome = era.resolveItems(elem);
            if (outcome.error() == null) {
                for (String warning : outcome.warnings()) {
                    recordLoadWarning(file, fileName, "Item: " + warning);
                }
                stacks.addAll(outcome.stacks());
                itemWeights.addAll(outcome.weights());
                // v2.0.1+: prices come from the central price
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
        return new ParsedTier<>(stacks, prices, itemWeights, unpricedRejected);
    }

    /** Parses an ISO-8601 date-time ("2026-12-24T00:00:00+08:00" or a UTC
     *  instant) into epoch millis; null when unparseable (the validator
     *  reports the mistake; the loader ignores the field). */
    private static Long parseIsoMillis(String raw) {
        try {
            return java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
            try {
                return java.time.Instant.parse(raw).toEpochMilli();
            } catch (RuntimeException ignored2) {
                return null;
            }
        }
    }

    /** Level of the tier with the given id in a parsed tier list (highest →
     *  lowest), or 0 when absent. */
    private static int tierLevelOf(List<? extends ParsedGrade<?>> grades, String tierId) {
        for (int i = 0; i < grades.size(); i++) {
            if (grades.get(i).id().equals(tierId)) {
                return BoxGrades.levelAt(grades.size(), i);
            }
        }
        return 0;
    }

    /**
     * JSON "random" is ordered grade1 -> grade5.
     */
    private int[] parseWeights(JsonObject json, Path file, String fileName) {
        int[] weights = BoxGrades.DEFAULT_WEIGHTS.clone();
        boolean[] explicitWeight = new boolean[5];
        if (json.has("random")) {
            JsonArray randomArr = json.getAsJsonArray("random");
            for (int i = 0; i < Math.min(randomArr.size(), 5); i++) {
                weights[i] = randomArr.get(i).getAsInt();
                explicitWeight[i] = true;
            }
        }
        for (int i = 0; i < 5; i++) {
            String gradeKey = "grade" + (i + 1);
            if (weights[i] <= 0) {
                if (weights[i] < 0) {
                    logger.warn("Negative weight {} for {} in box config, using default: {}",
                            weights[i], gradeKey, BoxGrades.DEFAULT_WEIGHTS[i]);
                    recordLoadError(file, fileName,
                            "Random[" + (i + 1) + "]: negative weight " + weights[i]
                                    + ", using default " + BoxGrades.DEFAULT_WEIGHTS[i]);
                    weights[i] = BoxGrades.DEFAULT_WEIGHTS[i];
                } else if (!explicitWeight[i]) {
                    // Slot absent from "random" (short array / no field):
                    // keep the per-grade default. An EXPLICIT 0 stays 0 —
                    // that tier legitimately never rolls, matching the
                    // weight:0 semantics of the v2.x custom grades array.
                    weights[i] = BoxGrades.DEFAULT_WEIGHTS[i];
                }
            } else if (weights[i] > 10000) {
                logger.warn("Weight {} for {} exceeds maximum, clamping to 10000", weights[i], gradeKey);
                recordLoadError(file, fileName,
                        "Random[" + (i + 1) + "]: weight " + weights[i]
                                + " exceeds 10000, clamped");
                weights[i] = 10000;
            }
        }
        return weights;
    }

    /** Parses a plain entity id list, or alternating id/drop-rate pairs
     *  (ids validated via the era hook, kept as strings). */
    private void parseEntities(JsonObject json, List<String> dropEntityIds,
                               Map<String, Float> entityDropRates,
                               Path file, String fileName) {
        if (!json.has("entity")) return;
        JsonArray entityArr = json.getAsJsonArray("entity");
        if (entityArr.size() == 0) return;

        if (entityArr.size() == 1 || (entityArr.get(1).isJsonPrimitive()
                && entityArr.get(1).getAsJsonPrimitive().isString())) {
            for (JsonElement elem : entityArr) {
                dropEntityIds.add(requireIdSafe(elem.getAsString(), "entity"));
            }
            return;
        }

        if ((entityArr.size() & 1) != 0) {
            logger.warn("Ignoring trailing entity entry without drop rate: {}",
                    entityArr.get(entityArr.size() - 1));
            recordLoadError(file, fileName,
                    "Entity: odd number of entries (" + entityArr.size()
                            + "), trailing id without drop rate ignored");
        }
        for (int i = 0; i + 1 < entityArr.size(); i += 2) {
            String entityIdStr = entityArr.get(i).getAsString();
            float rate = entityArr.get(i + 1).getAsFloat();
            String entityId = requireIdSafe(entityIdStr, "entity");
            dropEntityIds.add(entityId);
            entityDropRates.put(entityId, rate);
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
     * v2.0.1+: reads and parses the central price table
     * ({@code config/csbox/} {@link PriceTable#FILE_NAME}). A missing file is
     * a valid empty table; malformed entries are recorded as diagnostics
     * (visible via {@code /csbox info error}) and the remaining valid prices
     * still apply.
     */
    private PriceTable loadPriceTable(Path boxesDir) {
        Path file = boxesDir.resolve(PriceTable.FILE_NAME);
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
                logger.warn("Price table issue in {}: {}", file, issue);
                recordLoadError(file, PriceTable.FILE_NAME, "PriceTable: " + issue);
            }
            return table;
        } catch (Exception e) {
            logger.error("Failed to load price table {}: {}", file, e.getMessage());
            recordLoadError(file, PriceTable.FILE_NAME,
                    "Failed to load price table: " + e.getMessage());
            return PriceTable.EMPTY;
        }
    }

    /**
     * Resolve the terminal price of every parsed stack from the central price
     * table. A plain {@code id} entry is priced by that id — or by its
     * {@code id#variant} sub-key when the entry JSON spells a variant. A
     * {@code #tag} expansion prices each expanded member by its own registry
     * id (the era adapter supplies the per-stack id strings). Entries absent
     * from the table resolve to {@link PriceRange#UNPRICED}: loaders reject
     * unpriced id entries (no grade-default fallback); loot_table placeholders
     * stay unpriced and are simply never offered by the terminal.
     */
    private List<PriceRange> resolvePrices(JsonElement elem,
                                                  ItemResolution<T> outcome,
                                                  PriceTable priceTable) {
        List<PriceRange> prices = new ArrayList<>(outcome.ids().size());
        if (elem.isJsonObject() && elem.getAsJsonObject().has("id")) {
            String id = elem.getAsJsonObject().get("id").getAsString();
            String variant = PriceTable.variantId(elem.getAsJsonObject()).orElse(null);
            PriceRange range = priceTable.lookupRange(id, variant);
            if (range == null) {
                range = PriceRange.UNPRICED;
            }
            for (int k = 0; k < outcome.ids().size(); k++) {
                prices.add(range);
            }
        } else {
            for (int k = 0; k < outcome.ids().size(); k++) {
                String variant = outcome.variants() == null ? null : outcome.variants().get(k);
                PriceRange range = priceTable.lookupRange(outcome.ids().get(k), variant);
                prices.add(range != null ? range : PriceRange.UNPRICED);
            }
        }
        return prices;
    }
}
