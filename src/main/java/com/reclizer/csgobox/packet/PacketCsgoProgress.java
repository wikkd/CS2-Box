package com.reclizer.csgobox.packet;

import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.capability.CsboxPlayerData;
import com.reclizer.csgobox.capability.ModCapability;
import com.reclizer.csgobox.item.ItemCsgoBox;
import com.reclizer.csgobox.utils.RandomItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
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

            if (isOpenBlocked(player)) {
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

            if (!tryConsumeKeys(player, box)) {
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

            blockFurtherOpens(player);

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

    private static boolean isOpenBlocked(Player player) {
        long now = player.level().getGameTime();
        Long blockedUntil = OPEN_BLOCKED_UNTIL_TICK.get(player.getUUID());
        if (blockedUntil == null || now >= blockedUntil) {
            OPEN_BLOCKED_UNTIL_TICK.remove(player.getUUID());
            return false;
        }
        return true;
    }

    private static void blockFurtherOpens(Player player) {
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

    private static boolean tryConsumeKeys(Player entity, ItemStack box) {
        ResourceLocation keyId = ItemCsgoBox.getKey(box);
        if (keyId == null || keyId.equals(ResourceLocation.parse("minecraft:air"))) {
            return true;
        }
        for (ItemStack stack : entity.getInventory().items) {
            if (keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
