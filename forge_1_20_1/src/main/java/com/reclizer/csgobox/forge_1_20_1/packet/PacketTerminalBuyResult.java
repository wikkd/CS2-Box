package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketTerminalBuyResult {

    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_INSUFFICIENT = 1;
    public static final int RESULT_INVALID = 2;

    private final long requestId;
    private final int result;
    private final ItemStack givenItem;
    private final int grade;

    public PacketTerminalBuyResult(long requestId, int result, ItemStack givenItem, int grade) {
        this.requestId = requestId;
        this.result = result;
        this.givenItem = givenItem == null ? ItemStack.EMPTY : givenItem.copy();
        this.grade = grade;
    }

    public PacketTerminalBuyResult(FriendlyByteBuf buf) {
        this.requestId = buf.readLong();
        this.result = buf.readVarInt();
        CompoundTag tag = buf.readNbt();
        this.givenItem = tag == null ? ItemStack.EMPTY : ItemStack.of(tag);
        this.grade = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(requestId);
        buf.writeVarInt(result);
        buf.writeNbt(givenItem.save(new CompoundTag()));
        buf.writeVarInt(grade);
    }

    public long getRequestId() { return requestId; }
    public int getResult() { return result; }
    public ItemStack getGivenItem() { return givenItem; }
    public int getGrade() { return grade; }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.reclizer.csgobox.forge_1_20_1.gui.TerminalScreen ts =
                        com.reclizer.csgobox.forge_1_20_1.gui.TerminalScreen.getOpen();
                if (ts != null) {
                    ts.onBuyResult(requestId, result, givenItem);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
