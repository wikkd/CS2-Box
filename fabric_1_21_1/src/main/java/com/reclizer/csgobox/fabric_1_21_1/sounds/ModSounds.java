package com.reclizer.csgobox.fabric_1_21_1.sounds;

import com.reclizer.csgobox.fabric_1_21_1.CsgoBox;
import com.google.common.base.Suppliers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Supplier;
import net.minecraft.core.Registry;

public final class ModSounds {
    private ModSounds() {
    }

    /** Suppliers.memoize keeps the DeferredRegister call-site shape
     *  ({@code .get()}) while registering exactly once. */
    private static Supplier<SoundEvent> registerSoundEvent(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, name);
        return Suppliers.memoize(() ->
                Registry.register(BuiltInRegistries.SOUND_EVENT, id,
                        SoundEvent.createVariableRangeEvent(id)));
    }

    public static final Supplier<SoundEvent> CS_DITA = registerSoundEvent("cs_dita");
    public static final Supplier<SoundEvent> CS_OPEN = registerSoundEvent("cs_open");
    public static final Supplier<SoundEvent> CS_FINSH = registerSoundEvent("cs_finish");

    // ---- terminal machine UI sounds (2.0.0) ----
    public static final Supplier<SoundEvent> TERMINAL_OPEN_UI = registerSoundEvent("terminal_open_ui");
    public static final Supplier<SoundEvent> TERMINAL_BUTTON_OPEN = registerSoundEvent("terminal_button_open");
    public static final Supplier<SoundEvent> TERMINAL_ITEM_POP = registerSoundEvent("terminal_item_pop");
    public static final Supplier<SoundEvent> TERMINAL_LONG_PRESS = registerSoundEvent("terminal_long_press");

    /** Eager registration during mod init (mirrors the DeferredRegister flush). */
    public static void register() {
        CS_DITA.get();
        CS_OPEN.get();
        CS_FINSH.get();
        TERMINAL_OPEN_UI.get();
        TERMINAL_BUTTON_OPEN.get();
        TERMINAL_ITEM_POP.get();
        TERMINAL_LONG_PRESS.get();
    }
}
