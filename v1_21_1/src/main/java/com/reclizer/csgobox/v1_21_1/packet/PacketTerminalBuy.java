package com.reclizer.csgobox.v1_21_1.packet;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ItemTerminal;
import com.reclizer.csgobox.v1_21_1.item.ModItems;
import com.reclizer.csgobox.v1_21_1.terminal.TerminalRoundData;
import com.reclizer.csgobox.v1_21_1.terminal.TerminalSession;
import com.reclizer.csgobox.v1_21_1.terminal.TerminalSessionManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
 *
 * era: legacy
 */
public record PacketTerminalBuy(
        long requestId,
        ItemStack terminalStack,
        ItemStack offerItem,
        float wearVal,
        int offerRound
) implements CustomPacketPayload {

    public static final Type<PacketTerminalBuy> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "terminal_buy"));

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
        ResourceLocation heldBox = ItemCsgoBox.getBoxId(held);
        ResourceLocation sentBox = ItemCsgoBox.getBoxId(message.terminalStack());
        if (heldBox == null || !heldBox.equals(sentBox)) {
            return invalid;
        }
        // The offer must come from the locked session's current round.
        TerminalSession session = TerminalSessionManager.getByUid(sp, ItemCsgoBox.getTerminalUid(held));
        if (session == null || session.isFinished() || session.model().round() != message.offerRound()) {
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
        // World clock (game ticks × 50): history timestamps must match the
        // client's render clock, otherwise reopened cards stay invisible.
        long worldMs = sp.level().getGameTime() * 50L;
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

    // ---- Armory Point inventory walking (items / armor / offhand slices) ----

    private static int countArmoryPoints(Player entity) {
        return countFromList(entity.getInventory().items)
                + countFromList(entity.getInventory().armor)
                + countFromList(entity.getInventory().offhand);
    }

    private static void consumeArmoryPoints(Player entity, int amount) {
        int remaining = consumeFromList(entity.getInventory().items, amount);
        if (remaining > 0) remaining = consumeFromList(entity.getInventory().armor, remaining);
        if (remaining > 0) consumeFromList(entity.getInventory().offhand, remaining);
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

    private static int consumeFromList(java.util.List<ItemStack> stacks, int amount) {
        for (ItemStack stack : stacks) {
            if (amount <= 0) {
                return 0;
            }
            if (stack.isEmpty() || stack.getItem() != ModItems.ITEM_ARMORY_POINT.get()) {
                continue;
            }
            int take = Math.min(amount, stack.getCount());
            stack.shrink(take);
            amount -= take;
        }
        return amount;
    }
}
