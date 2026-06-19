package com.reclizer.csgobox.packet;

import com.reclizer.csgobox.CsgoBox;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

/**
 * Server-to-client box preview data response.
 */
public record PacketSyncBoxItems(
        long requestId,
        Optional<ResourceLocation> boxId,
        List<ItemStack> items,
        List<Integer> grades,
        List<Integer> weights,
        ItemStack keyItem
) implements CustomPacketPayload {

    private static final int MAX_ITEMS = 256;
    private static final int MAX_WEIGHTS = 5;
    private static final int MAX_PENDING_RESPONSES = 8;

    public static final Type<PacketSyncBoxItems> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "sync_box_items"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSyncBoxItems> STREAM_CODEC = StreamCodec.of(
            PacketSyncBoxItems::write,
            PacketSyncBoxItems::read
    );

    public PacketSyncBoxItems {
        boxId = boxId == null ? Optional.empty() : boxId;
        if (items == null) {
            items = List.of();
        }
        if (grades == null) {
            grades = List.of();
        }
        if (weights == null) {
            weights = List.of();
        }
        keyItem = keyItem == null ? ItemStack.EMPTY : keyItem.copy();

        PacketValidation.requireSameSize("items", items, "grades", grades);
        PacketValidation.requireMaxSize("items", items, MAX_ITEMS);
        PacketValidation.requireMaxSize("weights", weights, MAX_WEIGHTS);
        items = PacketValidation.copyStacks(items);
        grades = PacketValidation.copyClampedInts(grades, 1, 5, 1);
        weights = PacketValidation.copyNonNegativeInts(weights);
    }

    private static void write(RegistryFriendlyByteBuf buf, PacketSyncBoxItems packet) {
        buf.writeLong(packet.requestId);
        buf.writeBoolean(packet.boxId.isPresent());
        packet.boxId.ifPresent(id -> ResourceLocation.STREAM_CODEC.encode(buf, id));

        buf.writeVarInt(packet.items.size());
        for (int i = 0; i < packet.items.size(); i++) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.items.get(i));
            buf.writeVarInt(packet.grades.get(i));
        }

        buf.writeVarInt(packet.weights.size());
        for (int weight : packet.weights) {
            buf.writeVarInt(weight);
        }

        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.keyItem);
    }

    private static PacketSyncBoxItems read(RegistryFriendlyByteBuf buf) {
        long requestId = buf.readLong();
        Optional<ResourceLocation> boxId = buf.readBoolean()
                ? Optional.of(ResourceLocation.STREAM_CODEC.decode(buf))
                : Optional.empty();

        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ITEMS) {
            throw new DecoderException("Invalid synced item count: " + size);
        }
        List<ItemStack> items = new ArrayList<>(size);
        List<Integer> grades = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            grades.add(Mth.clamp(buf.readVarInt(), 1, 5));
        }

        int weightLen = buf.readVarInt();
        if (weightLen < 0 || weightLen > MAX_WEIGHTS) {
            throw new DecoderException("Invalid weight count: " + weightLen);
        }
        List<Integer> weights = new ArrayList<>(weightLen);
        for (int i = 0; i < weightLen; i++) {
            weights.add(Math.max(0, buf.readVarInt()));
        }

        ItemStack keyItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        return new PacketSyncBoxItems(requestId, boxId, items, grades, weights, keyItem);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static final Queue<BoxData> sPendingResponses = new ArrayDeque<>();

    public static void handle(final PacketSyncBoxItems message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            PacketValidation.trimQueue(sPendingResponses, MAX_PENDING_RESPONSES);
            sPendingResponses.add(new BoxData(
                    message.requestId,
                    message.boxId,
                    message.items,
                    message.grades,
                    message.weights,
                    message.keyItem
            ));
        });
    }

    public static BoxData consumeMatching(long requestId, Optional<ResourceLocation> expectedBoxId) {
        Optional<ResourceLocation> normalizedBoxId = expectedBoxId == null ? Optional.empty() : expectedBoxId;
        Iterator<BoxData> iterator = sPendingResponses.iterator();
        while (iterator.hasNext()) {
            BoxData data = iterator.next();
            if (data.requestId() == requestId && data.boxId().equals(normalizedBoxId)) {
                iterator.remove();
                return data;
            }
        }
        return null;
    }

    public record BoxData(
            long requestId,
            Optional<ResourceLocation> boxId,
            List<ItemStack> items,
            List<Integer> grades,
            List<Integer> weights,
            ItemStack keyItem
    ) {}
}
