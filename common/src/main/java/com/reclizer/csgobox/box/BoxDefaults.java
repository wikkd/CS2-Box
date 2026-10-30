package com.reclizer.csgobox.box;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.reclizer.csgobox.villager.VillagerPricingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Default-config installer for {@code config/csbox/}: writes the default
 * {@code _villager_prices.json} and migrates a pre-v2.0.0 {@code terminal.json}.
 * Existing player files are never overwritten. common/ stays platform-free.
 *
 * <p>The bundled-tutorial installer that used to live here was removed:
 * tutorials are online-only now (Gitee mirror linked from {@code /csbox help}).</p>
 */
public final class BoxDefaults {

    private static final Gson GSON = new Gson();

    private static final Logger LOGGER = LoggerFactory.getLogger(BoxDefaults.class);

    private BoxDefaults() {
    }

    /**
     * Ensures the config directory exists before writing a default file.
     * Client-side callers have no server {@code loadAll()} to create
     * {@code config/csbox/} for them, so callers must not assume the
     * directory is pre-created.
     */
    static void ensureBoxesDir(Path boxesDir) throws IOException {
        Files.createDirectories(boxesDir);
    }

    /**
     * Writes a default {@code _villager_prices.json} when the file does not
     * exist yet, so server owners can see and tune the arms-dealer's dynamic
     * pricing. Existing files are never overwritten (player edits are
     * respected). Same defensive try-catch contract as the loader flow —
     * the worst case is no config file being written (the loader then uses
     * {@link VillagerPricingConfig#DEFAULT}).
     */
    public static synchronized void writeVillagerPricesIfMissing(Path boxesDir) {
        Path file = boxesDir.resolve(VillagerPricingConfig.FILE_NAME);
        if (Files.exists(file)) {
            return;
        }
        try {
            ensureBoxesDir(boxesDir);
            Files.writeString(file, VillagerPricingConfig.DEFAULT.toJson());
            LOGGER.info("Wrote default villager pricing config: {}", file);
        } catch (IOException e) {
            LOGGER.warn("Failed to write villager pricing config {}: {}", file, e.getMessage());
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

}
