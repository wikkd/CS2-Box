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
}
