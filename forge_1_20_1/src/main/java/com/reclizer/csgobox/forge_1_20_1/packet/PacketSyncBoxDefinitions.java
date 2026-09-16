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

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(definitions.size());
        for (BoxDefinition definition : definitions) {
            definition.encode(buf);
        }
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
