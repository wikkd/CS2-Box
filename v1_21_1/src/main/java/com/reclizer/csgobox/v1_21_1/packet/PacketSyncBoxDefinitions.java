package com.reclizer.csgobox.v1_21_1.packet;

import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.jei.BoxJeiSync;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server → client broadcast of the full box-definition registry. Sent on
 * player join, after {@code /csbox reload} and after file hot reloads, so the
 * client registry (and the JEI probability category) stays authoritative and
 * complete even on dedicated-server clients, which never load
 * {@code config/csbox/*.json} themselves.
 */
public record PacketSyncBoxDefinitions(List<BoxDefinition> definitions) implements CustomPacketPayload {

    public static final Type<PacketSyncBoxDefinitions> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "sync_box_definitions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSyncBoxDefinitions> STREAM_CODEC =
            StreamCodec.composite(
                    BoxDefinition.STREAM_CODEC.apply(ByteBufCodecs.list()), PacketSyncBoxDefinitions::definitions,
                    PacketSyncBoxDefinitions::new
            );

    public static PacketSyncBoxDefinitions ofAll() {
        return new PacketSyncBoxDefinitions(List.copyOf(BoxRegistry.getAll()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final PacketSyncBoxDefinitions message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            BoxRegistry.clear();
            for (BoxDefinition definition : message.definitions()) {
                BoxRegistry.register(definition);
            }
            BoxJeiSync.onBoxRegistryChanged();
        });
    }
}
