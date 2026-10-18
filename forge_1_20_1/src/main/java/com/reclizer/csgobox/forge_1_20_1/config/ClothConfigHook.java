package com.reclizer.csgobox.forge_1_20_1.config;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Holds the Cloth Config extension-point registration so the lambda that
 * references client-only classes lives in a class the dedicated server
 * never loads.
 *
 * <p>The entry point only calls {@link #registerIfClientWithCloth()}, but
 * even a guarded lambda is compiled into the calling class as a synthetic
 * method whose signature mentions a {@code Screen} type. Forge's event bus
 * reflects over method signatures of registered objects, so keeping the
 * lambda near any class that might later be event-bus-registered would
 * risk the issue #2 crash pattern ("Attempted to load class ... Screen for
 * invalid dist DEDICATED_SERVER"). This class is only loaded on clients
 * with Cloth installed.
 */
public final class ClothConfigHook {

    private ClothConfigHook() {
    }

    public static void registerIfClientWithCloth() {
        if (FMLEnvironment.dist == Dist.CLIENT
                && ModList.get().isLoaded("cloth_config")) {
            ModLoadingContext.get().registerExtensionPoint(
                    net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                            (minecraft, screen) -> CsboxClothConfigScreen.create(screen)));
        }
    }
}
