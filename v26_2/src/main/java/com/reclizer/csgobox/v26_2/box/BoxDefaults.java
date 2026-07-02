package com.reclizer.csgobox.v26_2.box;

import com.reclizer.csgobox.v26_2.CsgoBox;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * {@code _tutorial_v*_.md} file is moved to a recoverable location —
 * either the host operating system's recycle bin (Windows Recycle Bin,
 * macOS Finder Trash, Linux XDG Trash) when available, or a
 * {@code config/csbox/.trash/} subfolder as a portable fallback.</p>
 *
 * <p>The pattern {@code ^_tutorial_v.*\.md$} deliberately does NOT match:
 * user-authored {@code notes.md}, the older un-versioned
 * {@code _tutorial.md}, or the {@code _tutorial_sources.json} config.
 * Only mod-managed tutorial files with a version stamp are candidates
 * for trashing.</p>
 *
 * <p>Tutorials are network-only: if every configured source fails (or the
 * player is offline), no file is written. Existing files are never
 * overwritten by name, so user edits to current-version files survive.</p>
 */
final class BoxDefaults {

    /**
     * Filename pattern for mod-managed, version-stamped tutorials. Files
     * matching this pattern are candidates for trashing on a version
     * upgrade; anything else in {@code config/csbox/} is left alone.
     */
    private static final Pattern STALE_TUTORIAL = Pattern.compile("^_tutorial_v.*\\.md$");

    /** Subfolder used as a soft-delete trash bin when the OS has none. Created lazily. */
    private static final String FALLBACK_TRASH_DIR_NAME = ".trash";

    private BoxDefaults() {
    }

