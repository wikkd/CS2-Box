package com.reclizer.csgobox.box;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tutorial installer: copies the bundled (JAR-embedded) version-stamped
 * markdown tutorials into {@code config/csbox/}. No network is involved —
 * the tutorial content ships inside the mod, so first launch works offline
 * and no mirror configuration exists.
 *
 * <p>Deletion of stale {@code _tutorial_v*_.md} happens ONLY after the
 * current version's files are fully in place (already present or freshly
 * copied) — a copy failure never deletes what the player already has.
 * Existing current-version files are never overwritten (player edits are
 * respected).</p>
 *
 * <p>Mod version comes from the jar manifest via
 * {@link Package#getImplementationVersion()}; when unavailable (dev/IDE
 * run without a jar manifest) the whole tutorial flow is skipped so the
 * "unknown" fallback can never trigger deletion. common/ stays platform-free.
 */
public final class BoxDefaults {

    private static final Gson GSON = new Gson();

    /**
     * Classpath location of the bundled tutorial markdown files. Content
     * ships in {@code common/src/main/resources/assets/csgobox/tutorials/}
     * under fixed names; on-disk names are version-stamped by the current
     * mod version (see {@link #onDiskName(String, String)}).
     */
    private static final String TUTORIAL_RESOURCE_PREFIX = "/assets/csgobox/tutorials/";

