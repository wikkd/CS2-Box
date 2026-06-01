package com.reclizer.csgobox.packet;

import com.reclizer.csgobox.config.CsboxConfigGuiProvider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public record PacketOpenBoxEditor() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PacketOpenBoxEditor> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("csgobox", "open_box_editor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketOpenBoxEditor> CODEC =
            StreamCodec.unit(new PacketOpenBoxEditor());

    @Override
    public CustomPacketPayload.Type<PacketOpenBoxEditor> type() {
        return TYPE;
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(TYPE, CODEC, PacketOpenBoxEditor::handle);
    }

    public static void handle(PacketOpenBoxEditor packet, IPayloadContext ctx) {
        ctx.enqueueWork(CsboxConfigGuiProvider::openScreen);
    }
}