package com.reclizer.csgobox.inspectaudio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.reclizer.csgobox.preview.PreviewDeclarationParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Era-neutral parser for inspect-audio declaration JSON (2026-11-22 检视音频
 * 接口 JSON 声明层). One call parses one declaration file; malformed input
 * yields an error diagnostic instead of an exception so one bad pack can
 * never break the inspect audio pipeline. Shape validation only — whether a
 * sound id actually exists in the sound-event registry is a platform-side,
 * resolve-time check with a one-shot degrade log.
 *
 * <p>Accepted shape (unknown top-level keys produce a warning, not an error —
 * the field set only grows):</p>
 *
 * <pre>{@code
 * {
 *   "items": ["modid:item_a"],   // required, non-empty
 *   "sound": "modid:my_sound",   // required: registered SoundEvent id
 *   "priority": 0                // optional integer, default 0
 * }
 * }</pre>
 */
public final class InspectAudioDeclarationParser {

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
    private static final List<String> KNOWN_KEYS = List.of("items", "sound", "priority");

    private InspectAudioDeclarationParser() {
    }

    /**
     * Parses one declaration file. Returns {@code null} on any error (each
     * error is reported through {@code diagnosticsOut}; warnings alone still
     * produce a declaration).
     */
    public static InspectAudioDeclaration parse(String jsonText, String source, List<Diagnostic> diagnosticsOut) {
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
        JsonElement sound = root.get("sound");
        if (sound == null) {
            diagnosticsOut.add(Diagnostic.error(source, "sound is required"));
            return null;
        }
        if (!sound.isJsonPrimitive() || !sound.getAsJsonPrimitive().isString()) {
            diagnosticsOut.add(Diagnostic.error(source, "sound must be a string"));
            return null;
        }
        String soundId = sound.getAsString();
        // Same "ns:path" grammar as the preview declarations; sound event ids
        // regularly carry dots (minecraft:music_disc.pigstep), which the path
        // charset allows.
        if (!PreviewDeclarationParser.isValidResourceId(soundId)) {
            diagnosticsOut.add(Diagnostic.error(source, "sound id is not \"ns:path\": " + soundId));
            return null;
        }

        int priority = 0;
        JsonElement prio = root.get("priority");
        if (prio != null) {
            if (!prio.isJsonPrimitive() || !prio.getAsJsonPrimitive().isNumber()) {
                diagnosticsOut.add(Diagnostic.error(source, "priority must be an integer"));
                return null;
            }
            priority = prio.getAsInt();
        }

        return new InspectAudioDeclaration(items, soundId, priority, source);
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
            if (!PreviewDeclarationParser.isValidResourceId(id)) {
                out.add(Diagnostic.error(source, "item id is not \"ns:path\": " + id));
                return null;
            }
            result.add(id);
        }
        return result;
    }
}
