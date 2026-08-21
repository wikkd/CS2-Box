package com.reclizer.csgobox.box;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

    /**
 * Tutorial downloader: on first load, fetches version-stamped markdown
 * tutorials into {@code config/csbox/}. Version mismatch deletes every
 * {@code _tutorial_v*_.md} (pattern excludes user files like
 * {@code notes.md}); downloads only if all sources are reachable and never
 * overwrites existing files. Mod version comes from the jar manifest via
 * {@link Package#getImplementationVersion()}, so common/ stays platform-free.
 */
public final class BoxDefaults {

    private static final Gson GSON = new Gson();

    /**
     * Filename pattern for mod-managed, version-stamped tutorials. Files
     * matching this pattern are candidates for deletion on a version
     * upgrade; anything else in {@code config/csbox/} is left alone.
     */
    private static final Pattern STALE_TUTORIAL = Pattern.compile("^_tutorial_v.*\\.md$");

    private static final Logger LOGGER = LoggerFactory.getLogger(BoxDefaults.class);

    private BoxDefaults() {
    }

    /** Cached mod version, read from the jar manifest on first access. */
    private static volatile String cachedModVersion;

    private static String modVersion() {
        String cached = cachedModVersion;
        if (cached != null) {
            return cached;
        }
        String v = BoxDefaults.class.getPackage().getImplementationVersion();
        String resolved = v == null ? "unknown" : v;
        cachedModVersion = resolved;
        return resolved;
    }