    /** Bundled tutorial resources, in copy order (fixed resource names). */
    private static final List<String> TUTORIAL_RESOURCES = List.of("tutorial.md", "tutorial_zh_cn.md");

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
        if (v == null || v.isBlank()) {
            // No jar manifest (dev/IDE run): return null instead of a fake
            // "unknown" version. A fake version would both match no existing
            // tutorial (triggering deletion) and stamp file names that can
            // never be valid (_tutorial_vunknown.md).
            return null;
        }
        cachedModVersion = v;
        return v;
    }

    /**
     * Copies each missing tutorial file from the bundled resources into
     * {@code config/csbox/}.
     *
     * <p>Ordering guarantee (v2.1.0 fix): stale {@code _tutorial_v*_.md}
     * files are deleted ONLY after the current version's tutorial files are
     * fully in place — a copy failure keeps the player's existing tutorials
     * instead of wiping them. Existing current-version files are never
     * overwritten.</p>
     *
     * <p>If the mod version cannot be resolved (dev/IDE run without a jar
     * manifest) the whole flow is skipped: no copy, no deletion, just a
     * warning.</p>
     *
     * <p>The entire body is wrapped in a defensive try-catch so that no
     * exception can propagate out and break the surrounding box-loading
     * flow. The worst that can happen is no tutorial file is written.</p>
     */
    public static synchronized void writeTutorialIfMissing(Path boxesDir) {
        String version = modVersion();
        if (version == null) {
            LOGGER.warn(
                    "Mod version unavailable (dev/IDE run without jar manifest?); "
                            + "skipping tutorial setup so existing tutorials are never deleted");
            return;
        }
        writeTutorialIfMissing(boxesDir, version);
    }

    /**
     * Package-visible for tests: same contract as
     * {@link #writeTutorialIfMissing(Path)} with the version pinned.
     */
    static void writeTutorialIfMissing(Path boxesDir, String version) {
        try {
            ensureBoxesDir(boxesDir);

            boolean currentVersionComplete = true;
            for (String resource : TUTORIAL_RESOURCES) {
                String fileName = onDiskName(resource, version);
                Path file = boxesDir.resolve(fileName);
                if (Files.exists(file)) {
                    continue;
                }

                String content = readTutorialResource(resource);
                if (content == null) {
                    LOGGER.warn(
                            "Bundled tutorial resource {} missing; keeping existing tutorial files",
                            resource);
                    currentVersionComplete = false;
                    continue;
                }
                try {
                    Files.writeString(file, content);
                    LOGGER.info("Wrote tutorial: {}", file);
                } catch (IOException e) {
                    LOGGER.warn("Failed to write tutorial {}: {}", file, e.getMessage());
                    currentVersionComplete = false;
                }
            }

            // Only now, with the current version's files guaranteed present,
            // is it safe to remove tutorials from older versions.
            if (currentVersionComplete) {
                deleteStaleTutorials(boxesDir, version);
            }
        } catch (Exception e) {
            LOGGER.warn("Tutorial setup skipped due to unexpected error: {}",
                    e.getMessage());
        }
    }

    /**
     * Ensures the tutorial target directory exists before any copy or stale
     * scan. Client-side callers (dedicated-server players) have no server
     * {@code loadAll()} to create {@code config/csbox/} for them, so the
     * tutorial flow must not assume the directory is pre-created.
     */
    static void ensureBoxesDir(Path boxesDir) throws IOException {
        Files.createDirectories(boxesDir);
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
     * file is kept IN PLACE (so the loader keeps reporting it via
     * {@code /csbox info error}) and a copy is preserved as
     * {@code terminal.json.corrupt-<millis>} for manual recovery.</p>
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

    /**
     * Preserves an unreadable terminal.json: the original file is kept IN
     * PLACE (the loader keeps surfacing its error via {@code /csbox info
     * error}, and the author can fix it without hunting a backup), while a
     * copy is written to {@code terminal.json.corrupt-<millis>} so no data
     * is ever lost. No default replaces it (the terminal becomes
     * unconfigured).
     */
    private static void recoverCorruptTerminal(Path file) {
        try {
            Path backup = file.resolveSibling("terminal.json.corrupt-" + System.currentTimeMillis());
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Corrupt terminal.json kept in place for fixing; backup copy at {}",
                    backup.getFileName());
        } catch (IOException e) {
            LOGGER.warn("Terminal config recovery failed: {}", e.getMessage());
        }
    }

    /**
     * Forces a re-copy of every bundled tutorial file for the current mod
     * version, overwriting existing copies. Used by
     * {@code /csbox reload tutorial}; does not touch stale versioned
     * tutorials. No network involved — content comes from the jar.
     *
     * <p>Same defensive try-catch contract as the startup path. Unresolved
     * mod version (dev/IDE run) skips the whole refresh.</p>
     */
    public static synchronized void refreshTutorials(Path boxesDir) {
        String version = modVersion();
        if (version == null) {
            LOGGER.warn(
                    "Mod version unavailable (dev/IDE run without jar manifest?); "
                            + "skipping tutorial refresh");
            return;
        }
        refreshTutorials(boxesDir, version);
    }

    /**
     * Package-visible for tests: same contract as
     * {@link #refreshTutorials(Path)} with the version pinned.
     */
    static void refreshTutorials(Path boxesDir, String version) {
        try {
            ensureBoxesDir(boxesDir);
            for (String resource : TUTORIAL_RESOURCES) {
                String fileName = onDiskName(resource, version);
                String content = readTutorialResource(resource);
                if (content == null) {
                    LOGGER.warn("Bundled tutorial resource {} missing; skipping", resource);
                    continue;
                }
                try {
                    Files.writeString(boxesDir.resolve(fileName), content);
                    LOGGER.info("Refreshed tutorial: {}", fileName);
                } catch (IOException e) {
                    LOGGER.warn("Failed to write tutorial {}: {}", fileName, e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Tutorial refresh skipped due to unexpected error: {}",
                    e.getMessage());
        }
    }

    /** Bundled resource name -> version-stamped on-disk file name. */
    private static String onDiskName(String resource, String version) {
        if (resource.equals("tutorial_zh_cn.md")) {
            return "_tutorial_v" + version + "_zh_cn.md";
        }
        return "_tutorial_v" + version + ".md";
    }

    /** Reads one bundled tutorial resource from the classpath, or null on failure. */
    private static String readTutorialResource(String name) {
        try (InputStream in = BoxDefaults.class.getResourceAsStream(TUTORIAL_RESOURCE_PREFIX + name)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to read bundled tutorial {}: {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * Deletes stale versioned tutorials in {@code boxesDir}, i.e. files
     * matching {@link #STALE_TUTORIAL} whose version prefix differs from
     * {@code currentVersion}. Current-version files and user files
     * (e.g. {@code notes.md}) are never touched. Per-file failures are
     * logged but do not abort the loop. Callers must only invoke this after
     * the current version's tutorials are guaranteed present.
     */
    static void deleteStaleTutorials(Path boxesDir, String currentVersion) {
        String currentPrefix = "_tutorial_v" + currentVersion;
        List<Path> deleted = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(boxesDir, "*.md")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if (!STALE_TUTORIAL.matcher(name).matches()) {
                    continue;
                }
                if (name.startsWith(currentPrefix)) {
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
            LOGGER.info("Deleted {} stale tutorial(s) (current mod {}): {}",
                    deleted.size(), currentVersion, deleted);
        }
    }
}
