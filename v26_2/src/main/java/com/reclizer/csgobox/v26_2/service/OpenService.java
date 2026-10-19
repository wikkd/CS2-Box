package com.reclizer.csgobox.v26_2.service;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.box.BoxStripGenerator;
import com.reclizer.csgobox.logic.BoxConstraintTracker;
import com.reclizer.csgobox.logic.GradeMapCache;
import com.reclizer.csgobox.logic.OddsCalculator;
import com.reclizer.csgobox.logic.OpenBlockGuard;
import com.reclizer.csgobox.logic.PityPolicy;
import com.reclizer.csgobox.logic.PityTracker;
import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.v26_2.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.v26_2.box.BulkBoxContext;
import com.reclizer.csgobox.mcshared.box.BulkOpenResult;
import com.reclizer.csgobox.v26_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_2.box.BoxItemResolver;
import com.reclizer.csgobox.v26_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_2.box.GradeGroup;
import com.reclizer.csgobox.v26_2.event.BoxOpeningEvent;
import com.reclizer.csgobox.v26_2.event.BoxOpenedEvent;
import com.reclizer.csgobox.v26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_2.item.ItemTerminal;
import com.reclizer.csgobox.v26_2.item.ModItems;
import com.reclizer.csgobox.v26_2.packet.PacketBoxBulkResult;
import com.reclizer.csgobox.v26_2.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.v26_2.packet.PacketCsgoBulkProgress;
import com.reclizer.csgobox.v26_2.packet.PacketCsgoProgress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Server-side orchestration for the open flows (single open + bulk open).
 * The packets in {@code com.reclizer.csgobox.v26_2.packet} stay
 * transport-only: they decode the request and hand it over here, so the
 * network layer never embeds roll/grant business logic.
 */
public final class OpenService {

    private OpenService() {
    }

    // v2.0.1-hardening: serverSeed comes from a CSPRNG and must NEVER be
    // logged, sent to clients or exposed through events — Random(seed)
    // is a 48-bit LCG; anyone holding the seed can replay the roll.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ------------------------------------------------------------------
    // Single open (PacketCsgoProgress)
    // ------------------------------------------------------------------

    public static void handleSingleOpen(final PacketCsgoProgress message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) {
                return;
            }
            var box = sp.getMainHandItem();
            long requestId = message.requestId();
            if (!(box.getItem() instanceof ItemCsgoBox)) {
                return;
            }
            // Terminals are only buyable via the negotiation protocol; the
            // classic crate pipeline would open them for free. Refuse crafted packets.
            if (box.getItem() instanceof ItemTerminal) {
                sendRejected(context, requestId);
                return;
            }

            if (sp.isRemoved() || !sp.isAlive()) {
                sendRejected(context, requestId);
                return;
            }

            if (OpenBlockGuard.isBlocked(sp.getUUID(), sp.level().getGameTime())) {
                sendRejected(context, requestId);
                return;
            }

            var boxId = ItemCsgoBox.getBoxId(box);
            if (boxId == null) {
                sendRejected(context, requestId);
                return;
            }
            // Same guard for a box_id pointing at a terminal from a plain box stack.
            BoxDefinition def = BoxRegistry.get(boxId);
            if (def != null && def.isTerminal()) {
                sendRejected(context, requestId);
                return;
            }

            // Mods may veto the open before any roll or consumption.
            BoxOpeningEvent opening = new BoxOpeningEvent(sp, boxId, false, 1);
            NeoForge.EVENT_BUS.post(opening);
            if (opening.isCanceled()) {
                sendRejected(context, requestId);
                return;
            }

            int[] weights = ItemCsgoBox.getRandom(box);
            if (weights.length == 0 || !BoxOdds.hasOpenableWeights(weights)) {
                sendRejected(context, requestId);
                return;
            }

            long serverSeed = SECURE_RANDOM.nextLong();
            var rng = new Random(serverSeed);

