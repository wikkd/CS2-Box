package com.reclizer.csgobox.forge_1_20_1.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * TACZ (Timeless & Classics Guns: Zero) inspect viewport integration facade.
 *
 * <p>This class contains no direct references to TACZ classes.
 * TACZ types are only referenced by {@link TaczInspectViewportImpl}, which is
 * loaded lazily via reflection when TACZ is actually present. The JVM
 * resolves class constants during verification, so any direct TACZ reference
 * here would throw NoClassDefFoundError at class-load time in environments
 * without TACZ - despite every method being guarded by {@link #isTaczLoaded()}.
 *
 * <p>Every method delegates to the impl through reflection; the impl catches
 * {@code Throwable} and degrades to a silent no-op (the 2D item icon).
 */
public final class TaczInspectViewport {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TACZ_MOD_ID = "tacz";
    private static final String IMPL_NAME =
            "com.reclizer.csgobox.forge_1_20_1.compat.TaczInspectViewportImpl";

    private static Class<?> implClass;

    private TaczInspectViewport() {
    }

    private static Class<?> impl() {
        if (implClass == null) {
            try {
                implClass = Class.forName(IMPL_NAME);
            } catch (ClassNotFoundException e) {
                LOGGER.warn("TACZ inspect impl class not found: {}", IMPL_NAME, e);
                implClass = TaczInspectViewportImplMissing.class;
            } catch (LinkageError e) {
                // TACZ classes unavailable at link time (verification resolves
                // the impl's TACZ-touching signatures): degrade silently.
                LOGGER.warn("TACZ inspect impl link failed: {}", IMPL_NAME, e);
                implClass = TaczInspectViewportImplMissing.class;
            }
        }
        return implClass;
    }

    /** Placeholder used when the impl class is missing entirely. */
    private static final class TaczInspectViewportImplMissing {
    }

    private static Object invoke(String method, Object... args) {
        try {
            Class<?> cls = impl();
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (a instanceof Float) {
                    types[i] = float.class;
                } else if (a instanceof Integer) {
                    types[i] = int.class;
                } else {
                    types[i] = a.getClass();
                }
            }
            Method m = cls.getDeclaredMethod(method, types);
            return m.invoke(null, args);
        } catch (Throwable t) {
            LOGGER.warn("TACZ facade invoke({}) failed", method, t);
            return null;
        }
    }

    public static boolean isTaczLoaded() {
        try {
            return ModList.get().isLoaded(TACZ_MOD_ID);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isAvailable(ItemStack stack) {
        if (!isTaczLoaded()) {
            return false;
        }
        Object r = invoke("isAvailable", stack);
        return Boolean.TRUE.equals(r);
    }

    public static boolean enter(ItemStack stack, LocalPlayer player) {
        if (!isTaczLoaded()) {
            return false;
        }
        Object r = invoke("enter", stack, player);
        return Boolean.TRUE.equals(r);
    }

    public static boolean enterDisplay(ItemStack stack, LocalPlayer player) {
        if (!isTaczLoaded()) {
            return false;
        }
        Object r = invoke("enterDisplay", stack, player);
        return Boolean.TRUE.equals(r);
    }

    public static void triggerInspect(ItemStack stack, LocalPlayer player) {
        if (!isTaczLoaded()) {
            return;
        }
        invoke("triggerInspect", stack, player);
    }

    public static boolean renderViewport(GuiGraphics guiGraphics, ItemStack stack, LocalPlayer player,
                                         float partialTicks, int centerX, int centerY, float scale) {
        if (!isTaczLoaded()) {
            return false;
        }
        Object r = invoke("renderViewport", guiGraphics, stack, player,
                Float.valueOf(partialTicks), Integer.valueOf(centerX), Integer.valueOf(centerY), Float.valueOf(scale));
        return Boolean.TRUE.equals(r);
    }

    public static void exit(ItemStack stack) {
        if (!isTaczLoaded()) {
            return;
        }
        invoke("exit", stack);
    }
}
