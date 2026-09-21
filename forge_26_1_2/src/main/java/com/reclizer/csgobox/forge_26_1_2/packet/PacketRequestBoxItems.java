package com.reclizer.csgobox.forge_26_1_2.packet;

import com.reclizer.csgobox.forge_26_1_2.CsgoBox;
import com.reclizer.csgobox.forge_26_1_2.item.ItemCsgoBox;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client-to-server request for the server's current box preview data.
 */
public record PacketRequestBoxItems(long requestId) implements CustomPacketPayload {

    public static final Type<PacketRequestBoxItems> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "request_box_items"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRequestBoxItems> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeLong(packet.requestId),
            buf -> new PacketRequestBoxItems(buf.readLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final PacketRequestBoxItems message, final CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) {
                return;
            }
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

            // v2.0.2: pity (保底) hint - how many more opens until the policy
            // forces the target grade; -1 when the box configures no pity.
            int pityRemaining = -1;
            var def = com.reclizer.csgobox.forge_26_1_2.box.BoxRegistry.get(
                    ItemCsgoBox.getBoxId(box));
            if (def != null) {
                var policy = def.pity().orElse(null);
                if (policy != null && policy.every() > 0) {
                    pityRemaining = Math.max(0, policy.every()
                            - com.reclizer.csgobox.logic.PityTracker.missStreak(
                            player.getStringUUID(), ItemCsgoBox.getBoxId(box).toString()));
                }
            }

            Networking.INSTANCE.reply(new PacketSyncBoxItems(
                    message.requestId(),
                    Optional.ofNullable(ItemCsgoBox.getBoxId(box)),
                    items,
                    grades,
                    weights,
                    keyStack,
                    pityRemaining
            ), context);
        });
    }
}
