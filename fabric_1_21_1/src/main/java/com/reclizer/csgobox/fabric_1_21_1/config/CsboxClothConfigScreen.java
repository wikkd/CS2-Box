package com.reclizer.csgobox.fabric_1_21_1.config;

import com.reclizer.csgobox.fabric_1_21_1.CsgoBox;
import com.reclizer.csgobox.mcshared.gui.config.SharedClothConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config GUI entry for {@link CsboxConfig}: a thin shell delegating to
 * the shared {@link SharedClothConfigScreen} (single-sourced in
 * {@code common/src/main/mcshared}) with this platform's config bindings
 * injected. Fabric has no config spec — saving persists
 * {@code config/csgobox.json} through {@link CsgoBox#saveConfig()}.
 * Registration lives in {@link CsgoBoxModMenuHook} (Mod Menu); the class
 * only loads on clients with Cloth Config installed.
 */
public final class CsboxClothConfigScreen {

    private CsboxClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        return SharedClothConfigScreen.create(parent, CsgoBox.CONFIG,
                Component::translatable, CsgoBox::saveConfig);
    }
}
