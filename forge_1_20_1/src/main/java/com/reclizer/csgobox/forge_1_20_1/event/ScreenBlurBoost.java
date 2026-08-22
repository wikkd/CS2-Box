package com.reclizer.csgobox.forge_1_20_1.event;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = CsgoBox.MODID)
public final class ScreenBlurBoost {

    private static Integer originalBlurriness;

    private ScreenBlurBoost() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        boolean openingOurs = isModScreen(event.getNewScreen());
        boolean leavingOurs = isModScreen(event.getCurrentScreen());
        if (openingOurs && !leavingOurs) {
            boost();
        } else if (leavingOurs && !openingOurs) {
            restore();
        }
    }

    private static void boost() {
        int radius = CsgoBox.CONFIG.blurRadius();
        if (radius <= 0) {
            return;
        }
        int current = getMenuBlurRadius();
        if (originalBlurriness == null) {
            originalBlurriness = current;
        }
        if (current < radius) {
            setMenuBlurRadius(radius);
        }
    }

    private static void restore() {
        if (originalBlurriness == null) {
            return;
        }
        setMenuBlurRadius(originalBlurriness);
        originalBlurriness = null;
    }

    private static boolean isModScreen(Screen screen) {
        // TODO: uncomment gui class checks as they are ported
        // return screen instanceof CsboxScreen
        //         || screen instanceof CsboxProgressScreen
        //         || screen instanceof CsboxBulkOverviewScreen
        //         || screen instanceof CsboxBulkResultScreen
        //         || screen instanceof CsLookItemScreen;
        return false;
    }

    @SuppressWarnings("unchecked")
    private static int getMenuBlurRadius() {
        try {
            Object options = Minecraft.getInstance().options;
            Field blurField = findBlurField(options);
            if (blurField == null) return 0;
            blurField.setAccessible(true);
            Object val = blurField.get(options);
            // OptionInstance<Integer> — call .get()
            if (val != null) {
                try {
                    return (int) val.getClass().getMethod("get").invoke(val);
                } catch (NoSuchMethodException e) {
                    // raw int field
                    return (int) val;
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static void setMenuBlurRadius(int value) {
        try {
            Object options = Minecraft.getInstance().options;
            Field blurField = findBlurField(options);
            if (blurField == null) return;
            blurField.setAccessible(true);
            Object val = blurField.get(options);
            if (val != null) {
                try {
                    val.getClass().getMethod("set", Object.class).invoke(val, value);
                } catch (NoSuchMethodException e) {
                    // raw int field
                    blurField.setInt(options, value);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static Field findBlurField(Object options) {
        // Try known field names: Mojang mapped, SRG, MCP
        String[] names = {"menuBackgroundBlurriness", "menuBlur", "f_92144_"};
        for (String name : names) {
            try {
                Field f = options.getClass().getField(name);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        // Fallback: scan all OptionInstance<int> fields
        for (Field f : options.getClass().getFields()) {
            if (f.getType().getSimpleName().equals("OptionInstance")) {
                return f;
            }
        }
        return null;
    }
}