    /**
     * Downloads and writes each missing tutorial file from the first
     * successful source in {@link TutorialSources}. If no tutorial file
     * for the current mod version is present, every stale versioned
     * {@code _tutorial_v*_.md} file is deleted.
     *
     * <p>The entire body is wrapped in a defensive try-catch so that no
     * exception (offline, DNS failure, malformed user JSON, JVM resource
     * exhaustion during HttpClient construction, etc.) can propagate out
     * and break the surrounding box-loading flow. The worst that can
     * happen is no tutorial file is written.</p>
     */
    public static void writeTutorialIfMissing(Path boxesDir) {
        try {
            if (needsRefresh(boxesDir)) {
                LOGGER.info(
                        "Tutorial version mismatch (current mod is {}); deleting stale tutorials",
                        modVersion());
                deleteStaleTutorials(boxesDir);
            }

            TutorialSources sources = TutorialSources.loadOrDefault(boxesDir);
            TutorialFetcher fetcher = new TutorialFetcher();

            for (String fileName : tutorialFileNames()) {
                Path file = boxesDir.resolve(fileName);
                if (Files.exists(file)) {
                    continue;
                }

                String content = fetcher.fetch(fileName, sources.sources());
                if (content == null) {
                    LOGGER.warn(
                            "No tutorial available for {} (offline or all sources failed); skipping",
                            fileName);
                    continue;
                }
                try {
                    Files.writeString(file, content);
                    LOGGER.info("Wrote box configuration reference: {}", file);
                } catch (IOException e) {
                    LOGGER.warn("Failed to write tutorial markdown {}: {}",
                            file, e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Tutorial setup skipped due to unexpected error: {}",
                    e.getMessage());
        }
    }

    /**
     * Upgrades a pre-v2.0.0 {@code terminal.json} to the type-driven format.
     * Since v2.0.0 the JSON {@code type} field is the single source of truth
     * for item registration, and the terminal machine no longer has a
     * {@code key} field (strict separation from regular crates). Configs
     * written against the pre-v2.0.0 schema have neither: this one-time migration
     * adds {@code "type": "terminal"} and drops a legacy {@code key} (e.g.
     * {@code minecraft:air}) so existing servers keep their terminal without
     * manual edits. Only the exact {@code terminal.json} file is touched;
     * anything that already declares a {@code type} is left alone.
     *
     * <p>Since 2.0.0 the terminal ships UNCONFIGURED (empty crate, same as
     * the default box), so no default is ever written back: an empty file is
     * left alone (the terminal simply stays unbound); a non-empty corrupt
     * file is backed up as {@code terminal.json.corrupt-<millis>} and
     * removed from the load path, preserving the player's data for manual
     * recovery.</p>
     */
    public static void upgradeLegacyTerminalConfig(Path boxesDir) {
        Path file = boxesDir.resolve("terminal.json");
        if (!Files.exists(file)) {
            return;
        }
        try {
            if (Files.size(file) == 0L) {
                // An empty terminal.json is a valid "unconfigured" state —
                // the loader skips it and the terminal stays an empty crate.
                LOGGER.info("Empty terminal.json left unconfigured: {}", file);
                return;
            }
            JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (json == null) {
                recoverCorruptTerminal(file);
                return;
            }
            if (json.has("type")) {
                return;
            }
            json.addProperty("type", "terminal");
            if (json.has("key")) {
                json.remove("key");
            }
            Files.writeString(file, GSON.toJson(json));
            LOGGER.info("Upgraded legacy terminal.json to type-driven format: added \"type\": \"terminal\", removed \"key\"");
        } catch (JsonSyntaxException e) {
            recoverCorruptTerminal(file);
        } catch (Exception e) {
            LOGGER.warn("Legacy terminal.json upgrade skipped due to error: {}", e.getMessage());
        }
    }

    /** Backs up an unreadable terminal.json; no default replaces it (the terminal becomes unconfigured). */
    private static void recoverCorruptTerminal(Path file) {
        try {
            Path backup = file.resolveSibling("terminal.json.corrupt-" + System.currentTimeMillis());
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Corrupt terminal.json backed up and left unconfigured (backup at {})", backup.getFileName());
        } catch (IOException e) {
            LOGGER.warn("Terminal config recovery failed: {}", e.getMessage());
        }
    }

    /**
     * Forces a re-download of every tutorial file for the current mod
     * version, overwriting existing copies. Used by
     * {@code /csbox reload tutorial}; unlike {@link #writeTutorialIfMissing}
     * this does not consult {@link #needsRefresh} and does not touch stale
     * versioned tutorials.
     *
     * <p>Same defensive try-catch contract as the startup path: a network
     * failure leaves existing files untouched and only logs a warning.</p>
     */
    public static void refreshTutorials(Path boxesDir) {
        try {
            TutorialSources sources = TutorialSources.loadOrDefault(boxesDir);
            TutorialFetcher fetcher = new TutorialFetcher();
            for (String fileName : tutorialFileNames()) {
                String content = fetcher.fetch(fileName, sources.sources());
                if (content == null) {
                    LOGGER.warn(
                            "No tutorial available for {} (offline or all sources failed); skipping",
                            fileName);
                    continue;
                }
                try {
                    Files.writeString(boxesDir.resolve(fileName), content);
                    LOGGER.info("Refreshed tutorial: {}", fileName);
                } catch (IOException e) {
                    LOGGER.warn("Failed to write tutorial markdown {}: {}",
                            fileName, e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Tutorial refresh skipped due to unexpected error: {}",
                    e.getMessage());
        }
    }

    /** File names that the mod will try to populate, in download order. */
    private static List<String> tutorialFileNames() {
        String v = modVersion();
        return List.of(
                "_tutorial_v" + v + ".md",
                "_tutorial_v" + v + "_zh_cn.md"
        );
    }

    /**
     * Returns true if no {@code _tutorial_v<currentVersion>*.md} file is
     * present in {@code boxesDir}. First install and any version change
     * both trigger a refresh.
     */
    private static boolean needsRefresh(Path boxesDir) {
        String prefix = "_tutorial_v" + modVersion();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(boxesDir, "*.md")) {
            for (Path file : stream) {
                if (file.getFileName().toString().startsWith(prefix)) {
                    return false;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to scan tutorials in {}: {}", boxesDir, e.getMessage());
        }
        return true;
    }

    /**
     * Deletes every stale versioned tutorial in {@code boxesDir}. Only
     * files matching {@link #STALE_TUTORIAL} are removed; user files
     * (e.g. {@code notes.md}) are never touched. Per-file failures are
     * logged but do not abort the loop.
     */
    private static void deleteStaleTutorials(Path boxesDir) {
        List<Path> deleted = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(boxesDir, "*.md")) {
            for (Path file : stream) {
                if (!STALE_TUTORIAL.matcher(file.getFileName().toString()).matches()) {
                    continue;
                }
                try {
                    Files.delete(file);
                    deleted.add(file);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete stale tutorial {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to enumerate .md files in {}: {}", boxesDir, e.getMessage());
            return;
        }

        if (!deleted.isEmpty()) {
            LOGGER.info("Deleted {} stale tutorial(s): {}", deleted.size(), deleted);
        }
    }
}
