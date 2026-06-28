package com.reclizer.csgobox.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CsboxConfig {

    public boolean loadDefaultBoxes;
    public boolean enableDebugLogging;

    public int openSoundVolume;
    public int tickSoundVolume;
    public int finishSoundVolume;

    public int totalAnimationTicks;
    public int animationSpeedMultiplier;
    public boolean showItemNames;

    public AnimationSpeed animationSpeed;
    public int globalDropRatePercent;

    private final ModConfigSpec.BooleanValue loadDefaultBoxesValue;
    private final ModConfigSpec.BooleanValue enableDebugLoggingValue;
    private final ModConfigSpec.IntValue openSoundVolumeValue;
    private final ModConfigSpec.IntValue tickSoundVolumeValue;
    private final ModConfigSpec.IntValue finishSoundVolumeValue;
    private final ModConfigSpec.IntValue totalAnimationTicksValue;
    private final ModConfigSpec.IntValue animationSpeedMultiplierValue;
    private final ModConfigSpec.BooleanValue showItemNamesValue;
    private final ModConfigSpec.EnumValue<AnimationSpeed> animationSpeedValue;
    private final ModConfigSpec.IntValue globalDropRatePercentValue;

    public CsboxConfig(ModConfigSpec.Builder builder) {
        builder.comment("General settings").push("general");
        this.animationSpeedValue = builder
                .comment("Animation playback speed: SLOW = 2x base, NORMAL = 1x base, FAST = 0.5x base")
                .defineEnum("animationSpeed", AnimationSpeed.NORMAL);
        this.globalDropRatePercentValue = builder
                .comment("Global drop rate multiplier in percent (0-1000, default 100)")
                .defineInRange("globalDropRatePercent", 100, 0, 1000);
        builder.pop();

        builder.comment("Advanced settings").push("advanced");
        this.loadDefaultBoxesValue = builder
                .comment("Auto-load default boxes from config/csbox/*.json on startup")
                .define("loadDefaultBoxes", true);
        this.enableDebugLoggingValue = builder
                .comment("Enable verbose debug logging")
                .define("enableDebugLogging", false);
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

    public void init() {
        this.animationSpeed = this.animationSpeedValue.get();
        this.globalDropRatePercent = this.globalDropRatePercentValue.get();
        this.loadDefaultBoxes = this.loadDefaultBoxesValue.get();
        this.enableDebugLogging = this.enableDebugLoggingValue.get();
        this.openSoundVolume = this.openSoundVolumeValue.get();
        this.tickSoundVolume = this.tickSoundVolumeValue.get();
        this.finishSoundVolume = this.finishSoundVolumeValue.get();
        this.totalAnimationTicks = this.totalAnimationTicksValue.get();
        this.animationSpeedMultiplier = this.animationSpeedMultiplierValue.get();
        this.showItemNames = this.showItemNamesValue.get();
    }

    public enum AnimationSpeed {
        SLOW,
        NORMAL,
        FAST
    }
}
