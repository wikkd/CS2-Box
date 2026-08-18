package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.logic.GradeMapCache;
import com.reclizer.csgobox.forge_1_20_1.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.forge_1_20_1.box.BulkBoxContext;
import com.reclizer.csgobox.forge_1_20_1.box.BulkOpenResult;
import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.event.BoxOpeningEvent;
import com.reclizer.csgobox.forge_1_20_1.event.BoxOpenedEvent;
import com.reclizer.csgobox.box.BoxStripGenerator;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ItemTerminal;
import com.reclizer.csgobox.logic.GradeMap;
import com.reclizer.csgobox.logic.OpenBlockGuard;
import com.reclizer.csgobox.logic.OddsCalculator;
import com.reclizer.csgobox.forge_1_20_1.item.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public class PacketCsgoBulkProgress {

    private final long requestId;

    public PacketCsgoBulkProgress(long requestId) {
        this.requestId = requestId;
    }

    public PacketCsgoBulkProgress(FriendlyByteBuf buf) {
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

    public static void handleServer(final PacketCsgoBulkProgress message, final NetworkEvent.Context context) {
        Player player = context.getSender();
        if (player == null) {
            return;
        }
        ItemStack templateBox = player.getMainHandItem();
        if (!(templateBox.getItem() instanceof ItemCsgoBox)) {
            return;
        }
        if (templateBox.getItem() instanceof ItemTerminal) {
            PacketCsgoProgress.sendRejectedToPlayer(message.requestId, (ServerPlayer) player);
            return;
        }
        ResourceLocation boxId = ItemCsgoBox.getBoxId(templateBox);
        BoxDefinition def = boxId == null ? null : BoxRegistry.get(boxId);
        if (def != null && def.isTerminal()) {
            PacketCsgoProgress.sendRejectedToPlayer(message.requestId, (ServerPlayer) player);
            return;
        }
        if (player instanceof ServerPlayer sp && (sp.isRemoved() || !sp.isAlive())) {
            return;
        }
        if (OpenBlockGuard.isBlocked(player.getUUID(), player.level().getGameTime())) {
            PacketCsgoProgress.sendRejectedToPlayer(message.requestId, (ServerPlayer) player);
            return;
        }

        var gradeMap = GradeMapCache.get(boxId.toString(),
                () -> GradeMap.build(ItemCsgoBox.getItemGroup(templateBox), stack -> !stack.isEmpty(), ItemStack::copy));
        if (gradeMap.isEmpty()) {
            return;
        }
        int[] weights = ItemCsgoBox.getRandom(templateBox);
        if (weights.length == 0) {
            return;
        }

        Availability avail = countAvailability(player, templateBox, ItemCsgoBox.getKey(templateBox));
        int availableBoxes = avail.boxes;
        int availableKeys = avail.keys;
        int K = Math.min(availableBoxes, availableKeys);
        if (K <= 0) {
            return;
        }
        int limit = CsgoBox.CONFIG.bulkOpenCount();
        if (limit > 0) {
            K = Math.min(K, limit);
        }
        if (K <= 0) {
            return;
        }

        BoxOpeningEvent opening = new BoxOpeningEvent(player, boxId, true, K);
        BoxOpeningEvent.BUS.post(opening);
        if (opening.isCanceled()) {
            PacketCsgoProgress.sendRejectedToPlayer(message.requestId, (ServerPlayer) player);
            return;
        }

        OpenBlockGuard.block(player.getUUID(), player.level().getGameTime(), OpenBlockGuard.DEFAULT_COOLDOWN_TICKS);
        final int requestedK = K;
        final long reqId = message.requestId;
        BulkBoxContext snapshot = new BulkBoxContext(boxId, weights, gradeMap);

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
                                finalizeBulkOpen(sp, snapshot, requestedK, results, reqId, templateBox);
                            } catch (Throwable t) {
                                CsgoBox.LOGGER.error("[csgo-bulk] finalizeBulkOpen failed: player={} K={}",
                                        sp.getName().getString(), requestedK, t);
                            }
                        });
                    } else if (playerFinal instanceof ServerPlayer sp) {
                        CsgoBox.LOGGER.warn("[csgo-bulk] no finalize for player {} (dead/logged-out)", sp.getName().getString());
                    }
                });
    }

    private static class Availability {
        final int boxes;
        final int keys;
        Availability(int boxes, int keys) { this.boxes = boxes; this.keys = keys; }
    }

    private static Availability countAvailability(Player player, ItemStack box, ResourceLocation keyId) {
        boolean noKey = keyId == null || keyId.equals(new ResourceLocation("minecraft", "air"));
        boolean countKeys = !noKey && !player.getAbilities().instabuild;
        int boxes = 0;
        int keys = countKeys ? 0 : Integer.MAX_VALUE;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof ItemCsgoBox) {
                if (ItemStack.isSameItemSameTags(stack, box)) {
                    boxes += stack.getCount();
                }
            } else if (countKeys && keyId.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
                keys += stack.getCount();
            }
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ItemCsgoBox) {
                if (ItemStack.isSameItemSameTags(stack, box)) {
                    boxes += stack.getCount();
                }
            } else if (countKeys && keyId.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
                keys += stack.getCount();
            }
        }
        return new Availability(boxes, keys);
    }

    private static List<BulkOpenResult> computeKResults(BulkBoxContext snapshot, int K) {
        List<BulkOpenResult> out = new ArrayList<>(K);
        OddsCalculator.Precomputed pre = OddsCalculator.precomputeWeights(snapshot.weights());
        for (int i = 0; i < K; i++) {
            long seed = ThreadLocalRandom.current().nextLong();
            Random rng = new Random(seed);
            if (i == 0) {
                var strip = BoxStripGenerator.generate(snapshot.gradeMap(), snapshot.weights(), rng, ItemStack.EMPTY);
                int winningIndex = Math.max(0, strip.winningIndex());
                ItemStack giveItem = strip.items().get(winningIndex);
                int finalGrade = strip.grades().get(winningIndex);
                boolean fallback = giveItem.isEmpty();
                if (fallback) {
                    ItemStack fb = snapshot.gradeMap().findFallback(1);
                    if (fb != null && !fb.isEmpty()) {
                        giveItem = fb;
                        finalGrade = 1;
                        strip.items().set(winningIndex, giveItem.copy());
                        strip.grades().set(winningIndex, finalGrade);
                    }
                }
                out.add(new BulkOpenResult(giveItem, finalGrade, seed, winningIndex, strip.items(), strip.grades(), rng.nextFloat(), fallback));
            } else {
                int g = (pre != null) ? pre.pickGrade(rng) : 1;
                ItemStack s = snapshot.gradeMap().pickRandom(rng, g);
                boolean fallback = s == null;
                if (s == null) {
                    s = snapshot.gradeMap().findFallback(g);
                }
                if (s == null) {
                    s = ItemStack.EMPTY;
                }
                out.add(new BulkOpenResult(s, Mth.clamp(g, 1, 5), 0L, -1, List.of(), List.of(), rng.nextFloat(), fallback));
            }
        }
        return out;
    }

    private static void finalizeBulkOpen(ServerPlayer sp, BulkBoxContext snapshot, int K,
                                         List<BulkOpenResult> results, long requestId,
                                         ItemStack templateBox) {
        if (results == null || results.isEmpty()) {
            CsgoBox.LOGGER.warn("[csgo-bulk] empty results for player={}; consumption cancelled", sp.getName().getString());
            return;
        }

        ResourceLocation keyId = ItemCsgoBox.getKey(templateBox);
        Availability avail = countAvailability(sp, templateBox, keyId);
        int recheckBoxes = avail.boxes;
        int recheckKeys = avail.keys;
        int actualK = Math.min(recheckBoxes, recheckKeys);
        actualK = Math.min(actualK, results.size());
        if (actualK < K) {
            CsgoBox.LOGGER.warn("[csgo-bulk] player {} availability changed during compute: requested={} available={}",
                    sp.getName().getString(), K, actualK);
        }
        if (actualK <= 0) {
            CsgoBox.LOGGER.warn("[csgo-bulk] player {} has no boxes left; aborting without consumption", sp.getName().getString());
            return;
        }

        List<BulkOpenResult> truncated = results.subList(0, actualK);

        if (truncated.stream().allMatch(r -> r.resultItem().isEmpty())) {
            CsgoBox.LOGGER.warn("[csgo-bulk] all {} rolls empty for box={}; aborting without consumption",
                    truncated.size(), snapshot.boxId());
            return;
        }

        for (int i = 0; i < truncated.size(); i++) {
            BulkOpenResult r = truncated.get(i);
            if (!r.fallback() || r.resultItem().isEmpty()) {
                continue;
            }
            int realGrade = PacketCsgoProgress.resolveGrade(r.resultItem(), snapshot.boxId(), r.resultGrade());
            if (realGrade == r.resultGrade()) {
                continue;
            }
            BulkOpenResult fixed = new BulkOpenResult(r.resultItem(), realGrade, r.serverSeed(),
                    r.winningIndex(), r.animationItems(), r.animationGrades(), r.wear(), true);
            truncated.set(i, fixed);
            if (i == 0 && fixed.winningIndex() >= 0 && fixed.winningIndex() < fixed.animationGrades().size()) {
                fixed.animationGrades().set(fixed.winningIndex(), realGrade);
            }
        }

        if (CsgoBox.CONFIG.damageItemByWear()) {
            for (BulkOpenResult r : truncated) {
                if (r.wear() > 0F && r.resultItem().getMaxDamage() > 0) {
                    PacketCsgoProgress.applyWearDamage(r.resultItem(), r.wear());
                    if (!r.animationItems().isEmpty()
                            && r.winningIndex() >= 0
                            && r.winningIndex() < r.animationItems().size()) {
                        ItemStack animWinner = r.animationItems().get(r.winningIndex());
                        if (!animWinner.isEmpty() && animWinner.getMaxDamage() > 0) {
                            PacketCsgoProgress.applyWearDamage(animWinner, r.wear());
                        }
                    }
                }
            }
        }

        if (!PacketCsgoProgress.tryConsumeBoxes(sp, templateBox, actualK)) {
            CsgoBox.LOGGER.error("[csgo-bulk] tryConsumeBoxes failed for player={} want={}; aborting", sp.getName().getString(), actualK);
            return;
        }
        if (!PacketCsgoProgress.tryConsumeKeys(sp, templateBox, actualK)) {
            CsgoBox.LOGGER.error("[csgo-bulk] tryConsumeKeys failed for player={} want={}; refunding boxes", sp.getName().getString(), actualK);
            refundBoxes(sp, snapshot.boxId(), templateBox, actualK);
            return;
        }

        BulkOpenResult box1 = truncated.get(0);
        Networking.sendToPlayer(new PacketBoxOpenResult(
                box1.resultItem().copy(),
                box1.resultGrade(),
                box1.winningIndex(),
                box1.serverSeed(),
                requestId,
                box1.animationItems(),
                box1.animationGrades()
        ), sp);

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
                int chunkSize = PacketBoxBulkResult.BULK_PER_PACKET;
                for (int from = 0; from < restItems.size(); from += chunkSize) {
                    int to = Math.min(from + chunkSize, restItems.size());
                    Networking.sendToPlayer(new PacketBoxBulkResult(
                            requestId,
                            restItems.subList(from, to),
                            restGrades.subList(from, to)
                    ), sp);
                }
            }
        }

        for (BulkOpenResult r : truncated) {
            if (r.resultItem().isEmpty()) {
                continue;
            }
            ItemStack toGive = r.resultItem().copy();
            ItemCsgoBox.setGrade(toGive, r.resultGrade());
            if (!sp.getInventory().add(toGive) && !toGive.isEmpty()) {
                sp.drop(toGive, false);
            }
            BoxOpenedEvent.BUS.post(new BoxOpenedEvent(sp, snapshot.boxId(), r.resultItem().copy(), r.resultGrade(), true));
        }

        sp.awardStat(CsgoBox.OPENED_BOXES_STAT, actualK);
        if (CsgoBox.CONFIG.enableAchievements()) {
            for (int i = 0; i < actualK; i++) {
                OpenedBoxTrigger.INSTANCE.trigger(sp);
            }
        }

        OpenBlockGuard.block(sp.getUUID(), sp.level().getGameTime(), OpenBlockGuard.DEFAULT_COOLDOWN_TICKS);

        if (CsgoBox.debug()) {
            CsgoBox.LOGGER.info("[csgo-bulk] player={} K={} (re-validated from {}) -> {} items granted",
                    sp.getName().getString(), actualK, K, truncated.size());
        }
    }

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
