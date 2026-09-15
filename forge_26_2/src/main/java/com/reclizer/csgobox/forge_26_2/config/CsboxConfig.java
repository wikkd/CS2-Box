package com.reclizer.csgobox.forge_26_2.config;

import com.reclizer.csgobox.config.CsboxConfigDefaults;
import net.minecraftforge.common.ForgeConfigSpec;

public class CsboxConfig {

    /** Sentinel upper bound meaning "no limit" for unbounded config ranges. */
    public static final int NO_UPPER_BOUND = CsboxConfigDefaults.NO_UPPER_BOUND;

    private final ForgeConfigSpec.BooleanValue loadDefaultBoxesValue;
    private final ForgeConfigSpec.BooleanValue enableAchievementsValue;
    private final ForgeConfigSpec.BooleanValue enableHotReloadValue;
    private final ForgeConfigSpec.IntValue bulkOpenCountValue;
    private final ForgeConfigSpec.IntValue globalDropRatePercentValue;
    private final ForgeConfigSpec.EnumValue<ErrorChatAudience> jsonErrorAudienceValue;
    private final ForgeConfigSpec.BooleanValue damageItemByWearValue;

    public CsboxConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("General settings").push("general");
        this.globalDropRatePercentValue = builder
                .comment("Global drop rate multiplier in percent (default 100; 0 = off, no upper bound)")
                .defineInRange("globalDropRatePercent", CsboxConfigDefaults.GLOBAL_DROP_RATE_PERCENT,
                        CsboxConfigDefaults.GLOBAL_DROP_RATE_PERCENT_MIN, NO_UPPER_BOUND);
        builder.pop();

        builder.comment("Advanced settings").push("advanced");
        this.loadDefaultBoxesValue = builder
                .comment("Auto-load box definitions from config/csbox/*.json on startup")
                .define("loadDefaultBoxes", CsboxConfigDefaults.LOAD_DEFAULT_BOXES);
        this.enableAchievementsValue = builder
                .comment("Enable the achievement system (stats are still accumulated when off)")
                .define("enableAchievements", CsboxConfigDefaults.ENABLE_ACHIEVEMENTS);
        this.enableHotReloadValue = builder
                .comment("Watch config/csbox/*.json and auto-reload on file changes (300ms debounce)")
                .define("enableHotReload", CsboxConfigDefaults.ENABLE_HOT_RELOAD);
        this.bulkOpenCountValue = builder
                .comment("Max boxes per bulk open (0 = unlimited, default). Server-enforced; the overview screen clamps its estimate to this value.")
                .defineInRange("bulkOpenCount", CsboxConfigDefaults.BULK_OPEN_COUNT,
                        CsboxConfigDefaults.BULK_OPEN_COUNT_MIN, NO_UPPER_BOUND);
        this.jsonErrorAudienceValue = builder
                .comment("Who can see JSON load errors in chat on join: OP_ONLY (default) or EVERYONE")
                .defineEnum("jsonErrorAudience", ErrorChatAudience.valueOf(CsboxConfigDefaults.JSON_ERROR_AUDIENCE));
        this.damageItemByWearValue = builder
                .comment("Drawn items with durability lose durability by their wear value percentage (default on)")
                .define("damageItemByWear", CsboxConfigDefaults.DAMAGE_ITEM_BY_WEAR);
        builder.pop();
    }

    public boolean loadDefaultBoxes() {
        return loadDefaultBoxesValue.get();
    }

    public boolean enableAchievements() {
        return enableAchievementsValue.get();
    }

    public boolean enableHotReload() {
        return enableHotReloadValue.get();
    }

    public int bulkOpenCount() {
        return bulkOpenCountValue.get();
    }

    public int globalDropRatePercent() {
        return globalDropRatePercentValue.get();
    }

    public ErrorChatAudience jsonErrorAudience() {
        return jsonErrorAudienceValue.get();
    }

    public boolean damageItemByWear() {
        return damageItemByWearValue.get();
    }

    // --- writable bindings (Cloth Config GUI) ---
    // The GUI writes through ForgeConfigSpec so config/csgobox.toml remains
    // the single source of truth; call CsgoBox.CONFIG_SPEC.save() after.

    public void setLoadDefaultBoxes(boolean value) {
        loadDefaultBoxesValue.set(value);
    }

    public void setEnableAchievements(boolean value) {
        enableAchievementsValue.set(value);
    }

    public void setEnableHotReload(boolean value) {
        enableHotReloadValue.set(value);
    }

    public void setBulkOpenCount(int value) {
        bulkOpenCountValue.set(value);
    }

    public void setGlobalDropRatePercent(int value) {
        globalDropRatePercentValue.set(value);
    }

    public void setJsonErrorAudience(ErrorChatAudience value) {
        jsonErrorAudienceValue.set(value);
    }

    public void setDamageItemByWear(boolean value) {
        damageItemByWearValue.set(value);
    }

    public enum ErrorChatAudience {
        OP_ONLY,
        EVERYONE
    }
}
