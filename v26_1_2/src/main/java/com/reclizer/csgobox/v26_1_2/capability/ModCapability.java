package com.reclizer.csgobox.v26_1_2.capability;

import com.reclizer.csgobox.v26_1_2.CsgoBox;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModCapability {
    private ModCapability() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CsgoBox.MODID);

    public static final Supplier<AttachmentType<CsboxPlayerData>> PLAYER_DATA =
            ATTACHMENT_TYPES.register("player_data",
                    () -> AttachmentType.<CsboxPlayerData>builder(CsboxPlayerData::new)
                            .serialize(CsboxPlayerData.CODEC)
                            .build());
}
