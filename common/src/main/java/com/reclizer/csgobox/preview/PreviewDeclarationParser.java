package com.reclizer.csgobox.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Era-neutral parser for preview declaration JSON (2026-11-22 预览适配接口
 * v1). One call parses one declaration file; malformed input yields an error
 * diagnostic instead of an exception so one bad jar can never break the
 * preview pipeline. Shape validation only — whether an entity/block id
 * actually exists in the registry is a platform-side, render-time check with
 * a one-shot degrade log.
 *
 * <p>Accepted shape (unknown top-level keys produce a warning, not an error —
 * the field set only grows):</p>
 *
 * <pre>{@code
 * {
 *   "items": ["modid:item_a"],                       // required, non-empty
 *   "mode": "entity",                                // required: "entity" | "block"
 *   "entity": "modid:boss",                          // mode=entity required
 *   "spawn_data": { ... },                           // optional entity NBT (raw JSON)
 *   "block": "modid:my_block",                       // mode=block required
 *   "block_properties": {"facing": "north"},         // optional, string→string
 *   "rest_pose": {"yaw": 180, "pitch": 0, "roll": 0},// optional, degrees
 *   "fit_boost": 1.3,                                // optional, > 0, default 1.0
 *   "priority": 0                                    // optional integer, default 0
 * }
 * }</pre>
 */
public final class PreviewDeclarationParser {

    /** One parse diagnostic (error or warning), pure data. */
    public record Diagnostic(String source, String reason, boolean warning) {
        public static Diagnostic error(String source, String reason) {
            return new Diagnostic(source, reason, false);
        }

        public static Diagnostic warn(String source, String reason) {
            return new Diagnostic(source, reason, true);
        }
    }

    /** Top-level keys understood by this parser (anything else warns). */
    private static final List<String> KNOWN_KEYS = List.of(
            "items", "mode", "entity", "spawn_data", "block", "block_properties",
            "rest_pose", "fit_boost", "priority");

    private PreviewDeclarationParser() {
    }

