package com.reclizer.csgobox.v26_1_2.packet;

import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client-to-server request for the server's current box preview data.
 */
public record PacketRequestBoxItems(long requestId) implements CustomPacketPayload {

    public static final Type<PacketRequestBoxItems> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "request_box_items"));

    public static final StreamCodec<FriendlyByteBuf, PacketRequestBoxItems> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeLong(packet.requestId),
            buf -> new PacketRequestBoxItems(buf.readLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final PacketRequestBoxItems message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            var box = player.getMainHandItem();
            if (!(box.getItem() instanceof ItemCsgoBox)) return;

            var itemList = ItemCsgoBox.getItemGroup(box);
            int[] rawWeights = ItemCsgoBox.getRandom(box);
            List<Integer> weights = new ArrayList<>(rawWeights.length);
            for (int w : rawWeights) weights.add(w);

            List<ItemStack> items = new ArrayList<>();
            List<Integer> grades = new ArrayList<>();
            for (var entry : itemList.entrySet()) {
                if (!entry.getKey().isEmpty()) {
                    items.add(entry.getKey().copy());
                    grades.add(entry.getValue());
                }
            }

            ItemStack keyStack = ItemStack.EMPTY;
            Identifier keyRl = ItemCsgoBox.getKey(box);
            if (keyRl != null) {
                Item keyItem = BuiltInRegistries.ITEM.get(keyRl).map(Holder.Reference::value).orElse(null);
                if (keyItem != null) {
                    keyStack = new ItemStack(keyItem);
                }
            }

            context.reply(new PacketSyncBoxItems(
                    message.requestId(),
                    Optional.ofNullable(ItemCsgoBox.getBoxId(box)),
                    items,
                    grades,
                    weights,
                    keyStack
            ));
        });
    }
}
