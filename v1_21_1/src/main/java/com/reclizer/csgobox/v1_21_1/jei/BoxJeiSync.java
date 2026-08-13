package com.reclizer.csgobox.v1_21_1.jei;

/**
 * Client-side bridge between box-registry mutations and the JEI plugin.
 *
 * <p>The JEI plugin (present only when JEI is installed) registers a
 * refresher here; the box-definition sync packet handler invokes
 * {@link #onBoxRegistryChanged()} after repopulating the client registry.
 * This class itself never references JEI classes, so the packet path stays
 * JEI-free when JEI is absent.</p>
 */
public final class BoxJeiSync {

    private static volatile Runnable refresher;

    private BoxJeiSync() {
    }

    public static void setRefresher(Runnable refresher) {
        BoxJeiSync.refresher = refresher;
    }

    /** Must be called on the client thread after the box registry changed. */
    public static void onBoxRegistryChanged() {
        Runnable runnable = refresher;
        if (runnable != null) {
            runnable.run();
        }
    }
}
