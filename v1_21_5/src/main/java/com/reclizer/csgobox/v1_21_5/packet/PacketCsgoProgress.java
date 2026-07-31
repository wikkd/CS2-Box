package com.reclizer.csgobox.v1_21_5.packet;

import com.reclizer.csgobox.v1_21_5.CsgoBox;
import com.reclizer.csgobox.v1_21_5.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.v1_21_5.capability.CsboxPlayerData;
import com.reclizer.csgobox.v1_21_5.capability.ModCapability;
import com.reclizer.csgobox.v1_21_5.command.CsboxCommand;
import com.reclizer.csgobox.v1_21_5.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_5.utils.RandomItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.List;

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
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "csgo_progress"));

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
                sendRejected(sp, message.requestId());
                return;
            }

            if (isOpenBlockedStatic(player)) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(sp, message.requestId());
                }
                return;
            }

            var itemList = ItemCsgoBox.getItemGroup(box);
            if (itemList.isEmpty()) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(sp, message.requestId());
                }
                return;
            }

            int[] weights = ItemCsgoBox.getRandom(box);
            if (weights.length == 0) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(sp, message.requestId());
                }
                return;
            }

            if (!tryConsumeKeys(player, box, 1)) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(sp, message.requestId());
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
                    sendRejected(sp, message.requestId());
                }
                return;
            }

            ItemStack giveItem = animationItems.get(winningIndex);
            int finalGrade = animationGrades.get(winningIndex);

            if (giveItem.isEmpty()) {
                giveItem = RandomItem.findFallback(1, itemList);
                if (giveItem.isEmpty()) {
                    if (player instanceof ServerPlayer sp) {
                        sendRejected(sp, message.requestId());
                    }
                    return;
                }
                finalGrade = resolveGrade(giveItem, itemList, 1);
                animationItems.set(winningIndex, giveItem.copy());
                animationGrades.set(winningIndex, finalGrade);
            }

            blockFurtherOpensStatic(player);

            player.setData(ModCapability.PLAYER_DATA,
                    new CsboxPlayerData(0L, 0, ItemStack.EMPTY, 0));
            player.setData(ModCapability.PLAYER_DATA,
                    new CsboxPlayerData(serverSeed, 0, giveItem.copy(), finalGrade));

            if (player instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, new PacketBoxOpenResult(
                        giveItem.copy(),
                        finalGrade,
                        winningIndex,
                        serverSeed,
                        message.requestId(),
                        animationItems,
                        animationGrades
                ));
            }

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

    private static void sendRejected(ServerPlayer player, long requestId) {
        PacketDistributor.sendToPlayer(player, new PacketBoxOpenResult(
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
     * <p>Scans all 41 player inventory slots — the previous implementation
     * only walked {@code items} (36 hotbar + main slots), so a player holding
     * the key in offhand or wearing a key-as-armor would be silently
     * under-deducted. The bulk path would then crash with a "missing keys"
     * assertion and refund the boxes; the operator-facing log only saw the
     * refund, never the under-count cause.</p>
     */
    static boolean tryConsumeKeys(Player entity, ItemStack box, int count) {
        ResourceLocation keyId = ItemCsgoBox.getKey(box);
        return tryConsumeKeys(entity, keyId, count);
    }

    /**
     * Consume keys by their id directly. This avoids repeated lookups of the key id
     * from the box, which could return different values if the player's hand changes
     * between calls (e.g., during bulk operations).
     * Adapted for NeoForge 21.4+: uses slot iteration instead of direct field access.
     */
    static boolean tryConsumeKeys(Player entity, ResourceLocation keyId, int count) {
        if (keyId == null || keyId.toString().equals("minecraft:air")) {
            return true;
        }
        if (count <= 0) {
            return true;
        }
        int remaining = count;
        // Main inventory: slots 0-35 (36 slots)
        for (int i = 0; i < 36 && remaining > 0; i++) {
            remaining = consumeSingle(entity.getInventory().getItem(i), keyId, null, remaining);
        }
        // Armor slots via getItemBySlot
        if (remaining > 0) remaining = consumeSingle(entity.getItemBySlot(EquipmentSlot.HEAD), keyId, null, remaining);
        if (remaining > 0) remaining = consumeSingle(entity.getItemBySlot(EquipmentSlot.CHEST), keyId, null, remaining);
        if (remaining > 0) remaining = consumeSingle(entity.getItemBySlot(EquipmentSlot.LEGS), keyId, null, remaining);
        if (remaining > 0) remaining = consumeSingle(entity.getItemBySlot(EquipmentSlot.FEET), keyId, null, remaining);
        // Offhand
        if (remaining > 0) remaining = consumeSingle(entity.getItemBySlot(EquipmentSlot.OFFHAND), keyId, null, remaining);
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
        // Main inventory: slots 0-35
        for (int i = 0; i < 36 && remaining > 0; i++) {
            remaining = consumeSingle(entity.getInventory().getItem(i), null, box, remaining);
        }
        // Armor slots
        if (remaining > 0) remaining = consumeSingle(entity.getItemBySlot(EquipmentSlot.HEAD), null, box, remaining);
        if (remaining > 0) remaining = consumeSingle(entity.getItemBySlot(EquipmentSlot.CHEST), null, box, remaining);
        if (remaining > 0) remaining = consumeSingle(entity.getItemBySlot(EquipmentSlot.LEGS), null, box, remaining);
        if (remaining > 0) remaining = consumeSingle(entity.getItemBySlot(EquipmentSlot.FEET), null, box, remaining);
        // Offhand
        if (remaining > 0) remaining = consumeSingle(entity.getItemBySlot(EquipmentSlot.OFFHAND), null, box, remaining);
        return remaining == 0;
    }

    /**
     * Consume from a single ItemStack, modifying it in-place if it matches.
     * Returns the remaining count after this stack.
     */
    private static int consumeSingle(ItemStack stack,
                                     ResourceLocation keyId,
                                     ItemStack boxTemplate,
                                     int remaining) {
        if (remaining <= 0 || stack.isEmpty()) {
            return remaining;
        }
        if (stack.getItem() instanceof ItemCsgoBox) {
            // Skip box instances
            return remaining;
        }
        boolean matches;
        if (keyId != null) {
            matches = keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        } else {
            matches = ItemStack.isSameItemSameComponents(stack, boxTemplate);
        }
        if (!matches) {
            return remaining;
        }
        int take = Math.min(remaining, stack.getCount());
        stack.shrink(take);
        return remaining - take;
    }

    /**
     * Shrinks matching stacks from the given inventory slice until either the
     * requested count is satisfied or the slice is exhausted. Returns the
     * remaining (un-fulfilled) count.
     *
     * <p>Either {@code keyId} (for keys) or {@code boxTemplate} (for boxes)
     * must be non-null; the other is ignored. Keys match by item id; boxes
     * match by item type + components ({@code
     * ItemStack.isSameItemSameComponents}).</p>
     */
    private static int consumeFromList(java.util.List<ItemStack> stacks,
                                       ResourceLocation keyId,
                                       ItemStack boxTemplate,
                                       int remaining) {
        for (ItemStack stack : stacks) {
            remaining = consumeSingle(stack, keyId, boxTemplate, remaining);
            if (remaining <= 0) return 0;
        }
        return remaining;
    }
}
