package com.reclizer.csgobox.fabric_1_21_1.event;

import com.reclizer.csgobox.fabric_1_21_1.gui.CsLookItemScreen;
import com.reclizer.csgobox.fabric_1_21_1.gui.CsboxBulkOverviewScreen;
import com.reclizer.csgobox.fabric_1_21_1.gui.CsboxBulkResultScreen;
import com.reclizer.csgobox.fabric_1_21_1.gui.CsboxProgressScreen;
import com.reclizer.csgobox.fabric_1_21_1.gui.CsboxScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;

/**
 * Boosts the menu-blur radius while a csgobox translucent-background screen is
 * open (Fabric edition of the ScreenEvent.Opening/Closing wiring): the option
 * is temporarily raised to a fixed radius of 8 while our screens are shown and
 * restored when the last one closes.
 *
 * <p>Fabric has no Opening/Closing pair, so the same contract is reproduced
 * with the screen-api's global BEFORE_INIT (fires for every screen with the
 * NEW screen already active) plus the per-screen remove event. restore() is
 * idempotent, so the double trigger is harmless.</p>
 *
 * <p>Soft-adaptation contract: when the Blur mod is installed it renders its
 * own blur with its own radius, so this boost intentionally does not apply to
 * that path.</p>
 */
public final class ScreenBlurBoost {

    /** Vanilla option value captured before the first boost, or null. */
    private static Integer originalBlurriness;

    private ScreenBlurBoost() {
    }

    public static void register() {
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (isModScreen(screen)) {
                boost();
            } else {
                // Transitioning from one of our screens to a foreign one.
                restore();
            }
            // Closing our screen back to the game never fires BEFORE_INIT, so
            // hook the per-screen remove event (idempotent restore).
            ScreenEvents.remove(screen).register(c -> {
                if (isModScreen(screen)) {
                    restore();
                }
            });
        });
    }

    private static void boost() {
        int radius = 8;
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
