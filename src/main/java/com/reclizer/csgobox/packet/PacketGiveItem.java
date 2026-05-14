package com.reclizer.csgobox.packet;

import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.item.ItemCsgoBox;
import com.reclizer.csgobox.utils.RandomItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record PacketGiveItem(long seed) implements CustomPacketPayload {

    public static final Type<PacketGiveItem> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "give_item"));

    public static final StreamCodec<FriendlyByteBuf, PacketGiveItem> STREAM_CODEC = StreamCodec.of(
            PacketGiveItem::write,
            PacketGiveItem::read
    );

    private static void write(FriendlyByteBuf buf, PacketGiveItem packet) {
        buf.writeLong(packet.seed);
    }

    private static PacketGiveItem read(FriendlyByteBuf buf) {
        return new PacketGiveItem(buf.readLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final PacketGiveItem message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            var box = player.getMainHandItem();
            if (!(box.getItem() instanceof ItemCsgoBox)) {
                return;
            }

            if (!tryConsumeKeys(player, box)) {
                return;
            }

            var itemList = ItemCsgoBox.getItemGroup(box);
            var rng = new Random(message.seed);

            ItemStack giveItem = ItemStack.EMPTY;
            for (int i = 0; i < 46; i++) {
                int grade = RandomItem.randomItemsGrade(rng, ItemCsgoBox.getRandom(box), player);
                ItemStack itemStack = RandomItem.randomItems(rng, grade, itemList);
                if (i == 45) {
                    giveItem = itemStack;
                }
            }

            if (!giveItem.isEmpty()) {
                var inventory = player.getInventory();
                int emptySlot = -1;
                for (int i = 0; i < 36; i++) {
                    if (inventory.getItem(i).isEmpty()) {
                        emptySlot = i;
                        break;
                    }
                }
                if (emptySlot != -1) {
                    player.getInventory().add(giveItem);
                } else {
                    player.drop(giveItem, true);
                }
            }

            box.shrink(1);
        });
    }

    private static boolean tryConsumeKeys(Player entity, ItemStack box) {
        return Optional.ofNullable(ItemCsgoBox.getKey(box)).filter(key -> !key.isEmpty()).map(key -> {
            var keyItem = entity.getInventory().items.stream()
                    .filter(stack -> key.equals(Objects.requireNonNull(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())).toString()))
                    .findAny();
            if (keyItem.isPresent()) {
                keyItem.get().shrink(1);
                return true;
            } else {
                return false;
            }
        }).orElse(true);
    }
}