package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxStripGenerator;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.forge_1_20_1.capability.CsboxPlayerData;
import com.reclizer.csgobox.forge_1_20_1.capability.ModCapability;
import com.reclizer.csgobox.forge_1_20_1.event.BoxOpeningEvent;
import com.reclizer.csgobox.forge_1_20_1.event.BoxOpenedEvent;
import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.box.GradeGroup;
import com.reclizer.csgobox.logic.GradeMap;
import com.reclizer.csgobox.logic.GradeMapCache;
import com.reclizer.csgobox.logic.OpenBlockGuard;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ItemTerminal;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class PacketCsgoProgress {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

        if (OpenBlockGuard.isBlocked(player.getUUID(), player.level().getGameTime())) {
            sendRejectedToPlayer(message.requestId, player);
            return;
        }

        var boxId = ItemCsgoBox.getBoxId(box);
        if (boxId == null) {
            sendRejectedToPlayer(message.requestId, player);
            return;
        }
        if (BoxRegistry.get(boxId) != null && BoxRegistry.get(boxId).isTerminal()) {
            sendRejectedToPlayer(message.requestId, player);
            return;
        }

        BoxOpeningEvent opening = new BoxOpeningEvent(player, boxId, false, 1);
        BoxOpeningEvent.BUS.post(opening);
        if (opening.isCanceled()) {
            sendRejectedToPlayer(message.requestId, player);
            return;
        }

        int[] weights = ItemCsgoBox.getRandom(box);
        if (weights.length == 0) {
            sendRejectedToPlayer(message.requestId, player);
            return;
        }

        long serverSeed = SECURE_RANDOM.nextLong();
        var rng = new Random(serverSeed);

        var gradeMap = GradeMapCache.get(boxId.toString(),
                () -> GradeMap.build(ItemCsgoBox.getItemGroup(box), stack -> !stack.isEmpty(), ItemStack::copy));
        if (gradeMap.isEmpty()) {
            sendRejectedToPlayer(message.requestId, player);
            return;
        }

        var strip = BoxStripGenerator.generate(gradeMap, weights, rng, ItemStack.EMPTY);
        int winningIndex = strip.winningIndex();
        if (winningIndex < 0) {
            sendRejectedToPlayer(message.requestId, player);
            return;
        }

        ItemStack giveItem = strip.items().get(winningIndex);
        int finalGrade = strip.grades().get(winningIndex);

        if (giveItem.isEmpty()) {
            giveItem = gradeMap.findFallback(1);
            if (giveItem == null) giveItem = ItemStack.EMPTY;
            if (giveItem.isEmpty()) {
                sendRejectedToPlayer(message.requestId, player);
                return;
            }
            finalGrade = resolveGrade(giveItem, boxId, 1);
            strip.items().set(winningIndex, giveItem.copy());
            strip.grades().set(winningIndex, finalGrade);
        }

        if (!tryConsumeKeys(player, box, 1)) {
            sendRejectedToPlayer(message.requestId, player);
            return;
        }

        float wear = 0F;
        if (CsgoBox.CONFIG.damageItemByWear() && giveItem.getMaxDamage() > 0) {
            wear = rng.nextFloat();
            applyWearDamage(giveItem, wear);
        }

        OpenBlockGuard.block(player.getUUID(), player.level().getGameTime(), OpenBlockGuard.DEFAULT_COOLDOWN_TICKS);

        final ItemStack capturedGiveItem = giveItem.copy();
        final int capturedGrade = finalGrade;
        player.getCapability(ModCapability.PLAYER_DATA).ifPresent(holder -> holder.set(
                new CsboxPlayerData(serverSeed, 0, capturedGiveItem, capturedGrade)));

        Networking.INSTANCE.reply(new PacketBoxOpenResult(
                giveItem.copy(),
                finalGrade,
                winningIndex,
                serverSeed,
                message.requestId,
                strip.items(),
                strip.grades()
        ), context);

        ItemStack toGive = giveItem.copy();
        boolean added = player.getInventory().add(toGive);
        if (!added && !toGive.isEmpty()) {
            player.drop(toGive, false);
        }
        if (!player.getAbilities().instabuild) {
            box.shrink(1);
        }

        player.awardStat(CsgoBox.OPENED_BOXES_STAT, 1);
        if (CsgoBox.CONFIG.enableAchievements()) {
            OpenedBoxTrigger.INSTANCE.trigger(player);
        }

        BoxOpenedEvent.BUS.post(new BoxOpenedEvent(player, boxId, giveItem.copy(), finalGrade, false));
    }

    static void sendRejected(long requestId) {
        // No-op fallback; prefer sendRejectedToPlayer with an explicit target.
    }

    static void sendRejectedToPlayer(long requestId, ServerPlayer player) {
        Networking.sendToPlayer(new PacketBoxOpenResult(
                ItemStack.EMPTY, 1, 0, 0L, requestId, List.of(), List.of()), player);
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
            for (GradeGroup grade : def.grades()) {
                int gradeLevel = BoxGrades.gradeLevel(grade.id());
                if (gradeLevel == 0) continue;
                for (ItemStack candidate : grade.items()) {
                    if (ItemStack.isSameItemSameTags(item, candidate)) {
                        return Mth.clamp(gradeLevel, 1, 5);
                    }
                }
            }
        }
        return Mth.clamp(fallback, 1, 5);
    }

    public static boolean tryConsumeKeys(Player entity, ItemStack box, int count) {
        ResourceLocation keyId = ItemCsgoBox.getKey(box);
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
