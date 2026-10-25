package com.reclizer.csgobox.forge_1_18_2.config;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Holds the Cloth Config extension-point registration so the lambda that
 * references client-only classes lives in a class the dedicated server
 * never loads (issue #2 pattern).
 *
 * <p>1.18.2: the {@code ConfigScreenHandler.ConfigScreenFactory} extension
 * class is an injected Forge source that is not on this module's compile
 * classpath, so the registration is done reflectively (registered at runtime
 * on real Forge 1.18.2, silently skipped where the class is absent).</p>
 */
public final class ClothConfigHook {

    private ClothConfigHook() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerIfClientWithCloth() {
        if (FMLEnvironment.dist == Dist.CLIENT
                && ModList.get().isLoaded("cloth_config")) {
            try {
                Class<?> factoryClass = Class.forName(
                        "net.minecraftforge.client.ConfigScreenHandler$ConfigScreenFactory");
                Object factory = factoryClass
                        .getConstructor(java.util.function.Function.class)
                        .newInstance((java.util.function.Function<
                                net.minecraft.client.gui.screens.Screen,
                                net.minecraft.client.gui.screens.Screen>)
                                screen -> CsboxClothConfigScreen.create(screen));
                java.lang.reflect.Method register = ModLoadingContext.class
                        .getMethod("registerExtensionPoint", Class.class, java.util.function.Supplier.class);
                register.invoke(ModLoadingContext.get(), factoryClass,
                        (java.util.function.Supplier) () -> factory);
            } catch (Throwable t) {
                com.reclizer.csgobox.forge_1_18_2.CsgoBox.LOGGER
                        .warn("Cloth Config screen registration unavailable: {}", t.toString());
            }
        }
    }
}
