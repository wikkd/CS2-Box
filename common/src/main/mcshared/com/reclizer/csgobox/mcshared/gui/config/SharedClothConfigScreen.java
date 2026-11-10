package com.reclizer.csgobox.mcshared.gui.config;

import com.reclizer.csgobox.config.CsboxConfigAccess;
import com.reclizer.csgobox.config.CsboxConfigDefaults;
import com.reclizer.csgobox.config.ErrorChatAudience;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

/**
 * Shared Cloth Config GUI for the {@link CsboxConfigAccess} bindings,
 * single-sourced across all eight platforms via {@code src/main/mcshared}
 * (compiled into every platform module).
 *
 * <p>The screen is a thin, optional wrapper over the existing platform
 * config bindings: it reads the current values through the
 * {@link CsboxConfigAccess} getters, writes through the {@code set*}
 * methods and lets the injected {@code save} runnable persist them back
 * (spec {@code save()} on NeoForge/Forge, JSON write on Fabric). No storage
 * migration happens — the TOML/JSON stays the single source of truth.
 *
 * <p>Translatable components are created through the injected {@code text}
 * factory because the 1.18.2-era MC API has no {@code Component.translatable}
 * static factory (platform shells pass {@code TranslatableComponent::new};
 * every newer platform passes {@code Component::translatable}).
 *
 * <p>Each platform keeps a thin same-named shell in its own {@code config}
 * package and only reaches this class behind {@code cloth_config} presence
 * + client dist; without Cloth Config installed the mod keeps the
 * TOML/JSON-only workflow.
 */
public final class SharedClothConfigScreen {

    private SharedClothConfigScreen() {
    }

    public static Screen create(Screen parent, CsboxConfigAccess config,
                                Function<String, Component> text, Runnable save) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(text.apply("gui.csgobox.config.title"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(
                text.apply("gui.csgobox.config.category.general"));
        general.addEntry(entryBuilder.startIntField(
                        text.apply("gui.csgobox.config.option.globalDropRatePercent"),
                        config.globalDropRatePercent())
                .setMin(CsboxConfigDefaults.GLOBAL_DROP_RATE_PERCENT_MIN)
                .setDefaultValue(CsboxConfigDefaults.GLOBAL_DROP_RATE_PERCENT)
                .setSaveConsumer(config::setGlobalDropRatePercent)
                .setTooltip(text.apply("gui.csgobox.config.option.globalDropRatePercent.tooltip"))
                .build());

        ConfigCategory advanced = builder.getOrCreateCategory(
                text.apply("gui.csgobox.config.category.advanced"));

        advanced.addEntry(entryBuilder.startBooleanToggle(
                        text.apply("gui.csgobox.config.option.loadDefaultBoxes"),
                        config.loadDefaultBoxes())
                .setDefaultValue(CsboxConfigDefaults.LOAD_DEFAULT_BOXES)
                .setSaveConsumer(config::setLoadDefaultBoxes)
                .setTooltip(text.apply("gui.csgobox.config.option.loadDefaultBoxes.tooltip"))
                .build());

        advanced.addEntry(entryBuilder.startBooleanToggle(
                        text.apply("gui.csgobox.config.option.enableAchievements"),
                        config.enableAchievements())
                .setDefaultValue(CsboxConfigDefaults.ENABLE_ACHIEVEMENTS)
                .setSaveConsumer(config::setEnableAchievements)
                .setTooltip(text.apply("gui.csgobox.config.option.enableAchievements.tooltip"))
                .build());

        advanced.addEntry(entryBuilder.startBooleanToggle(
                        text.apply("gui.csgobox.config.option.enableHotReload"),
                        config.enableHotReload())
                .setDefaultValue(CsboxConfigDefaults.ENABLE_HOT_RELOAD)
                .setSaveConsumer(config::setEnableHotReload)
                .setTooltip(text.apply("gui.csgobox.config.option.enableHotReload.tooltip"))
                .build());

        advanced.addEntry(entryBuilder.startIntField(
                        text.apply("gui.csgobox.config.option.bulkOpenCount"),
                        config.bulkOpenCount())
                .setMin(CsboxConfigDefaults.BULK_OPEN_COUNT_MIN)
                .setDefaultValue(CsboxConfigDefaults.BULK_OPEN_COUNT)
                .setSaveConsumer(config::setBulkOpenCount)
                .setTooltip(text.apply("gui.csgobox.config.option.bulkOpenCount.tooltip"))
                .build());

        advanced.addEntry(entryBuilder.startEnumSelector(
                        text.apply("gui.csgobox.config.option.jsonErrorAudience"),
                        ErrorChatAudience.class,
                        config.jsonErrorAudience())
                .setDefaultValue(ErrorChatAudience.valueOf(CsboxConfigDefaults.JSON_ERROR_AUDIENCE))
                .setEnumNameProvider(value -> text.apply(
                        "gui.csgobox.config.option.jsonErrorAudience." + value.name()))
                .setSaveConsumer(config::setJsonErrorAudience)
                .setTooltip(text.apply("gui.csgobox.config.option.jsonErrorAudience.tooltip"))
                .build());

        advanced.addEntry(entryBuilder.startBooleanToggle(
                        text.apply("gui.csgobox.config.option.damageItemByWear"),
                        config.damageItemByWear())
                .setDefaultValue(CsboxConfigDefaults.DAMAGE_ITEM_BY_WEAR)
                .setSaveConsumer(config::setDamageItemByWear)
                .setTooltip(text.apply("gui.csgobox.config.option.damageItemByWear.tooltip"))
                .build());

        builder.setSavingRunnable(save);
        return builder.build();
    }
}
