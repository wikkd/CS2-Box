package com.reclizer.csgobox.packet;

import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.capability.CsboxPlayerData;
import com.reclizer.csgobox.capability.ModCapability;
import com.reclizer.csgobox.item.ItemCsgoBox;
import com.reclizer.csgobox.utils.RandomItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record PacketGiveItem(long seed, int grade) implements CustomPacketPayload {

    public static final Type<PacketGiveItem> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "give_item"));

    public static final StreamCodec<FriendlyByteBuf, PacketGiveItem> STREAM_CODEC = StreamCodec.of(
            PacketGiveItem::write,
            PacketGiveItem::read
    );

    private static void write(FriendlyByteBuf buf, PacketGiveItem packet) {
        buf.writeLong(packet.seed);
        buf.writeInt(packet.grade);
    }

    private static PacketGiveItem read(FriendlyByteBuf buf) {
        return new PacketGiveItem(buf.readLong(), buf.readInt());
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
            int finalGrade = 0;
            int maxIter = message.grade > 0 ? Math.min(message.grade + 1, 50) : 46;

            for (int i = 0; i < maxIter; i++) {
                int grade = RandomItem.randomItemsGrade(rng, ItemCsgoBox.getRandom(box));
                ItemStack itemStack = RandomItem.randomItems(rng, grade, itemList);
                if (i == maxIter - 1) {
                    giveItem = itemStack;
                    finalGrade = grade;
                }
            }

            if (!giveItem.isEmpty()) {
                player.setData(ModCapability.PLAYER_DATA,
                        new CsboxPlayerData(message.seed(), 0, giveItem.copy(), finalGrade));
                if (player instanceof ServerPlayer sp) {
                    PacketDistributor.sendToPlayer(sp, new PacketBoxOpenResult(giveItem.copy(), finalGrade));
                }
                player.getInventory().add(giveItem.copy());
                box.shrink(1);
            }
        });
    }

    private static boolean tryConsumeKeys(Player entity, ItemStack box) {
        ResourceLocation keyId = ItemCsgoBox.getKey(box);
        return Optional.ofNullable(keyId).filter(key -> !key.equals(ResourceLocation.parse("minecraft:air"))).map(key -> {
            var keyItem = entity.getInventory().items.stream()
                    .filter(stack -> key.equals(Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(stack.getItem()))))
                    .findAny();
            if (keyItem.isPresent()) {
                keyItem.get().shrink(1);
                return true;
            } else {
                return false;
            }
        }).orElse(true);
    }

    public record GiveRequest(long seed, int grade) {}
}
