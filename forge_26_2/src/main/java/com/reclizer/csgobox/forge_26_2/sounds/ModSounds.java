package com.reclizer.csgobox.forge_26_2.sounds;

import com.reclizer.csgobox.forge_26_2.CsgoBox;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModSounds {
    private ModSounds() {
    }

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(net.minecraft.core.registries.Registries.SOUND_EVENT, CsgoBox.MODID);

    private static Supplier<SoundEvent> registerSoundEvent(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(CsgoBox.MODID, name)));
    }

    public static final Supplier<SoundEvent> CS_DITA = registerSoundEvent("cs_dita");
    public static final Supplier<SoundEvent> CS_OPEN = registerSoundEvent("cs_open");
    public static final Supplier<SoundEvent> CS_FINSH = registerSoundEvent("cs_finish");

    // ---- terminal machine UI sounds (2.0.0) ----
    /** Terminal boot screen appears (right-click). */
    public static final Supplier<SoundEvent> TERMINAL_OPEN_UI = registerSoundEvent("terminal_open_ui");
    /** "Open terminal" button clicked on the boot screen. */
    public static final Supplier<SoundEvent> TERMINAL_BUTTON_OPEN = registerSoundEvent("terminal_button_open");
    /** A new offer card pops into the chat. */
    public static final Supplier<SoundEvent> TERMINAL_ITEM_POP = registerSoundEvent("terminal_item_pop");
    /** Accept / reject capsule completes its 700ms hold. */
    public static final Supplier<SoundEvent> TERMINAL_LONG_PRESS = registerSoundEvent("terminal_long_press");
}
