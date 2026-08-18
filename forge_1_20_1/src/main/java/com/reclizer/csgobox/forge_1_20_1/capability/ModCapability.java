package com.reclizer.csgobox.forge_1_20_1.capability;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CsgoBox.MODID)
public final class ModCapability {

    private ModCapability() {
    }

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
    public static void onAttachCapabilities(AttachCapabilitiesEvent event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(new ResourceLocation(CsgoBox.MODID + ":player_data"), new Provider());
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
        public CompoundTag serializeNBT() {
            return (CompoundTag) CsboxPlayerData.CODEC.encodeStart(NbtOps.INSTANCE, holder.value)
                    .result().orElseGet(CompoundTag::new);
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            holder.value = CsboxPlayerData.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseGet(CsboxPlayerData::new);
        }
    }
}
