package com.reclizer.csgobox.packet;

import com.reclizer.csgobox.CsgoBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PacketBoxOpenResult(ItemStack item, int grade) implements CustomPacketPayload {

    public static final Type<PacketBoxOpenResult> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "box_open_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketBoxOpenResult> STREAM_CODEC = StreamCodec.of(
            PacketBoxOpenResult::write,
            PacketBoxOpenResult::read
    );

    private static void write(RegistryFriendlyByteBuf buf, PacketBoxOpenResult packet) {
        ItemStack.STREAM_CODEC.encode(buf, packet.item);
        buf.writeInt(packet.grade);
    }

    private static PacketBoxOpenResult read(RegistryFriendlyByteBuf buf) {
        ItemStack item = ItemStack.STREAM_CODEC.decode(buf);
        int grade = buf.readInt();
        return new PacketBoxOpenResult(item, grade);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static ItemStack sPendingItem = ItemStack.EMPTY;
    private static int sPendingGrade = 0;

    public static void handle(final PacketBoxOpenResult message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            sPendingItem = message.item.copy();
            sPendingGrade = message.grade;
        });
    }

    public static ItemStack consumePendingItem() {
        ItemStack result = sPendingItem;
        sPendingItem = ItemStack.EMPTY;
        return result;
    }

    public static int consumePendingGrade() {
        int result = sPendingGrade;
        sPendingGrade = 0;
        return result;
    }
}
