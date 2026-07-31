package com.reclizer.csgobox.v26_1_2.packet;

import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.v26_1_2.capability.CsboxPlayerData;
import com.reclizer.csgobox.v26_1_2.capability.ModCapability;
import com.reclizer.csgobox.v26_1_2.command.CsboxCommand;
import com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_1_2.utils.RandomItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.ArrayList;

/**
 * Client-to-server request to open the currently held box.
 *
 * <p>The request id is for matching the later client animation result only. The
 * server never trusts it for authorization.</p>
 */
public record PacketCsgoProgress(long requestId) implements CustomPacketPayload {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Map<UUID, Long> OPEN_BLOCKED_UNTIL_TICK = new HashMap<>();

    public static final Type<PacketCsgoProgress> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "csgo_progress"));

    public static final StreamCodec<FriendlyByteBuf, PacketCsgoProgress> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeLong(packet.requestId),
            buf -> new PacketCsgoProgress(buf.readLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final PacketCsgoProgress message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            var box = player.getMainHandItem();
            if (!(box.getItem() instanceof ItemCsgoBox)) {
                return;
            }

            if (player instanceof ServerPlayer sp && (sp.isRemoved() || !sp.isAlive())) {
                sendRejected(context, message.requestId());
                return;
            }

            if (isOpenBlockedStatic(player)) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(context, message.requestId());
                }
                return;
            }

            var itemList = ItemCsgoBox.getItemGroup(box);
            if (itemList.isEmpty()) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(context, message.requestId());
                }
                return;
            }

            int[] weights = ItemCsgoBox.getRandom(box);
            if (weights.length == 0) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(context, message.requestId());
                }
                return;
            }

            if (!tryConsumeKeys(player, box, 1)) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(context, message.requestId());
                }
                return;
            }

            long serverSeed = SECURE_RANDOM.nextLong();
            var rng = new Random(serverSeed);
            var gradeMap = RandomItem.precomputeGradeMap(itemList);

            List<ItemStack> animationItems = new ArrayList<>(PacketBoxOpenResult.ANIMATION_ITEM_COUNT);
            List<Integer> animationGrades = new ArrayList<>(PacketBoxOpenResult.ANIMATION_ITEM_COUNT);
            for (int i = 0; i < PacketBoxOpenResult.ANIMATION_ITEM_COUNT; i++) {
                int grade = RandomItem.randomItemsGrade(rng, weights);
                ItemStack itemStack = RandomItem.randomItemsFromGradeMap(rng, grade, gradeMap);
                if (itemStack.isEmpty()) {
                    itemStack = RandomItem.findFallbackFromGradeMap(grade, gradeMap);
                }
                animationGrades.add(Mth.clamp(grade, 1, 5));
                animationItems.add(itemStack);
            }

            int winningIndex = randomWinningIndex(animationItems.size());
            winningIndex = RandomItem.clampToValidItem(animationItems, winningIndex);
            if (winningIndex < 0) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(context, message.requestId());
                }
                return;
            }

            ItemStack giveItem = animationItems.get(winningIndex);
            int finalGrade = animationGrades.get(winningIndex);

            if (giveItem.isEmpty()) {
                giveItem = RandomItem.findFallback(1, itemList);
                if (giveItem.isEmpty()) {
                    if (player instanceof ServerPlayer sp) {
                        sendRejected(context, message.requestId());
                    }
                    return;
                }
                finalGrade = resolveGrade(giveItem, itemList, 1);
                animationItems.set(winningIndex, giveItem.copy());
                animationGrades.set(winningIndex, finalGrade);
            }

            blockFurtherOpensStatic(player);

            player.setData(ModCapability.PLAYER_DATA,
                    new CsboxPlayerData(serverSeed, 0, giveItem.copy(), finalGrade));

            context.reply(new PacketBoxOpenResult(
                    giveItem.copy(),
                    finalGrade,
                    winningIndex,
                    serverSeed,
                    message.requestId(),
                    animationItems,
                    animationGrades
            ));

            ItemStack toGive = giveItem.copy();
            boolean added = player.getInventory().add(toGive);
            if (!added && !toGive.isEmpty()) {
                player.drop(toGive, false);
            }
            box.shrink(1);

            if (player instanceof ServerPlayer sp) {
                sp.awardStat(CsgoBox.OPENED_BOXES_STAT, 1);
                CsboxCommand.syncOpenedBoxesToScoreboard(sp);
                if (CsgoBox.CONFIG.enableAchievements()) {
                    OpenedBoxTrigger.INSTANCE.trigger(sp);
                }
            }
        });
    }

    private static int randomWinningIndex(int itemCount) {
        int maxIndex = itemCount - 1;
        int min = Math.min(PacketBoxOpenResult.MIN_WINNING_INDEX, maxIndex);
        int max = Math.min(PacketBoxOpenResult.MAX_WINNING_INDEX, maxIndex);
        if (max <= min) {
            return min;
        }
        return min + SECURE_RANDOM.nextInt(max - min + 1);
    }

    private static void sendRejected(IPayloadContext context, long requestId) {
        context.reply(new PacketBoxOpenResult(
                ItemStack.EMPTY,
                1,
                0,
                0L,
                requestId,
                List.of(),
                List.of()
        ));
    }

    static boolean isOpenBlockedStatic(Player player) {
        long now = player.level().getGameTime();
        Long blockedUntil = OPEN_BLOCKED_UNTIL_TICK.get(player.getUUID());
        if (blockedUntil == null || now >= blockedUntil) {
            OPEN_BLOCKED_UNTIL_TICK.remove(player.getUUID());
            return false;
        }
        return true;
    }

    static void blockFurtherOpensStatic(Player player) {
        long now = player.level().getGameTime();
        OPEN_BLOCKED_UNTIL_TICK.put(player.getUUID(), now + serverOpenCooldownTicks());
    }

    private static int serverOpenCooldownTicks() {
        return 10;
    }

    private static int resolveGrade(ItemStack item, Map<ItemStack, Integer> itemList, int fallback) {
        for (Map.Entry<ItemStack, Integer> entry : itemList.entrySet()) {
            if (ItemStack.isSameItemSameComponents(item, entry.getKey())) {
                return Mth.clamp(entry.getValue(), 1, 5);
            }
        }
        return Mth.clamp(fallback, 1, 5);
    }

    /**
     * Consume up to {@code count} keys matching the box's key id from anywhere
     * in the player's inventory (items, armor, offhand). If the box has no key
     * requirement, returns true without touching inventory. Returns true only
     * when the requested count was fully consumed (or none was required).
     *
     * <p>26.x has no public {@code inventory.armor/offhand} list; armor is
     * reached via {@code Player.getItemBySlot(EquipmentSlot.*)} and offhand
     * similarly. The previous implementation only walked
     * {@code getNonEquipmentItems()} (36 hotbar + main slots), so a player
     * holding the key in offhand or wearing a key-as-armor would be silently
     * under-deducted. The bulk path would then crash with a "missing keys"
     * assertion and refund the boxes; the operator-facing log only saw the
     * refund, never the under-count cause.</p>
     */
    static boolean tryConsumeKeys(Player entity, ItemStack box, int count) {
        Identifier keyId = ItemCsgoBox.getKey(box);
        if (keyId == null || keyId.equals(Identifier.parse("minecraft:air"))) {
            return true;
        }
        if (count <= 0) {
            return true;
        }
        int remaining = count;
        remaining = consumeFromList(entity.getInventory().getNonEquipmentItems(), keyId, null, remaining);
        if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.HEAD, keyId, remaining);
        if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.CHEST, keyId, remaining);
        if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.LEGS, keyId, remaining);
        if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.FEET, keyId, remaining);
        if (remaining > 0) remaining = consumeKeyFromSlot(entity, EquipmentSlot.OFFHAND, keyId, remaining);
        return remaining == 0;
    }

    /**
     * Consume up to {@code count} boxes matching the template (same item,
     * same components) from anywhere in the player's inventory (items, armor,
     * offhand). Returns true only when the full count was consumed.
     */
    static boolean tryConsumeBoxes(Player entity, ItemStack box, int count) {
        if (count <= 0) {
            return true;
        }
        int remaining = count;
        remaining = consumeFromList(entity.getInventory().getNonEquipmentItems(), null, box, remaining);
        if (remaining > 0) remaining = consumeBoxFromSlot(entity, EquipmentSlot.HEAD, box, remaining);
        if (remaining > 0) remaining = consumeBoxFromSlot(entity, EquipmentSlot.CHEST, box, remaining);
        if (remaining > 0) remaining = consumeBoxFromSlot(entity, EquipmentSlot.LEGS, box, remaining);
        if (remaining > 0) remaining = consumeBoxFromSlot(entity, EquipmentSlot.FEET, box, remaining);
        if (remaining > 0) remaining = consumeBoxFromSlot(entity, EquipmentSlot.OFFHAND, box, remaining);
        return remaining == 0;
    }

    /**
     * Shrinks matching stacks from the given inventory slice until either the
     * requested count is satisfied or the slice is exhausted. Returns the
     * remaining (un-fulfilled) count.
     */
    private static int consumeFromList(java.util.List<ItemStack> stacks,
                                       Identifier keyId,
                                       ItemStack boxTemplate,
                                       int remaining) {
        for (ItemStack stack : stacks) {
            if (remaining <= 0) {
                return 0;
            }
            boolean matches;
            if (keyId != null) {
                // CRITICAL: skip box instances. ItemCsgoBox.getKey(box) returns
                // the box's own configured key id (via getBoxId → ITEM.getKey
                // fallback), so a naive "keyId equals getKey(stack.item)"
                // check would match boxes that the player also happens to own
                // and would shrink them under the guise of "key consumption".
                // In the bulk path this led to boxes being double-counted
                // (once as boxes, once as keys) — 5 boxes + 5 keys opened
                // 5 times would drain 5 boxes + 5 boxes = 10 boxes total.
                if (stack.getItem() instanceof ItemCsgoBox) {
                    continue;
                }
                matches = keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            } else {
                matches = stack.getItem() instanceof ItemCsgoBox
                        && ItemStack.isSameItemSameComponents(stack, boxTemplate);
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

    private static int consumeKeyFromSlot(Player entity, EquipmentSlot slot, Identifier keyId, int remaining) {
        ItemStack stack = entity.getItemBySlot(slot);
        // Skip box instances on armor/offhand for the same reason as
        // consumeFromList above — ItemCsgoBox.getKey(box) returns the box's
        // own key id, so a keyId match would otherwise shrink armor/offhand
        // boxes that happen to share a registry id with the targeted key
        // (modded boxes whose registry id is the same as a default key, e.g.
        // csgobox:csgo_key3, would be misclassified as keys).
        if (stack.isEmpty()
                || stack.getItem() instanceof ItemCsgoBox
                || !keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
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
                || !ItemStack.isSameItemSameComponents(stack, boxTemplate)) {
            return remaining;
        }
        int take = Math.min(remaining, stack.getCount());
        stack.shrink(take);
        return remaining - take;
    }
}
