package com.reclizer.csgobox.api.preview;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Entry point of the CS2-Box preview API (2026-11-22 预览适配接口 v1):
 * {@code CsboxPreviewAPI.register(new MyPreviewProvider())} during client
 * setup. Safe to call any time on the client — resolution happens at render
 * time, so registration order relative to CS2-Box's own init is irrelevant.
 *
 * <p>See {@code docs/PREVIEW-API.md} for the full guide, including the
 * zero-code JSON declaration layer ({@code assets/<modid>/csgobox_preview/})
 * and the per-platform availability matrix.</p>
 */
public final class CsboxPreviewAPI {

    private static final CopyOnWriteArrayList<CsboxPreviewProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private CsboxPreviewAPI() {
    }

    /**
     * Registers a provider. Re-registering the same instance is a no-op, so
     * double client-init is harmless. {@code null} is ignored.
     */
    public static void register(CsboxPreviewProvider provider) {
        if (provider != null) {
            PROVIDERS.addIfAbsent(provider);
        }
    }

    /**
     * Platform wiring only — not for third-party use. Live snapshot of the
     * registered providers in registration order (the copy-on-write list is
     * iteration-stable); the wiring sorts by {@code priority()} descending,
     * ties keep registration order.
     */
    public static List<CsboxPreviewProvider> registered() {
        return PROVIDERS;
    }
}
