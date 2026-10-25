package com.reclizer.csgobox.fabric_1_21_1.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod Menu integration: hosts the Cloth Config entry button in Mod Menu's
 * mod list. Fabric only invokes the "modmenu" entrypoint when Mod Menu is
 * installed, and Cloth Config is checked again here — the entrypoint implies
 * nothing about Cloth. Without either mod this class is never loaded.
 */
public final class CsgoBoxModMenuHook implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return parent -> null;
        }
        return CsboxClothConfigScreen::create;
    }
}
