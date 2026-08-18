package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Supplier;

public class PacketSyncBoxItems {

    private static final int MAX_ITEMS = 256;
    private static final int MAX_WEIGHTS = 5;
    private static final int MAX_PENDING_RESPONSES = 8;

    private final long requestId;
    private final Optional<ResourceLocation> boxId;
    private final List<ItemStack> items;
    private final List<Integer> grades;
    private final List<Integer> weights;
    private final ItemStack keyItem;

    public PacketSyncBoxItems(long requestId, Optional<ResourceLocation> boxId,
                              List<ItemStack> items, List<Integer> grades,
                              List<Integer> weights, ItemStack keyItem) {
        this.requestId = requestId;
        this.boxId = boxId == null ? Optional.empty() : boxId;
        this.items = items == null ? List.of() : List.copyOf(PacketValidation.copyStacks(items));
        this.grades = grades == null ? List.of() : List.copyOf(PacketValidation.copyClampedInts(grades, 1, 5, 1));
        this.weights = weights == null ? List.of() : List.copyOf(PacketValidation.copyNonNegativeInts(weights));
        this.keyItem = keyItem == null ? ItemStack.EMPTY : keyItem.copy();
        PacketValidation.requireSameSize("items", this.items, "grades", this.grades);
        PacketValidation.requireMaxSize("items", this.items, MAX_ITEMS);
        PacketValidation.requireMaxSize("weights", this.weights, MAX_WEIGHTS);
    }

    public PacketSyncBoxItems(FriendlyByteBuf buf) {
        this.requestId = buf.readLong();
        this.boxId = buf.readBoolean()
                ? Optional.of(new ResourceLocation(buf.readUtf()))
                : Optional.empty();

        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ITEMS) {
            throw new DecoderException("Invalid synced item count: " + size);
        }
        List<ItemStack> itms = new ArrayList<>(size);
        List<Integer> grds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CompoundTag tag = buf.readNbt();
            itms.add(tag == null ? ItemStack.EMPTY : ItemStack.of(tag));
            grds.add(Mth.clamp(buf.readVarInt(), 1, 5));
        }
        this.items = List.copyOf(itms);
        this.grades = List.copyOf(grds);

        int weightLen = buf.readVarInt();
        if (weightLen < 0 || weightLen > MAX_WEIGHTS) {
            throw new DecoderException("Invalid weight count: " + weightLen);
        }
        List<Integer> w = new ArrayList<>(weightLen);
        for (int i = 0; i < weightLen; i++) {
            w.add(Math.max(0, buf.readVarInt()));
        }
        this.weights = List.copyOf(w);

        CompoundTag kiTag = buf.readNbt();
        this.keyItem = kiTag == null ? ItemStack.EMPTY : ItemStack.of(kiTag);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(requestId);
        buf.writeBoolean(boxId.isPresent());
        boxId.ifPresent(id -> buf.writeUtf(id.toString()));

        buf.writeVarInt(items.size());
        for (int i = 0; i < items.size(); i++) {
            buf.writeNbt(items.get(i).save(new CompoundTag()));
            buf.writeVarInt(grades.get(i));
        }

        buf.writeVarInt(weights.size());
        for (int weight : weights) {
            buf.writeVarInt(weight);
        }

        buf.writeNbt(keyItem.save(new CompoundTag()));
    }

    public long getRequestId() { return requestId; }
    public Optional<ResourceLocation> getBoxId() { return boxId; }
    public List<ItemStack> getItems() { return items; }
    public List<Integer> getGrades() { return grades; }
    public List<Integer> getWeights() { return weights; }
    public ItemStack getKeyItem() { return keyItem; }

    private static final Queue<BoxData> sPendingResponses = new ArrayDeque<>();

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            PacketValidation.trimQueue(sPendingResponses, MAX_PENDING_RESPONSES);
            sPendingResponses.add(new BoxData(requestId, boxId, items, grades, weights, keyItem));
        });
        ctx.get().setPacketHandled(true);
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
