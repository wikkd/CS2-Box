package com.reclizer.csgobox.packet;

import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.item.ItemCsgoBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client-to-server request for the server's current box preview data.
 */
public record PacketRequestBoxItems(long requestId) implements CustomPacketPayload {

    public static final Type<PacketRequestBoxItems> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "request_box_items"));

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
            ResourceLocation keyRl = ItemCsgoBox.getKey(box);
            if (keyRl != null) {
                Item keyItem = BuiltInRegistries.ITEM.get(keyRl);
                if (keyItem != null) {
                    keyStack = new ItemStack(keyItem);
                }
            }

            if (player instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, new PacketSyncBoxItems(
                        message.requestId(),
                        Optional.ofNullable(ItemCsgoBox.getBoxId(box)),
                        items,
                        grades,
                        weights,
                        keyStack
                ));
            }
        });
    }
}