            // Grade pool is immutable per box id (shared cache with the bulk
            // path, invalidated on reload). v2.0.1 builds the pool with
            // per-item weights. pickRandom returns copies, so callers may
            // mutate freely.
            var gradeMap = GradeMapCache.get(boxId.toString(),
                    () -> ItemCsgoBox.buildGradeMap(box));
            if (gradeMap.isEmpty()) {
                sendRejected(context, requestId);
                return;
            }

            // v2.0.2-fix: explicit null guard — this used to lean on the
            // gradeMap check above happening to reject unbound boxes first
            // (fragile order). An unbound box is refused outright, no
            // def-dependent message.
            if (def == null) {
                sendRejected(context, requestId);
                return;
            }
            // v2.0.1 constraints (in-memory, server-authoritative): per-player
            // open cap and per-box cooldown are checked before the roll so a
            // capped player never wastes a key.
            if (!BoxConstraintTracker.underPerPlayerCap(sp.getStringUUID(), boxId.toString(), def.maxPerPlayer())) {
                sp.sendSystemMessage(Component.translatable(
                        "commands.csgobox.constraint.capped", def.name(), def.maxPerPlayer()));
                sendRejected(context, requestId);
                return;
            }
            if (!BoxConstraintTracker.cooldownElapsed(
                    sp.getStringUUID(), boxId.toString(), def.cooldownSeconds(), sp.level().getGameTime())) {
                sp.sendSystemMessage(Component.translatable(
                        "commands.csgobox.constraint.cooldown", def.name()));
                sendRejected(context, requestId);
                return;
            }
            if (!def.permission().isBlank() && !CsgoBox.PERMISSION_GATE.test(sp, def.permission())) {
                sp.sendSystemMessage(Component.translatable(
                        "commands.csgobox.constraint.permission", def.name()));
                sendRejected(context, requestId);
                return;
            }

            // v2.0.1 pity (保底): snapshot the per-player miss streak before
            // the roll; a forced roll replaces the winning slot below.
            PityPolicy pity = def != null ? def.pity().orElse(null) : null;
            int pityStreak = pity != null
                    ? PityTracker.missStreak(sp.getStringUUID(), boxId.toString())
                    : 0;

            var strip = BoxStripGenerator.generate(gradeMap, weights, rng, ItemStack.EMPTY);
            int winningIndex = strip.winningIndex();
            if (winningIndex < 0) {
                sendRejected(context, requestId);
                return;
            }

            ItemStack giveItem = strip.items().get(winningIndex);
            int finalGrade = strip.grades().get(winningIndex);
            // v2.0.1-fix(B): pity counts the ROLLED grade, never the resolved
            // (post-fallback) one — a forced roll that lands on the target
            // grade must reset the streak even when the item pool falls back
            // to a lower-tier item.
            int pityRollGrade = finalGrade;

            // v2.0.1 pity: replace the winning slot with a forced roll from
            // [targetLevel..5] when the miss streak reached the threshold.
            // pickGradeWithPity returns forced=false when the pity slice has
            // no positive weight (misconfigured) — the plain roll stands.
            if (pity != null && pity.shouldForce(pityStreak)) {
                OddsCalculator.PityResult pityRoll =
                        OddsCalculator.pickGradeWithPity(rng, weights, pity, pityStreak);
                if (pityRoll.forced()) {
                    ItemStack pityItem = gradeMap.pickRandom(rng, pityRoll.grade());
                    if (pityItem == null) {
                        pityItem = gradeMap.findFallback(pityRoll.grade());
                    }
                    if (pityItem != null) {
                        giveItem = pityItem;
                        pityRollGrade = Math.min(Math.max(pityRoll.grade(), 1), 5);
                        finalGrade = resolveGrade(giveItem, boxId, pityRoll.grade());
                        strip.items().set(winningIndex, giveItem.copy());
                        strip.grades().set(winningIndex, finalGrade);
                    }
                }
            }

