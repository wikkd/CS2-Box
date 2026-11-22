package com.reclizer.csgobox.forge_1_20_1.utils;

import com.reclizer.csgobox.api.audio.CsboxInspectAudioAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;

/**
 * Music-disc inspect (2026-11-22): the look screen's inspect toolbar button
 * doubles as the music switch for records — click to play the disc's song,
 * click again to stop, and closing the screen always stops playback.
 *
 * <p>Playback reuses the jukebox's own {@link SimpleSoundInstance#forRecord}
 * (RECORDS sound source, so the record volume slider applies) anchored at
 * the player's feet — the player stands still while the screen is up, which
 * plays at full volume. Stopping targets only our instance via
 * {@link SoundManager#stop(SoundInstance)}, leaving real jukeboxes alone.
 * {@link SoundManager#isActive(SoundInstance)} drops to false once a record
 * finishes on its own, so the next click restarts it instead of needing a
 * double click. Modded discs extend {@link RecordItem} and play the same;
 * third-party items ride the inspect-audio SPI ({@link CsboxInspectAudioAPI})
 * and its zero-code JSON declarations
 * ({@code assets/&lt;modid&gt;/csgobox_inspect_audio/*.json}, see
 * {@link CsboxInspectAudioManager}), both resolving before the built-in
 * record path.</p>
 */
public final class DiscInspect {

    private DiscInspect() {
    }

    /** The instance currently playing for the inspect flow (null = silent). */
    private static SoundInstance playing;

    /** Whether the item plays audio through the inspect path: a registered
     *  inspect-audio provider first, then a JSON declaration, then
     *  vanilla/modded {@link RecordItem} subclasses. */
    public static boolean isAudioItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return CsboxInspectAudioAPI.firstSound(stack) != null
                || CsboxInspectAudioManager.firstDeclaredSound(stack) != null
                || stack.getItem() instanceof RecordItem;
    }

    /** Inspect-button toggle: true = now playing. */
    public static boolean toggle(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (playing != null) {
            if (mc.getSoundManager().isActive(playing)) {
                mc.getSoundManager().stop(playing);
                playing = null;
                return false;
            }
            playing = null; // finished on its own: fall through and replay.
        }
        if (!isAudioItem(stack) || mc.player == null) {
            return false;
        }
        SoundEvent sound = CsboxInspectAudioAPI.firstSound(stack);
        if (sound == null) {
            sound = CsboxInspectAudioManager.firstDeclaredSound(stack);
        }
        if (sound == null && stack.getItem() instanceof RecordItem record) {
            sound = record.getSound();
        }
        if (sound == null) {
            return false;
        }
        SimpleSoundInstance instance = SimpleSoundInstance.forRecord(sound, mc.player.position());
        mc.getSoundManager().play(instance);
        playing = instance;
        return true;
    }

    /** Screen teardown (removed()): stop our instance; idempotent. */
    public static void stop() {
        if (playing == null) {
            return;
        }
        Minecraft.getInstance().getSoundManager().stop(playing);
        playing = null;
    }

    /** Green underline state for the inspect button: lit only while the
     *  record is actually (or still) playing. */
    public static boolean isPlaying() {
        return playing != null && Minecraft.getInstance().getSoundManager().isActive(playing);
    }
}
