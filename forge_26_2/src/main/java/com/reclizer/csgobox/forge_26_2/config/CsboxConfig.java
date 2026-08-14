package com.reclizer.csgobox.forge_26_2.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class CsboxConfig {

    /** Sentinel upper bound meaning "no limit" for unbounded config ranges. */
    public static final int NO_UPPER_BOUND = Integer.MAX_VALUE;

    private final ForgeConfigSpec.BooleanValue loadDefaultBoxesValue;
    private final ForgeConfigSpec.BooleanValue enableDebugLoggingValue;
    private final ForgeConfigSpec.BooleanValue enableAchievementsValue;
    private final ForgeConfigSpec.BooleanValue enableHotReloadValue;
    private final ForgeConfigSpec.IntValue bulkOpenCountValue;
    private final ForgeConfigSpec.IntValue openSoundVolumeValue;
    private final ForgeConfigSpec.IntValue tickSoundVolumeValue;
    private final ForgeConfigSpec.IntValue finishSoundVolumeValue;
    private final ForgeConfigSpec.IntValue totalAnimationTicksValue;
    private final ForgeConfigSpec.IntValue animationSpeedMultiplierValue;
    private final ForgeConfigSpec.BooleanValue showItemNamesValue;
    private final ForgeConfigSpec.EnumValue<AnimationSpeed> animationSpeedValue;
    private final ForgeConfigSpec.IntValue globalDropRatePercentValue;
    private final ForgeConfigSpec.EnumValue<ErrorChatAudience> jsonErrorAudienceValue;
    private final ForgeConfigSpec.BooleanValue damageItemByWearValue;

    public CsboxConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("General settings").push("general");
        this.animationSpeedValue = builder
                .comment("Animation playback speed: SLOW = 2x base, NORMAL = 1x base, FAST = 0.5x base")
                .defineEnum("animationSpeed", AnimationSpeed.NORMAL);
        this.globalDropRatePercentValue = builder
                .comment("Global drop rate multiplier in percent (default 100; 0 = off, no upper bound)")
                .defineInRange("globalDropRatePercent", 100, 0, NO_UPPER_BOUND);
        builder.pop();

        builder.comment("Advanced settings").push("advanced");
        this.loadDefaultBoxesValue = builder
                .comment("Auto-load default boxes from config/csbox/*.json on startup")
                .define("loadDefaultBoxes", true);
        this.enableDebugLoggingValue = builder
                .comment("Enable verbose debug logging")
                .define("enableDebugLogging", false);
        this.enableAchievementsValue = builder
                .comment("Enable the achievement system (stats are still accumulated when off)")
                .define("enableAchievements", true);
        this.enableHotReloadValue = builder
                .comment("Watch config/csbox/*.json and auto-reload on file changes (300ms debounce)")
                .define("enableHotReload", true);
        this.bulkOpenCountValue = builder
                .comment("Max boxes per bulk open (0 = unlimited, default). Server-enforced; the overview screen clamps its estimate to this value.")
                .defineInRange("bulkOpenCount", 0, 0, NO_UPPER_BOUND);
        this.jsonErrorAudienceValue = builder
                .comment("Who can see JSON load errors in chat on join: OP_ONLY (default) or EVERYONE")
                .defineEnum("jsonErrorAudience", ErrorChatAudience.OP_ONLY);
        this.damageItemByWearValue = builder
                .comment("Drawn items with durability lose durability by their wear value percentage (default on)")
                .define("damageItemByWear", true);
        builder.pop();

        builder.comment("Sound settings").push("sound");
        this.openSoundVolumeValue = builder
                .comment("Open sound volume in percent (0-100)")
                .defineInRange("openSoundVolume", 100, 0, 100);
        this.tickSoundVolumeValue = builder
                .comment("Tick sound volume in percent (0-100)")
                .defineInRange("tickSoundVolume", 50, 0, 100);
        this.finishSoundVolumeValue = builder
                .comment("Finish sound volume in percent (0-100)")
                .defineInRange("finishSoundVolume", 100, 0, 100);
        builder.pop();

        builder.comment("Animation settings").push("animation");
        this.totalAnimationTicksValue = builder
                .comment("Base animation duration in ticks")
                .defineInRange("totalAnimationTicks", 145, 20, 500);
        this.animationSpeedMultiplierValue = builder
                .comment("Animation speed multiplier (higher = faster, minimum 1)")
                .defineInRange("animationSpeedMultiplier", 1, 1, 10);
        this.showItemNamesValue = builder
                .comment("Show item names in box preview screen")
                .define("showItemNames", true);
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

    public enum AnimationSpeed {
        SLOW,
        NORMAL,
        FAST
    }

    public enum ErrorChatAudience {
        OP_ONLY,
        EVERYONE
    }
}
