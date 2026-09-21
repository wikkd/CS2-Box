package com.reclizer.csgobox.forge_26_2.packet;

import com.reclizer.csgobox.forge_26_2.CsgoBox;
import com.reclizer.csgobox.forge_26_2.box.BoxDefinition;
import com.reclizer.csgobox.forge_26_2.box.BoxRegistry;
import com.reclizer.csgobox.box.PriceTableRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client broadcast of the full box-definition registry. Sent on
 * player join, after {@code /csbox reload} and after file hot reloads, so the
 * client registry stays authoritative and complete even on dedicated-server
 * clients, which never load {@code config/csbox/*.json} themselves.
 */
public record PacketSyncBoxDefinitions(List<BoxDefinition> definitions, int[] quoteCaps) implements CustomPacketPayload {

    public static final Type<PacketSyncBoxDefinitions> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "sync_box_definitions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSyncBoxDefinitions> STREAM_CODEC =
            StreamCodec.of(PacketSyncBoxDefinitions::write, PacketSyncBoxDefinitions::read);

    /** A single definition above this is pathological (huge components /
     *  item lists) and is skipped instead of kicking every joining client. */
    private static final int MAX_DEFINITION_BYTES = 128 * 1024;

    /** Total encoded-definitions budget, kept under the 1 MiB clientbound
     *  custom-payload limit (breaching it disconnects the client). */
    private static final int TOTAL_SYNC_BUDGET = 896 * 1024;

    private static void write(RegistryFriendlyByteBuf buf, PacketSyncBoxDefinitions packet) {
        int[] caps = packet.quoteCaps != null ? packet.quoteCaps : new int[0];
        // v2.0.2 capacity guard: a too-large registry would breach the
        // clientbound custom-payload limit and disconnect every client.
        // Encode into a scratch buffer first: oversized single definitions
        // are skipped, and once the total budget is hit the remainder is
        // dropped - both with a loud log naming what was left out.
        RegistryFriendlyByteBuf scratch = new RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), buf.registryAccess());
        int total = 0;
        int sent = 0;
        for (BoxDefinition definition : packet.definitions) {
            int start = scratch.writerIndex();
            BoxDefinition.STREAM_CODEC.encode(scratch, definition);
            int size = scratch.writerIndex() - start;
            if (size > MAX_DEFINITION_BYTES) {
                CsgoBox.LOGGER.error(
                        "[csbox-sync] definition '{}' is {} bytes, above the {} byte per-box cap - NOT synced (trim its item list / components)",
                        definition.id(), size, MAX_DEFINITION_BYTES);
                continue;
            }
            if (total + size > TOTAL_SYNC_BUDGET) {
                CsgoBox.LOGGER.error(
                        "[csbox-sync] box registry too large for one sync packet: sent {} of {} definitions ({} KiB budget) - trim or split the largest boxes",
                        sent, packet.definitions.size(), TOTAL_SYNC_BUDGET / 1024);
                break;
            }
            total += size;
            sent++;
        }
        buf.writeVarInt(sent);
        buf.writeBytes(scratch);
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

    public static PacketSyncBoxDefinitions ofAll() {
        return new PacketSyncBoxDefinitions(List.copyOf(BoxRegistry.getAll()), PriceTableRegistry.quoteCaps());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final PacketSyncBoxDefinitions message, final CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            BoxRegistry.clear();
            for (BoxDefinition definition : message.definitions()) {
                BoxRegistry.register(definition);
            }
            PriceTableRegistry.setQuoteCaps(message.quoteCaps());
        });
    }
}
