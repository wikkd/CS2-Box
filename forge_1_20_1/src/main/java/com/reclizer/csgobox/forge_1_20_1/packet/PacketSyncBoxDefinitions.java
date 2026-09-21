package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.forge_1_20_1.emi.BoxEmiReload;
import com.reclizer.csgobox.forge_1_20_1.jei.BoxJeiSync;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client broadcast of the full box-definition registry. Sent on
 * player join, after {@code /csbox reload} and after file hot reloads, so the
 * client registry (and the JEI probability category) stays authoritative and
 * complete even on dedicated-server clients, which never load
 * {@code config/csbox/*.json} themselves.
 */
public class PacketSyncBoxDefinitions {

    private static final int MAX_BOXES = 512;

    private final List<BoxDefinition> definitions;
    private final int[] quoteCaps;

    public PacketSyncBoxDefinitions(List<BoxDefinition> definitions, int[] quoteCaps) {
        this.definitions = definitions == null ? List.of() : List.copyOf(definitions);
        this.quoteCaps = quoteCaps != null ? quoteCaps.clone() : new int[0];
    }

    public PacketSyncBoxDefinitions(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_BOXES) {
            throw new DecoderException("Invalid synced box-definition count: " + size);
        }
        List<BoxDefinition> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(BoxDefinition.decode(buf));
        }
        this.definitions = List.copyOf(list);
        int capCount = buf.readVarInt();
        if (capCount < 0 || capCount > 256) {
            throw new DecoderException("Invalid synced quote-cap count: " + capCount);
        }
        int[] caps = new int[capCount];
        for (int i = 0; i < capCount; i++) {
            caps[i] = buf.readVarInt();
        }
        this.quoteCaps = caps;
    }

    /** A single definition above this is pathological (huge NBT / item
     *  lists) and is skipped instead of kicking every joining client. */
    private static final int MAX_DEFINITION_BYTES = 128 * 1024;

    /** Total encoded-definitions budget, kept under the vanilla server →
     *  client packet limit (breaching it disconnects the client). */
    private static final int TOTAL_SYNC_BUDGET = 896 * 1024;

    public void encode(FriendlyByteBuf buf) {
        // v2.0.2 capacity guard: a too-large registry would breach the
        // clientbound packet size limit and disconnect every client.
        // Encode into a scratch buffer first: oversized single definitions
        // are skipped, and once the total budget is hit the remainder is
        // dropped — both with a loud log naming what was left out.
        FriendlyByteBuf scratch = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        int total = 0;
        int sent = 0;
        for (BoxDefinition definition : definitions) {
            int start = scratch.writerIndex();
            definition.encode(scratch);
            int size = scratch.writerIndex() - start;
            if (size > MAX_DEFINITION_BYTES) {
                com.reclizer.csgobox.forge_1_20_1.CsgoBox.LOGGER.error(
                        "[csbox-sync] definition '{}' is {} bytes, above the {} byte per-box cap — NOT synced (trim its item list / NBT)",
                        definition.id(), size, MAX_DEFINITION_BYTES);
                continue;
            }
            if (total + size > TOTAL_SYNC_BUDGET) {
                com.reclizer.csgobox.forge_1_20_1.CsgoBox.LOGGER.error(
                        "[csbox-sync] box registry too large for one sync packet: sent {} of {} definitions ({} KiB budget) — trim or split the largest boxes",
                        sent, definitions.size(), TOTAL_SYNC_BUDGET / 1024);
                break;
            }
            total += size;
            sent++;
        }
        buf.writeVarInt(sent);
        buf.writeBytes(scratch);
        buf.writeVarInt(quoteCaps.length);
        for (int cap : quoteCaps) {
            buf.writeVarInt(cap);
        }
    }

    public static PacketSyncBoxDefinitions ofAll() {
        return new PacketSyncBoxDefinitions(List.copyOf(BoxRegistry.getAll()), PriceTableRegistry.quoteCaps());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            BoxRegistry.clear();
            for (BoxDefinition definition : definitions) {
                BoxRegistry.register(definition);
            }
            PriceTableRegistry.setQuoteCaps(quoteCaps);
            BoxJeiSync.onBoxRegistryChanged();
            BoxEmiReload.onBoxRegistryChanged();
        });
        ctx.get().setPacketHandled(true);
    }
}
