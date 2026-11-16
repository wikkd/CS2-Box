package com.reclizer.csgobox.v1_21_1.utils;

/**
 * Public facade for the SlashBlade rack-form preview controls (立式/挂式
 * toggle). SlashBlade: Resharped only ships builds for 1.21.1 NeoForge and
 * 1.20.1 Forge, so this class exists on exactly those two platforms — it
 * keeps the SlashBlade-specific public statics out of {@link AnimRenderOps},
 * whose public op surface is signature-drift-checked across all platforms.
 * All state lives in AnimRenderOps (package-private); nothing here holds
 * SlashBlade classes.
 */
public final class SlashBladePreview {

    private SlashBladePreview() {
    }

    /** True while the current 3D preview renders a SlashBlade blade (arms
     *  the look screen's inspect-button style toggle). */
    public static boolean hasPreviewBlade() {
        return AnimRenderOps.hasSlashBladePreviewInstance();
    }

    /** Drops the blade marker; called when a look screen opens so the next
     *  preview re-rolls its random style. */
    public static void clearPreviewBlade() {
        AnimRenderOps.clearSlashBladePreviewInstance();
    }

    /** Flips the previewed blade between the floor rack (horizontal, 挂式)
     *  and the wall mount (vertical, 立式). */
    public static void toggleStyle() {
        AnimRenderOps.toggleSlashBladeStyle();
    }
}
