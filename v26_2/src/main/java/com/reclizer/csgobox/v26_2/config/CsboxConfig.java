package com.reclizer.csgobox.v26_2.config;

import com.reclizer.csgobox.config.CsboxConfigDefaults;
import net.neoforged.neoforge.common.ModConfigSpec;

public class CsboxConfig {

    /** Sentinel upper bound meaning "no limit" for unbounded config ranges. */
    public static final int NO_UPPER_BOUND = CsboxConfigDefaults.NO_UPPER_BOUND;

    private final ModConfigSpec.BooleanValue loadDefaultBoxesValue;
    private final ModConfigSpec.BooleanValue enableAchievementsValue;
    private final ModConfigSpec.BooleanValue enableHotReloadValue;
    private final ModConfigSpec.IntValue bulkOpenCountValue;
    private final ModConfigSpec.IntValue globalDropRatePercentValue;
    private final ModConfigSpec.EnumValue<ErrorChatAudience> jsonErrorAudienceValue;
    private final ModConfigSpec.BooleanValue damageItemByWearValue;

    public CsboxConfig(ModConfigSpec.Builder builder) {
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

    public enum ErrorChatAudience {
        OP_ONLY,
        EVERYONE
    }
}
