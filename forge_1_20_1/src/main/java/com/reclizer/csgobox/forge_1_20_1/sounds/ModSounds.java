package com.reclizer.csgobox.forge_1_20_1.sounds;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private ModSounds() {
    }

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, CsgoBox.MODID);

    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(CsgoBox.MODID, name)));
    }

    public static final RegistryObject<SoundEvent> CS_DITA = registerSoundEvent("cs_dita");
    public static final RegistryObject<SoundEvent> CS_OPEN = registerSoundEvent("cs_open");
    public static final RegistryObject<SoundEvent> CS_FINSH = registerSoundEvent("cs_finish");

    // ---- terminal machine UI sounds (2.0.0) ----
    /** Terminal boot screen appears (right-click). */
    public static final RegistryObject<SoundEvent> TERMINAL_OPEN_UI = registerSoundEvent("terminal_open_ui");
    /** "Open terminal" button clicked on the boot screen. */
    public static final RegistryObject<SoundEvent> TERMINAL_BUTTON_OPEN = registerSoundEvent("terminal_button_open");
    /** A new offer card pops into the chat. */
    public static final RegistryObject<SoundEvent> TERMINAL_ITEM_POP = registerSoundEvent("terminal_item_pop");
    /** Accept / reject capsule completes its 700ms hold. */
    public static final RegistryObject<SoundEvent> TERMINAL_LONG_PRESS = registerSoundEvent("terminal_long_press");
}
