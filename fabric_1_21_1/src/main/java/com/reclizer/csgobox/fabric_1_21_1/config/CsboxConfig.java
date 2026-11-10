package com.reclizer.csgobox.fabric_1_21_1.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.reclizer.csgobox.config.CsboxConfigAccess;
import com.reclizer.csgobox.config.CsboxConfigDefaults;
import com.reclizer.csgobox.config.ErrorChatAudience;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Fabric config holder — same getter/setter surface as the NeoForge
 * {@code CsboxConfig} (the Cloth Config screen and every gameplay call site
 * port unchanged), backed by a plain JSON file at {@code config/csgobox.json}
 * because Fabric has no ModConfigSpec.
 *
 * <p>Values are clamped to the shared {@link CsboxConfigDefaults} ranges on
 * load; writes through the setters are clamped the same way and only take
 * effect in memory until {@link #save()} persists the file.</p>
 */
public class CsboxConfig implements CsboxConfigAccess {

    /** Sentinel upper bound meaning "no limit" for unbounded config ranges. */
    public static final int NO_UPPER_BOUND = CsboxConfigDefaults.NO_UPPER_BOUND;

    private static final Logger LOGGER = LoggerFactory.getLogger(CsboxConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private volatile Path filePath;

    /** Resolved lazily: FabricLoader is not initialized in bare JVMs (unit
     *  tests), so the entry class must stay loadable without it. */
    private Path file() {
        Path f = filePath;
        if (f == null) {
            synchronized (this) {
                f = filePath;
                if (f == null) {
                    f = FabricLoader.getInstance().getConfigDir().resolve("csgobox.json");
                    filePath = f;
                }
            }
        }
        return f;
    }

    private volatile boolean loadDefaultBoxes = CsboxConfigDefaults.LOAD_DEFAULT_BOXES;
    private volatile boolean enableAchievements = CsboxConfigDefaults.ENABLE_ACHIEVEMENTS;
    private volatile boolean enableHotReload = CsboxConfigDefaults.ENABLE_HOT_RELOAD;
    private volatile int bulkOpenCount = CsboxConfigDefaults.BULK_OPEN_COUNT;
    private volatile int globalDropRatePercent = CsboxConfigDefaults.GLOBAL_DROP_RATE_PERCENT;
    private volatile ErrorChatAudience jsonErrorAudience =
            ErrorChatAudience.valueOf(CsboxConfigDefaults.JSON_ERROR_AUDIENCE);
    private volatile boolean damageItemByWear = CsboxConfigDefaults.DAMAGE_ITEM_BY_WEAR;
    private volatile boolean loaded;

    /** Loads (or re-loads) {@code config/csgobox.json}; missing file = defaults. */
    public synchronized void load() {
        Path file = file();
        if (!Files.exists(file)) {
            loaded = true;
            save();
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            if (root != null) {
                if (root.has("globalDropRatePercent")) {
                    this.globalDropRatePercent = clampInt(root.get("globalDropRatePercent").getAsInt(),
                            CsboxConfigDefaults.GLOBAL_DROP_RATE_PERCENT_MIN, NO_UPPER_BOUND);
                }
                if (root.has("loadDefaultBoxes")) {
                    this.loadDefaultBoxes = root.get("loadDefaultBoxes").getAsBoolean();
                }
                if (root.has("enableAchievements")) {
                    this.enableAchievements = root.get("enableAchievements").getAsBoolean();
                }
                if (root.has("enableHotReload")) {
                    this.enableHotReload = root.get("enableHotReload").getAsBoolean();
                }
                if (root.has("bulkOpenCount")) {
                    this.bulkOpenCount = clampInt(root.get("bulkOpenCount").getAsInt(),
                            CsboxConfigDefaults.BULK_OPEN_COUNT_MIN, NO_UPPER_BOUND);
                }
                if (root.has("jsonErrorAudience")) {
                    String value = root.get("jsonErrorAudience").getAsString();
                    try {
                        this.jsonErrorAudience = ErrorChatAudience.valueOf(value);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Unknown jsonErrorAudience '{}' in csgobox.json — keeping default", value);
                    }
                }
                if (root.has("damageItemByWear")) {
                    this.damageItemByWear = root.get("damageItemByWear").getAsBoolean();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read csgobox.json — keeping in-memory defaults", e);
        }
        loaded = true;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    /** Persists the current values to {@code config/csgobox.json} atomically. */
    public synchronized void save() {
        Path file = file();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("globalDropRatePercent", globalDropRatePercent);
            root.addProperty("loadDefaultBoxes", loadDefaultBoxes);
            root.addProperty("enableAchievements", enableAchievements);
            root.addProperty("enableHotReload", enableHotReload);
            root.addProperty("bulkOpenCount", bulkOpenCount);
            root.addProperty("jsonErrorAudience", jsonErrorAudience.name());
            root.addProperty("damageItemByWear", damageItemByWear);
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOGGER.error("Failed to write csgobox.json", e);
        }
    }

    /** Lazily loads the file exactly once at first gameplay access. */
    private void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    public boolean loadDefaultBoxes() {
        ensureLoaded();
        return loadDefaultBoxes;
    }

    public boolean enableAchievements() {
        ensureLoaded();
        return enableAchievements;
    }

    public boolean enableHotReload() {
        ensureLoaded();
        return enableHotReload;
    }

    public int bulkOpenCount() {
        ensureLoaded();
        return bulkOpenCount;
    }

    public int globalDropRatePercent() {
        ensureLoaded();
        return globalDropRatePercent;
    }

    public ErrorChatAudience jsonErrorAudience() {
        ensureLoaded();
        return jsonErrorAudience;
    }

    public boolean damageItemByWear() {
        ensureLoaded();
        return damageItemByWear;
    }

    // --- writable bindings (Cloth Config GUI) ---
    // Writes clamp to the shared ranges; call CsgoBox.saveConfig() to persist
    // (the Cloth screen's saving runnable does exactly that).

    public void setLoadDefaultBoxes(boolean value) {
        this.loadDefaultBoxes = value;
    }

    public void setEnableAchievements(boolean value) {
        this.enableAchievements = value;
    }

    public void setEnableHotReload(boolean value) {
        this.enableHotReload = value;
    }

    public void setBulkOpenCount(int value) {
        this.bulkOpenCount = clampInt(value, CsboxConfigDefaults.BULK_OPEN_COUNT_MIN, NO_UPPER_BOUND);
    }

    public void setGlobalDropRatePercent(int value) {
        this.globalDropRatePercent = clampInt(value, CsboxConfigDefaults.GLOBAL_DROP_RATE_PERCENT_MIN, NO_UPPER_BOUND);
    }

    public void setJsonErrorAudience(ErrorChatAudience value) {
        this.jsonErrorAudience = value;
    }

    public void setDamageItemByWear(boolean value) {
        this.damageItemByWear = value;
    }
}
