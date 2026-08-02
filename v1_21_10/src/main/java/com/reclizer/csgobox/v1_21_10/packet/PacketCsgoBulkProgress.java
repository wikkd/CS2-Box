package com.reclizer.csgobox.v1_21_10.packet;

import com.reclizer.csgobox.v1_21_10.CsgoBox;
import com.reclizer.csgobox.v1_21_10.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.v1_21_10.box.BulkBoxContext;
import com.reclizer.csgobox.v1_21_10.box.BulkOpenResult;
import com.reclizer.csgobox.v1_21_10.event.BoxOpenedEvent;
import com.reclizer.csgobox.v1_21_10.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_10.item.ModItems;
import com.reclizer.csgobox.v1_21_10.utils.RandomItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client-to-server request to bulk-open every matching box in the player's
 * inventory. The server re-counts the actual box+key availability and may
 * open fewer boxes than the client estimates.
 *
 * <p><b>Atomicity:</b> validation, snapshot, and async compute happen first;
 * consumption (boxes + keys) is deferred until the main-thread
 * {@link #finalizeBulkOpen}. If anything throws between submission and
 * consumption, the player loses nothing.</p>
 */
public record PacketCsgoBulkProgress(long requestId) implements CustomPacketPayload {

    public static final Type<PacketCsgoBulkProgress> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "csgo_bulk_progress"));

    public static final StreamCodec<FriendlyByteBuf, PacketCsgoBulkProgress> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeLong(packet.requestId),
            buf -> new PacketCsgoBulkProgress(buf.readLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final PacketCsgoBulkProgress message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null) {
                return;
            }
            ItemStack templateBox = player.getMainHandItem();
            if (!(templateBox.getItem() instanceof ItemCsgoBox)) {
                return;
            }
            if (player instanceof ServerPlayer sp && (sp.isRemoved() || !sp.isAlive())) {
                return;
            }
            if (PacketCsgoProgress.isOpenBlockedStatic(player)) {
                return;
            }

            var itemList = ItemCsgoBox.getItemGroup(templateBox);
            if (itemList.isEmpty()) {
                return;
            }
            int[] weights = ItemCsgoBox.getRandom(templateBox);
            if (weights.length == 0) {
                return;
            }

            int availableBoxes = countMatchingBoxes(player, templateBox);
            int availableKeys = countMatchingKeys(player, templateBox);
            int K = Math.min(availableBoxes, availableKeys);
            if (K <= 0) {
                return;
            }
            // Server-enforced per-open cap (0 = unlimited). The client overview
            // screen clamps its estimate to the same value, but the server is
            // authoritative: a crafted packet can never open more than this.
            int limit = CsgoBox.CONFIG.bulkOpenCount();
            if (limit > 0) {
                K = Math.min(K, limit);
            }
            if (K <= 0) {
                return;
            }

            PacketCsgoProgress.blockFurtherOpensStatic(player);
            final int requestedK = K;
            final long requestId = message.requestId();
            final ResourceLocation boxId = ItemCsgoBox.getBoxId(templateBox);
            BulkBoxContext snapshot = new BulkBoxContext(boxId, weights, RandomItem.precomputeGradeMap(itemList));

            final Player playerFinal = player;
            CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return computeKResults(snapshot, requestedK);
                        } catch (Throwable t) {
                            CsgoBox.LOGGER.error("[csgo-bulk] computeKResults failed: K={} box={}", requestedK, boxId, t);
                            return List.<BulkOpenResult>of();
                        }
                    }, CsgoBox.BULK_COMPUTE_POOL)
                    .thenAccept(results -> {
                        if (playerFinal instanceof ServerPlayer sp && !sp.isRemoved() && sp.isAlive()) {
                            sp.level().getServer().execute(() -> {
                                try {
                                    finalizeBulkOpen(sp, snapshot, requestedK, results, requestId, templateBox);
                                } catch (Throwable t) {
                                    CsgoBox.LOGGER.error("[csgo-bulk] finalizeBulkOpen failed: player={} K={}",
                                            sp.getName().getString(), requestedK, t);
                                }
                            });
                        } else if (playerFinal instanceof ServerPlayer sp) {
                            CsgoBox.LOGGER.warn("[csgo-bulk] no finalize for player {} (dead/logged-out)", sp.getName().getString());
                        }
                    });
        });
    }

    private static int countMatchingKeys(Player player, ResourceLocation keyId) {
        if (keyId == null || keyId.equals(ResourceLocation.parse("minecraft:air"))) {
            return Integer.MAX_VALUE;
        }
        int total = 0;
        // Main inventory: slots 0-35
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof ItemCsgoBox) continue;
            if (keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                total += stack.getCount();
            }
        }
        // Armor
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ItemCsgoBox) continue;
            if (keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                total += stack.getCount();
            }
        }
        // Offhand
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!(offhand.getItem() instanceof ItemCsgoBox) && keyId.equals(BuiltInRegistries.ITEM.getKey(offhand.getItem()))) {
            total += offhand.getCount();
        }
        return total;
    }

    private static int countMatchingKeys(Player player, ItemStack box) {
        ResourceLocation keyId = ItemCsgoBox.getKey(box);
        return countMatchingKeys(player, keyId);
    }

    private static int countMatchingBoxes(Player player, ItemStack box) {
        int total = 0;
        // Main inventory: slots 0-35
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof ItemCsgoBox && ItemStack.isSameItemSameComponents(stack, box)) {
                total += stack.getCount();
            }
        }
        // Armor
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ItemCsgoBox && ItemStack.isSameItemSameComponents(stack, box)) {
                total += stack.getCount();
            }
        }
        // Offhand
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (offhand.getItem() instanceof ItemCsgoBox && ItemStack.isSameItemSameComponents(offhand, box)) {
            total += offhand.getCount();
        }
        return total;
    }

    /**
     * Pure-Java RNG loop. Must not touch Minecraft APIs. Consumes from
     * {@code snapshot} only (read-only).
     */
    private static List<BulkOpenResult> computeKResults(BulkBoxContext snapshot, int K) {
        List<BulkOpenResult> out = new ArrayList<>(K);
        for (int i = 0; i < K; i++) {
            long seed = ThreadLocalRandom.current().nextLong();
            Random rng = new Random(seed);
            if (i == 0) {
                List<ItemStack> animItems = new ArrayList<>(PacketBoxOpenResult.ANIMATION_ITEM_COUNT);
                List<Integer> animGrades = new ArrayList<>(PacketBoxOpenResult.ANIMATION_ITEM_COUNT);
                for (int j = 0; j < PacketBoxOpenResult.ANIMATION_ITEM_COUNT; j++) {
                    int g = RandomItem.randomItemsGrade(rng, snapshot.weights());
                    ItemStack s = RandomItem.randomItemsFromGradeMap(rng, g, snapshot.gradeMap());
                    if (s.isEmpty()) {
                        s = RandomItem.findFallbackFromGradeMap(g, snapshot.gradeMap());
                    }
                    animItems.add(s);
                    animGrades.add(Mth.clamp(g, 1, 5));
                }
                int winningIndex = randomWinningIndex(rng, animItems.size());
                winningIndex = RandomItem.clampToValidItem(animItems, winningIndex);
                if (winningIndex < 0) {
                    winningIndex = 0;
                }
                ItemStack giveItem = animItems.get(winningIndex);
                int finalGrade = animGrades.get(winningIndex);
                if (giveItem.isEmpty()) {
                    ItemStack fb = RandomItem.findFallbackFromGradeMap(1, snapshot.gradeMap());
                    if (!fb.isEmpty()) {
                        giveItem = fb;
                        finalGrade = 1;
                        animItems.set(winningIndex, giveItem.copy());
                        animGrades.set(winningIndex, finalGrade);
                    }
                }
                out.add(new BulkOpenResult(giveItem, finalGrade, seed, winningIndex, animItems, animGrades));
            } else {
                int g = RandomItem.randomItemsGrade(rng, snapshot.weights());
                ItemStack s = RandomItem.randomItemsFromGradeMap(rng, g, snapshot.gradeMap());
                if (s.isEmpty()) {
                    s = RandomItem.findFallbackFromGradeMap(g, snapshot.gradeMap());
                }
                out.add(new BulkOpenResult(s, Mth.clamp(g, 1, 5), 0L, -1, List.of(), List.of()));
            }
        }
        return out;
    }

    private static int randomWinningIndex(Random rng, int itemCount) {
        int maxIndex = itemCount - 1;
        int min = Math.min(PacketBoxOpenResult.MIN_WINNING_INDEX, maxIndex);
        int max = Math.min(PacketBoxOpenResult.MAX_WINNING_INDEX, maxIndex);
        if (max <= min) {
            return min;
        }
        return min + rng.nextInt(max - min + 1);
    }

    /**
     * Main-thread finalization. Re-validates inventory (boxes/keys might have
     * changed during async compute), consumes exactly what is still available,
     * sends the animation result packet(s), adds items to inventory (with drop
     * fallback), awards stats. Called by the main server thread.
     */
    private static void finalizeBulkOpen(ServerPlayer sp, BulkBoxContext snapshot, int K,
                                         List<BulkOpenResult> results, long requestId,
                                         ItemStack templateBox) {
        if (results == null || results.isEmpty()) {
            CsgoBox.LOGGER.warn("[csgo-bulk] empty results for player={}; consumption cancelled", sp.getName().getString());
            return;
        }

        // Get keyId ONCE from templateBox to avoid race conditions if player's hand changes
        ResourceLocation keyId = ItemCsgoBox.getKey(templateBox);

        // Re-validate after async compute; inventory might have changed.
        int recheckBoxes = countMatchingBoxes(sp, templateBox);
        int recheckKeys = countMatchingKeys(sp, keyId);
        int actualK = Math.min(recheckBoxes, recheckKeys);
        if (actualK < K) {
            CsgoBox.LOGGER.warn("[csgo-bulk] player {} availability changed during compute: requested={} available={}",
                    sp.getName().getString(), K, actualK);
        }
        if (actualK <= 0) {
            CsgoBox.LOGGER.warn("[csgo-bulk] player {} has no boxes left; aborting without consumption", sp.getName().getString());
            return;
        }

        // Truncate results to actualK (we can't give items for boxes the player no longer has).
        List<BulkOpenResult> truncated = results.subList(0, actualK);

        // Now consume exactly actualK boxes + keys. Use the SAME keyId we got above.
        if (!PacketCsgoProgress.tryConsumeBoxes(sp, templateBox, actualK)) {
            CsgoBox.LOGGER.error("[csgo-bulk] tryConsumeBoxes failed for player={} want={}; aborting", sp.getName().getString(), actualK);
            return;
        }
        if (!PacketCsgoProgress.tryConsumeKeys(sp, keyId, actualK)) {
            CsgoBox.LOGGER.error("[csgo-bulk] tryConsumeKeys failed for player={} want={}; refunding boxes", sp.getName().getString(), actualK);
            refundBoxes(sp, snapshot.boxId(), templateBox, actualK);
            return;
        }

        BulkOpenResult box1 = truncated.get(0);
        PacketDistributor.sendToPlayer(sp, new PacketBoxOpenResult(
                box1.resultItem().copy(),
                box1.resultGrade(),
                box1.winningIndex(),
                box1.serverSeed(),
                requestId,
                box1.animationItems(),
                box1.animationGrades()
        ));

        if (actualK > 1) {
            List<ItemStack> restItems = new ArrayList<>(actualK - 1);
            List<Integer> restGrades = new ArrayList<>(actualK - 1);
            for (int i = 1; i < actualK; i++) {
                BulkOpenResult r = truncated.get(i);
                if (r.resultItem().isEmpty()) {
                    continue;
                }
                restItems.add(r.resultItem().copy());
                restGrades.add(r.resultGrade());
            }
            if (!restItems.isEmpty()) {
                PacketDistributor.sendToPlayer(sp, new PacketBoxBulkResult(requestId, restItems, restGrades));
            }
        }

        for (BulkOpenResult r : truncated) {
            if (r.resultItem().isEmpty()) {
                continue;
            }
            ItemStack toGive = r.resultItem().copy();
            if (!sp.getInventory().add(toGive) && !toGive.isEmpty()) {
                sp.drop(toGive, false);
            }
            NeoForge.EVENT_BUS.post(new BoxOpenedEvent(sp, snapshot.boxId(), r.resultItem().copy(), r.resultGrade(), true));
        }

        sp.awardStat(CsgoBox.OPENED_BOXES_STAT, actualK);
        if (CsgoBox.CONFIG.enableAchievements()) {
            for (int i = 0; i < actualK; i++) {
                OpenedBoxTrigger.INSTANCE.trigger(sp);
            }
        }

        if (CsgoBox.debug()) {
            CsgoBox.LOGGER.info("[csgo-bulk] player={} K={} (re-validated from {}) -> {} items granted",
                    sp.getName().getString(), actualK, K, truncated.size());
        }
    }

    /** Add boxes back to a player if consumption partially failed. */
    private static void refundBoxes(ServerPlayer sp, ResourceLocation boxId, ItemStack templateBox, int count) {
        ItemStack refund = new ItemStack(ModItems.ITEM_CSGOBOX.get());
        ItemCsgoBox.setBoxId(boxId, refund);
        for (int i = 0; i < count; i++) {
            ItemStack one = refund.copy();
            one.setCount(1);
            if (!sp.getInventory().add(one)) {
                sp.drop(one.copy(), false);
            }
        }
    }
}
