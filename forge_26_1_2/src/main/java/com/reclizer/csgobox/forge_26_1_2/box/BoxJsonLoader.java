package com.reclizer.csgobox.forge_26_1_2.box;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.BoxJsonSchemaValidator;
import com.reclizer.csgobox.forge_26_1_2.CsgoBox;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and writes box definitions under config/csbox.
 *
 * <p>Per-item parsing and serialization is delegated to {@link BoxItemCodec};
 * default-config generation is delegated to {@link BoxDefaults}. This class
 * focuses on directory I/O, top-level JSON shape, and registration.</p>
 */
public final class BoxJsonLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path BOXES_DIR = FMLPaths.CONFIGDIR.get().resolve("csbox");

    private static final String[] GRADE_IDS = {"classified", "restricted", "mil_spec", "industrial", "consumer"};
    private static final String[] GRADE_NAMES = {"保密", "受限", "军规级", "工业级", "消费级"};
    private static final int[] GRADE_COLORS = {0xFFD32CE6, 0xFF8847FF, 0xFF4B69FF, 0xFF5E98D9, 0xFFB0C3D9};

    private static final List<LoadError> LAST_LOAD_ERRORS = new ArrayList<>();

    /** Matches an optional leading hex color in the box "name" field, e.g.
     *  {@code "#FF5555 高级补给箱"}. Group 1 = 6 hex digits, group 2 = display text. */
    private static final Pattern NAME_COLOR_PREFIX =
            Pattern.compile("^#([0-9A-Fa-f]{6}) (.*)$");

    private BoxJsonLoader() {
    }

    /** Parsed result of a box "name" value: display text + optional 0xRRGGBB color. */
    private record ParsedName(String text, OptionalInt color) {}

    /** Strips an optional {@code "#RRGGBB "} prefix from a name and returns the
     *  bare text + 0xRRGGBB color. If the prefix is absent, malformed, or the
     *  remaining text is empty, the original string is returned as the text
     *  and no color is set (matches the pre-color behavior). */
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

    /** Inverse of {@link #parseColoredName}: serializes a styled Component back
     *  to a "name" string, prepending the color as a hex prefix when the
     *  Component carries one. */
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
     * Re-scan the box directory and update {@link BoxRegistry} in place.
     *
     * <p>Unlike {@link #loadAll()} this does NOT call {@code BoxRegistry.clear()}:
     * <ul>
     *   <li>Files that fail to parse leave the previous {@link BoxDefinition}
     *       in place (no wholesale data loss from one bad JSON).</li>
     *   <li>Files that parse successfully {@code put}-overwrite the entry with
     *       the same id.</li>
     *   <li>Files that disappeared since the previous load are explicitly
     *       {@link BoxRegistry#remove removed} so the registry mirrors disk.</li>
     * </ul>
     *
     * <p>Used by the {@code /csbox reload} command and by the common
     * {@code BoxFileWatcher} when JSON files change on disk.</p>
     *
     * <p>Does NOT call {@link BoxDefaults#writeTutorialIfMissing} so that
     * auto-reload never resurrects the sample config the user deleted.</p>
     */
    public static void reloadPreserving() {
        LAST_LOAD_ERRORS.clear();

        if (!Files.exists(BOXES_DIR)) {
            CsgoBox.LOGGER.info("Reload preserving skipped: {} does not exist", BOXES_DIR);
            return;
        }

        Set<Identifier> previousIds = new HashSet<>(BoxRegistry.getIds());
        Set<Identifier> seenIds = new HashSet<>();
        int[] loaded = {0};
        int[] skipped = {0};

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(BOXES_DIR, "*.json")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                if (fileName.startsWith("_")) {
                    continue;
                }
                String boxIdStr = fileName.substring(0, fileName.length() - 5);
                Identifier boxId;
                try {
                    boxId = Identifier.fromNamespaceAndPath(CsgoBox.MODID, boxIdStr);
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

        Set<Identifier> toRemove = new HashSet<>(previousIds);
        toRemove.removeAll(seenIds);
        int removed = 0;
        for (Identifier id : toRemove) {
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

    /** Overload for the common case where the error has no JSON position. */
    private static void recordLoadError(Path file, String fileName, String reason) {
        recordLoadError(file, fileName, reason, -1, -1);
    }

    /**
     * Extracts the {@code at line N column M} tuple from a Gson error message.
     * Gson 2.13+ removed {@code JsonSyntaxException.getLocation()}, so we have
     * to fish the numbers out of the formatted message. Returns {@code {-1,-1}}
     * when the pattern is not found.
     */
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
            Identifier keyItem = parseIdentifierSafe(getString(json, "key", "csgobox:csgo_key0"), "key");
            float dropRate = getFloat(json, "drop", 0.12F);
            int[] weights = parseWeights(json, file, fileName);

            List<Identifier> dropEntityIds = new ArrayList<>();
            Map<Identifier, Float> entityDropRates = new HashMap<>();
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
                    Identifier.parse("csgobox:" + boxIdStr), parsedName.text());
            parsedName.color().ifPresent(builder::nameColor);
            builder.key(keyItem);
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

            return Optional.of(builder.build());
        } catch (IllegalArgumentException e) {
            CsgoBox.LOGGER.error("Invalid identifier in {}: {}", file, e.getMessage());
            recordLoadError(file, fileName,
                    "Invalid identifier: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parses an {@link Identifier} while normalizing any thrown exception to
     * {@link IllegalArgumentException}. MC's {@code Identifier.parse} throws
     * a Mojang-specific {@code ResourceLocationException} whose concrete type
     * changes between versions; the rest of this loader only relies on the
     * IAE contract.
     */
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
        int[] weights = BoxDefinition.DEFAULT_WEIGHTS.clone();
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
                            weights[i], gradeKey, BoxDefinition.DEFAULT_WEIGHTS[i]);
                    recordLoadError(file, fileName,
                            "Random[" + (i + 1) + "]: negative weight " + weights[i]
                                    + ", using default " + BoxDefinition.DEFAULT_WEIGHTS[i]);
                }
                weights[i] = BoxDefinition.DEFAULT_WEIGHTS[i];
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

    /**
     * Parses either a plain entity id list or alternating entity id/drop-rate pairs.
     */
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
            for (Map.Entry<Identifier, Float> entry : def.entityDropRates().entrySet()) {
                entity.add(entry.getKey().toString());
                entity.add(entry.getValue());
            }
        } else {
            for (Identifier e : def.dropEntities()) {
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

    public static void deleteFile(Identifier boxId) {
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
