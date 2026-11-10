package com.reclizer.csgobox.forge_1_18_2.packet;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.box.BoxStripGenerator;
import com.reclizer.csgobox.forge_1_18_2.CsgoBox;
import com.reclizer.csgobox.forge_1_18_2.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.forge_1_18_2.event.BoxOpeningEvent;
import com.reclizer.csgobox.forge_1_18_2.event.BoxOpenedEvent;
import com.reclizer.csgobox.forge_1_18_2.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_18_2.box.BoxItemResolver;
import com.reclizer.csgobox.forge_1_18_2.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_18_2.box.GradeGroup;
import com.reclizer.csgobox.logic.BoxConstraintTracker;
import com.reclizer.csgobox.logic.GradeMapCache;
import com.reclizer.csgobox.logic.OddsCalculator;
import com.reclizer.csgobox.logic.OpenBlockGuard;
import com.reclizer.csgobox.logic.PityPolicy;
import com.reclizer.csgobox.logic.PityTracker;
import com.reclizer.csgobox.forge_1_18_2.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_18_2.item.ItemTerminal;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class PacketCsgoProgress {


    private final long requestId;

    public PacketCsgoProgress(long requestId) {
        this.requestId = requestId;
    }

    public PacketCsgoProgress(FriendlyByteBuf buf) {
        this.requestId = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(requestId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleServer(this, ctx.get()));
        ctx.get().setPacketHandled(true);
    }

    public long getRequestId() {
        return requestId;
    }

    public static void handleServer(final PacketCsgoProgress message, final NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        var box = player.getMainHandItem();
        if (!(box.getItem() instanceof ItemCsgoBox)) {
            return;
        }
        if (box.getItem() instanceof ItemTerminal) {
            sendRejectedToPlayer(message.requestId, player);
            return;
        }

        if (player.isRemoved() || !player.isAlive()) {
            sendRejectedToPlayer(message.requestId, player);
            return;
        }

        // v2.2.0: the whole headless pipeline (guards, BoxOpeningEvent,
        // constraints, pity, roll, keys, grant, records) moved to
        // BoxOpenExecutor, shared with the Create deployer compat so a
        // mechanical deployer holding the key opens crates with identical
        // rules. Note the prize is now granted slightly before the
        // animation packet is sent (same tick, no visible change).
        BoxOpenExecutor.Outcome outcome = BoxOpenExecutor.execute(player, box, true, true);
        if (outcome == null) {
            sendRejectedToPlayer(message.requestId, player);
            return;
        }

        Networking.INSTANCE.reply(new PacketBoxOpenResult(
                outcome.animItem().copy(),
                outcome.grade(),
                outcome.winningIndex(),
                message.requestId,
                outcome.animationItems(),
                outcome.animationGrades()
        ), context);
    }

    static void sendRejected(long requestId) {
        // No-op fallback; prefer sendRejectedToPlayer with an explicit target.
    }

    static void sendRejectedToPlayer(long requestId, ServerPlayer player) {
        Networking.sendToPlayer(new PacketBoxOpenResult(
                ItemStack.EMPTY, 1, 0, requestId, List.of(), List.of()), player);
    }

    public static void applyWearDamage(ItemStack stack, float wear) {
        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) {
            return;
        }
        int damage = Math.max(0, Math.min(Math.round(wear * maxDamage), maxDamage - 1));
        stack.setDamageValue(damage);
    }

    static int resolveGrade(ItemStack item, ResourceLocation boxId, int fallback) {
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def != null) {
            List<GradeGroup> tiers = def.grades();
            for (int i = 0; i < tiers.size(); i++) {
                for (ItemStack candidate : tiers.get(i).items()) {
                    if (ItemStack.isSameItemSameTags(item, candidate)) {
                        return Mth.clamp(BoxGrades.levelAt(tiers.size(), i), 1, Math.max(1, tiers.size()));
                    }
                }
            }
        }
        return Mth.clamp(fallback, 1, com.reclizer.csgobox.box.BoxGrades.MAX_GRADES);
    }

    public static boolean tryConsumeKeys(Player entity, ItemStack box, int count) {
        java.util.List<String> acceptedKeys = ItemCsgoBox.getAcceptedKeys(box);
        if (acceptedKeys.isEmpty()) {
            return true;
        }
        for (String acceptedKey : acceptedKeys) {
            if (tryConsumeKeys(entity, new ResourceLocation(acceptedKey), count)) {
                return true;
            }
        }
        return false;
    }

    /** Consume up to {@code count} keys of one id; true only when fully consumed (or no key required). */
    public static boolean tryConsumeKeys(Player entity, ResourceLocation keyId, int count) {
        if (keyId == null || keyId.equals(new ResourceLocation("minecraft", "air"))) {
            return true;
        }
        if (count <= 0) {
            return true;
        }
        if (entity.getAbilities().instabuild) {
            return true;
        }
        int remaining = count;
        remaining = consumeFromList(entity.getInventory().items, keyId, null, remaining);
        if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.HEAD, keyId, remaining);
        if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.CHEST, keyId, remaining);
        if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.LEGS, keyId, remaining);
        if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.FEET, keyId, remaining);
        if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.OFFHAND, keyId, remaining);
        return remaining == 0;
    }

    public static boolean tryConsumeBoxes(Player entity, ItemStack box, int count) {
        if (count <= 0) {
            return true;
        }
        if (entity.getAbilities().instabuild) {
            return true;
        }
        int remaining = count;
        remaining = consumeFromList(entity.getInventory().items, null, box, remaining);
        if (remaining > 0) remaining = consumeBoxFromSlot(entity, EquipmentSlot.HEAD, box, remaining);
        if (remaining > 0) remaining = consumeBoxFromSlot(entity, EquipmentSlot.CHEST, box, remaining);
        if (remaining > 0) remaining = consumeBoxFromSlot(entity, EquipmentSlot.LEGS, box, remaining);
        if (remaining > 0) remaining = consumeBoxFromSlot(entity, EquipmentSlot.FEET, box, remaining);
        if (remaining > 0) remaining = consumeBoxFromSlot(entity, EquipmentSlot.OFFHAND, box, remaining);
        return remaining == 0;
    }

    /**
     * Count-first, all-or-nothing key consumption across the box's full
     * accepted-keys list (mirrors {@code BoxOpenExecutor.countAvailability}'s
     * union count). The any-of/single-id overloads consumed as they scanned,
     * so a partial match (e.g. 1 primary + 3 extra keys against a 4-box batch)
     * destroyed the keys it found and THEN failed — the bulk finalizer
     * refunded boxes, but those keys were gone for good. This variant counts
     * availability across ALL accepted ids first and consumes nothing unless
     * the total covers {@code count}; both passes run back-to-back on the
     * server thread, so nothing can interleave between them.
     */
    public static boolean tryConsumeKeysExact(Player entity, java.util.List<String> acceptedKeys, int count) {
        if (acceptedKeys == null || acceptedKeys.isEmpty() || count <= 0
                || entity.getAbilities().instabuild) {
            return true;
        }
        java.util.List<ResourceLocation> ids = new java.util.ArrayList<>(acceptedKeys.size());
        int total = 0;
        for (String acceptedKey : acceptedKeys) {
            ResourceLocation id = new ResourceLocation(acceptedKey);
            if (id.equals(new ResourceLocation("minecraft", "air"))) {
                return true;
            }
            ids.add(id);
            total += countKeys(entity, id);
        }
        if (total < count) {
            return false;
        }
        int remaining = count;
        for (ResourceLocation id : ids) {
            if (remaining <= 0) {
                break;
            }
            remaining = consumeFromList(entity.getInventory().items, id, null, remaining);
            if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.HEAD, id, remaining);
            if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.CHEST, id, remaining);
            if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.LEGS, id, remaining);
            if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.FEET, id, remaining);
            if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.OFFHAND, id, remaining);
        }
        return remaining == 0;
    }

    /** Availability count of one key id across main inventory + equipment slots (box stacks excluded). */
    private static int countKeys(Player entity, ResourceLocation keyId) {
        int n = 0;
        for (ItemStack stack : entity.getInventory().items) {
            if (!stack.isEmpty() && !(stack.getItem() instanceof ItemCsgoBox)
                    && keyId.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
                n += stack.getCount();
            }
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty() && !(stack.getItem() instanceof ItemCsgoBox)
                    && keyId.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static int consumeFromList(java.util.List<ItemStack> stacks,
                                       ResourceLocation keyId,
                                       ItemStack boxTemplate,
                                       int remaining) {
        for (ItemStack stack : stacks) {
            if (remaining <= 0) {
                return 0;
            }
            boolean matches;
            if (keyId != null) {
                if (stack.getItem() instanceof ItemCsgoBox) {
                    continue;
                }
                matches = keyId.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
            } else {
                matches = stack.getItem() instanceof ItemCsgoBox
                        && ItemStack.isSameItemSameTags(stack, boxTemplate);
            }
            if (!matches) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        return remaining;
    }

    private static int consumeKeyFromSlot(Player entity, EquipmentSlot slot, ResourceLocation keyId, int remaining) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (stack.isEmpty()
                || stack.getItem() instanceof ItemCsgoBox
                || !keyId.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
            return remaining;
        }
        int take = Math.min(remaining, stack.getCount());
        stack.shrink(take);
        return remaining - take;
    }

    private static int consumeBoxFromSlot(Player entity, EquipmentSlot slot, ItemStack boxTemplate, int remaining) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (stack.isEmpty()
                || !(stack.getItem() instanceof ItemCsgoBox)
                || !ItemStack.isSameItemSameTags(stack, boxTemplate)) {
            return remaining;
        }
        int take = Math.min(remaining, stack.getCount());
        stack.shrink(take);
        return remaining - take;
    }
}
