package com.reclizer.csgobox.v1_21_1.box;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Structural validator for box JSON files. Runs after Gson syntax parsing and
 * before any field-level fallback logic in {@link BoxJsonLoader}, so problems
 * here surface as {@link LoadError} entries (visible via {@code /csbox info error})
 * rather than silent fallbacks.
 *
 * <p>The validator is a pure {@link JsonElement} function — it never touches
 * Minecraft types — so the same source compiles against every platform with
 * only the package name changing.</p>
 */
public final class BoxJsonSchemaValidator {

    /** A single structural problem found in a box JSON file.
     *  {@code field} uses JSON-path notation like {@code "random[2]"} or
     *  {@code "entity[1]"} so the player can locate the offending element. */
    public record SchemaIssue(String field, String reason) {}

    private BoxJsonSchemaValidator() {
    }

    /**
     * Validates the structural shape of a parsed box JSON. Returns an empty
     * list when the structure is acceptable. Non-fatal — callers decide whether
     * to abort loading or fall back to defaults.
     */
    public static List<SchemaIssue> validate(JsonObject json) {
        List<SchemaIssue> issues = new ArrayList<>();
        validateRandom(json, issues);
        validateDrop(json, issues);
        validateGrades(json, issues);
        validateEntity(json, issues);
        validateNameColorPrefix(json, issues);
        return issues;
    }

    private static void validateRandom(JsonObject json, List<SchemaIssue> issues) {
        if (!json.has("random")) return;
        JsonElement elem = json.get("random");
        if (!elem.isJsonArray()) {
            issues.add(new SchemaIssue("random",
                    "Expected array of 5 integers, got " + typeOf(elem)));
            return;
        }
        JsonArray arr = elem.getAsJsonArray();
        if (arr.size() != 5) {
            issues.add(new SchemaIssue("random",
                    "Expected exactly 5 entries (grade1..grade5), got " + arr.size()));
        }
        for (int i = 0; i < arr.size(); i++) {
            JsonElement e = arr.get(i);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
                issues.add(new SchemaIssue("random[" + i + "]",
                        "Expected integer, got " + typeOf(e)));
            }
        }
    }

    private static void validateDrop(JsonObject json, List<SchemaIssue> issues) {
        if (!json.has("drop")) return;
        JsonElement elem = json.get("drop");
        if (!elem.isJsonPrimitive() || !elem.getAsJsonPrimitive().isNumber()) {
            issues.add(new SchemaIssue("drop",
                    "Expected number 0.0-1.0, got " + typeOf(elem)));
        }
    }

    private static void validateGrades(JsonObject json, List<SchemaIssue> issues) {
        for (int g = 1; g <= 5; g++) {
            String key = "grade" + g;
            if (!json.has(key)) continue;
            JsonElement elem = json.get(key);
            if (!elem.isJsonArray()) {
                issues.add(new SchemaIssue(key,
                        "Expected array of items, got " + typeOf(elem)));
            }
        }
    }

    private static void validateEntity(JsonObject json, List<SchemaIssue> issues) {
        if (!json.has("entity")) return;
        JsonElement elem = json.get("entity");
        if (!elem.isJsonArray()) {
            issues.add(new SchemaIssue("entity",
                    "Expected array, got " + typeOf(elem)));
            return;
        }
        JsonArray arr = elem.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement e = arr.get(i);
            boolean isString = e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
            boolean isNumber = e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
            if (!isString && !isNumber) {
                issues.add(new SchemaIssue("entity[" + i + "]",
                        "Expected entity id (string) or drop rate (number), got " + typeOf(e)));
            }
        }
    }

    private static void validateNameColorPrefix(JsonObject json, List<SchemaIssue> issues) {
        if (!json.has("name")) return;
        JsonElement elem = json.get("name");
        if (!elem.isJsonPrimitive() || !elem.getAsJsonPrimitive().isString()) return;
        String raw = elem.getAsString();
        if (raw.length() < 7 || raw.charAt(0) != '#') return;
        String tail = raw.substring(1, 7);
        if (!tail.matches("[0-9A-Fa-f]{6}")) return;
        if (raw.length() == 7 || raw.charAt(7) != ' ') {
            issues.add(new SchemaIssue("name",
                    "Color prefix '#" + tail + "' must be followed by a single ASCII space"));
        }
    }

    private static String typeOf(JsonElement e) {
        if (e == null || e.isJsonNull()) return "null";
        if (e.isJsonArray()) return "array";
        if (e.isJsonObject()) return "object";
        if (e.isJsonPrimitive()) {
            var p = e.getAsJsonPrimitive();
            if (p.isString()) return "string";
            if (p.isNumber()) return "number";
            if (p.isBoolean()) return "boolean";
        }
        return e.toString();
    }
}
