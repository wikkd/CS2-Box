package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.function.Supplier;

public class PacketBoxBulkResult {

    // Must hold the largest legal batch without trimming: /csbox give grants
    // at most 6400 boxes per call (200 chunks at 32 entries each), and
    // bulkOpenCount is unlimited by default, so no smaller server-side bound
    // can be assumed. The old 64-packet cap silently dropped the oldest ~4.4k
    // results of a full batch off the consolidated popup.
    private static final int MAX_PENDING_BULK = 256;
    /** Number of entries the server puts into one bulk payload. */
    public static final int BULK_PER_PACKET = 32;

    private final long requestId;
    private final List<ItemStack> items;
    private final List<Integer> grades;

    public PacketBoxBulkResult(long requestId, List<ItemStack> items, List<Integer> grades) {
        this.requestId = requestId;
        this.items = items == null ? List.of() : List.copyOf(PacketValidation.copyStacks(items));
        this.grades = grades == null ? List.of() : List.copyOf(PacketValidation.copyClampedInts(grades, 1, 5, 1));
        PacketValidation.requireSameSize("bulk items", this.items, "grades", this.grades);
        PacketValidation.requireMaxSize("bulk items", this.items, BULK_PER_PACKET);
    }

    public PacketBoxBulkResult(FriendlyByteBuf buf) {
        this.requestId = buf.readLong();
        int size = buf.readVarInt();
        if (size < 0 || size > BULK_PER_PACKET) {
            throw new DecoderException("Invalid bulk result size: " + size);
        }
        List<ItemStack> itms = new ArrayList<>(size);
        List<Integer> grds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CompoundTag tag = buf.readNbt();
            itms.add(tag == null ? ItemStack.EMPTY : ItemStack.of(tag));
            grds.add(buf.readVarInt());
        }
        this.items = List.copyOf(itms);
        this.grades = List.copyOf(grds);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(requestId);
        buf.writeVarInt(items.size());
        for (int i = 0; i < items.size(); i++) {
            buf.writeNbt(items.get(i).save(new CompoundTag()));
            buf.writeVarInt(grades.get(i));
        }
    }

    public long getRequestId() { return requestId; }
    public List<ItemStack> getItems() { return items; }
    public List<Integer> getGrades() { return grades; }

    private static final Queue<PacketBoxBulkResult> sPendingBulkResults = new ArrayDeque<>();

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            PacketValidation.trimQueue(sPendingBulkResults, MAX_PENDING_BULK);
            sPendingBulkResults.add(this);
        });
        ctx.get().setPacketHandled(true);
    }

    public static PacketBoxBulkResult consumeMatching(long requestId) {
        Iterator<PacketBoxBulkResult> iterator = sPendingBulkResults.iterator();
        while (iterator.hasNext()) {
            PacketBoxBulkResult result = iterator.next();
            if (result.requestId == requestId) {
                iterator.remove();
                return result;
            }
        }
        return null;
    }
}