            if (giveItem.isEmpty()) {
                giveItem = gradeMap.findFallback(1);
                if (giveItem == null) giveItem = ItemStack.EMPTY;
                if (giveItem.isEmpty()) {
                    sendRejected(context, requestId);
                    return;
                }
                finalGrade = resolveGrade(giveItem, boxId, 1);
                strip.items().set(winningIndex, giveItem.copy());
                strip.grades().set(winningIndex, finalGrade);
            }

            // v2.0.1: resolve count-range / random-enchant / loot-table specs
            // on the winning item BEFORE keys are consumed (a broken loot
            // table must never eat a key).
            giveItem = BoxItemResolver.resolve(giveItem, sp.level(), rng);
            if (giveItem.isEmpty()) {
                sendRejected(context, requestId);
                return;
            }

            // Consume keys only after the whole roll is validated — a broken
            // definition must never eat a key.
            if (!tryConsumeKeys(sp, box, 1)) {
                sendRejected(context, requestId);
                return;
            }

            float wear = 0F;
            if (CsgoBox.CONFIG.damageItemByWear() && giveItem.getMaxDamage() > 0) {
                wear = rng.nextFloat();
                applyWearDamage(giveItem, wear);
            }

            OpenBlockGuard.block(sp.getUUID(), sp.level().getGameTime(), OpenBlockGuard.DEFAULT_COOLDOWN_TICKS);

            // v2.2.0-fix: sync the resolved (and wear-damaged) winner back into
            // the animation strip so the client reveal always matches the
            // granted item (count-range / random-enchant / loot-table / wear).
            // The plain roll shares the strip reference, but pity/fallback
            // branches replace the winner with a fresh copy — mirror the bulk
            // path (finalizeBulkOpen) and make it unconditional.
            strip.items().set(winningIndex, giveItem.copy());

            context.reply(new PacketBoxOpenResult(
                    finalGrade,
                    winningIndex,
                    requestId,
                    strip.items(),
                    strip.grades()
            ));

            ItemStack toGive = giveItem.copy();
            toGive.set(ItemCsgoBox.GRADE.get(), finalGrade);
            boolean added = sp.getInventory().add(toGive);
            if (!added && !toGive.isEmpty()) {
                sp.drop(toGive, false);
            }
            // Creative mode is fully free (parity with tryConsumeKeys).
            if (!sp.getAbilities().instabuild) {
                box.shrink(1);
            }

            // Record the successful open for max_per_player / cooldown.
            BoxConstraintTracker.recordOpen(sp.getStringUUID(), boxId.toString(), sp.level().getGameTime());

            // v2.0.1 pity: advance the miss streak only after the item was
            // actually given (rejected/aborted opens never count). Uses the
            // ROLLED grade (pityRollGrade), not the resolved/final one.
            if (pity != null) {
                PityTracker.recordOpen(sp.getStringUUID(), boxId.toString(),
                        pityRollGrade, pity.targetLevel());
            }

            sp.awardStat(CsgoBox.OPENED_BOXES_STAT, 1);
            if (CsgoBox.CONFIG.enableAchievements()) {
                OpenedBoxTrigger.INSTANCE.trigger(sp, finalGrade);
            }