    /**
     * Downloads and writes each missing tutorial file from the first
     * successful source in {@link TutorialSources}. If no tutorial file
     * for the current mod version is present, every stale versioned
     * {@code _tutorial_v*_.md} file is moved to a recoverable location.
     *
     * <p>The entire body is wrapped in a defensive try-catch so that no
     * exception (offline, DNS failure, malformed user JSON, JVM resource
     * exhaustion during HttpClient construction, etc.) can propagate out
     * and break the surrounding box-loading flow. The worst that can
     * happen is no tutorial file is written.</p>
     */
    static void writeTutorialIfMissing(Path boxesDir) {
        try {
            if (needsRefresh(boxesDir)) {
                CsgoBox.LOGGER.info(
                        "Tutorial version mismatch (current mod is {}); moving stale tutorials",
                        CsgoBox.MODVERSION);
                moveStaleTutorials(boxesDir);
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
                    CsgoBox.LOGGER.warn(
                            "No tutorial available for {} (offline or all sources failed); skipping",
                            fileName);
                    continue;
                }
                try {
                    Files.writeString(file, content);
                    CsgoBox.LOGGER.info("Wrote box configuration reference: {}", file);
                } catch (IOException e) {
                    CsgoBox.LOGGER.warn("Failed to write tutorial markdown {}: {}",
                            file, e.getMessage());
                }
            }
        } catch (Exception e) {
            CsgoBox.LOGGER.warn("Tutorial setup skipped due to unexpected error: {}",
                    e.getMessage());
        }
    }

    /** File names that the mod will try to populate, in download order. */
    private static List<String> tutorialFileNames() {
        String v = CsgoBox.MODVERSION == null ? "unknown" : CsgoBox.MODVERSION;
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
        String prefix = "_tutorial_v" + CsgoBox.MODVERSION;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(boxesDir, "*.md")) {
            for (Path file : stream) {
                if (file.getFileName().toString().startsWith(prefix)) {
                    return false;
                }
            }
        } catch (IOException e) {
            CsgoBox.LOGGER.warn("Failed to scan tutorials in {}: {}", boxesDir, e.getMessage());
        }
        return true;
    }

    /**
     * Tries the host operating system's recycle bin first (works on
     * Windows, macOS, and Linux desktops with a trash-aware file
     * manager). Falls back to a {@code .trash/} subfolder under
     * {@code boxesDir} when the OS path is unavailable — typical for
     * headless servers or stripped-down JVMs without {@code java.desktop}.
     *
     * <p>Files outside {@link #STALE_TUTORIAL} are never touched on
     * either path.</p>
     */
    private static void moveStaleTutorials(Path boxesDir) {
        if (canUseOsTrash()) {
            try {
                moveToOsTrash(boxesDir);
                return;
            } catch (Throwable e) {
                CsgoBox.LOGGER.warn(
                        "OS recycle bin refused ({}); falling back to .trash/ folder",
                        e.getMessage());
            }
        }
        moveToFallbackTrash(boxesDir);
    }

    /**
     * Detects whether {@link Desktop#moveToTrash} is callable in this
     * environment. Returns false on headless servers, missing
     * {@code java.desktop} module, or platforms without a trash spec.
     * Any unexpected error is treated as "not supported" so we silently
     * drop to the fallback.
     */
    private static boolean canUseOsTrash() {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return false;
            }
            if (!Desktop.isDesktopSupported()) {
                return false;
            }
            return Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Sends each stale tutorial file to the OS recycle bin. The user
     * can recover them via their normal OS UI (Finder Trash, Windows
     * Recycle Bin, KDE/Gnome Trash). Per-file failures are logged but
     * do not abort the loop.
     */
    private static void moveToOsTrash(Path boxesDir) {
        List<Path> moved = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(boxesDir, "*.md")) {
            for (Path file : stream) {
                if (!STALE_TUTORIAL.matcher(file.getFileName().toString()).matches()) {
                    continue;
                }
                try {
                    if (Desktop.getDesktop().moveToTrash(file.toFile())) {
                        moved.add(file);
                    } else {
                        CsgoBox.LOGGER.warn("OS trash refused {} (returned false)", file);
                    }
                } catch (IllegalArgumentException e) {
                    CsgoBox.LOGGER.warn("Cannot trash {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            CsgoBox.LOGGER.warn("Failed to enumerate .md files in {}: {}",
                    boxesDir, e.getMessage());
            return;
        }

        if (!moved.isEmpty()) {
            CsgoBox.LOGGER.info(
                    "Moved {} stale tutorial(s) to the system recycle bin (recoverable from there): {}",
                    moved.size(), moved);
        }
    }

    /**
     * Fallback trash used when the OS recycle bin is unavailable
     * (headless server, minimal JVM, exotic filesystem). Moves files
     * into {@code boxesDir/.trash/} with a timestamp prefix to avoid
     * name collisions. Cross-filesystem moves fall back to
     * copy+delete.
     */
    private static void moveToFallbackTrash(Path boxesDir) {
        Path trashDir = boxesDir.resolve(FALLBACK_TRASH_DIR_NAME);
        try {
            Files.createDirectories(trashDir);
        } catch (IOException e) {
            CsgoBox.LOGGER.warn("Could not create {}; skipping trash: {}",
                    trashDir, e.getMessage());
            return;
        }

        List<Path> moved = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(boxesDir, "*.md")) {
            for (Path file : stream) {
                if (!STALE_TUTORIAL.matcher(file.getFileName().toString()).matches()) {
                    continue;
                }
                try {
                    Path dest = uniqueFallbackPath(trashDir, file.getFileName().toString());
                    tryMoveOrCopy(file, dest);
                    moved.add(file);
                } catch (IOException e) {
                    CsgoBox.LOGGER.warn("Failed to trash {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            CsgoBox.LOGGER.warn("Failed to enumerate .md files in {}: {}",
                    boxesDir, e.getMessage());
            return;
        }

        if (!moved.isEmpty()) {
            CsgoBox.LOGGER.info(
                    "Moved {} stale tutorial(s) to fallback {} (recoverable from there): {}",
                    moved.size(), trashDir, moved);
        }
    }

    /**
     * Cross-filesystem safe move. Tries atomic move first; on
     * {@link IOException} (typically {@code EXDEV} on Linux/macOS
     * when source and destination are on different filesystems),
     * falls back to copy + delete.
     */
    private static void tryMoveOrCopy(Path source, Path dest) throws IOException {
        try {
            Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException primary) {
            try {
                Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(source);
            } catch (IOException secondary) {
                IOException combined = new IOException(
                        "Failed to move " + source + " (move: " + primary.getMessage()
                                + "; copy+delete: " + secondary.getMessage() + ")");
                combined.addSuppressed(primary);
                combined.addSuppressed(secondary);
                throw combined;
            }
        }
    }

    /**
     * Picks a destination path inside {@code trashDir} that does not
     * collide with an existing file. The original name is reused when
     * possible; otherwise a millisecond-precision timestamp prefix is
     * prepended (with a numeric suffix if even that collides). The
     * timestamp format {@code yyyyMMdd-HHmmss-SSS} contains only
     * characters that are legal on every common filesystem
     * (Windows, macOS APFS/HFS+, Linux ext4/btrfs/xfs).
     */
    private static Path uniqueFallbackPath(Path trashDir, String name) throws IOException {
        Path direct = trashDir.resolve(name);
        if (!Files.exists(direct)) return direct;

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        for (int i = 0; i < 1000; i++) {
            String suffix = i == 0 ? "" : ("_" + i);
            Path dated = trashDir.resolve(stamp + suffix + "_" + name);
            if (!Files.exists(dated)) return dated;
        }
        throw new IOException("Could not find unique fallback trash path for " + name);
    }
}