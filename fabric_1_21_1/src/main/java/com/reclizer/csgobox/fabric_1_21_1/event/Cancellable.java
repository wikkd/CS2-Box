package com.reclizer.csgobox.fabric_1_21_1.event;

/**
 * Fabric equivalent of NeoForge's {@code ICancellableEvent} — the mod's own
 * cancellable events implement this so listener code ports unchanged.
 */
public interface Cancellable {
    boolean isCanceled();

    void setCanceled(boolean canceled);
}
