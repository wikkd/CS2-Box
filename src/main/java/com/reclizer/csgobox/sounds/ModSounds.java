package com.reclizer.csgobox.sounds;

import com.reclizer.csgobox.CsgoBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModSounds {
    private ModSounds() {
    }

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(net.minecraft.core.registries.Registries.SOUND_EVENT, CsgoBox.MODID);

    private static Supplier<SoundEvent> registerSoundEvent(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, name)));
    }

    public static final Supplier<SoundEvent> CS_DITA = registerSoundEvent("cs_dita");
    public static final Supplier<SoundEvent> CS_OPEN = registerSoundEvent("cs_open");
    public static final Supplier<SoundEvent> CS_FINSH = registerSoundEvent("cs_finish");
}
