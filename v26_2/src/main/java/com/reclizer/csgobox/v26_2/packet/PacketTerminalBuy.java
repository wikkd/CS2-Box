package com.reclizer.csgobox.v26_2.packet;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.v26_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_2.box.GradeGroup;
import com.reclizer.csgobox.v26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_2.item.ItemTerminal;
import com.reclizer.csgobox.v26_2.item.ModItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server request to buy the current terminal offer after the
 * in-screen confirm dialog is accepted.
 *
 * <p>The server never trusts the client price: the offered item is matched
 * against the terminal's box definition to re-derive its grade and the
 * authoritative Armory Point price, then Armory Points are consumed and the
 * item is granted with the offered wear applied. {@code requestId} only ties
 * the reply to the open screen's dialog.</p>
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
        if (sp.isRemoved() || !sp.isAlive() || PacketCsgoProgress.isOpenBlockedStatic(sp)) {
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
        ItemStack offerItem = message.offerItem();
        if (offerItem == null || offerItem.isEmpty()) {
            return invalid;
        }
        int grade = resolveGrade(offerItem, heldBox);
        if (grade < 1 || grade > 5) {
            return invalid;
        }
        int price = NegotiationModel.priceForGrade(grade);
        boolean creative = sp.getAbilities().instabuild;
        if (!creative && countArmoryPoints(sp) < price) {
            return new PacketTerminalBuyResult(message.requestId(),
                    PacketTerminalBuyResult.RESULT_INSUFFICIENT, ItemStack.EMPTY, grade);
        }

        PacketCsgoProgress.blockFurtherOpensStatic(sp);
        if (!creative) {
            consumeArmoryPoints(sp, price);
        }
        ItemStack toGive = offerItem.copy();
        PacketCsgoProgress.applyWearDamage(toGive, Mth.clamp(message.wearVal(), 0F, 1F));
        toGive.set(ItemCsgoBox.GRADE.get(), grade);
        boolean added = sp.getInventory().add(toGive);
        if (!added && !toGive.isEmpty()) {
            sp.drop(toGive, false);
        }
        return new PacketTerminalBuyResult(message.requestId(),
                PacketTerminalBuyResult.RESULT_SUCCESS, toGive, grade);
    }

    /** Grade (1..5) of the offered item within the terminal's box definition, 0 if absent. */
    private static int resolveGrade(ItemStack item, Identifier boxId) {
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            return 0;
        }
        for (GradeGroup grade : def.grades()) {
            int level = BoxDefinition.gradeLevel(grade.id());
            if (level == 0) {
                continue;
            }
            for (ItemStack candidate : grade.items()) {
                if (ItemStack.isSameItemSameComponents(item, candidate)) {
                    return Mth.clamp(level, 1, 5);
                }
            }
        }
        return 0;
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
