package com.reclizer.csgobox.v26_2.config;

import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;

/**
 * Holds the Cloth Config extension-point registration so the lambda that
 * references client-only classes lives in a class the dedicated server
 * never loads.
 *
 * <p>The entry point only calls {@link #registerIfClientWithCloth()} inside a
 * dist guard, but even a guarded lambda is compiled into the main class as a
 * synthetic method whose signature mentions
 * {@code net.minecraft.client.gui.screens.Screen}. When the entry point later
 * registers itself on the game event bus, the bus reflects over every
 * declared method and resolves those parameter types, which crashes a
 * dedicated server with "Attempted to load class ... Screen for invalid dist
 * DEDICATED_SERVER" (issue #2). Keeping the lambda here means the synthetic
 * method is scanned nowhere: this class is only loaded on clients with Cloth
 * installed.
 */
public final class ClothConfigHook {

    private ClothConfigHook() {
    }

    public static void registerIfClientWithCloth() {
        if (FMLEnvironment.getDist() == Dist.CLIENT
                && ModList.get().isLoaded("cloth_config")) {
            ModLoadingContext.get().getActiveContainer().registerExtensionPoint(
                    net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    (modContainer, screen) -> CsboxClothConfigScreen.create(screen));
        }
    }
}
