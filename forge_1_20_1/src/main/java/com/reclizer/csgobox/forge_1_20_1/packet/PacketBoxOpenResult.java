package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.logic.AnimationStrip;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.function.Supplier;

public class PacketBoxOpenResult {

    public static final int ANIMATION_ITEM_COUNT = AnimationStrip.ITEM_COUNT;
    public static final int MIN_WINNING_INDEX = AnimationStrip.MIN_WINNING_INDEX;
    public static final int MAX_WINNING_INDEX = AnimationStrip.MAX_WINNING_INDEX;
    private static final int MAX_PENDING_RESULTS = 8;

    private final ItemStack item;
    private final int grade;
    private final int winningIndex;
    private final long serverSeed;
    private final long requestId;
    private final List<ItemStack> animationItems;
    private final List<Integer> animationGrades;

    public PacketBoxOpenResult(ItemStack item, int grade, int winningIndex, long serverSeed,
                               long requestId, List<ItemStack> animationItems, List<Integer> animationGrades) {
        this.item = item == null ? ItemStack.EMPTY : item.copy();
        this.grade = Mth.clamp(grade, 1, 5);
        this.serverSeed = serverSeed;
        this.requestId = requestId;
        this.animationItems = animationItems == null ? List.of() : List.copyOf(PacketValidation.copyStacks(animationItems));
        this.animationGrades = animationGrades == null ? List.of() : List.copyOf(PacketValidation.copyClampedInts(animationGrades, 1, 5, 1));
        PacketValidation.requireSameSize("animationItems", this.animationItems, "animationGrades", this.animationGrades);
        PacketValidation.requireMaxSize("animationItems", this.animationItems, ANIMATION_ITEM_COUNT);
        this.winningIndex = this.animationItems.isEmpty() ? 0 : Mth.clamp(winningIndex, 0, this.animationItems.size() - 1);
    }

    public PacketBoxOpenResult(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        this.item = tag == null ? ItemStack.EMPTY : ItemStack.of(tag);
        this.grade = Mth.clamp(buf.readVarInt(), 1, 5);
        this.winningIndex = Mth.clamp(buf.readVarInt(), 0, ANIMATION_ITEM_COUNT - 1);
        this.serverSeed = buf.readLong();
        this.requestId = buf.readLong();
        int animationSize = buf.readVarInt();
        if (animationSize < 0 || animationSize > ANIMATION_ITEM_COUNT) {
            throw new DecoderException("Invalid animation item count: " + animationSize);
        }
        List<ItemStack> ai = new ArrayList<>(animationSize);
        List<Integer> ag = new ArrayList<>(animationSize);
        for (int i = 0; i < animationSize; i++) {
            CompoundTag aTag = buf.readNbt();
            ai.add(aTag == null ? ItemStack.EMPTY : ItemStack.of(aTag));
            ag.add(Mth.clamp(buf.readVarInt(), 1, 5));
        }
        this.animationItems = List.copyOf(ai);
        this.animationGrades = List.copyOf(ag);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(item.save(new CompoundTag()));
        buf.writeVarInt(grade);
        buf.writeVarInt(winningIndex);
        buf.writeLong(serverSeed);
        buf.writeLong(requestId);
        buf.writeVarInt(animationItems.size());
        for (int i = 0; i < animationItems.size(); i++) {
            buf.writeNbt(animationItems.get(i).save(new CompoundTag()));
            buf.writeVarInt(animationGrades.get(i));
        }
    }

    public ItemStack getItem() { return item; }
    public int getGrade() { return grade; }
    public int getWinningIndex() { return winningIndex; }
    public long getServerSeed() { return serverSeed; }
    public long getRequestId() { return requestId; }
    public List<ItemStack> getAnimationItems() { return animationItems; }
    public List<Integer> getAnimationGrades() { return animationGrades; }

    private static final Queue<PacketBoxOpenResult> sPendingResults = new ArrayDeque<>();

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            PacketValidation.trimQueue(sPendingResults, MAX_PENDING_RESULTS);
            sPendingResults.add(this);
        });
        ctx.get().setPacketHandled(true);
    }

    public static PacketBoxOpenResult consumeMatching(long requestId) {
        Iterator<PacketBoxOpenResult> iterator = sPendingResults.iterator();
        while (iterator.hasNext()) {
            PacketBoxOpenResult result = iterator.next();
            if (result.requestId == requestId) {
                iterator.remove();
                return result;
            }
        }
        return null;
    }
}
