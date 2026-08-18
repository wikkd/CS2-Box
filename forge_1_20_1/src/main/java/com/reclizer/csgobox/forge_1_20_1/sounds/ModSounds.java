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
}
