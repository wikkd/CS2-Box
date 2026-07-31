package com.reclizer.csgobox.v1_21_3.box;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.reclizer.csgobox.v1_21_3.CsgoBox;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads {@code config/csbox/_tutorial_sources.json} for the ordered list of
 * download mirrors used to fetch tutorial markdown files on first load.
 *
 * <p>Sources are tried in array order. Each entry has:</p>
 * <ul>
 *   <li>{@code name} — display label for log messages</li>
 *   <li>{@code baseUrl} — must end with {@code /}; the tutorial file name is appended</li>
 *   <li>{@code enabled} — set {@code false} to skip the source without removing it</li>
 *   <li>{@code timeoutSeconds} — per-request timeout (default 8)</li>
 * </ul>
 *
 * <p>The file is never written automatically. If the player has not
 * created one, the built-in defaults are used silently and no file
 * appears in {@code config/csbox/}. Players who want to add custom
 * mirrors can create the file by hand. Tutorials are network-only:
 * if every source fails (or the player is offline), no file is written.</p>
 */
final class TutorialSources {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SOURCES_FILE = "_tutorial_sources.json";

    record Source(String name, String baseUrl, boolean enabled, int timeoutSeconds) {
    }

    private final List<Source> sources;

    private TutorialSources(List<Source> sources) {
        this.sources = sources;
    }

    List<Source> sources() {
        return Collections.unmodifiableList(sources);
    }

    /**
     * Loads sources from {@code config/csbox/_tutorial_sources.json} if the
     * player has manually created one. Otherwise returns the built-in
     * defaults (gitee) without writing anything to disk — players who want
     * to add custom mirrors can create the file themselves; everyone else
     * sees a clean {@code config/csbox/} directory.
     */
    static TutorialSources loadOrDefault(Path boxesDir) {
        Path file = boxesDir.resolve(SOURCES_FILE);
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                TutorialSources parsed = fromJson(json);
                if (!parsed.sources.isEmpty()) {
                    return parsed;
                }
                CsgoBox.LOGGER.warn("{} had no valid sources; using built-in defaults", file);
            } catch (IOException | JsonSyntaxException e) {
                CsgoBox.LOGGER.warn("Failed to read {}: {}", file, e.getMessage());
            }
        }
        return defaults();
    }

    private static TutorialSources fromJson(JsonObject json) {
        List<Source> list = new ArrayList<>();
        if (json != null && json.has("sources") && json.get("sources").isJsonArray()) {
            for (var elem : json.getAsJsonArray("sources")) {
                if (!elem.isJsonObject()) continue;
                Source src = parseSource(elem.getAsJsonObject());
                if (src != null) list.add(src);
            }
        }
        return new TutorialSources(list);
    }

    private static Source parseSource(JsonObject o) {
        String name = getString(o, "name", "unnamed");
        String baseUrl = getString(o, "baseUrl", "");
        boolean enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
        int timeout = o.has("timeoutSeconds") ? o.get("timeoutSeconds").getAsInt() : 8;
        if (baseUrl.isBlank() || !baseUrl.endsWith("/")) {
            CsgoBox.LOGGER.warn("Skipping source '{}': baseUrl must be non-blank and end with '/'", name);
            return null;
        }
        String scheme;
        try {
            scheme = URI.create(baseUrl).getScheme();
        } catch (IllegalArgumentException e) {
            CsgoBox.LOGGER.warn("Skipping source '{}': baseUrl '{}' is not a valid URI",
                    name, baseUrl);
            return null;
        }
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            CsgoBox.LOGGER.warn(
                    "Skipping source '{}': baseUrl scheme '{}' must be http(s)",
                    name, scheme);
            return null;
        }
        return new Source(name, baseUrl, enabled, timeout);
    }

    static TutorialSources defaults() {
        return new TutorialSources(List.of(
                new Source("gitee",
                        "https://gitee.com/hou-xiangling/CS2-Box/raw/main/docs/tutorials/",
                        true, 8)
        ));
    }

    private static String getString(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }
}