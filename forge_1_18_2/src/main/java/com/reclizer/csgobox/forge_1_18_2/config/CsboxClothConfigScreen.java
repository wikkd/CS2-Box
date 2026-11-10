package com.reclizer.csgobox.forge_1_18_2.config;

import com.reclizer.csgobox.forge_1_18_2.CsgoBox;
import com.reclizer.csgobox.mcshared.gui.config.SharedClothConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * Cloth Config GUI entry for {@link CsboxConfig}: a thin shell delegating to
 * the shared {@link SharedClothConfigScreen} (single-sourced in
 * {@code common/src/main/mcshared}) with this platform's config bindings and
 * spec save callback injected. 1.18.2 has no {@code Component.translatable}
 * static factory, so the shell passes the constructor reference instead.
 * Registration lives in {@link ClothConfigHook}; the class only loads on
 * clients with Cloth Config installed.
 */
public final class CsboxClothConfigScreen {

    private CsboxClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        return SharedClothConfigScreen.create(parent, CsgoBox.CONFIG,
                TranslatableComponent::new, () -> CsgoBox.CONFIG_SPEC.save());
    }
}
