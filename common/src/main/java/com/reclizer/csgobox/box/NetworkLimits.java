package com.reclizer.csgobox.box;

/**
 * Shared upper bounds for box data sent over the network. Every payload-size
 * guard (per-grade item lists, the preview sync packet) reads from here, so
 * raising the item budget only touches one place.
 */
public final class NetworkLimits {

    /** Max items carried by any single box network list (per-grade and preview sync). */
    public static final int MAX_ITEMS = 256;

    private NetworkLimits() {
    }
}