            NeoForge.EVENT_BUS.post(new BoxOpenedEvent(sp, boxId, giveItem.copy(), finalGrade, false));
        });
    }

    /** Replies an empty animation result; the client treats it as a rejected open. */
    private static void sendRejected(IPayloadContext context, long requestId) {
        context.reply(new PacketBoxOpenResult(
                1,
                0,
                requestId,
                List.of(),
                List.of()
        ));
    }

    // ------------------------------------------------------------------
    // Bulk open (PacketCsgoBulkProgress)
    // ------------------------------------------------------------------

    public static void handleBulkOpen(final PacketCsgoBulkProgress message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null) {
                return;
            }
            ItemStack templateBox = player.getMainHandItem();
            if (!(templateBox.getItem() instanceof ItemCsgoBox)) {
                return;
            }
            // Strict separation (v2.0.0): terminals are only buyable through
            // the terminal negotiation protocol — never through the classic
            // crate pipeline, which would open them for free (no key, no
            // Armory Points). A crafted packet holding a terminal is refused.
            if (templateBox.getItem() instanceof ItemTerminal) {
                sendRejected(context, message.requestId());
                return;
            }
            Identifier boxId = ItemCsgoBox.getBoxId(templateBox);
            if (boxId == null) {
                sendRejected(context, message.requestId());
                return;
            }
            BoxDefinition def = boxId == null ? null : BoxRegistry.get(boxId);
            if (def != null && def.isTerminal()) {
                sendRejected(context, message.requestId());
                return;
            }
            if (player instanceof ServerPlayer sp && (sp.isRemoved() || !sp.isAlive())) {
                return;
            }
            if (OpenBlockGuard.isBlocked(player.getUUID(), player.level().getGameTime())) {
                sendRejected(context, message.requestId());
                return;
            }

            // Fetch the cached grade pool; the costly per-item deep-copy build
            // (ItemCsgoBox.buildGradeMap) only runs on a cache miss, not on every
            // bulk request. v2.0.1: pool carries per-item weights.
            var gradeMap = GradeMapCache.get(boxId.toString(),
                    () -> ItemCsgoBox.buildGradeMap(templateBox));
            if (gradeMap.isEmpty()) {
                // v2.0.1-fix(empty-box bulk): an unbound/empty crate is
                // unopenable — refuse explicitly so the client does not wait
                // on the bulk progress screen forever (server is authoritative).
                sendRejected(context, message.requestId());
                return;
            }
            int[] weights = ItemCsgoBox.getRandom(templateBox);
            if (weights.length == 0 || !BoxOdds.hasOpenableWeights(weights)) {
                // v2.0.1-fix(all-zero weights): unopenable crate — refuse.
                sendRejected(context, message.requestId());
                return;
            }

            // v2.0.1 constraints (batch-level): a batch may never exceed the
            // player's remaining per-player allowance, and the per-box
            // cooldown is checked once for the whole batch.
            if (def != null && !BoxConstraintTracker.underPerPlayerCap(
                    player.getStringUUID(), boxId.toString(), def.maxPerPlayer())) {
                if (player instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.translatable(
                            "commands.csgobox.constraint.capped", def.name(), def.maxPerPlayer()));
                }
                sendRejected(context, message.requestId());
                return;
            }
            if (def != null && !BoxConstraintTracker.cooldownElapsed(
                    player.getStringUUID(), boxId.toString(), def.cooldownSeconds(), player.level().getGameTime())) {
                if (player instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.translatable(
                            "commands.csgobox.constraint.cooldown", def.name()));
                }
                sendRejected(context, message.requestId());
                return;
            }
            if (def != null && !def.permission().isBlank()
                    && !CsgoBox.PERMISSION_GATE.test((ServerPlayer) player, def.permission())) {
                if (player instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.translatable(
                            "commands.csgobox.constraint.permission", def.name()));
                }
                sendRejected(context, message.requestId());
                return;
            }

            Availability avail = countAvailability(player, templateBox, ItemCsgoBox.getKey(templateBox));
            int availableBoxes = avail.boxes();
            int availableKeys = avail.keys();
            int K = Math.min(availableBoxes, availableKeys);
            if (K <= 0) {
                sendRejected(context, message.requestId());
                return;
            }
            // Server-enforced per-open cap (0 = unlimited). The client overview
            // screen clamps its estimate to the same value, but the server is
            // authoritative: a crafted packet can never open more than this.
            int limit = CsgoBox.CONFIG.bulkOpenCount();
            if (limit > 0) {
                K = Math.min(K, limit);
            }
            // v2.0.1-fix(max_per_player): the batch is clamped to the player's
            // remaining per-player allowance, not merely boolean-checked — a
            // hoarded stack could otherwise drain the whole quota in one ask.
            if (def != null && def.maxPerPlayer() > 0) {
                int allowance = def.maxPerPlayer()
                        - BoxConstraintTracker.openCount(player.getStringUUID(), boxId.toString());
                K = Math.min(K, Math.max(0, allowance));
            }
            if (K <= 0) {
                sendRejected(context, message.requestId());
                return;
            }

            // Policy hook (batch-level): scripts may veto the whole batch
            // before any roll or consumption. count = K (the server-authorized
            // batch size). Canceling aborts the batch without setting cooldown.
            BoxOpeningEvent opening = new BoxOpeningEvent(player, boxId, true, K);
            NeoForge.EVENT_BUS.post(opening);
            if (opening.isCanceled()) {
                sendRejected(context, message.requestId());
                return;
            }

            OpenBlockGuard.block(player.getUUID(), player.level().getGameTime(), OpenBlockGuard.DEFAULT_COOLDOWN_TICKS);
            final int requestedK = K;
            final long requestId = message.requestId();
            BulkBoxContext snapshot = new BulkBoxContext(boxId, weights, gradeMap);

            // v2.0.1 pity (保底): snapshot the shared streak once for the whole
            // batch; computeKResults advances a batch-local copy so concurrent
            // single opens cannot corrupt the sequence, and finalizeBulkOpen
            // writes the final streak back only after the grant succeeded.
            PityPolicy pity = def != null ? def.pity().orElse(null) : null;
            final int pityStreak = pity != null
                    ? PityTracker.startStreak(player.getStringUUID(), boxId.toString())
                    : 0;

            final Player playerFinal = player;
            try {
                CompletableFuture
                        .supplyAsync(() -> {
                            try {
                                return computeKResults(snapshot, requestedK, pityStreak, pity);
                            } catch (Throwable t) {
                                CsgoBox.LOGGER.error("[csgo-bulk] computeKResults failed: K={} box={}", requestedK, boxId, t);
                                return List.<BulkOpenResult>of();
                            }
                        }, CsgoBox.BULK_COMPUTE_POOL)
                        .thenAccept(results -> {
                            if (playerFinal instanceof ServerPlayer sp && !sp.isRemoved() && sp.isAlive()) {
                                sp.level().getServer().execute(() -> {
                                    try {
                                        finalizeBulkOpen(sp, snapshot, requestedK, results, requestId, templateBox, pityStreak, pity);
                                    } catch (Throwable t) {
                                        CsgoBox.LOGGER.error("[csgo-bulk] finalizeBulkOpen failed: player={} K={}",
                                                sp.getName().getString(), requestedK, t);
                                    }
                                });
                            } else if (playerFinal instanceof ServerPlayer sp) {
                                CsgoBox.LOGGER.warn("[csgo-bulk] no finalize for player {} (dead/logged-out)", sp.getName().getString());
                            }
                        });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // v2.0.2-fix: the bounded queue overflowed — instead of dropping
                // silently (client would wait out the full 10s screen timeout),
                // reject the batch like every other refusal path. Nothing was
                // consumed: OpenBlockGuard is still the only side effect.
                CsgoBox.LOGGER.warn("[csgo-bulk] compute queue full, batch rejected (player={} K={})", player.getName().getString(), requestedK);
                sendRejected(context, message.requestId());
            }
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
     * {@code snapshot} only (read-only). v2.0.1: {@code initialStreak} and
     * {@code pity} drive the batch-local pity sequence (each box may force a
     * roll at/above the target when the streak reached the threshold).
     */
    private static List<BulkOpenResult> computeKResults(BulkBoxContext snapshot, int K,
                                                        int initialStreak, PityPolicy pity) {
        List<BulkOpenResult> out = new ArrayList<>(K);
        // Precompute the weight table once for the whole batch so the K-1
        // follow-up rolls don't each rebuild it (pickGrade(int[]) re-scans).
        OddsCalculator.Precomputed pre = OddsCalculator.precomputeWeights(snapshot.weights());
        int streak = initialStreak;
        for (int i = 0; i < K; i++) {
            // v2.0.1-fix(F): SecureRandom seeds (parity with the single-open
            // path) keep the server-authoritative RNG outside predictable
            // ThreadLocalRandom state.
            long seed = SECURE_RANDOM.nextLong();
            Random rng = new Random(seed);
            if (i == 0) {
                var strip = BoxStripGenerator.generate(snapshot.gradeMap(), snapshot.weights(), rng, ItemStack.EMPTY);
                int winningIndex = Math.max(0, strip.winningIndex());
                ItemStack giveItem = strip.items().get(winningIndex);
                int finalGrade = strip.grades().get(winningIndex);
                // v2.0.1-fix(B): pity counts the ROLLED grade, never the
                // resolved (post-fallback) one.
                int pityRollGrade = finalGrade;
                boolean fallback = giveItem.isEmpty();
                // v2.0.1 pity: patch the winning slot when the threshold is met.
                if (pity != null && pity.shouldForce(streak)) {
                    OddsCalculator.PityResult pr = OddsCalculator.pickGradeWithPity(
                            rng, snapshot.weights(), pity, streak);
                    if (pr.forced()) {
                        ItemStack pityItem = snapshot.gradeMap().pickRandom(rng, pr.grade());
                        if (pityItem == null) {
                            pityItem = snapshot.gradeMap().findFallback(pr.grade());
                        }
                        if (pityItem != null && !pityItem.isEmpty()) {
                            giveItem = pityItem;
                            pityRollGrade = Mth.clamp(pr.grade(), 1, 5);
                            finalGrade = Mth.clamp(pr.grade(), 1, 5);
                            strip.items().set(winningIndex, giveItem.copy());
                            strip.grades().set(winningIndex, finalGrade);
                            fallback = false;
                        }
                    }
                }
                if (fallback) {
                    ItemStack fb = snapshot.gradeMap().findFallback(1);
                    if (fb != null && !fb.isEmpty()) {
                        giveItem = fb;
                        finalGrade = 1;
                        strip.items().set(winningIndex, giveItem.copy());
                        strip.grades().set(winningIndex, finalGrade);
                    }
                }
                float wear = rng.nextFloat();
                out.add(new BulkOpenResult(giveItem, finalGrade, seed, winningIndex, strip.items(), strip.grades(), wear, fallback, pityRollGrade));
                // v2.0.1-fix(D): only granted (non-empty) results advance the
                // batch-local streak, matching finalizeBulkOpen's write-back.
                if (pity != null && !giveItem.isEmpty()) {
                    streak = pity.nextStreak(streak, pityRollGrade);
                }
            } else {
                int g;
                if (pity != null && pity.shouldForce(streak)) {
                    OddsCalculator.PityResult pr = OddsCalculator.pickGradeWithPity(
                            rng, snapshot.weights(), pity, streak);
                    g = pr.grade();
                } else {
                    g = (pre != null) ? pre.pickGrade(rng) : 1;
                }
                ItemStack s = snapshot.gradeMap().pickRandom(rng, g);
                boolean fallback = s == null;
                if (s == null) {
                    s = snapshot.gradeMap().findFallback(g);
                }
                if (s == null) {
                    s = ItemStack.EMPTY;
                }
                // v2.0.1-fix: every follow-up result needs its OWN seed — a
                // constant 0L made the spec resolve (count-range / enchant /
                // loot-table) in finalizeBulkOpen deterministic and identical
                // for every non-first item across players and batches.
                int rolledGrade = Mth.clamp(g, 1, 5);
                out.add(new BulkOpenResult(s, rolledGrade, SECURE_RANDOM.nextLong(), -1, List.of(), List.of(), rng.nextFloat(), fallback, rolledGrade));
                // v2.0.1-fix(D): empty results never advance the streak.
                if (pity != null && !s.isEmpty()) {
                    streak = pity.nextStreak(streak, rolledGrade);
                }
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
                                         ItemStack templateBox, int initialStreak,
                                         PityPolicy pity) {
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
            int realGrade = resolveGrade(r.resultItem(), snapshot.boxId(), r.resultGrade());
            if (realGrade == r.resultGrade()) {
                continue;
            }
            BulkOpenResult fixed = new BulkOpenResult(r.resultItem(), realGrade, r.serverSeed(),
                    r.winningIndex(), r.animationItems(), r.animationGrades(), r.wear(), true, r.pityGrade());
            truncated.set(i, fixed);
            if (i == 0 && fixed.winningIndex() >= 0 && fixed.winningIndex() < fixed.animationGrades().size()) {
                fixed.animationGrades().set(fixed.winningIndex(), realGrade);
            }
        }

        // v2.0.1: resolve count-range / random-enchant / loot-table specs on
        // every result, on the main thread (the resolver needs a ServerLevel).
        // A roll that resolves to empty is dropped from the batch (the box +
        // key are still consumed for it — the pool entry existed, the table
        // just drew nothing or was broken).
        for (int i = 0; i < truncated.size(); i++) {
            BulkOpenResult r = truncated.get(i);
            if (r.resultItem().isEmpty()) {
                continue;
            }
            ItemStack resolved = BoxItemResolver.resolve(
                    r.resultItem(), sp.level(), new Random(r.serverSeed() ^ 0x5DEECE66DL));
            if (!resolved.isEmpty()) {
                BulkOpenResult fixed = new BulkOpenResult(resolved, r.resultGrade(), r.serverSeed(),
                        r.winningIndex(), r.animationItems(), r.animationGrades(), r.wear(), r.fallback(), r.pityGrade());
                truncated.set(i, fixed);
                if (i == 0 && fixed.winningIndex() >= 0 && fixed.winningIndex() < fixed.animationItems().size()) {
                    fixed.animationItems().set(fixed.winningIndex(), resolved.copy());
                }
            }
        }

        // Wear-based durability damage, applied on the main thread. The first
        // box's animation strip shares the winner stack, so damage it too for a
        // consistent reveal.
        if (CsgoBox.CONFIG.damageItemByWear()) {
            for (BulkOpenResult r : truncated) {
                if (r.wear() > 0F && r.resultItem().getMaxDamage() > 0) {
                    applyWearDamage(r.resultItem(), r.wear());
                    if (!r.animationItems().isEmpty()
                            && r.winningIndex() >= 0
                            && r.winningIndex() < r.animationItems().size()) {
                        ItemStack animWinner = r.animationItems().get(r.winningIndex());
                        if (!animWinner.isEmpty() && animWinner.getMaxDamage() > 0) {
                            applyWearDamage(animWinner, r.wear());
                        }
                    }
                }
            }
        }

        // Now consume exactly actualK boxes + keys.
        if (!tryConsumeBoxes(sp, templateBox, actualK)) {
            CsgoBox.LOGGER.error("[csgo-bulk] tryConsumeBoxes failed for player={} want={}; aborting", sp.getName().getString(), actualK);
            return;
        }
        if (!tryConsumeKeys(sp, templateBox, actualK)) {
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
            if (!restItems.isEmpty()) {
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

        // v2.0.1: record successful opens for max_per_player / cooldown
        // (one entry per granted item).
        long now = sp.level().getGameTime();
        for (BulkOpenResult r : truncated) {
            if (r.resultItem().isEmpty()) {
                continue;
            }
            BoxConstraintTracker.recordOpen(sp.getStringUUID(), snapshot.boxId().toString(), now);
        }

        // v2.0.1 pity: write the final streak back only after the grant
        // succeeded (a cancelled/aborted batch must not advance pity). The
        // streak is recomputed from the ROLLED grades (pityGrade) of the
        // granted results, so a forced roll that fell back to a lower-tier
        // item still counts as a hit; empty results never advance.
        if (pity != null) {
            int finalStreak = initialStreak;
            for (BulkOpenResult r : truncated) {
                if (r.resultItem().isEmpty()) {
                    continue;
                }
                finalStreak = pity.nextStreak(finalStreak, r.pityGrade());
            }
            // v2.0.2-fix(lost update): write the batch's net delta, not an
            // absolute value — a concurrent single open inside the async
            // window must not be erased by the write-back.
            PityTracker.applyStreakDelta(sp.getStringUUID(), snapshot.boxId().toString(),
                    finalStreak - initialStreak);
        }

        sp.awardStat(CsgoBox.OPENED_BOXES_STAT, actualK);
        if (CsgoBox.CONFIG.enableAchievements()) {
            // Grade-aware: pass each result's grade through so the grade-line
            // advancements can match; pad with grade 0 to keep the historical
            // call count (one trigger per box) intact for count criteria.
            int fired = 0;
            for (BulkOpenResult r : truncated) {
                if (r.resultItem().isEmpty()) {
                    continue;
                }
                OpenedBoxTrigger.INSTANCE.trigger(sp, r.resultGrade());
                fired++;
            }
            for (; fired < actualK; fired++) {
                OpenedBoxTrigger.INSTANCE.trigger(sp, 0);
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

    // ------------------------------------------------------------------
    // Shared helpers (key/box consumption, grade resolution, wear)
    // ------------------------------------------------------------------

    /** Damages a stack by wear (0..1) × max durability; clamped to never break. */
    static void applyWearDamage(ItemStack stack, float wear) {
        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) {
            return;
        }
        int damage = Math.max(0, Math.min(Math.round(wear * maxDamage), maxDamage - 1));
        stack.set(DataComponents.DAMAGE, damage);
    }

    /** Resolves the grade (1..5) of a fallback item against the box definition. */
    private static int resolveGrade(ItemStack item, Identifier boxId, int fallback) {
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def != null) {
            for (GradeGroup grade : def.grades()) {
                int gradeLevel = BoxGrades.gradeLevel(grade.id());
                if (gradeLevel == 0) continue;
                for (ItemStack candidate : grade.items()) {
                    if (ItemStack.isSameItemSameComponents(item, candidate)) {
                        return Mth.clamp(gradeLevel, 1, 5);
                    }
                }
            }
        }
        return Mth.clamp(fallback, 1, 5);
    }

    /**
     * Consume up to {@code count} keys from anywhere (items, armor, offhand);
     * true only when fully consumed (or no key required). 26.x has no public
     * armor/offhand list — they are walked via {@code getItemBySlot}.
     */
    private static boolean tryConsumeKeys(Player entity, ItemStack box, int count) {
        Identifier keyId = ItemCsgoBox.getKey(box);
        if (keyId == null || keyId.equals(Identifier.parse("minecraft:air"))) {
            return true;
        }
        if (count <= 0) {
            return true;
        }
        if (entity.getAbilities().instabuild) {
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

    /** Consume up to {@code count} boxes matching the template from anywhere; true when fully consumed. */
    private static boolean tryConsumeBoxes(Player entity, ItemStack box, int count) {
        if (count <= 0) {
            return true;
        }
        if (entity.getAbilities().instabuild) {
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

    /** Shrinks matching stacks until the count is satisfied or the slice exhausted; returns what's left. */
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
                // Never consume boxes as keys: ItemCsgoBox.getKey(box) returns
                // the box's own key id, so a plain id match would shrink boxes
                // too (a past double-count bug).
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
        // Same as consumeFromList: boxes must never match as keys.
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
