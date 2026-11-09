package com.reclizer.csgobox.v1_21_1.emi;

/**
 * Client-side bridge between box-registry mutations and the EMI plugin.
 *
 * <p>EMI has no runtime recipe-add/remove API like JEI's
 * {@code IRecipeManager}: every {@code @EmiEntrypoint} plugin is re-run on an
 * EMI reload, and EMI reloads ride on Minecraft's resource reload. The plugin
 * registers a refresher here; the box-definition sync packet handler invokes
 * {@link #onBoxRegistryChanged()} after repopulating the client registry, and
 * the refresher schedules one {@code Minecraft.reloadResourcePacks()} so EMI
 * re-runs the plugin against the fresh registry.</p>
 *
 * <p>This class itself never references EMI classes, so the packet path stays
 * EMI-free when EMI is absent (the refresher is only installed by the EMI
 * plugin, which only exists under an EMI runtime).</p>
 */
public final class BoxEmiReload {

    private static volatile Runnable refresher;

    private BoxEmiReload() {
    }

    public static void setRefresher(Runnable refresher) {
        BoxEmiReload.refresher = refresher;
    }

    /**
     * Called on the client thread after the box registry changed. A content
     * fingerprint skips the (expensive) EMI reload when a sync carried an
     * unchanged registry — the packet fires on every player join, and EMI's
     * only reload path is a full resource reload. The first sync and any
     * real content change always reload.
     */
    /** Content fingerprint at the last reload trigger (0 hash = never fired). */
    private static int lastFingerprint;
    private static boolean fingerprintValid;

    public static void onBoxRegistryChanged() {
        int fingerprint = com.reclizer.csgobox.v1_21_1.box.BoxRegistry.getAll().hashCode();
        if (fingerprintValid && fingerprint == lastFingerprint) {
            return;
        }
        lastFingerprint = fingerprint;
        fingerprintValid = true;
        Runnable runnable = refresher;
        if (runnable != null) {
            runnable.run();
        }
    }
}