package com.reclizer.csgobox.v26_2.utils;

import net.minecraft.client.Minecraft;

/**
 * HUD visibility helper for MC 26.2.
 *
 * <p>{@code Options.hideGui} was removed in 26.2; the replacement is
 * {@code Minecraft.gui.hud.toggle()}/{@code isHidden()}, which flip a
 * boolean instead of setting one. This class wraps the toggle with a
 * set-style API so screens can hide the hotbar/health bar while a
 * box-opening animation is on screen and restore it afterwards,
 * independent of how many times the state was touched.</p>
 */
public final class HudVisibility {

    private HudVisibility() {
    }

    /** Hide the HUD (hotbar, health bar, ...) until {@link #show()} is called. */
    public static void hide() {
        setHidden(true);
    }

    /** Restore the HUD to visible. Safe to call multiple times. */
    public static void show() {
        setHidden(false);
    }

    private static void setHidden(boolean hidden) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.hud == null) {
            return;
        }
        if (mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
    }
}
