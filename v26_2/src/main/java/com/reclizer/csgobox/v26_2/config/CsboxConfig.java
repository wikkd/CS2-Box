package com.reclizer.csgobox.v26_2.config;

import com.reclizer.csgobox.config.CsboxConfigDefaults;
import net.neoforged.neoforge.common.ModConfigSpec;

public class CsboxConfig {

    /** Sentinel upper bound meaning "no limit" for unbounded config ranges. */
    public static final int NO_UPPER_BOUND = CsboxConfigDefaults.NO_UPPER_BOUND;

    private final ModConfigSpec.BooleanValue loadDefaultBoxesValue;
    private final ModConfigSpec.BooleanValue enableDebugLoggingValue;
    private final ModConfigSpec.BooleanValue enableAchievementsValue;
    private final ModConfigSpec.BooleanValue enableHotReloadValue;
    private final ModConfigSpec.IntValue bulkOpenCountValue;
    private final ModConfigSpec.IntValue openSoundVolumeValue;
    private final ModConfigSpec.IntValue tickSoundVolumeValue;
    private final ModConfigSpec.IntValue finishSoundVolumeValue;
    private final ModConfigSpec.IntValue totalAnimationTicksValue;
    private final ModConfigSpec.IntValue animationSpeedMultiplierValue;
    private final ModConfigSpec.BooleanValue showItemNamesValue;
    private final ModConfigSpec.EnumValue<AnimationSpeed> animationSpeedValue;
    private final ModConfigSpec.IntValue globalDropRatePercentValue;
    private final ModConfigSpec.EnumValue<ErrorChatAudience> jsonErrorAudienceValue;
    private final ModConfigSpec.BooleanValue damageItemByWearValue;
    private final ModConfigSpec.EnumValue<BackgroundStyle> backgroundStyleValue;

    public CsboxConfig(ModConfigSpec.Builder builder) {
        builder.comment("General settings").push("general");
        this.animationSpeedValue = builder
                .comment("Animation playback speed: SLOW = 2x base, NORMAL = 1x base, FAST = 0.5x base")
                .defineEnum("animationSpeed", AnimationSpeed.valueOf(CsboxConfigDefaults.ANIMATION_SPEED));
        this.globalDropRatePercentValue = builder
                .comment("Global drop rate multiplier in percent (default 100; 0 = off, no upper bound)")
                .defineInRange("globalDropRatePercent", CsboxConfigDefaults.GLOBAL_DROP_RATE_PERCENT,
                        CsboxConfigDefaults.GLOBAL_DROP_RATE_PERCENT_MIN, NO_UPPER_BOUND);
        builder.pop();

        builder.comment("Advanced settings").push("advanced");
        this.loadDefaultBoxesValue = builder
                .comment("Auto-load default boxes from config/csbox/*.json on startup")
                .define("loadDefaultBoxes", CsboxConfigDefaults.LOAD_DEFAULT_BOXES);
        this.enableDebugLoggingValue = builder
                .comment("Enable verbose debug logging")
                .define("enableDebugLogging", CsboxConfigDefaults.ENABLE_DEBUG_LOGGING);
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

        builder.comment("Sound settings").push("sound");
        this.openSoundVolumeValue = builder
                .comment("Open sound volume in percent (0-100)")
                .defineInRange("openSoundVolume", CsboxConfigDefaults.OPEN_SOUND_VOLUME,
                        CsboxConfigDefaults.SOUND_VOLUME_MIN, CsboxConfigDefaults.SOUND_VOLUME_MAX);
        this.tickSoundVolumeValue = builder
                .comment("Tick sound volume in percent (0-100)")
                .defineInRange("tickSoundVolume", CsboxConfigDefaults.TICK_SOUND_VOLUME,
                        CsboxConfigDefaults.SOUND_VOLUME_MIN, CsboxConfigDefaults.SOUND_VOLUME_MAX);
        this.finishSoundVolumeValue = builder
                .comment("Finish sound volume in percent (0-100)")
                .defineInRange("finishSoundVolume", CsboxConfigDefaults.FINISH_SOUND_VOLUME,
                        CsboxConfigDefaults.SOUND_VOLUME_MIN, CsboxConfigDefaults.SOUND_VOLUME_MAX);
        builder.pop();

        builder.comment("Animation settings").push("animation");
        this.totalAnimationTicksValue = builder
                .comment("Base animation duration in ticks")
                .defineInRange("totalAnimationTicks", CsboxConfigDefaults.TOTAL_ANIMATION_TICKS,
                        CsboxConfigDefaults.TOTAL_ANIMATION_TICKS_MIN, CsboxConfigDefaults.TOTAL_ANIMATION_TICKS_MAX);
        this.animationSpeedMultiplierValue = builder
                .comment("Animation speed multiplier (higher = faster, minimum 1)")
                .defineInRange("animationSpeedMultiplier", CsboxConfigDefaults.ANIMATION_SPEED_MULTIPLIER,
                        CsboxConfigDefaults.ANIMATION_SPEED_MULTIPLIER_MIN, CsboxConfigDefaults.ANIMATION_SPEED_MULTIPLIER_MAX);
        this.showItemNamesValue = builder
                .comment("Show item names in box preview screen")
                .define("showItemNames", CsboxConfigDefaults.SHOW_ITEM_NAMES);
        builder.pop();

        builder.comment("UI settings").push("ui");
        this.backgroundStyleValue = builder
                .comment("Screen background style: TRANSLUCENT = blurred world shows through (default), OPAQUE = solid dark panels")
                .defineEnum("backgroundStyle", BackgroundStyle.valueOf(CsboxConfigDefaults.BACKGROUND_STYLE));
        builder.pop();
    }

    public boolean loadDefaultBoxes() {
        return loadDefaultBoxesValue.get();
    }

    public boolean enableDebugLogging() {
        return enableDebugLoggingValue.get();
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

    public int openSoundVolume() {
        return openSoundVolumeValue.get();
    }

    public int tickSoundVolume() {
        return tickSoundVolumeValue.get();
    }

    public int finishSoundVolume() {
        return finishSoundVolumeValue.get();
    }

    public int totalAnimationTicks() {
        return totalAnimationTicksValue.get();
    }

    public int animationSpeedMultiplier() {
        return animationSpeedMultiplierValue.get();
    }

    public boolean showItemNames() {
        return showItemNamesValue.get();
    }

    public AnimationSpeed animationSpeed() {
        return animationSpeedValue.get();
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

    public BackgroundStyle backgroundStyle() {
        return backgroundStyleValue.get();
    }

    public enum AnimationSpeed {
        SLOW,
        NORMAL,
        FAST
    }

    public enum ErrorChatAudience {
        OP_ONLY,
        EVERYONE
    }

    public enum BackgroundStyle {
        OPAQUE,
        TRANSLUCENT
    }
}
