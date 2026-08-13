package com.reclizer.csgobox.v26_2.packet;

import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.logic.GradeMapCache;
import com.reclizer.csgobox.v26_2.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.v26_2.box.BulkBoxContext;
import com.reclizer.csgobox.v26_2.box.BulkOpenResult;
import com.reclizer.csgobox.box.BoxStripGenerator;
import com.reclizer.csgobox.v26_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_2.event.BoxOpenedEvent;
import com.reclizer.csgobox.v26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_2.item.ItemTerminal;
import com.reclizer.csgobox.logic.GradeMap;
import com.reclizer.csgobox.logic.OpenBlockGuard;
import com.reclizer.csgobox.logic.OddsCalculator;
import com.reclizer.csgobox.v26_2.item.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "csgo_bulk_progress"));

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
            // Strict separation (v1.0.8): terminals are only buyable through
            // the terminal negotiation protocol — never through the classic
            // crate pipeline, which would open them for free (no key, no
            // Armory Points). A crafted packet holding a terminal is refused.
            if (templateBox.getItem() instanceof ItemTerminal) {
                PacketCsgoProgress.sendRejected(context, message.requestId());
                return;
            }
            Identifier boxId = ItemCsgoBox.getBoxId(templateBox);
            BoxDefinition def = boxId == null ? null : BoxRegistry.get(boxId);
            if (def != null && def.isTerminal()) {
                PacketCsgoProgress.sendRejected(context, message.requestId());
                return;
            }
            if (player instanceof ServerPlayer sp && (sp.isRemoved() || !sp.isAlive())) {
                return;
            }
            if (OpenBlockGuard.isBlocked(player.getUUID(), player.level().getGameTime())) {
                PacketCsgoProgress.sendRejected(context, message.requestId());
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

            Availability avail = countAvailability(player, templateBox, ItemCsgoBox.getKey(templateBox));
            int availableBoxes = avail.boxes();
            int availableKeys = avail.keys();
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

            OpenBlockGuard.block(player.getUUID(), player.level().getGameTime(), OpenBlockGuard.DEFAULT_COOLDOWN_TICKS);
            final int requestedK = K;
            final long requestId = message.requestId();
            BulkBoxContext snapshot = new BulkBoxContext(boxId, weights, GradeMapCache.get(boxId.toString(), () -> GradeMap.build(itemList, stack -> !stack.isEmpty(), ItemStack::copy)));

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


    private record Availability(int boxes, int keys) {
    }

    private static Availability countAvailability(Player player, ItemStack box, Identifier keyId) {
        boolean noKey = keyId == null || keyId.equals(Identifier.parse("minecraft:air"));
        boolean countKeys = !noKey && !player.getAbilities().instabuild;
        int boxes = 0;
        int keys = countKeys ? 0 : Integer.MAX_VALUE;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.getItem() instanceof ItemCsgoBox) {
                if (ItemStack.isSameItemSameComponents(stack, box)) {
                    boxes += stack.getCount();
                }
            } else if (countKeys && keyId.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                keys += stack.getCount();
            }
        }
        // Armor + offhand as well so the consume step below does not pull
        // more than the player actually has (or vice versa).
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ItemCsgoBox) {
                if (ItemStack.isSameItemSameComponents(stack, box)) {
                    boxes += stack.getCount();
                }
            } else if (countKeys && keyId.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                keys += stack.getCount();
            }
        }
        return new Availability(boxes, keys);
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
                int g = OddsCalculator.pickGrade(rng, snapshot.weights());
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

        // Re-validate after async compute; inventory might have changed.
        Identifier keyId = ItemCsgoBox.getKey(templateBox);
        Availability avail = countAvailability(sp, templateBox, keyId);
        int recheckBoxes = avail.boxes();
        int recheckKeys = avail.keys();
        int actualK = Math.min(recheckBoxes, recheckKeys);
        // Clamp to the results actually computed: if inventory grew during the
        // async compute we can only open the K boxes already rolled (the rest
        // are reopened on the player's next request).
        actualK = Math.min(actualK, results.size());
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

        // A fully-broken definition (every grade pool empty even after
        // fallback) rolls empty winners for the whole batch. Abort before
        // consuming anything: a bad config must not eat boxes + keys.
        if (truncated.stream().allMatch(r -> r.resultItem().isEmpty())) {
            CsgoBox.LOGGER.warn("[csgo-bulk] all {} rolls empty for box={}; aborting without consumption",
                    truncated.size(), snapshot.boxId());
            return;
        }

        // Resolve the true grade for fallback items (parity with the single-open
        // path, which resolves the fallback winner against BoxDefinition). A
        // fallback item drawn from another grade pool must not keep the picked
        // grade, or its GRADE component and UI colour are wrong. Box 1's
        // animation strip shows the same winner, so patch that slot too.
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

        // Wear-based durability damage, applied on the main thread. The first
        // box's animation strip shares the winner stack, so damage it too for a
        // consistent reveal.
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


        // Now consume exactly actualK boxes + keys.
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
        PacketDistributor.sendToPlayer(sp, new PacketBoxOpenResult(
                box1.resultGrade(),
                box1.winningIndex(),
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
            // Chunked: a single payload must stay small even when every
            // item carries heavy NBT. The client aggregates chunks.
            int chunkSize = PacketBoxBulkResult.BULK_PER_PACKET;
            for (int from = 0; from < restItems.size(); from += chunkSize) {
                int to = Math.min(from + chunkSize, restItems.size());
                PacketDistributor.sendToPlayer(sp, new PacketBoxBulkResult(
                        requestId,
                        restItems.subList(from, to),
                        restGrades.subList(from, to)
                ));
            }
        }

        for (BulkOpenResult r : truncated) {
            if (r.resultItem().isEmpty()) {
                continue;
            }
            ItemStack toGive = r.resultItem().copy();
            toGive.set(ItemCsgoBox.GRADE.get(), r.resultGrade());
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

        // Renew the open cooldown: the block placed at request time (10 ticks)
        // can expire while the async compute is still running, letting a
        // concurrent bulk request slip in against the same inventory. This
        // cannot double-consume (finalize re-checks availability), but it
        // wastes a full batch of rolls.
        OpenBlockGuard.block(sp.getUUID(), sp.level().getGameTime(), OpenBlockGuard.DEFAULT_COOLDOWN_TICKS);

        if (CsgoBox.debug()) {
            CsgoBox.LOGGER.info("[csgo-bulk] player={} K={} (re-validated from {}) -> {} items granted",
                    sp.getName().getString(), actualK, K, truncated.size());
        }
    }

    /** Add boxes back to a player if consumption partially failed. */
    private static void refundBoxes(ServerPlayer sp, Identifier boxId, ItemStack templateBox, int count) {
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
