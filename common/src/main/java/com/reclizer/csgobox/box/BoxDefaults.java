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

    /**
     * Default terminal loot pool, written on first run. Own config so the
     * terminal's loot stays decoupled from other crates. Vanilla-only items,
     * weighted toward higher tiers.
     */
    private static final String TERMINAL_DEFAULT_JSON = """
            {
              "name": "#00E5FF CS2 终端机",
              "type": "terminal",
              "drop": 0.06,
              "random": [
                20,
                40,
                80,
                160,
                300
              ],
              "grade5": [
                {
                  "id": "minecraft:netherite_sword",
                  "price": 4000
                },
                {
                  "id": "minecraft:netherite_axe",
                  "price": 4000
                },
                {
                  "id": "minecraft:netherite_pickaxe",
                  "price": 4000
                },
                {
                  "id": "minecraft:netherite_helmet",
                  "price": 4000
                },
                {
                  "id": "minecraft:netherite_chestplate",
                  "price": 4000
                },
                {
                  "id": "minecraft:netherite_leggings",
                  "price": 4000
                },
                {
                  "id": "minecraft:netherite_boots",
                  "price": 4000
                }
              ],
              "grade4": [
                {
                  "id": "minecraft:diamond_sword",
                  "price": 1500
                },
                {
                  "id": "minecraft:diamond_axe",
                  "price": 1500
                },
                {
                  "id": "minecraft:diamond_pickaxe",
                  "price": 1500
                },
                {
                  "id": "minecraft:diamond_helmet",
                  "price": 1500
                },
                {
                  "id": "minecraft:diamond_chestplate",
                  "price": 1500
                },
                {
                  "id": "minecraft:diamond_leggings",
                  "price": 1500
                },
                {
                  "id": "minecraft:diamond_boots",
                  "price": 1500
                }
              ],
              "grade3": [
                {
                  "id": "minecraft:golden_sword",
                  "price": 500
                },
                {
                  "id": "minecraft:golden_axe",
                  "price": 500
                },
                {
                  "id": "minecraft:golden_pickaxe",
                  "price": 500
                },
                {
                  "id": "minecraft:golden_helmet",
                  "price": 500
                },
                {
                  "id": "minecraft:golden_chestplate",
                  "price": 500
                },
                {
                  "id": "minecraft:golden_leggings",
                  "price": 500
                },
                {
                  "id": "minecraft:golden_boots",
                  "price": 500
                },
                {
                  "id": "minecraft:totem_of_undying",
                  "price": 500
                }
              ],
              "grade2": [
                {
                  "id": "minecraft:iron_sword",
                  "price": 200
                },
                {
                  "id": "minecraft:iron_axe",
                  "price": 200
                },
                {
                  "id": "minecraft:iron_pickaxe",
                  "price": 200
                },
                {
                  "id": "minecraft:iron_helmet",
                  "price": 200
                },
                {
                  "id": "minecraft:iron_chestplate",
                  "price": 200
                },
                {
                  "id": "minecraft:iron_leggings",
                  "price": 200
                },
                {
                  "id": "minecraft:iron_boots",
                  "price": 200
                },
                {
                  "id": "minecraft:crossbow",
                  "price": 200
                }
              ],
              "grade1": [
                {
                  "id": "minecraft:leather_helmet",
                  "price": 50
                },
                {
                  "id": "minecraft:leather_chestplate",
                  "price": 50
                },
                {
                  "id": "minecraft:leather_leggings",
                  "price": 50
                },
                {
                  "id": "minecraft:leather_boots",
                  "price": 50
                },
                {
                  "id": "minecraft:bow",
                  "price": 50
                }
              ],
              "entity": [
                "minecraft:wither_skeleton",
                "minecraft:piglin_brute",
                "minecraft:elder_guardian",
                "minecraft:ravager",
                "minecraft:warden"
              ]
            }
            """;

    /**
     * Default loot pool for the village-exclusive premium case. Unlike the
     * terminal (no key, minecraft:air) the premium case opens with the mid-tier gold key
     * (key1 = 3 gold = 12 points), so its total cost is the trade price
     * plus 12 points. It has NO {@code entity} list and {@code drop} 0, so
     * it never drops from mobs — the arms-dealer villager is the only
     * source (GDD §三 premium sink). Weights favour the upper tiers
     * ([40,80,140,200,300] ≈ 39% grade5) without matching the terminal's
     * 50%, keeping the terminal the premium apex.
     */
    private static final String PREMIUM_DEFAULT_JSON = """
            {
              "name": "#FFD700 军火商高级箱",
              "key": "csgobox:csgo_key1",
              "type": "csbox",
              "drop": 0.0,
              "random": [40, 80, 140, 200, 300],
              "grade5": [
                {"id": "minecraft:netherite_sword"},
                {"id": "minecraft:netherite_axe"},
                {"id": "minecraft:netherite_pickaxe"},
                {"id": "minecraft:netherite_shovel"},
                {"id": "minecraft:netherite_helmet"},
                {"id": "minecraft:netherite_chestplate"},
                {"id": "minecraft:netherite_leggings"},
                {"id": "minecraft:netherite_boots"},
                {"id": "minecraft:elytra"},
                {"id": "minecraft:totem_of_undying"}
              ],
              "grade4": [
                {"id": "minecraft:diamond_sword"},
                {"id": "minecraft:diamond_axe"},
                {"id": "minecraft:diamond_pickaxe"},
                {"id": "minecraft:diamond_shovel"},
                {"id": "minecraft:diamond_helmet"},
                {"id": "minecraft:diamond_chestplate"},
                {"id": "minecraft:diamond_leggings"},
                {"id": "minecraft:diamond_boots"},
                {"id": "minecraft:trident"},
                {"id": "minecraft:crossbow"}
              ],
              "grade3": [
                {"id": "minecraft:golden_sword"},
                {"id": "minecraft:golden_axe"},
                {"id": "minecraft:golden_pickaxe"},
                {"id": "minecraft:golden_helmet"},
                {"id": "minecraft:golden_chestplate"},
                {"id": "minecraft:golden_leggings"},
                {"id": "minecraft:golden_boots"},
                {"id": "minecraft:shield"},
                {"id": "minecraft:bow"},
                {"id": "minecraft:enchanted_golden_apple"}
              ],
              "grade2": [
                {"id": "minecraft:iron_sword"},
                {"id": "minecraft:iron_axe"},
                {"id": "minecraft:iron_pickaxe"},
                {"id": "minecraft:iron_helmet"},
                {"id": "minecraft:iron_chestplate"},
                {"id": "minecraft:iron_leggings"},
                {"id": "minecraft:iron_boots"},
                {"id": "minecraft:chainmail_helmet"},
                {"id": "minecraft:chainmail_chestplate"},
                {"id": "minecraft:chainmail_leggings"},
                {"id": "minecraft:chainmail_boots"}
              ],
              "grade1": [
                {"id": "minecraft:stone_sword"},
                {"id": "minecraft:stone_axe"},
                {"id": "minecraft:stone_pickaxe"},
                {"id": "minecraft:leather_helmet"},
                {"id": "minecraft:leather_chestplate"},
                {"id": "minecraft:leather_leggings"},
                {"id": "minecraft:leather_boots"},
                {"id": "minecraft:fishing_rod"}
              ]
            }
            """;

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
     * Writes a default {@code terminal.json} into the boxes directory when
     * none exists yet, so the terminal machine has its own decoupled loot
     * out of the box. A user-authored terminal config is never overwritten.
     */
    public static void writeDefaultTerminalIfMissing(Path boxesDir) {
        try {
            Path file = boxesDir.resolve("terminal.json");
            if (Files.exists(file)) {
                return;
            }
            Files.writeString(file, TERMINAL_DEFAULT_JSON);
            LOGGER.info("Wrote default terminal box config: {}", file);
        } catch (Exception e) {
            LOGGER.warn("Default terminal config skipped due to error: {}",
                    e.getMessage());
        }
    }

    /**
     * Upgrades a pre-v1.0.8 {@code terminal.json} to the type-driven format.
     * Since v1.0.8 the JSON {@code type} field is the single source of truth
     * for item registration, and the terminal machine no longer has a
     * {@code key} field (strict separation from regular crates). Configs
     * written against the v1.0.7 schema have neither: this one-time migration
     * adds {@code "type": "terminal"} and drops a legacy {@code key} (e.g.
     * {@code minecraft:air}) so existing servers keep their terminal without
     * manual edits. Only the exact {@code terminal.json} file is touched;
     * anything that already declares a {@code type} is left alone.
     *
     * <p>Unloadable files self-heal instead of silently degrading into a
     * regular crate: an empty file is rewritten with the default;
     * a non-empty corrupt file is backed up as
     * {@code terminal.json.corrupt-<millis>} before the default is written,
     * so the player's data is preserved for manual recovery while the
     * terminal keeps registering as a terminal.</p>
     */
    public static void upgradeLegacyTerminalConfig(Path boxesDir) {
        Path file = boxesDir.resolve("terminal.json");
        if (!Files.exists(file)) {
            return;
        }
        try {
            if (Files.size(file) == 0L) {
                Files.writeString(file, TERMINAL_DEFAULT_JSON);
                LOGGER.info("Recovered empty terminal.json with the default config: {}", file);
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

    /** Backs up an unreadable terminal.json and writes the default in its place. */
    private static void recoverCorruptTerminal(Path file) {
        try {
            Path backup = file.resolveSibling("terminal.json.corrupt-" + System.currentTimeMillis());
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(file, TERMINAL_DEFAULT_JSON);
            LOGGER.warn("Recovered corrupt terminal.json (backup kept at {}): {}", backup.getFileName(), file);
        } catch (IOException e) {
            LOGGER.warn("Terminal config recovery failed: {}", e.getMessage());
        }
    }

    /**
     * Writes a default {@code premium_supply_box.json} into the boxes
     * directory when none exists yet, so the village-exclusive premium case
     * (sold by the arms-dealer villager at level 3) resolves its loot out of
     * the box. A user-authored config is never overwritten.
     */
    public static void writeDefaultPremiumBoxIfMissing(Path boxesDir) {
        try {
            Path file = boxesDir.resolve("premium_supply_box.json");
            if (Files.exists(file)) {
                return;
            }
            Files.writeString(file, PREMIUM_DEFAULT_JSON);
            LOGGER.info("Wrote default premium box config: {}", file);
        } catch (Exception e) {
            LOGGER.warn("Default premium box config skipped due to error: {}",
                    e.getMessage());
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
