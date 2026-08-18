package com.reclizer.csgobox.forge_26_2.capability;

import com.reclizer.csgobox.forge_26_2.CsgoBox;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge capability equivalent of the NeoForge {@code AttachmentType<CsboxPlayerData>}
 * from {@code v26_1_2}. Attached to every {@link Player} and persisted through
 * {@link ICapabilitySerializable} (serialization reuses {@link CsboxPlayerData#CODEC}).
 */
@Mod.EventBusSubscriber(modid = CsgoBox.MODID)
public final class ModCapability {

    private ModCapability() {
    }

    /**
     * Mutable holder around the immutable {@link CsboxPlayerData} record so callers can
     * replace the value (the NeoForge {@code setData} equivalent is
     * {@code holder.set(...)} after {@code player.getCapability(PLAYER_DATA)}).
     */
    public static final class Holder {
        private CsboxPlayerData value = new CsboxPlayerData();

        public CsboxPlayerData get() {
            return value;
        }

        public void set(CsboxPlayerData value) {
            this.value = value;
        }
    }

    public static final Capability<Holder> PLAYER_DATA = CapabilityManager.get(new CapabilityToken<>() {
    });

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent.Entities event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(Identifier.fromNamespaceAndPath(CsgoBox.MODID, "player_data"), new Provider());
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {

        private final Holder holder = new Holder();
        private final LazyOptional<Holder> lazy = LazyOptional.of(() -> holder);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            return cap == PLAYER_DATA ? lazy.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider registryAccess) {
            return (CompoundTag) CsboxPlayerData.CODEC.encodeStart(NbtOps.INSTANCE, holder.value)
                    .result().orElseGet(CompoundTag::new);
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registryAccess, CompoundTag tag) {
            holder.value = CsboxPlayerData.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseGet(CsboxPlayerData::new);
        }
    }
}
