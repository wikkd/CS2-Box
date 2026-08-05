package com.reclizer.csgobox.box;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Writes tutorial markdown files into the {@code config/csbox/} directory
 * on first load by downloading them from the configured sources (see
 * {@link TutorialSources}). The files are not loaded as boxes because
 * their extension is not {@code .json}.
 *
 * <p>Tutorials are versioned: file names embed the mod version, e.g.
 * {@code _tutorial_v1.0.5.md} and {@code _tutorial_v1.0.5_zh_cn.md}. On
 * startup, if no file matches the current mod version, every stale
 * {@code _tutorial_v*_.md} file is deleted outright — the new-version
 * tutorial is downloaded right after, so the stale copy has no value
 * to keep around.</p>
 *
 * <p>The pattern {@code ^_tutorial_v.*\.md$} deliberately does NOT match:
 * user-authored {@code notes.md}, the older un-versioned
 * {@code _tutorial.md}, or the {@code _tutorial_sources.json} config.
 * Only mod-managed tutorial files with a version stamp are candidates
 * for deletion.</p>
 *
 * <p>Tutorials are network-only: if every configured source fails (or the
 * player is offline), no file is written. Existing files are never
 * overwritten by name, so user edits to current-version files survive.</p>
 *
 * <p>Resolved from the {@code common/} source set so all Minecraft
 * version modules share a single class file. The mod version is read
 * from the jar manifest via
 * {@link Package#getImplementationVersion()} so we no longer depend on
 * any per-platform {@code CsgoBox} entry point.</p>
 */
public final class BoxDefaults {

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
     * Forces a re-download of every tutorial file for the current mod
     * version, overwriting existing copies. Used by
     * {@code /csbox tutorial refresh}; unlike {@link #writeTutorialIfMissing}
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