    /**
     * Parses one declaration file. Returns {@code null} on any error (each
     * error is reported through {@code diagnosticsOut}; warnings alone still
     * produce a declaration).
     */
    public static PreviewDeclaration parse(String jsonText, String source, List<Diagnostic> diagnosticsOut) {
        JsonObject root;
        try {
            root = JsonParser.parseString(jsonText).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            diagnosticsOut.add(Diagnostic.error(source, "not a JSON object: " + e.getMessage()));
            return null;
        }
        for (String key : root.keySet()) {
            if (!KNOWN_KEYS.contains(key)) {
                diagnosticsOut.add(Diagnostic.warn(source, "unknown key ignored: " + key));
            }
        }

        List<String> items = parseItems(root, source, diagnosticsOut);
        if (items == null) {
            return null;
        }
        String mode = optString(root, "mode", null);
        if (!"entity".equals(mode) && !"block".equals(mode)) {
            diagnosticsOut.add(Diagnostic.error(source, "mode must be \"entity\" or \"block\", got: " + mode));
            return null;
        }

        String entityId = null;
        String blockId = null;
        Map<String, String> blockProperties = Map.of();
        String spawnDataJson = null;
        if ("entity".equals(mode)) {
            entityId = optString(root, "entity", null);
            if (entityId == null) {
                diagnosticsOut.add(Diagnostic.error(source, "mode=entity requires \"entity\""));
                return null;
            }
            if (!isValidResourceId(entityId)) {
                diagnosticsOut.add(Diagnostic.error(source, "entity id is not \"ns:path\": " + entityId));
                return null;
            }
            JsonElement spawn = root.get("spawn_data");
            if (spawn != null) {
                if (!spawn.isJsonObject()) {
                    diagnosticsOut.add(Diagnostic.error(source, "spawn_data must be a JSON object"));
                    return null;
                }
                spawnDataJson = spawn.toString();
            }
        } else {
            blockId = optString(root, "block", null);
            if (blockId == null) {
                diagnosticsOut.add(Diagnostic.error(source, "mode=block requires \"block\""));
                return null;
            }
            if (!isValidResourceId(blockId)) {
                diagnosticsOut.add(Diagnostic.error(source, "block id is not \"ns:path\": " + blockId));
                return null;
            }
            JsonElement props = root.get("block_properties");
            if (props != null) {
                if (!props.isJsonObject()) {
                    diagnosticsOut.add(Diagnostic.error(source, "block_properties must be a JSON object"));
                    return null;
                }
                blockProperties = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : props.getAsJsonObject().entrySet()) {
                    JsonElement v = e.getValue();
                    if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) {
                        diagnosticsOut.add(Diagnostic.error(source,
                                "block_properties values must be strings, key: " + e.getKey()));
                        return null;
                    }
                    blockProperties.put(e.getKey(), v.getAsString());
                }
            }
        }

        float yaw = 0F;
        float pitch = 0F;
        float roll = 0F;
        JsonElement pose = root.get("rest_pose");
        if (pose != null) {
            if (!pose.isJsonObject()) {
                diagnosticsOut.add(Diagnostic.error(source, "rest_pose must be a JSON object"));
                return null;
            }
            // optFloat reports the type error and returns null — abort per the
            // "any error ⇒ no declaration" contract, not silently defaulting.
            Float yawBoxed = optFloat(pose.getAsJsonObject(), "yaw", source, diagnosticsOut);
            Float pitchBoxed = optFloat(pose.getAsJsonObject(), "pitch", source, diagnosticsOut);
            Float rollBoxed = optFloat(pose.getAsJsonObject(), "roll", source, diagnosticsOut);
            if (yawBoxed == null || pitchBoxed == null || rollBoxed == null) {
                return null;
            }
            yaw = yawBoxed;
            pitch = pitchBoxed;
            roll = rollBoxed;
        }

        float fitBoost = 1.0F;
        JsonElement boost = root.get("fit_boost");
        if (boost != null) {
            if (!isNumber(boost)) {
                diagnosticsOut.add(Diagnostic.error(source, "fit_boost must be a number"));
                return null;
            }
            fitBoost = boost.getAsFloat();
            if (!(fitBoost > 0F)) {
                diagnosticsOut.add(Diagnostic.error(source, "fit_boost must be > 0, got: " + fitBoost));
                return null;
            }
        }

        int priority = 0;
        JsonElement prio = root.get("priority");
        if (prio != null) {
            if (!isNumber(prio) || !prio.getAsJsonPrimitive().isNumber()) {
                diagnosticsOut.add(Diagnostic.error(source, "priority must be an integer"));
                return null;
            }
            priority = prio.getAsInt();
        }

        return new PreviewDeclaration(items, mode, entityId, blockId, blockProperties,
                spawnDataJson, yaw, pitch, roll, fitBoost, priority, source);
    }

    private static List<String> parseItems(JsonObject root, String source, List<Diagnostic> out) {
        JsonElement items = root.get("items");
        if (items == null || !items.isJsonArray()) {
            out.add(Diagnostic.error(source, "items must be a non-empty JSON array of item ids"));
            return null;
        }
        JsonArray arr = items.getAsJsonArray();
        if (arr.isEmpty()) {
            out.add(Diagnostic.error(source, "items must be a non-empty JSON array of item ids"));
            return null;
        }
        List<String> result = new ArrayList<>(arr.size());
        for (JsonElement e : arr) {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
                out.add(Diagnostic.error(source, "items entries must be strings"));
                return null;
            }
            String id = e.getAsString();
            if (!isValidResourceId(id)) {
                out.add(Diagnostic.error(source, "item id is not \"ns:path\": " + id));
                return null;
            }
            result.add(id);
        }
        return result;
    }

    private static String optString(JsonObject obj, String key, String fallback) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            return fallback;
        }
        return e.getAsString();
    }

    /** Numeric optional field; {@code null} after reporting an error (caller
     *  aborts the whole declaration), the caller's own default when absent. */
    private static Float optFloat(JsonObject obj, String key, String source, List<Diagnostic> out) {
        JsonElement e = obj.get(key);
        if (e == null) {
            return 0F;
        }
        if (!isNumber(e)) {
            out.add(Diagnostic.error(source, "rest_pose." + key + " must be a number"));
            return null;
        }
        return e.getAsFloat();
    }

    private static boolean isNumber(JsonElement e) {
        return e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    /**
     * Vanilla resource-id grammar: {@code ns:path} with both parts non-empty —
     * the namespace limited to {@code [a-z0-9_.-]}, the path additionally
     * allowing {@code /} (mirrors the platform {@code requireId} hooks
     * without touching MC classes).
     */
    public static boolean isValidResourceId(String id) {
        if (id == null || id.isEmpty() || id.length() > 256) {
            return false;
        }
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1 || id.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        return isValidNamespaceChars(id.substring(0, colon)) && isValidPathChars(id.substring(colon + 1));
    }

    private static boolean isValidNamespaceChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return !s.isEmpty();
    }

    private static boolean isValidPathChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.'
                    || c == '-' || c == '/';
            if (!ok) {
                return false;
            }
        }
        return !s.isEmpty();
    }
}
