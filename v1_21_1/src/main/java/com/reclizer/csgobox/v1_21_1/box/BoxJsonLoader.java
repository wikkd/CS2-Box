package com.reclizer.csgobox.v1_21_1.box;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxJsonSchemaValidator;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
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

    /** Inverse of {@link #parseColoredName}: Component -> "name" string with hex color prefix. */
    private static String serializeColoredName(net.minecraft.network.chat.Component name) {
        if (name == null) return "";
        String text = name.getString();
        net.minecraft.network.chat.TextColor tc = name.getStyle().getColor();
        if (tc == null) return text;
        int rgb = tc.getValue() & 0xFFFFFF;
        return String.format("#%06X %s", rgb, text);
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

        // First-run defaults, before scanning existing configs.
        BoxDefaults.writeDefaultTerminalIfMissing(BOXES_DIR);
        // Pre-v1.0.8 terminal.json migration (no "type" field); must run before parsing.
        BoxDefaults.upgradeLegacyTerminalConfig(BOXES_DIR);

        BoxDefaults.writeTutorialIfMissing(BOXES_DIR);

        List<Path> scannedFiles = new ArrayList<>();
        int[] loaded = {0};
        int[] skipped = {0};
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(BOXES_DIR, "*.json")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                if (fileName.startsWith("_")) {
                    continue;
                }
                scannedFiles.add(file);
                try {
                    Optional<BoxDefinition> result = loadFromFile(file);
                    if (result.isEmpty()) {
                        // loadFromFile already logged the per-grade or per-item reason.
                        skipped[0]++;
                        continue;
                    }
                    BoxRegistry.register(result.get());
                    loaded[0]++;
                    CsgoBox.LOGGER.info("Loaded box from JSON: {} -> {}", file.getFileName(), result.get().id());
                } catch (Exception e) {
                    CsgoBox.LOGGER.error("Failed to load box JSON file: {}", file, e);
                    skipped[0]++;
                    recordLoadError(file, fileName, "Failed to load box JSON: " + e.getMessage());
                }
            }
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
     * resurrects the deleted sample tutorial, but re-runs terminal
     * defaults and the legacy migration so a deleted terminal.json stays
     * loadable.
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
        BoxDefaults.writeDefaultTerminalIfMissing(BOXES_DIR);
        BoxDefaults.upgradeLegacyTerminalConfig(BOXES_DIR);

        Set<ResourceLocation> previousIds = new HashSet<>(BoxRegistry.getIds());
        Set<ResourceLocation> seenIds = new HashSet<>();
        int[] loaded = {0};
        int[] skipped = {0};

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(BOXES_DIR, "*.json")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                if (fileName.startsWith("_")) {
                    continue;
                }
                String boxIdStr = fileName.substring(0, fileName.length() - 5);
                ResourceLocation boxId;
                try {
                    boxId = ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, boxIdStr);
                } catch (Exception e) {
                    CsgoBox.LOGGER.error("Invalid box id from filename {}: {}", file, e.getMessage());
                    recordLoadError(file, fileName, "Invalid identifier: " + e.getMessage());
                    skipped[0]++;
                    continue;
                }
                seenIds.add(boxId);
                try {
                    Optional<BoxDefinition> result = loadFromFile(file);
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
            }
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to list box JSON files in {}", BOXES_DIR, e);
            return;
        }

        Set<ResourceLocation> toRemove = new HashSet<>(previousIds);
        toRemove.removeAll(seenIds);
        int removed = 0;
        for (ResourceLocation id : toRemove) {
            BoxRegistry.remove(id);
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

    /** Records a non-fatal warning (yellow in chat) with no JSON position. */
    private static void recordLoadWarning(Path file, String fileName, String reason) {
        String boxId = fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
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

    private static Optional<BoxDefinition> loadFromFile(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String boxIdStr = fileName.substring(0, fileName.length() - 5);

        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file)) {
            json = GSON.fromJson(reader, JsonObject.class);
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
            ParsedName parsedName = parseColoredName(getString(json, "name", boxIdStr));
            String type = getString(json, "type", "csbox");
            if (!"csbox".equals(type) && !"terminal".equals(type)) {
                type = "csbox";
            }
            // v1.0.8: "type" is the single source of truth; a terminal.json
            // without "type":"terminal" would silently become a keyless
            // crate, so refuse to load it.
            if (boxIdStr.equals("terminal") && !"terminal".equals(type)) {
                String msg = "terminal.json must declare \"type\": \"terminal\" "
                        + "(v1.0.8+: type is the single registration source; terminals have no key field)";
                CsgoBox.LOGGER.error("Skipping {}: {}", file, msg);
                recordLoadError(file, fileName, msg);
                return Optional.empty();
            }
            float dropRate = getFloat(json, "drop", 0.12F);

            int[] weights = parseWeights(json, file, fileName);

            List<ResourceLocation> dropEntityIds = new ArrayList<>();
            Map<ResourceLocation, Float> entityDropRates = new HashMap<>();
            parseEntities(json, dropEntityIds, entityDropRates, file, fileName);

            List<GradeGroup> grades = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String gradeKey = "grade" + (5 - i);
                if (json.has(gradeKey)) {
                    JsonArray itemsArr = json.getAsJsonArray(gradeKey);
                    List<ItemStack> items = new ArrayList<>();
                    for (JsonElement elem : itemsArr) {
                        BoxItemCodec.ParseOutcome outcome = BoxItemCodec.parseItem(elem);
                        if (outcome.isSuccess()) {
                            for (String warning : outcome.warnings()) {
                                recordLoadWarning(file, fileName, "Item: " + warning);
                            }
                            items.add(outcome.stack());
                        } else {
                            recordLoadError(file, fileName,
                                    "Item: " + outcome.error());
                        }
                    }
                    if (!items.isEmpty()) {
                        grades.add(new GradeGroup(GRADE_IDS[i], GRADE_NAMES[i], GRADE_COLORS[i], weights[4 - i], items));
                    }
                }
            }

            if (grades.isEmpty()) {
                CsgoBox.LOGGER.warn("Skipping box '{}': all items failed to parse (missing mods?)", boxIdStr);
                recordLoadError(file, fileName,
                        "All items failed to parse (missing mods?)");
                return Optional.empty();
            }

            BoxDefinition.Builder builder = BoxDefinition.builder(
                    ResourceLocation.parse("csgobox:" + boxIdStr), parsedName.text());
            parsedName.color().ifPresent(builder::nameColor);
            builder.type(type);
            if (!"terminal".equals(type)) {
                builder.key(parseIdentifierSafe(getString(json, "key", "csgobox:csgo_key0"), "key"));
            }
            builder.dropRate(dropRate);
            for (ResourceLocation entityId : dropEntityIds) {
                Float rate = entityDropRates.get(entityId);
                if (rate != null) {
                    builder.entityDropRate(entityId.toString(), rate);
                }
                builder.dropFrom(entityId.toString());
            }
            for (GradeGroup grade : grades) {
                builder.addGrade(grade);
            }

            return Optional.of(builder.build());
        } catch (IllegalArgumentException e) {
            CsgoBox.LOGGER.error("Invalid identifier in {}: {}", file, e.getMessage());
            recordLoadError(file, fileName,
                    "Invalid identifier: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Parses an {@link ResourceLocation}, normalizing any exception to
     *  {@link IllegalArgumentException} (the MC exception type varies by version). */
    private static ResourceLocation parseIdentifierSafe(String value, String fieldName) {
        try {
            return ResourceLocation.parse(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "' = \"" + value + "\": " + e.getMessage(), e);
        }
    }

    /** JSON "random" is ordered grade1 -> grade5. */
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
    private static void parseEntities(JsonObject json, List<ResourceLocation> dropEntityIds,
                                       Map<ResourceLocation, Float> entityDropRates,
                                       Path file, String fileName) {
        if (!json.has("entity")) return;
        JsonArray entityArr = json.getAsJsonArray("entity");
        if (entityArr.size() == 0) return;

        if (entityArr.size() == 1 || (entityArr.get(1).isJsonPrimitive()
                && entityArr.get(1).getAsJsonPrimitive().isString())) {
            for (JsonElement elem : entityArr) {
                ResourceLocation entityId = parseIdentifierSafe(elem.getAsString(), "entity");
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
            ResourceLocation entityId = parseIdentifierSafe(entityIdStr, "entity");
            dropEntityIds.add(entityId);
            entityDropRates.put(entityId, rate);
        }
    }

    public static void saveToFile(BoxDefinition def) {
        try {
            Files.createDirectories(BOXES_DIR);
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to create boxes directory for save", e);
            return;
        }

        Path file = BOXES_DIR.resolve(def.id().getPath() + ".json");
        Path tempFile = BOXES_DIR.resolve(def.id().getPath() + ".json.tmp");

        JsonObject json = new JsonObject();
        json.addProperty("name", serializeColoredName(def.name()));
        json.addProperty("key", def.keyItem().toString());
        json.addProperty("drop", def.dropRate());

        JsonArray random = new JsonArray();
        for (int i = 4; i >= 0; i--) {
            GradeGroup g = def.findGrade(GRADE_IDS[i]).orElse(null);
            random.add(g != null ? g.weight() : 0);
        }
        json.add("random", random);

        JsonArray entity = new JsonArray();
        if (!def.entityDropRates().isEmpty()) {
            for (Map.Entry<ResourceLocation, Float> entry : def.entityDropRates().entrySet()) {
                entity.add(entry.getKey().toString());
                entity.add(entry.getValue());
            }
        } else {
            for (ResourceLocation e : def.dropEntities()) {
                entity.add(e.toString());
                entity.add(1);
            }
        }
        json.add("entity", entity);

        for (int i = 0; i < 5; i++) {
            String gradeKey = "grade" + (5 - i);
            GradeGroup g = def.findGrade(GRADE_IDS[i]).orElse(null);
            JsonArray itemsArr = new JsonArray();
            if (g != null) {
                for (ItemStack item : g.items()) {
                    itemsArr.add(BoxItemCodec.serializeItemStack(item));
                }
            }
            json.add(gradeKey, itemsArr);
        }

        try (Writer writer = Files.newBufferedWriter(tempFile)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to save box JSON: {}", file, e);
            return;
        }

        try {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            CsgoBox.LOGGER.info("Saved box to JSON: {} -> {}", def.id(), file);
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to finalize box JSON: {}", file, e);
        }
    }

    public static void deleteFile(ResourceLocation boxId) {
        Path file = BOXES_DIR.resolve(boxId.getPath() + ".json").normalize();
        if (!file.startsWith(BOXES_DIR.normalize())) {
            CsgoBox.LOGGER.warn("Rejected path traversal attempt: {}", boxId.getPath());
            return;
        }
        try {
            if (Files.exists(file)) {
                Files.delete(file);
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
}
