package com.reclizer.csgobox.forge_1_20_1.utils;

/**
 * Public facade for the Touhou Little Maid soul-slab preview controls
 * (session re-roll of the random maid model). Touhou Little Maid only ships
 * builds for 1.21.1 NeoForge and 1.20.1 Forge, so this class exists on
 * exactly those two platforms — it keeps the TLM-specific public statics out
 * of {@link AnimRenderOps}, whose public op surface is signature-drift
 * checked across all platforms. All state lives in AnimRenderOps
 * (package-private); nothing here holds TLM classes.
 */
public final class MaidSlabPreview {

    private MaidSlabPreview() {
    }

    /** Drops the session's random maid pick; called when a look screen opens
     *  so the next soul-slab preview rolls a fresh random maid (mirrors
     *  TLM's own random spawn semantics). */
    public static void clearPreviewPick() {
        AnimRenderOps.clearMaidPreviewPick();
    }
}
