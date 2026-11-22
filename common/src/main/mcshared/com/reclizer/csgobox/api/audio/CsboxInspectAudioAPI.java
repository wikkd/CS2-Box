package com.reclizer.csgobox.api.audio;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Entry point for the look screen's inspect music switch (2026-11-22 v1) —
 * see {@link CsboxInspectAudioProvider} for the provider contract and
 * docs/INSPECT-AUDIO-API.md for the full reference.
 *
 * <p>Pure holder with no loader-specific code: the same class compiles on
 * every platform from the shared mcshared root. Playback itself is driven by
 * the platform-side inspect audio player, which consults {@link #firstSound}
 * before falling back to its built-in record path. Providers registered on a
 * platform whose player is not wired yet stay inert (no error) until that
 * platform's mirror lands.</p>
 */
public final class CsboxInspectAudioAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsboxInspectAudioAPI.class);

    /** Registered providers, highest priority first (ties keep order). */
    private static final CopyOnWriteArrayList<CsboxInspectAudioProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private CsboxInspectAudioAPI() {
    }

    /** Registers a provider for client-side playback resolution. */
    public static void register(CsboxInspectAudioProvider provider) {
        if (provider == null) {
            return;
        }
        int at = 0;
        for (CsboxInspectAudioProvider existing : PROVIDERS) {
            if (existing.priority() >= provider.priority()) {
                at++;
            } else {
                break;
            }
        }
        PROVIDERS.add(at, provider);
    }

    /** First provider sound for the item, or {@code null} when no provider
     *  matches (the caller then falls back to its built-in path). A provider
     *  that throws is logged once, removed and skipped. */
    public static SoundEvent firstSound(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (CsboxInspectAudioProvider provider : PROVIDERS) {
            try {
                if (!provider.matches(stack)) {
                    continue;
                }
                SoundEvent sound = provider.inspectSound(stack);
                if (sound != null) {
                    return sound;
                }
            } catch (Throwable t) {
                LOGGER.warn("[csgobox] inspect-audio provider {} threw for {}; removing it",
                        provider.getClass().getName(), stack.getItem(), t);
                PROVIDERS.remove(provider); // snapshot iterator: safe on COW list
            }
        }
        return null;
    }
}
