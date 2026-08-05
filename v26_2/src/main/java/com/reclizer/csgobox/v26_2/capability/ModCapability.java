package com.reclizer.csgobox.v26_2.capability;

import com.reclizer.csgobox.v26_2.CsgoBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
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
                            .serialize(new IAttachmentSerializer<CsboxPlayerData>() {
                                @Override
                                public CsboxPlayerData read(IAttachmentHolder holder, ValueInput input) {
                                    return input.read("data", CsboxPlayerData.CODEC).orElseGet(CsboxPlayerData::new);
                                }

                                @Override
                                public boolean write(CsboxPlayerData data, ValueOutput output) {
                                    output.store("data", CsboxPlayerData.CODEC, data);
                                    return true;
                                }
                            })
                            .build());
}
