package com.reclizer.csgobox.v26_2.config;

import com.reclizer.csgobox.mcshared.gui.config.SharedClothConfigScreen;
import com.reclizer.csgobox.v26_2.CsgoBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config GUI entry for {@link CsboxConfig}: a thin shell delegating to
 * the shared {@link SharedClothConfigScreen} (single-sourced in
 * {@code common/src/main/mcshared}) with this platform's config bindings and
 * spec save callback injected. Registration lives in {@link ClothConfigHook};
 * the class only loads on clients with Cloth Config installed.
 */
public final class CsboxClothConfigScreen {

    private CsboxClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        return SharedClothConfigScreen.create(parent, CsgoBox.CONFIG,
                Component::translatable, () -> CsgoBox.CONFIG_SPEC.save());
    }
}
