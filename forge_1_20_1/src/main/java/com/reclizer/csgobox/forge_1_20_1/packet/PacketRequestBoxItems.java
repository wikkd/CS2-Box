package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class PacketRequestBoxItems {

    private final long requestId;

    public PacketRequestBoxItems(long requestId) {
        this.requestId = requestId;
    }

    public PacketRequestBoxItems(FriendlyByteBuf buf) {
        this.requestId = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(requestId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleServer(this, ctx.get()));
        ctx.get().setPacketHandled(true);
    }

    public static void handleServer(final PacketRequestBoxItems message, final NetworkEvent.Context context) {
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
        ResourceLocation keyRl = ItemCsgoBox.getKey(box);
        if (keyRl != null) {
            Item keyItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(keyRl);
            if (keyItem != null) {
                keyStack = new ItemStack(keyItem);
            }
        }

        Networking.sendToPlayer(new PacketSyncBoxItems(
                message.requestId,
                Optional.ofNullable(ItemCsgoBox.getBoxId(box)),
                items,
                grades,
                weights,
                keyStack
        ), (net.minecraft.server.level.ServerPlayer) player);
    }
}
