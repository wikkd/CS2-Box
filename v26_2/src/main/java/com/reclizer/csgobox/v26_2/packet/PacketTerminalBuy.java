package com.reclizer.csgobox.v26_2.packet;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.v26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_2.item.ItemTerminal;
import com.reclizer.csgobox.v26_2.item.ModItems;
import com.reclizer.csgobox.v26_2.terminal.TerminalRoundData;
import com.reclizer.csgobox.v26_2.terminal.TerminalSession;
import com.reclizer.csgobox.v26_2.terminal.TerminalSessionManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server request to buy the current terminal offer after the
 * in-screen confirm dialog is accepted.
 *
 * <p>The server never trusts the client price: the buy is matched against the
 * player's locked {@link TerminalSession} — the offer must be the exact item
 * the server sampled for the current round, and its grade derives the
 * authoritative Armory Point price. Armory Points are then consumed and the
 * item granted with the offered wear applied. A successful buy releases the
 * lock ({@code CLOSED}); {@code requestId} only ties the reply to the open
 * screen's dialog.</p>
 */
public record PacketTerminalBuy(
        long requestId,
        ItemStack terminalStack,
        ItemStack offerItem,
        float wearVal,
        int offerRound
) implements CustomPacketPayload {

    public static final Type<PacketTerminalBuy> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "terminal_buy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTerminalBuy> STREAM_CODEC = StreamCodec.of(
            PacketTerminalBuy::write,
            PacketTerminalBuy::read
    );

    private static void write(RegistryFriendlyByteBuf buf, PacketTerminalBuy packet) {
        buf.writeLong(packet.requestId);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.terminalStack);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.offerItem);
        buf.writeFloat(packet.wearVal);
        buf.writeVarInt(packet.offerRound);
    }

    private static PacketTerminalBuy read(RegistryFriendlyByteBuf buf) {
        long requestId = buf.readLong();
        ItemStack terminalStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        ItemStack offerItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        float wearVal = buf.readFloat();
        int offerRound = buf.readVarInt();
        return new PacketTerminalBuy(requestId, terminalStack, offerItem, wearVal, offerRound);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final PacketTerminalBuy message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) {
                return;
            }
            context.reply(executeBuy(sp, message));
        });
    }

    private static PacketTerminalBuyResult executeBuy(ServerPlayer sp, PacketTerminalBuy message) {
        PacketTerminalBuyResult invalid = new PacketTerminalBuyResult(
                message.requestId(), PacketTerminalBuyResult.RESULT_INVALID, ItemStack.EMPTY, 1);
        if (sp.isRemoved() || !sp.isAlive()) {
            return invalid;
        }
        // The player must still be holding the terminal that generated the offer.
        ItemStack held = sp.getMainHandItem();
        if (!(held.getItem() instanceof ItemTerminal)) {
            return invalid;
        }
        Identifier heldBox = ItemCsgoBox.getBoxId(held);
        Identifier sentBox = ItemCsgoBox.getBoxId(message.terminalStack());
        if (heldBox == null || !heldBox.equals(sentBox)) {
            return invalid;
        }
        // The offer must come from the locked session's current round.
        TerminalSession session = TerminalSessionManager.getByUid(sp, ItemCsgoBox.getTerminalUid(held));
        if (session == null || session.isFinished() || session.model().round() != message.offerRound()) {
            return invalid;
        }
        // World clock (game ticks × 50): the countdown and the typing window
        // are server-authoritative. An expired negotiation must not be
        // buyable in the ≤1s window before the 1 Hz tick destroys it, and a
        // crafted buy before the 1100ms typing window elapsed would skip the
        // reveal animation — both are refused here.
        long worldMs = sp.level().getGameTime() * 50L;
        if (worldMs >= session.model().countdownDeadlineMs()
                || worldMs - session.model().roundStartMs() < NegotiationModel.TYPING_MS) {
            return invalid;
        }
        ItemStack offerItem = message.offerItem();
        if (offerItem == null || offerItem.isEmpty()) {
            return invalid;
        }
        TerminalRoundData roundData = session.rounds().get(message.offerRound());
        if (roundData == null || !ItemStack.isSameItemSameComponents(roundData.item(), offerItem)) {
            return invalid;
        }
        int grade = roundData.grade();
        if (grade < 1 || grade > 5) {
            return invalid;
        }
        int price = NegotiationModel.priceForGrade(grade);
        boolean creative = sp.getAbilities().instabuild;
        if (!creative && countArmoryPoints(sp) < price) {
            session.model().dealerReconsider(worldMs);
            session.model().addSystem("csgobox.terminal.sys.poor", worldMs);
            TerminalSessionManager.markDirty();
            return new PacketTerminalBuyResult(message.requestId(),
                    PacketTerminalBuyResult.RESULT_INSUFFICIENT, ItemStack.EMPTY, grade);
        }

        if (!creative) {
            consumeArmoryPoints(sp, price);
        }
        ItemStack toGive = offerItem.copy();
        // The terminal sells ONE item per offer: isSameItemSameComponents
        // never compares count, so a crafted stack must not grant 64 items
        // for the price of one.
        toGive.setCount(1);
        // The wear comes from the server-sampled offer, never the client echo.
        PacketCsgoProgress.applyWearDamage(toGive, roundData.offer().wearVal());
        toGive.set(ItemCsgoBox.GRADE.get(), grade);
        boolean added = sp.getInventory().add(toGive);
        if (!added && !toGive.isEmpty()) {
            sp.drop(toGive, false);
        }
        session.model().buyForced(worldMs);
        // A purchase consumes the terminal machine: the item and its uid are
        // destroyed, and the session lock is released immediately.
        TerminalSessionManager.removeByUid(sp.getStringUUID(), ItemCsgoBox.getTerminalUid(held));
        held.setCount(0);
        return new PacketTerminalBuyResult(message.requestId(),
                PacketTerminalBuyResult.RESULT_SUCCESS, toGive, grade);
    }

    // ---- Armory Point inventory walking (same slot coverage as key consumption) ----

    private static int countArmoryPoints(Player entity) {
        int total = countFromList(entity.getInventory().getNonEquipmentItems());
        total += countFromSlot(entity, EquipmentSlot.HEAD);
        total += countFromSlot(entity, EquipmentSlot.CHEST);
        total += countFromSlot(entity, EquipmentSlot.LEGS);
        total += countFromSlot(entity, EquipmentSlot.FEET);
        total += countFromSlot(entity, EquipmentSlot.OFFHAND);
        return total;
    }

    private static void consumeArmoryPoints(Player entity, int amount) {
        int remaining = consumeFromList(entity.getInventory().getNonEquipmentItems(), amount);
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
