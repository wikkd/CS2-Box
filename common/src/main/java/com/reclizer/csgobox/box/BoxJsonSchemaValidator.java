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
        validatePity(json, issues);
        validateDrop(json, issues);
        validateGrades(json, issues);
        validateGradesArray(json, issues);
        validateEntity(json, issues);
        validateNameColorPrefix(json, issues);
        validateV2xGameplayFields(json, issues);
        validateRemovedPriceField(json, issues);
        validateV21Fields(json, issues);
        validateTopLevelKeys(json, issues);
        return issues;
    }

    /**
     * Every top-level field the loader reads (or has read historically —
     * legacy keys are listed so an old config degrades to a pointed warning
     * instead of a silent ignore). Anything else is reported: typos in
     * hand-written configs otherwise load with all defaults and are nearly
     * impossible to spot.
     */
    private static final java.util.Set<String> KNOWN_TOP_KEYS = java.util.Set.of(
            "name", "key", "drop", "type", "random", "entity",
            "grade1", "grade2", "grade3", "grade4", "grade5", "grades",
            "enabled", "requires", "icon", "discount", "stock",
            "max_per_player", "cooldown_seconds", "permission", "pity",
            "format_version", "restock_minutes",
            "hide_odds", "allow_bulk", "keys", "wear", "bonus",
            "announce_above", "available_from", "available_until", "open_cost",
            "sound", "negotiation", "upgrade");

    /** Tier-level fields of the custom {@code "grades"} array. */
    private static final java.util.Set<String> KNOWN_TIER_KEYS = java.util.Set.of(
            "id", "name", "color", "weight", "items", "stock");

    /** Item-level fields consumed by the item codec ({@code price} is
     *  whitelisted here because {@link #validateRemovedPriceField} reports it
     *  with a dedicated migration message). */
    private static final java.util.Set<String> KNOWN_ITEM_KEYS = java.util.Set.of(
            "id", "tag", "loot_table", "count", "weight", "enchant",
            "components", "price");

    private static void validateTopLevelKeys(JsonObject json, List<SchemaIssue> issues) {
        for (String key : json.keySet()) {
            if (KNOWN_TOP_KEYS.contains(key)) continue;
            issues.add(new SchemaIssue(key,
                    "Unknown top-level field — a typo is loaded with defaults, "
                            + "and fields removed in a later format are ignored"));
        }
        if (json.has("format_version")) {
            JsonElement fv = json.get("format_version");
            if (!fv.isJsonPrimitive() || !fv.getAsJsonPrimitive().isNumber()
                    || fv.getAsDouble() != Math.floor(fv.getAsDouble()) || fv.getAsDouble() < 1) {
                issues.add(new SchemaIssue("format_version",
                        "Expected a positive integer schema version, got " + typeOf(fv)));
            }
        }
    }

    /**
     * v2.0.1 top-level fields: enabled / requires / icon / discount / stock /
     * max_per_player / cooldown_seconds / permission, plus
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
        for (String numField : new String[]{"stock", "max_per_player", "cooldown_seconds"}) {
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
                validateItemEntry(e.getAsJsonObject(), key + "[" + i + "]", issues);
            }
        }
    }

    /** Shared per-item-entry validation (legacy gradeN arrays and the custom
     *  {@code grades[].items} arrays use the same item codec). */
    private static void validateItemEntry(JsonObject item, String base, List<SchemaIssue> issues) {
        // Unknown item-level fields: the codec ignores anything it
        // does not read, so a typo silently degrades to defaults.
        for (String itemKey : item.keySet()) {
            if (KNOWN_ITEM_KEYS.contains(itemKey)) continue;
            issues.add(new SchemaIssue(base + "." + itemKey,
                    "Unknown item field — a typo is loaded with defaults"));
        }

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

    /**
     * v2.x custom tier array ({@code "grades"}): ordered lowest → highest,
     * each entry {@code {id, name?, color?, weight?, items}}. When present it
     * replaces the legacy {@code random}/{@code grade1..5} keys entirely —
     * those are reported as ignored instead of silently doing nothing.
     */
    private static void validateGradesArray(JsonObject json, List<SchemaIssue> issues) {
        if (!json.has("grades")) {
            return;
        }
        JsonElement elem = json.get("grades");
        if (!elem.isJsonArray()) {
            issues.add(new SchemaIssue("grades",
                    "Expected an array of tier objects (lowest tier first), got " + typeOf(elem)));
            return;
        }
        JsonArray arr = elem.getAsJsonArray();
        if (arr.isEmpty()) {
            issues.add(new SchemaIssue("grades", "Expected at least 1 tier"));
        }
        if (arr.size() > BoxGrades.MAX_GRADES) {
            issues.add(new SchemaIssue("grades",
                    "Too many tiers: " + arr.size() + " (max " + BoxGrades.MAX_GRADES + ")"));
        }
        java.util.Set<String> seenIds = new java.util.LinkedHashSet<>();
        for (int t = 0; t < arr.size(); t++) {
            JsonElement te = arr.get(t);
            String base = "grades[" + t + "]";
            if (!te.isJsonObject()) {
                issues.add(new SchemaIssue(base, "Expected a tier object, got " + typeOf(te)));
                continue;
            }
            JsonObject tier = te.getAsJsonObject();
            for (String tierKey : tier.keySet()) {
                if (KNOWN_TIER_KEYS.contains(tierKey)) continue;
                issues.add(new SchemaIssue(base + "." + tierKey,
                        "Unknown tier field — a typo is loaded with defaults"));
            }
            if (tier.has("id")) {
                JsonElement id = tier.get("id");
                if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()
                        || id.getAsString().isBlank()) {
                    issues.add(new SchemaIssue(base + ".id", "Expected a non-empty id string"));
                } else if (!seenIds.add(id.getAsString())) {
                    issues.add(new SchemaIssue(base + ".id",
                            "Duplicate tier id '" + id.getAsString() + "' — pity and /csbox info key off it"));
                }
            } else {
                issues.add(new SchemaIssue(base + ".id", "Missing required field 'id'"));
            }
            if (tier.has("name") && (!tier.get("name").isJsonPrimitive()
                    || !tier.get("name").getAsJsonPrimitive().isString())) {
                issues.add(new SchemaIssue(base + ".name",
                        "Expected a display name string, got " + typeOf(tier.get("name"))));
            }
            if (tier.has("color")) {
                JsonElement c = tier.get("color");
                if (!c.isJsonPrimitive() || !c.getAsJsonPrimitive().isString()
                        || !c.getAsString().matches("#[0-9A-Fa-f]{6}")) {
                    issues.add(new SchemaIssue(base + ".color",
                            "Expected \"#RRGGBB\", got " + typeOf(c)));
                }
            }
            if (tier.has("weight")) {
                JsonElement w = tier.get("weight");
                if (!w.isJsonPrimitive() || !w.getAsJsonPrimitive().isNumber()
                        || w.getAsDouble() != Math.floor(w.getAsDouble())) {
                    issues.add(new SchemaIssue(base + ".weight",
                            "Expected an integer, got " + typeOf(w)));
                } else if (w.getAsDouble() < 0 || w.getAsDouble() > 10000) {
                    issues.add(new SchemaIssue(base + ".weight",
                            "Expected an integer in [0, 10000] (0 = tier never rolls), got " + w.getAsDouble()));
                }
            }
            if (tier.has("items")) {
                JsonElement it = tier.get("items");
                if (!it.isJsonArray()) {
                    issues.add(new SchemaIssue(base + ".items",
                            "Expected an array of item entries, got " + typeOf(it)));
                } else if (it.getAsJsonArray().isEmpty()) {
                    issues.add(new SchemaIssue(base + ".items",
                            "Tier has no items — it falls back to lower tiers when rolled (empty tiers are invisible to players)"));
                } else {
                    JsonArray items = it.getAsJsonArray();
                    for (int i = 0; i < items.size(); i++) {
                        JsonElement e = items.get(i);
                        if (e.isJsonObject()) {
                            validateItemEntry(e.getAsJsonObject(), base + ".items[" + i + "]", issues);
                        }
                    }
                }
            } else {
                issues.add(new SchemaIssue(base + ".items", "Missing required field 'items'"));
            }
        }
        // Legacy keys are ignored entirely once "grades" is present.
        if (json.has("random")) {
            issues.add(new SchemaIssue("random",
                    "Ignored — \"grades\" replaces the legacy random/grade1..5 format; remove it"));
        }
        for (int g = 1; g <= 5; g++) {
            if (json.has("grade" + g)) {
                issues.add(new SchemaIssue("grade" + g,
                        "Ignored — \"grades\" replaces the legacy random/grade1..5 format; remove it"));
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
        // v2.0.1: a box with no positive grade weight can never roll a grade;
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

    /**
     * v2.0.1 pity (保底) validation: shape of {@code pity} plus the
     * "can never fire" case — no positive grade weight at/above the target
     * tier in {@code random} (checked only when random is present; an absent
     * random uses the built-in positive defaults).
     */
    private static void validatePity(JsonObject json, List<SchemaIssue> issues) {
        if (!json.has("pity")) return;
        JsonElement elem = json.get("pity");
        if (!elem.isJsonObject()) {
            issues.add(new SchemaIssue("pity",
                    "Expected object {\"grade\": ..., \"every\": ...}, got " + typeOf(elem)));
            return;
        }
        JsonObject pity = elem.getAsJsonObject();
        String grade = "";
        if (pity.has("grade")) {
            JsonElement g = pity.get("grade");
            if (!g.isJsonPrimitive() || !g.getAsJsonPrimitive().isString()) {
                issues.add(new SchemaIssue("pity.grade", "Expected a grade id string"));
            } else {
                grade = g.getAsString();
                if (json.has("grades") && json.get("grades").isJsonArray()) {
                    // Custom tiers: the id must name one of the configured tiers.
                    if (findTierIndex(json.getAsJsonArray("grades"), grade) < 0) {
                        issues.add(new SchemaIssue("pity.grade",
                                "Unknown tier id '" + grade + "' — not among the box's configured grades ids"));
                    }
                } else if (BoxGrades.gradeLevel(grade) == 0) {
                    issues.add(new SchemaIssue("pity.grade",
                            "Unknown grade id '" + grade + "' (expected consumer/industrial/mil_spec/restricted/classified)"));
                }
            }
        } else {
            issues.add(new SchemaIssue("pity.grade", "Missing required field 'grade'"));
        }
        if (pity.has("every")) {
            JsonElement ev = pity.get("every");
            if (!ev.isJsonPrimitive() || !ev.getAsJsonPrimitive().isNumber()) {
                issues.add(new SchemaIssue("pity.every", "Expected integer >= 2"));
            } else {
                double v = ev.getAsDouble();
                if (v < 2 || v != Math.floor(v)) {
                    issues.add(new SchemaIssue("pity.every", "Expected integer >= 2, got " + v));
                }
            }
        } else {
            issues.add(new SchemaIssue("pity.every", "Missing required field 'every'"));
        }
        int level = BoxGrades.gradeLevel(grade);
        if (json.has("grades") && json.get("grades").isJsonArray()) {
            // Custom tiers: no positive weight at/above the target tier means
            // the forced roll has an empty slice and can never fire.
            JsonArray tiers = json.getAsJsonArray("grades");
            int idx = findTierIndex(tiers, grade);
            if (idx >= 0) {
                boolean anyPositive = false;
                for (int t = idx; t < tiers.size(); t++) {
                    JsonElement te = tiers.get(t);
                    if (te.isJsonObject() && te.getAsJsonObject().has("weight")) {
                        JsonElement w = te.getAsJsonObject().get("weight");
                        if (w.isJsonPrimitive() && w.getAsJsonPrimitive().isNumber() && w.getAsDouble() > 0) {
                            anyPositive = true;
                            break;
                        }
                    } else {
                        anyPositive = true; // default tier weight is 1
                        break;
                    }
                }
                if (!anyPositive) {
                    issues.add(new SchemaIssue("pity",
                            "No positive weight at/above target tier '" + grade + "' — the pity can never fire"));
                }
            }
        } else if (level > 0 && json.has("random") && json.get("random").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("random");
            boolean anyPositive = false;
            for (int i = gradeLevelToIndex(level); i < arr.size(); i++) {
                JsonElement w = arr.get(i);
                if (w.isJsonPrimitive() && w.getAsJsonPrimitive().isNumber() && w.getAsDouble() > 0) {
                    anyPositive = true;
                    break;
                }
            }
            if (!anyPositive) {
                issues.add(new SchemaIssue("pity",
                        "No positive weight at/above target grade '" + grade + "' in random — the pity can never fire"));
            }
        }
    }

    /** Index (0-based, lowest tier first) of a tier id in a {@code grades}
     *  array, or -1 when absent/malformed. */
    private static int findTierIndex(JsonArray tiers, String id) {
        for (int t = 0; t < tiers.size(); t++) {
            JsonElement te = tiers.get(t);
            if (te.isJsonObject() && te.getAsJsonObject().has("id")
                    && te.getAsJsonObject().get("id").isJsonPrimitive()
                    && id.equals(te.getAsJsonObject().get("id").getAsString())) {
                return t;
            }
        }
        return -1;
    }

    /** random array index (0-based) of a 1-based grade level. */
    private static int gradeLevelToIndex(int level) {
        return Math.max(0, level - 1);
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
     * v2.0.1+: the per-item {@code price} field is removed from box JSON —
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


    /** v2.x gameplay fields: hide_odds / allow_bulk / keys / wear / bonus /
     *  announce_above / available_from+until / open_cost (server-authoritative
     *  gates; shape errors here instead of silent fallbacks). */
    private static void validateV2xGameplayFields(JsonObject json, List<SchemaIssue> issues) {
        for (String boolField : new String[]{"hide_odds", "allow_bulk"}) {
            if (json.has(boolField) && (!json.get(boolField).isJsonPrimitive()
                    || !json.get(boolField).getAsJsonPrimitive().isBoolean())) {
                issues.add(new SchemaIssue(boolField, "Expected boolean, got " + typeOf(json.get(boolField))));
            }
        }
        if (json.has("sound") && (!json.get("sound").isJsonPrimitive()
                || !json.get("sound").getAsJsonPrimitive().isString()
                || json.get("sound").getAsString().isBlank())) {
            issues.add(new SchemaIssue("sound",
                    "Expected a sound event id string like minecraft:block.note_block.pling"));
        }
        if (json.has("negotiation")) {
            JsonElement n = json.get("negotiation");
            if (!n.isJsonObject()) {
                issues.add(new SchemaIssue("negotiation",
                        "Expected an object {rounds, timeout_minutes, price_bias}, got " + typeOf(n)));
            } else {
                JsonObject neg = n.getAsJsonObject();
                for (String k : neg.keySet()) {
                    if (!"rounds".equals(k) && !"timeout_minutes".equals(k) && !"price_bias".equals(k)) {
                        issues.add(new SchemaIssue("negotiation." + k, "Unknown negotiation field"));
                    }
                }
                if (neg.has("rounds") && (!neg.get("rounds").isJsonPrimitive()
                        || !neg.get("rounds").getAsJsonPrimitive().isNumber()
                        || neg.get("rounds").getAsDouble() != Math.floor(neg.get("rounds").getAsDouble())
                        || neg.get("rounds").getAsInt() < 1 || neg.get("rounds").getAsInt() > 5)) {
                    issues.add(new SchemaIssue("negotiation.rounds", "Expected integer 1..5"));
                }
                if (neg.has("timeout_minutes") && (!neg.get("timeout_minutes").isJsonPrimitive()
                        || !neg.get("timeout_minutes").getAsJsonPrimitive().isNumber()
                        || neg.get("timeout_minutes").getAsDouble() != Math.floor(neg.get("timeout_minutes").getAsDouble())
                        || neg.get("timeout_minutes").getAsInt() < 1)) {
                    issues.add(new SchemaIssue("negotiation.timeout_minutes", "Expected integer >= 1"));
                }
                if (neg.has("price_bias") && (!neg.get("price_bias").isJsonPrimitive()
                        || !neg.get("price_bias").getAsJsonPrimitive().isNumber()
                        || neg.get("price_bias").getAsDouble() < -0.9
                        || neg.get("price_bias").getAsDouble() > 9.0)) {
                    issues.add(new SchemaIssue("negotiation.price_bias", "Expected number in -0.9..9.0"));
                }
            }
        }
        if (json.has("upgrade")) {
            JsonElement u = json.get("upgrade");
            if (!u.isJsonObject()) {
                issues.add(new SchemaIssue("upgrade",
                        "Expected an object {target, consume}, got " + typeOf(u)));
            } else {
                JsonObject up = u.getAsJsonObject();
                for (String k : up.keySet()) {
                    if (!"target".equals(k) && !"consume".equals(k)) {
                        issues.add(new SchemaIssue("upgrade." + k, "Unknown upgrade field"));
                    }
                }
                if (!up.has("target")) {
                    issues.add(new SchemaIssue("upgrade.target", "Missing required field 'target'"));
                } else if (!up.get("target").isJsonPrimitive()
                        || !up.get("target").getAsJsonPrimitive().isString()
                        || up.get("target").getAsString().isBlank()) {
                    issues.add(new SchemaIssue("upgrade.target", "Expected a box id string"));
                }
                if (up.has("consume") && (!up.get("consume").isJsonPrimitive()
                        || !up.get("consume").getAsJsonPrimitive().isNumber()
                        || up.get("consume").getAsDouble() != Math.floor(up.get("consume").getAsDouble())
                        || up.get("consume").getAsInt() < 1)) {
                    issues.add(new SchemaIssue("upgrade.consume", "Expected integer >= 1"));
                }
            }
        }
        if (json.has("keys")) {
            JsonElement k = json.get("keys");
            if (!k.isJsonArray()) {
                issues.add(new SchemaIssue("keys", "Expected an array of key item ids, got " + typeOf(k)));
            } else {
                for (int i = 0; i < k.getAsJsonArray().size(); i++) {
                    JsonElement e = k.getAsJsonArray().get(i);
                    if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()
                            || e.getAsString().isBlank()) {
                        issues.add(new SchemaIssue("keys[" + i + "]", "Expected a key item id string"));
                    }
                }
                if (k.getAsJsonArray().size() > 8) {
                    issues.add(new SchemaIssue("keys", "Too many keys: " + k.getAsJsonArray().size() + " (max 8)"));
                }
            }
        }
        if (json.has("wear")) {
            JsonElement w = json.get("wear");
            if (!w.isJsonArray() || w.getAsJsonArray().size() != 2
                    || !w.getAsJsonArray().get(0).isJsonPrimitive()
                    || !w.getAsJsonArray().get(1).isJsonPrimitive()
                    || !w.getAsJsonArray().get(0).getAsJsonPrimitive().isNumber()
                    || !w.getAsJsonArray().get(1).getAsJsonPrimitive().isNumber()) {
                issues.add(new SchemaIssue("wear", "Expected a [min, max] pair of numbers in 0..1, got " + typeOf(w)));
            } else {
                double min = w.getAsJsonArray().get(0).getAsDouble();
                double max = w.getAsJsonArray().get(1).getAsDouble();
                if (min < 0 || max > 1 || min > max) {
                    issues.add(new SchemaIssue("wear", "Expected 0 <= min <= max <= 1, got [" + min + ", " + max + "]"));
                }
            }
        }
        if (json.has("bonus")) {
            JsonElement b = json.get("bonus");
            if (!b.isJsonObject()) {
                issues.add(new SchemaIssue("bonus", "Expected an object {chance, mode}, got " + typeOf(b)));
            } else {
                JsonObject bonus = b.getAsJsonObject();
                if (bonus.has("chance") && (!bonus.get("chance").isJsonPrimitive()
                        || !bonus.get("chance").getAsJsonPrimitive().isNumber()
                        || bonus.get("chance").getAsDouble() < 0
                        || bonus.get("chance").getAsDouble() > 1)) {
                    issues.add(new SchemaIssue("bonus.chance", "Expected a number in 0..1"));
                }
                if (bonus.has("mode") && (!bonus.get("mode").isJsonPrimitive()
                        || !bonus.get("mode").getAsJsonPrimitive().isString()
                        || !("double".equals(bonus.get("mode").getAsString())
                            || "box".equals(bonus.get("mode").getAsString())))) {
                    issues.add(new SchemaIssue("bonus.mode", "Expected \"double\" or \"box\""));
                }
            }
        }
        if (json.has("announce_above")) {
            JsonElement a = json.get("announce_above");
            boolean ok = a.isJsonPrimitive() && a.getAsJsonPrimitive().isNumber();
            if (!ok && a.isJsonPrimitive() && a.getAsJsonPrimitive().isString()) {
                String s = a.getAsString();
                // Mirror the loader: a custom "grades" box accepts its own tier
                // ids; a legacy box falls back to the five built-in ids.
                if (json.has("grades") && json.get("grades").isJsonArray()) {
                    ok = findTierIndex(json.getAsJsonArray("grades"), s) >= 0;
                } else {
                    ok = BoxGrades.gradeLevel(s) > 0;
                }
            }
            if (!ok) {
                issues.add(new SchemaIssue("announce_above",
                        "Expected a 1-based tier level or a tier id (legacy ids or the box's grades ids), got " + typeOf(a)));
            }
        }
        Long fromMs = null;
        Long untilMs = null;
        for (String timeField : new String[]{"available_from", "available_until"}) {
            if (!json.has(timeField)) continue;
            JsonElement e = json.get(timeField);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
                issues.add(new SchemaIssue(timeField, "Expected an ISO-8601 date-time string, got " + typeOf(e)));
                continue;
            }
            Long ms = parseIsoMillisLenient(e.getAsString());
            if (ms == null) {
                issues.add(new SchemaIssue(timeField, "Unparseable ISO-8601 date-time: " + e.getAsString()));
            } else if (timeField.endsWith("from")) {
                fromMs = ms;
            } else {
                untilMs = ms;
            }
        }
        if (fromMs != null && untilMs != null && fromMs > untilMs) {
            issues.add(new SchemaIssue("available_from", "available_from is after available_until"));
        }
        if (json.has("open_cost") && (!json.get("open_cost").isJsonPrimitive()
                || !json.get("open_cost").getAsJsonPrimitive().isNumber()
                || json.get("open_cost").getAsDouble() < 0
                || json.get("open_cost").getAsDouble() != Math.floor(json.get("open_cost").getAsDouble()))) {
            issues.add(new SchemaIssue("open_cost", "Expected a non-negative integer (Armory Points)"));
        }
        if (json.has("pity") && json.get("pity").isJsonObject()
                && json.getAsJsonObject("pity").has("first_open")
                && (!json.getAsJsonObject("pity").get("first_open").isJsonPrimitive()
                    || !json.getAsJsonObject("pity").get("first_open").getAsJsonPrimitive().isBoolean())) {
            issues.add(new SchemaIssue("pity.first_open", "Expected boolean"));
        }
    }

    /** Duplicate of the loader ISO parse (the validator stays a pure-gson
     *  class with no loader dependencies). */
    private static Long parseIsoMillisLenient(String raw) {
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
