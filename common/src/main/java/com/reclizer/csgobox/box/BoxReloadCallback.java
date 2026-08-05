package com.reclizer.csgobox.box;

/**
 * Hook invoked by the common {@link BoxFileWatcher} after a debounced file
 * change. The version-specific loader implements this to call
 * {@code BoxJsonLoader.reloadPreserving()} on its own thread, keeping the
 * watcher free of Minecraft dependencies.
 */
@FunctionalInterface
public interface BoxReloadCallback {
    void reload();
}
