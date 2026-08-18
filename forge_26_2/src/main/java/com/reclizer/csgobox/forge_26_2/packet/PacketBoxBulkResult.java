package com.reclizer.csgobox.forge_26_2.packet;

import com.reclizer.csgobox.forge_26_2.CsgoBox;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

/**
 * Server-to-client consolidated bulk result. Carries the per-box reward for
 * boxes 2..K of a bulk open (box 1's result travels in {@link PacketBoxOpenResult}
 * because it carries the full animation strip).
 *
 * <p>Large batches are sent as several packets (chunks) of up to
 * {@value #BULK_PER_PACKET} entries so a single payload cannot grow
 * unbounded with heavy-NBT items; the client aggregates chunks sharing the
 * same request id.</p>
 */
public record PacketBoxBulkResult(
        long requestId,
        List<ItemStack> items,
        List<Integer> grades
) implements CustomPacketPayload {

    private static final int MAX_BULK_RESULTS = 1024;
    private static final int MAX_PENDING_BULK = 64;
    /** Number of entries the server puts into one bulk payload. */
    public static final int BULK_PER_PACKET = 32;

    public static final Type<PacketBoxBulkResult> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "box_bulk_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketBoxBulkResult> STREAM_CODEC = StreamCodec.of(
            PacketBoxBulkResult::write,
            PacketBoxBulkResult::read
    );

    public PacketBoxBulkResult {
        if (items == null) {
            items = List.of();
        }
        if (grades == null) {
            grades = List.of();
        }
        PacketValidation.requireSameSize("items", items, "grades", grades);
        PacketValidation.requireMaxSize("items", items, MAX_BULK_RESULTS);
        items = PacketValidation.copyStacks(items);
        grades = PacketValidation.copyClampedInts(grades, 1, 5, 1);
    }

    private static void write(RegistryFriendlyByteBuf buf, PacketBoxBulkResult packet) {
        buf.writeLong(packet.requestId);
        buf.writeVarInt(packet.items.size());
        for (int i = 0; i < packet.items.size(); i++) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.items.get(i));
            buf.writeVarInt(packet.grades.get(i));
        }
    }

    private static PacketBoxBulkResult read(RegistryFriendlyByteBuf buf) {
        long requestId = buf.readLong();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_BULK_RESULTS) {
            throw new DecoderException("Invalid bulk result size: " + size);
        }
        List<ItemStack> items = new ArrayList<>(size);
        List<Integer> grades = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            grades.add(buf.readVarInt());
        }
        return new PacketBoxBulkResult(requestId, items, grades);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static final Queue<PacketBoxBulkResult> sPendingBulkResults = new ArrayDeque<>();

    public static void handle(final PacketBoxBulkResult message, final CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            PacketValidation.trimQueue(sPendingBulkResults, MAX_PENDING_BULK);
            sPendingBulkResults.add(message);
        });
    }

    public static PacketBoxBulkResult consumeMatching(long requestId) {
        Iterator<PacketBoxBulkResult> iterator = sPendingBulkResults.iterator();
        while (iterator.hasNext()) {
            PacketBoxBulkResult result = iterator.next();
            if (result.requestId() == requestId) {
                iterator.remove();
                return result;
            }
        }
        return null;
    }
}
