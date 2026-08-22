package com.reclizer.csgobox.v26_1_2.event;

import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.gui.CsLookItemScreen;
import com.reclizer.csgobox.v26_1_2.gui.CsboxBulkOverviewScreen;
import com.reclizer.csgobox.v26_1_2.gui.CsboxBulkResultScreen;
import com.reclizer.csgobox.v26_1_2.gui.CsboxProgressScreen;
import com.reclizer.csgobox.v26_1_2.gui.CsboxScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Boosts the menu-blur radius while a csgobox translucent-background screen is
 * open: the GUI blur (1.21.1 {@code GameRenderer.processBlurEffect} and the
 * 26.x {@code MenuBlurRadius} global uniform) is driven by the vanilla
 * {@code menuBackgroundBlurriness} option and cannot be set directly, so the
 * option is temporarily raised to {@link CsgoBox#CONFIG} {@code blurRadius}
 * while our screens are shown and restored when the last one closes.
 *
 * <p>Soft-adaptation contract: when the Blur mod is installed it renders its
 * own blur with its own radius, so this boost intentionally does not apply to
 * that path (users tune radius in the Blur mod config).</p>
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = CsgoBox.MODID)
public final class ScreenBlurBoost {

    /** Vanilla option value captured before the first boost, or null. */
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
        OptionInstance<Integer> option = Minecraft.getInstance().options.menuBackgroundBlurriness();
        if (originalBlurriness == null) {
            originalBlurriness = option.get();
        }
        if (option.get() < radius) {
            option.set(radius);
        }
    }

    private static void restore() {
        if (originalBlurriness == null) {
            return;
        }
        Minecraft.getInstance().options.menuBackgroundBlurriness().set(originalBlurriness);
        originalBlurriness = null;
    }

    private static boolean isModScreen(Screen screen) {
        return screen instanceof CsboxScreen
                || screen instanceof CsboxProgressScreen
                || screen instanceof CsboxBulkOverviewScreen
                || screen instanceof CsboxBulkResultScreen
                || screen instanceof CsLookItemScreen;
    }
}
