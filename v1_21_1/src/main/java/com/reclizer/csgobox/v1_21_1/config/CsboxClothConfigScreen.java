package com.reclizer.csgobox.v1_21_1.config;

import com.reclizer.csgobox.config.CsboxConfigDefaults;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config GUI for {@link CsboxConfig}.
 *
 * <p>This screen is a thin, optional wrapper over the existing
 * {@code ModConfigSpec} bindings: it reads the current values through the
 * config getters, writes through the new set* methods and lets
 * {@link CsgoBox#CONFIG_SPEC} persist them back to
 * {@code config/csgobox.toml}. No storage migration happens — Cloth Config
 * only provides the in-game editing UI, so the TOML stays the single source
 * of truth (identical to what the loaders would produce from ModConfigSpec).
 *
 * <p>Registered in the platform entry point behind
 * {@code ModList.isLoaded("cloth_config")} + {@code Dist.CLIENT}; without
 * Cloth Config installed the mod keeps the TOML-only workflow.
 */
public final class CsboxClothConfigScreen {

    private CsboxClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("gui.csgobox.config.title"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        CsboxConfig config = CsgoBox.CONFIG;

        ConfigCategory general = builder.getOrCreateCategory(
                Component.translatable("gui.csgobox.config.category.general"));
        general.addEntry(entryBuilder.startIntField(
                        Component.translatable("gui.csgobox.config.option.globalDropRatePercent"),
                        config.globalDropRatePercent())
                .setMin(CsboxConfigDefaults.GLOBAL_DROP_RATE_PERCENT_MIN)
                .setDefaultValue(CsboxConfigDefaults.GLOBAL_DROP_RATE_PERCENT)
                .setSaveConsumer(config::setGlobalDropRatePercent)
                .setTooltip(Component.translatable("gui.csgobox.config.option.globalDropRatePercent.tooltip"))
                .build());

        ConfigCategory advanced = builder.getOrCreateCategory(
                Component.translatable("gui.csgobox.config.category.advanced"));

        advanced.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("gui.csgobox.config.option.loadDefaultBoxes"),
                        config.loadDefaultBoxes())
                .setDefaultValue(CsboxConfigDefaults.LOAD_DEFAULT_BOXES)
                .setSaveConsumer(config::setLoadDefaultBoxes)
                .setTooltip(Component.translatable("gui.csgobox.config.option.loadDefaultBoxes.tooltip"))
                .build());

        advanced.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("gui.csgobox.config.option.enableAchievements"),
                        config.enableAchievements())
                .setDefaultValue(CsboxConfigDefaults.ENABLE_ACHIEVEMENTS)
                .setSaveConsumer(config::setEnableAchievements)
                .setTooltip(Component.translatable("gui.csgobox.config.option.enableAchievements.tooltip"))
                .build());

        advanced.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("gui.csgobox.config.option.enableHotReload"),
                        config.enableHotReload())
                .setDefaultValue(CsboxConfigDefaults.ENABLE_HOT_RELOAD)
                .setSaveConsumer(config::setEnableHotReload)
                .setTooltip(Component.translatable("gui.csgobox.config.option.enableHotReload.tooltip"))
                .build());

        advanced.addEntry(entryBuilder.startIntField(
                        Component.translatable("gui.csgobox.config.option.bulkOpenCount"),
                        config.bulkOpenCount())
                .setMin(CsboxConfigDefaults.BULK_OPEN_COUNT_MIN)
                .setDefaultValue(CsboxConfigDefaults.BULK_OPEN_COUNT)
                .setSaveConsumer(config::setBulkOpenCount)
                .setTooltip(Component.translatable("gui.csgobox.config.option.bulkOpenCount.tooltip"))
                .build());

        advanced.addEntry(entryBuilder.startEnumSelector(
                        Component.translatable("gui.csgobox.config.option.jsonErrorAudience"),
                        CsboxConfig.ErrorChatAudience.class,
                        config.jsonErrorAudience())
                .setDefaultValue(CsboxConfig.ErrorChatAudience.valueOf(CsboxConfigDefaults.JSON_ERROR_AUDIENCE))
                .setEnumNameProvider(value -> Component.translatable(
                        "gui.csgobox.config.option.jsonErrorAudience." + value.name()))
                .setSaveConsumer(config::setJsonErrorAudience)
                .setTooltip(Component.translatable("gui.csgobox.config.option.jsonErrorAudience.tooltip"))
                .build());

        advanced.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("gui.csgobox.config.option.damageItemByWear"),
                        config.damageItemByWear())
                .setDefaultValue(CsboxConfigDefaults.DAMAGE_ITEM_BY_WEAR)
                .setSaveConsumer(config::setDamageItemByWear)
                .setTooltip(Component.translatable("gui.csgobox.config.option.damageItemByWear.tooltip"))
                .build());

        builder.setSavingRunnable(() -> CsgoBox.CONFIG_SPEC.save());
        return builder.build();
    }
}