package com.reclizer.csgobox.v1_21_1.packet;

import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.jei.BoxJeiSync;
import com.reclizer.csgobox.v1_21_1.emi.BoxEmiReload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client broadcast of the full box-definition registry plus the
 * quote-cap ladder (报价上限) derived from {@code _prices.json}. Sent on
 * player join, after {@code /csbox reload} and after file hot reloads, so the
 * client registry (and the JEI probability category) stays authoritative and
 * complete even on dedicated-server clients, which never load
 * {@code config/csbox/*.json} themselves.
 *
 * <p>The caps travel with the definitions so the terminal action-bar selector
 * always matches the server's economy; a remote client has no price table of
 * its own and stores the ladder in {@link PriceTableRegistry} (set via
 * {@link PriceTableRegistry#setQuoteCaps}).</p>
 */
public record PacketSyncBoxDefinitions(List<BoxDefinition> definitions, int[] quoteCaps) implements CustomPacketPayload {

    public static final Type<PacketSyncBoxDefinitions> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "sync_box_definitions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSyncBoxDefinitions> STREAM_CODEC =
            StreamCodec.of(PacketSyncBoxDefinitions::write, PacketSyncBoxDefinitions::read);

    public static PacketSyncBoxDefinitions ofAll() {
        return new PacketSyncBoxDefinitions(List.copyOf(BoxRegistry.getAll()),
                PriceTableRegistry.quoteCaps());
    }

    private static void write(RegistryFriendlyByteBuf buf, PacketSyncBoxDefinitions packet) {
        buf.writeVarInt(packet.definitions.size());
        for (BoxDefinition definition : packet.definitions) {
            BoxDefinition.STREAM_CODEC.encode(buf, definition);
        }
        int[] caps = packet.quoteCaps != null ? packet.quoteCaps : new int[0];
        buf.writeVarInt(caps.length);
        for (int cap : caps) {
            buf.writeVarInt(cap);
        }
    }

    private static PacketSyncBoxDefinitions read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<BoxDefinition> definitions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            definitions.add(BoxDefinition.STREAM_CODEC.decode(buf));
        }
        int capCount = buf.readVarInt();
        int[] caps = new int[capCount];
        for (int i = 0; i < capCount; i++) {
            caps[i] = buf.readVarInt();
        }
        return new PacketSyncBoxDefinitions(List.copyOf(definitions), caps);
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
            PriceTableRegistry.setQuoteCaps(message.quoteCaps());
            BoxJeiSync.onBoxRegistryChanged();
            BoxEmiReload.onBoxRegistryChanged();
        });
    }
}