package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.terminal.WearPenalty;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.event.TerminalBuyEvent;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ItemTerminal;
import com.reclizer.csgobox.forge_1_20_1.item.ModItems;
import com.reclizer.csgobox.forge_1_20_1.terminal.TerminalRoundData;
import com.reclizer.csgobox.forge_1_20_1.terminal.TerminalSession;
import com.reclizer.csgobox.forge_1_20_1.terminal.TerminalSessionManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketTerminalBuy {

    private final long requestId;
    private final ItemStack terminalStack;
    private final ItemStack offerItem;
    private final float wearVal;
    private final int offerRound;

    public PacketTerminalBuy(long requestId, ItemStack terminalStack, ItemStack offerItem,
                             float wearVal, int offerRound) {
        this.requestId = requestId;
        this.terminalStack = terminalStack == null ? ItemStack.EMPTY : terminalStack.copy();
        this.offerItem = offerItem == null ? ItemStack.EMPTY : offerItem.copy();
        this.wearVal = wearVal;
        this.offerRound = offerRound;
    }

    public PacketTerminalBuy(FriendlyByteBuf buf) {
        this.requestId = buf.readLong();
        CompoundTag t1 = buf.readNbt();
        this.terminalStack = t1 == null ? ItemStack.EMPTY : ItemStack.of(t1);
        CompoundTag t2 = buf.readNbt();
        this.offerItem = t2 == null ? ItemStack.EMPTY : ItemStack.of(t2);
        this.wearVal = buf.readFloat();
        this.offerRound = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(requestId);
        buf.writeNbt(terminalStack.save(new CompoundTag()));
        buf.writeNbt(offerItem.save(new CompoundTag()));
        buf.writeFloat(wearVal);
        buf.writeVarInt(offerRound);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleServer(this, ctx.get()));
        ctx.get().setPacketHandled(true);
    }

    public long getRequestId() { return requestId; }
    public ItemStack getTerminalStack() { return terminalStack; }
    public ItemStack getOfferItem() { return offerItem; }
    public float getWearVal() { return wearVal; }
    public int getOfferRound() { return offerRound; }

    public static void handleServer(final PacketTerminalBuy message, final NetworkEvent.Context context) {
        ServerPlayer sp = context.getSender();
        if (sp == null) {
            return;
        }
        Networking.sendToPlayer(executeBuy(sp, message), sp);
    }

    private static PacketTerminalBuyResult executeBuy(ServerPlayer sp, PacketTerminalBuy message) {
        PacketTerminalBuyResult invalid = new PacketTerminalBuyResult(
                message.requestId, PacketTerminalBuyResult.RESULT_INVALID, ItemStack.EMPTY, 1);
        if (sp.isRemoved() || !sp.isAlive()) {
            return invalid;
        }
        ItemStack held = sp.getMainHandItem();
        if (!(held.getItem() instanceof ItemTerminal)) {
            return invalid;
        }
        ResourceLocation heldBox = ItemCsgoBox.getBoxId(held);
        ResourceLocation sentBox = ItemCsgoBox.getBoxId(message.terminalStack);
        if (heldBox == null || !heldBox.equals(sentBox)) {
            return invalid;
        }
        if (!TerminalSessionManager.isOpenBinding(sp.getStringUUID(), ItemCsgoBox.getTerminalUid(held))) {
            return invalid;
        }
        TerminalSession session = TerminalSessionManager.getByUid(sp, ItemCsgoBox.getTerminalUid(held));
        if (session == null || session.isFinished() || session.model().round() != message.offerRound) {
            return invalid;
        }
        long worldMs = sp.level().getGameTime() * 50L;
        if (worldMs >= session.model().countdownDeadlineMs()
                || worldMs - session.model().roundStartMs() < NegotiationModel.TYPING_MS) {
            return invalid;
        }
        ItemStack offerItem = message.offerItem;
        if (offerItem == null || offerItem.isEmpty()) {
            return invalid;
        }
        TerminalRoundData roundData = session.rounds().get(message.offerRound);
        if (roundData == null || !ItemStack.isSameItemSameTags(roundData.item(), offerItem)) {
            return invalid;
        }
        int grade = roundData.grade();
        if (grade < 1 || grade > 5) {
            return invalid;
        }
        int basePrice = NegotiationModel.priceForGrade(grade);
        int price = basePrice;
        if (!roundData.item().isDamageableItem()) {
            price += WearPenalty.surcharge(roundData.offer().wearVal());
        }
        boolean creative = sp.getAbilities().instabuild;
        if (!creative && countArmoryPoints(sp) < price) {
            session.model().dealerReconsider(worldMs);
            session.model().addSystem("csgobox.terminal.sys.poor", worldMs);
            TerminalSessionManager.markDirty();
            return new PacketTerminalBuyResult(message.requestId,
                    PacketTerminalBuyResult.RESULT_INSUFFICIENT, ItemStack.EMPTY, grade);
        }

        if (!creative) {
            consumeArmoryPoints(sp, price);
        }
        ItemStack toGive = offerItem.copy();
        toGive.setCount(1);
        PacketCsgoProgress.applyWearDamage(toGive, roundData.offer().wearVal());
        ItemCsgoBox.setGrade(toGive, grade);
        boolean added = sp.getInventory().add(toGive);
        if (!added && !toGive.isEmpty()) {
            sp.drop(toGive, false);
        }
        session.model().buyForced(worldMs);
        TerminalSessionManager.removeByUid(sp.getStringUUID(), ItemCsgoBox.getTerminalUid(held));
        TerminalSessionManager.clearOpenIf(sp.getStringUUID(), ItemCsgoBox.getTerminalUid(held));
        held.setCount(0);
        TerminalBuyEvent.BUS.post(new TerminalBuyEvent(sp, grade, price, roundData.offer().wearVal(), toGive, message.offerRound));
        return new PacketTerminalBuyResult(message.requestId,
                PacketTerminalBuyResult.RESULT_SUCCESS, toGive, grade);
    }

    private static int countArmoryPoints(Player entity) {
        int total = countFromList(entity.getInventory().items);
        total += countFromSlot(entity, EquipmentSlot.HEAD);
        total += countFromSlot(entity, EquipmentSlot.CHEST);
        total += countFromSlot(entity, EquipmentSlot.LEGS);
        total += countFromSlot(entity, EquipmentSlot.FEET);
        total += countFromSlot(entity, EquipmentSlot.OFFHAND);
        return total;
    }

    private static void consumeArmoryPoints(Player entity, int amount) {
        int remaining = consumeFromList(entity.getInventory().items, amount);
        if (remaining > 0) remaining = consumeFromSlot(entity, EquipmentSlot.HEAD, remaining);
        if (remaining > 0) remaining = consumeFromSlot(entity, EquipmentSlot.CHEST, remaining);
        if (remaining > 0) remaining = consumeFromSlot(entity, EquipmentSlot.LEGS, remaining);
        if (remaining > 0) remaining = consumeFromSlot(entity, EquipmentSlot.FEET, remaining);
        if (remaining > 0) consumeFromSlot(entity, EquipmentSlot.OFFHAND, remaining);
    }

    private static int countFromList(java.util.List<ItemStack> stacks) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && stack.getItem() == ModItems.ITEM_ARMORY_POINT.get()) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int countFromSlot(Player entity, EquipmentSlot slot) {
        ItemStack stack = entity.getItemBySlot(slot);
        return !stack.isEmpty() && stack.getItem() == ModItems.ITEM_ARMORY_POINT.get()
                ? stack.getCount() : 0;
    }

    private static int consumeFromList(java.util.List<ItemStack> stacks, int amount) {
        for (int i = 0; i < stacks.size() && amount > 0; i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty() || stack.getItem() != ModItems.ITEM_ARMORY_POINT.get()) {
                continue;
            }
            int take = Math.min(amount, stack.getCount());
            stack.shrink(take);
            amount -= take;
        }
        return amount;
    }

    private static int consumeFromSlot(Player entity, EquipmentSlot slot, int amount) {
        if (amount <= 0) {
            return 0;
        }
        ItemStack stack = entity.getItemBySlot(slot);
        if (stack.isEmpty() || stack.getItem() != ModItems.ITEM_ARMORY_POINT.get()) {
            return amount;
        }
        int take = Math.min(amount, stack.getCount());
        stack.shrink(take);
        return amount - take;
    }
}
