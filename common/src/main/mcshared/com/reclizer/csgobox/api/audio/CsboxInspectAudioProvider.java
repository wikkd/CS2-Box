package com.reclizer.csgobox.api.audio;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

/**
 * Third-party provider for the look screen's inspect music switch (2026-11-22
 * v1): lets any item play audio when the inspect toolbar button is clicked,
 * without being a {@code RecordItem}.
 *
 * <p>Register from client setup via {@link CsboxInspectAudioAPI#register}.
 * Resolution order per click: providers first (higher {@link #priority()}
 * wins, ties keep registration order), then the built-in RecordItem path.
 * Returning {@code null} from either method passes to the next provider (and
 * then the built-in path); a provider that throws is logged once, removed and
 * skipped. The returned sound plays through the jukebox path, so the record
 * volume slider applies. See docs/INSPECT-AUDIO-API.md for the full
 * reference.</p>
 */
public abstract class CsboxInspectAudioProvider {

    /** Higher wins; ties keep registration order. */
    public int priority() {
        return 0;
    }

    /** Whether this provider handles the item. */
    public abstract boolean matches(ItemStack stack);

    /** The sound to play for the item, or {@code null} to pass. */
    public abstract SoundEvent inspectSound(ItemStack stack);
}
