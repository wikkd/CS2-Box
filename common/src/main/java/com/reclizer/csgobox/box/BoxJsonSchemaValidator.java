package com.reclizer.csgobox.box;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Structural validator for box JSON files. Runs after Gson syntax parsing and
 * before any field-level fallback logic in the box loader, so problems here
 * surface as {@link LoadError} entries (visible via {@code /csbox info error})
 * rather than silent fallbacks.
 *
 * <p>Pure {@link JsonElement} function — no Minecraft or platform imports —
 * so the same source compiles in the common module and is reused by every
 * platform loader.</p>
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
        validateType(json, issues);
        validateRandom(json, issues);
        validateDrop(json, issues);
        validateGrades(json, issues);
        validateEntity(json, issues);
        validateNameColorPrefix(json, issues);
        validateRemovedPriceField(json, issues);
        validateV21Fields(json, issues);
        return issues;
    }

    /**
     * v2.1.0 top-level fields: enabled / requires / icon / discount / stock /
     * restock_minutes / max_per_player / cooldown_seconds / permission, plus
     * the per-item weight / count-range / tag-ref / loot_table / enchant
     * additions (validated inside {@link #validateV21Fields}).
     */
    private static void validateV21Fields(JsonObject json, List<SchemaIssue> issues) {
        if (json.has("enabled")) {
            JsonElement en = json.get("enabled");
            if (!en.isJsonPrimitive() || !en.getAsJsonPrimitive().isBoolean()) {
                issues.add(new SchemaIssue("enabled", "Expected boolean, got " + typeOf(en)));
            }
        }
        if (json.has("requires")) {
            JsonElement req = json.get("requires");
            if (!req.isJsonArray()) {
                issues.add(new SchemaIssue("requires", "Expected array of mod ids, got " + typeOf(req)));
            } else {
                for (int i = 0; i < req.getAsJsonArray().size(); i++) {
                    JsonElement e = req.getAsJsonArray().get(i);
                    if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
                        issues.add(new SchemaIssue("requires[" + i + "]",
                                "Expected mod id string, got " + typeOf(e)));
                    }
                }
            }
        }
        if (json.has("icon")) {
            JsonElement icon = json.get("icon");
            // Accepts a string item-model id OR a numeric CustomModelData value
            // (the loader coerces numbers to their string form).
            if (!icon.isJsonPrimitive()
                    || (!icon.getAsJsonPrimitive().isString() && !icon.getAsJsonPrimitive().isNumber())) {
                issues.add(new SchemaIssue("icon",
                        "Expected item-model id string or integer CustomModelData, got " + typeOf(icon)));
            }
        }
        if (json.has("discount")) {
            JsonElement d = json.get("discount");
            if (!d.isJsonPrimitive() || !d.getAsJsonPrimitive().isNumber()) {
                issues.add(new SchemaIssue("discount", "Expected number 0.0-1.0, got " + typeOf(d)));
            }
        }
        for (String numField : new String[]{"stock", "restock_minutes", "max_per_player", "cooldown_seconds"}) {
            if (!json.has(numField)) continue;
            JsonElement e = json.get(numField);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
                issues.add(new SchemaIssue(numField, "Expected integer, got " + typeOf(e)));
                continue;
            }
            double v = e.getAsDouble();
            if (v < -1 || v != Math.floor(v)) {
                issues.add(new SchemaIssue(numField, "Expected non-negative integer (or -1 = unlimited), got " + v));
            }
        }
        if (json.has("permission") && !json.get("permission").isJsonPrimitive()) {
            issues.add(new SchemaIssue("permission", "Expected permission node string, got " + typeOf(json.get("permission"))));
        }

        // Per-item fields (grade1..grade5 item objects).
        for (int g = 1; g <= 5; g++) {
            String key = "grade" + g;
            if (!json.has(key) || !json.get(key).isJsonArray()) continue;
            JsonArray arr = json.get(key).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonElement e = arr.get(i);
                if (!e.isJsonObject()) continue;
                JsonObject item = e.getAsJsonObject();
                String base = key + "[" + i + "]";

                // Source exclusivity: id / tag / loot_table (only one).
                int sources = 0;
                if (item.has("id")) sources++;
                if (item.has("tag") && item.get("tag").isJsonPrimitive()
                        && item.get("tag").getAsString().startsWith("#")) sources++;
                if (item.has("loot_table")) sources++;
                if (sources > 1) {
                    issues.add(new SchemaIssue(base,
                            "An item entry must declare exactly one of 'id', '#tag' or 'loot_table'"));
                }

                if (item.has("weight")) {
                    JsonElement w = item.get("weight");
                    if (!w.isJsonPrimitive() || !w.getAsJsonPrimitive().isNumber()) {
                        issues.add(new SchemaIssue(base + ".weight", "Expected integer, got " + typeOf(w)));
                    } else if (w.getAsDouble() < 0) {
                        issues.add(new SchemaIssue(base + ".weight", "Expected non-negative integer, got " + w.getAsDouble()));
                    }
                }
                if (item.has("count")) {
                    JsonElement c = item.get("count");
                    if (c.isJsonPrimitive() && c.getAsJsonPrimitive().isNumber()) {
                        if (c.getAsDouble() < 1) {
                            issues.add(new SchemaIssue(base + ".count", "Expected count >= 1, got " + c.getAsDouble()));
                        }
                    } else if (c.isJsonArray()) {
                        JsonArray ca = c.getAsJsonArray();
                        if (ca.size() != 2 || !ca.get(0).isJsonPrimitive() || !ca.get(1).isJsonPrimitive()
                                || !ca.get(0).getAsJsonPrimitive().isNumber()
                                || !ca.get(1).getAsJsonPrimitive().isNumber()) {
                            issues.add(new SchemaIssue(base + ".count",
                                    "Expected integer or [min,max] array, got " + typeOf(c)));
                        } else if (ca.get(0).getAsDouble() < 1 || ca.get(1).getAsDouble() < ca.get(0).getAsDouble()) {
                            issues.add(new SchemaIssue(base + ".count",
                                    "Invalid count range [" + ca.get(0).getAsDouble() + ","
                                            + ca.get(1).getAsDouble() + "]"));
                        }
                    } else {
                        issues.add(new SchemaIssue(base + ".count",
                                "Expected integer or [min,max] array, got " + typeOf(c)));
                    }
                }
                if (item.has("loot_table") && !item.get("loot_table").isJsonPrimitive()) {
                    issues.add(new SchemaIssue(base + ".loot_table",
                            "Expected loot table id string, got " + typeOf(item.get("loot_table"))));
                }
                if (item.has("enchant")) {
                    JsonElement en = item.get("enchant");
                    boolean okEnchant = (en.isJsonPrimitive() && en.getAsJsonPrimitive().isBoolean())
                            || en.isJsonObject();
                    if (!okEnchant) {
                        issues.add(new SchemaIssue(base + ".enchant",
                                "Expected true or an object {id, level}, got " + typeOf(en)));
                    }
                }
                // price validation already covered by validateItemPrices.
            }
        }
    }

    /**
     * The {@code type} field is the single source of truth for box kind
     * (v2.0.0): {@code "terminal"} registers an {@code ItemTerminal},
     * {@code "csbox"} (or absent) a regular crate. Fields are strictly
     * separated between the two kinds — a terminal must NOT declare a
     * {@code key} field (terminals have no key concept), so a leftover
     * {@code "key": "minecraft:air"} from the pre-v2.0.0 format surfaces
     * here instead of silently changing behavior.
     */
    private static void validateType(JsonObject json, List<SchemaIssue> issues) {
        if (!json.has("type")) {
            return;
        }
        JsonElement elem = json.get("type");
        if (!elem.isJsonPrimitive() || !elem.getAsJsonPrimitive().isString()) {
            issues.add(new SchemaIssue("type",
                    "Expected 'csbox' or 'terminal', got " + typeOf(elem)));
            return;
        }
        String type = elem.getAsString();
        if (!"csbox".equals(type) && !"terminal".equals(type)) {
            issues.add(new SchemaIssue("type",
                    "Expected 'csbox' or 'terminal', got \"" + type + "\""));
            return;
        }
        if ("terminal".equals(type) && json.has("key")) {
            issues.add(new SchemaIssue("key",
                    "Terminal machines have no key field (strict separation); remove \"key\""));
        }
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
        boolean anyPositive = false;
        for (int i = 0; i < arr.size(); i++) {
            JsonElement e = arr.get(i);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
                issues.add(new SchemaIssue("random[" + i + "]",
                        "Expected integer, got " + typeOf(e)));
            } else if (e.getAsDouble() > 0) {
                anyPositive = true;
            }
        }
        // v2.1.1: a box with no positive grade weight can never roll a grade;
        // opens are rejected server-side (no key/box consumption). Surface it
        // here so authors see the mistake in /csbox validate instead of a
        // silent no-op box.
        if (!anyPositive) {
            issues.add(new SchemaIssue("random",
                    "All grade weights are zero/negative — the box can never drop; opens are rejected until at least one weight is positive"));
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

    /**
     * v2.1.0+: the per-item {@code price} field is removed from box JSON —
     * terminal prices are centrally managed in {@code config/csbox/}
     * {@link PriceTable#FILE_NAME} (keyed by item id, optional {@code
     * #variant}). A leftover {@code price} is reported; on the next
     * load/reload it is auto-migrated into the table (conflicting prices are
     * averaged, existing table entries win) and stripped from the box file.
     * Until then the item loads with the grade default price (schema issues
     * are diagnostic, not load-blocking).
     */
    private static void validateRemovedPriceField(JsonObject json, List<SchemaIssue> issues) {
        for (int g = 1; g <= 5; g++) {
            String key = "grade" + g;
            if (!json.has(key)) continue;
            JsonElement elem = json.get(key);
            if (!elem.isJsonArray()) continue;
            JsonArray arr = elem.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonElement e = arr.get(i);
                if (!e.isJsonObject()) continue;
                JsonObject item = e.getAsJsonObject();
                if (item.has("price")) {
                    issues.add(new SchemaIssue(key + "[" + i + "].price",
                            "The 'price' field is removed — it is auto-migrated into "
                                    + PriceTable.FILE_NAME + " on the next load/reload "
                                    + "(conflicting prices are averaged, existing table entries "
                                    + "win; this field is then stripped from the box file). "
                                    + "Until then this item falls back to the grade default price."));
                }
            }
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
