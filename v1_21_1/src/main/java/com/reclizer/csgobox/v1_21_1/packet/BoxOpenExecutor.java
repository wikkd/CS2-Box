package com.reclizer.csgobox.v1_21_1.packet;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.box.BoxStripGenerator;
import com.reclizer.csgobox.logic.BoxConstraintTracker;
import com.reclizer.csgobox.logic.GradeMapCache;
import com.reclizer.csgobox.logic.OddsCalculator;
import com.reclizer.csgobox.logic.OpenBlockGuard;
import com.reclizer.csgobox.logic.PityPolicy;
import com.reclizer.csgobox.logic.PityTracker;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxItemResolver;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.event.BoxOpeningEvent;
import com.reclizer.csgobox.v1_21_1.event.BoxOpenedEvent;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ItemTerminal;
import com.reclizer.csgobox.v1_21_1.item.ModItems;
import com.reclizer.csgobox.v1_21_1.box.BulkBoxContext;
import com.reclizer.csgobox.mcshared.box.BulkOpenResult;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * v2.2.0: headless server-side box opening. The single-open packet pipeline
 * ({@link PacketCsgoProgress}) and the Create deployer compat
 * ({@code create.DeployerBoxOpen}) share this executor so a mechanical deployer
 * holding the box's key opens a crate sitting on a Create belt/depot with the
 * exact same rules, constraints, pity and events as a manual open.
 *
 * <p>Failure returns {@code null} and leaves the crate/keys untouched; success
 * consumes the key (unless creative) and stamps/grants (or returns) the prize.
 * The caller owns crate consumption: {@code consumeBox} shrinks the box stack
 * when it lives in the player's hand (classic path), while the deployer path
 * removes/replaces the crate on the platform itself.</p>
 */
public final class BoxOpenExecutor {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Outcome of a successful open, in the shape both callers need. */
    public record Outcome(
            int grade,
            int winningIndex,
            List<ItemStack> animationItems,
            List<Integer> animationGrades,
            /** Prize stamped with csgobox:grade (what gets granted / placed on the platform). */
            ItemStack giveItem
    ) {
    }

    private BoxOpenExecutor() {
    }

    /**
     * Runs the full server-side open pipeline. {@code giveToPlayer} decides where
     * the prize goes (player inventory/drop vs returned in the outcome for the
     * deployer platform-replacement path); {@code consumeBox} decides whether the
     * box stack itself is shrunk (hand-held open) or left for the caller.
     *
     * @return outcome on success, or {@code null} when the open was refused
     *         (guard, cancelled event, constraints, bad definition, missing key …)
     */
    public static Outcome execute(Player player, ItemStack box, boolean giveToPlayer, boolean consumeBox) {
        return execute(player, box, giveToPlayer, consumeBox, null);
    }

    /**
     * {@link #execute(Player, ItemStack, boolean, boolean)} with an explicit
     * tracker identity. Create deployers run through a single shared
     * FakePlayer (fixed UUID per loader), so the open cooldown / per-player
     * caps / pity streaks would be shared by every deployer on the server;
     * {@code create.DeployerBoxOpen} passes {@code "fake:<dim>:<pos>"} so
     * each deployer is isolated. Real players pass {@code null} and use
     * their real UUID. The string is hashed into a stable UUID so all three
     * trackers ({@link OpenBlockGuard}, {@link BoxConstraintTracker},
     * {@link PityTracker}) key identically.
     */
    public static Outcome execute(Player player, ItemStack box, boolean giveToPlayer, boolean consumeBox,
                                  String trackerKeyOverride) {
        return execute(player, box, giveToPlayer, consumeBox, trackerKeyOverride, true);
    }

    /**
     * {@link #execute(Player, ItemStack, boolean, boolean, String)} with key
     * consumption made optional. Create's deployer recipe path
     * ({@code DeployerBoxOpenRecipeSearch}) passes {@code consumeKey=false}:
     * Create owns the held-item consumption there, so the executor must not
     * also shrink a key from the fake player's inventory.
     */
    public static Outcome execute(Player player, ItemStack box, boolean giveToPlayer, boolean consumeBox,
                                  String trackerKeyOverride, boolean consumeKey) {
        // v2.2.0-fix: fake-player isolation (Create deployer compat).
        String trackerKey = trackerKeyOverride != null ? trackerKeyOverride : player.getStringUUID();
        UUID guardKey = UUID.nameUUIDFromBytes(("csgobox|" + trackerKey).getBytes(StandardCharsets.UTF_8));

        if (player.isRemoved() || !player.isAlive()) {
            return null;
        }
        if (OpenBlockGuard.isBlocked(guardKey, player.level().getGameTime())) {
            return null;
        }

        ResourceLocation boxId = ItemCsgoBox.getBoxId(box);
        if (boxId == null) {
            return null;
        }
        BoxDefinition def = BoxRegistry.get(boxId);
        // Same guard for a box_id pointing at a terminal from a plain box stack.
        if (def != null && def.isTerminal()) {
            return null;
        }

        // Mods may veto the open before any roll or consumption.
        BoxOpeningEvent opening = new BoxOpeningEvent(player, boxId, false, 1);
        NeoForge.EVENT_BUS.post(opening);
        if (opening.isCanceled()) {
            return null;
        }

        int[] weights = ItemCsgoBox.getRandom(box);
        if (weights.length == 0 || !BoxOdds.hasOpenableWeights(weights)) {
            return null;
        }

        // v2.0.1-hardening: serverSeed comes from a CSPRNG and must NEVER be
        // logged, sent to clients or exposed through events — Random(seed)
        // is a 48-bit LCG; anyone holding the seed can replay the roll.
        long serverSeed = SECURE_RANDOM.nextLong();
        var rng = new Random(serverSeed);

        // Grade pool is immutable per box id (shared cache with the bulk
        // path, invalidated on reload). v2.0.1 builds the pool with
        // per-item weights. pickRandom returns copies, so callers may
        // mutate freely.
        var gradeMap = GradeMapCache.get(boxId.toString(),
                () -> ItemCsgoBox.buildGradeMap(box));
        if (gradeMap.isEmpty()) {
            return null;
        }

        // v2.0.1 constraints (in-memory, server-authoritative): per-player
        // open cap and per-box cooldown are checked before the roll so a
        // capped player never wastes a key. Unknown definitions have no
        // constraints (parity with the pre-v2.2.0 packet path).
        if (def != null) {
            if (!BoxConstraintTracker.underPerPlayerCap(
                    trackerKey, boxId.toString(), def.maxPerPlayer())) {
                player.sendSystemMessage(Component.translatable(
                        "commands.csgobox.constraint.capped", def.name(), def.maxPerPlayer()));
                return null;
            }
            if (!BoxConstraintTracker.cooldownElapsed(
                    trackerKey, boxId.toString(), def.cooldownSeconds(), player.level().getGameTime())) {
                player.sendSystemMessage(Component.translatable(
                        "commands.csgobox.constraint.cooldown", def.name()));
                return null;
            }
            if (!def.permission().isBlank()
                    && player instanceof ServerPlayer sp
                    && !CsgoBox.PERMISSION_GATE.test(sp, def.permission())) {
                player.sendSystemMessage(Component.translatable(
                        "commands.csgobox.constraint.permission", def.name()));
                return null;
            }
        }

        // v2.0.1 pity (保底): snapshot the per-player miss streak before
        // the roll; a forced roll replaces the winning slot below.
        PityPolicy pity = def != null ? def.pity().orElse(null) : null;
        int pityStreak = pity != null
                ? PityTracker.missStreak(trackerKey, boxId.toString())
                : 0;

        var strip = BoxStripGenerator.generate(gradeMap, weights, rng, ItemStack.EMPTY);
        int winningIndex = strip.winningIndex();
        if (winningIndex < 0) {
            return null;
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
                    finalGrade = PacketCsgoProgress.resolveGrade(giveItem, boxId, pityRoll.grade());
                    strip.items().set(winningIndex, giveItem.copy());
                    strip.grades().set(winningIndex, finalGrade);
                }
            }
        }

        if (giveItem.isEmpty()) {
            giveItem = gradeMap.findFallback(1);
            if (giveItem == null) giveItem = ItemStack.EMPTY;
            if (giveItem.isEmpty()) {
                return null;
            }
            finalGrade = PacketCsgoProgress.resolveGrade(giveItem, boxId, 1);
            strip.items().set(winningIndex, giveItem.copy());
            strip.grades().set(winningIndex, finalGrade);
        }

        // v2.0.1: resolve count-range / random-enchant / loot-table specs
        // on the winning item BEFORE keys are consumed (a broken loot
        // table must never eat a key).
        if (player instanceof ServerPlayer sp) {
            giveItem = BoxItemResolver.resolve(giveItem, sp.serverLevel(), rng);
        }
        if (giveItem == null || giveItem.isEmpty()) {
            return null;
        }

        // Consume keys (from anywhere: items, armor, offhand) only after the
        // whole roll is validated — a broken definition must never eat a key.
        // Create's deployer recipe path disables this (Create consumes the
        // deployer's held item itself).
        if (consumeKey && !PacketCsgoProgress.tryConsumeKeys(player, box, 1)) {
            return null;
        }

        float wear = 0F;
        if (CsgoBox.CONFIG.damageItemByWear() && giveItem.getMaxDamage() > 0) {
            wear = rng.nextFloat();
            PacketCsgoProgress.applyWearDamage(giveItem, wear);
        }

        // v2.2.0-fix: sync the resolved (and wear-damaged) winner back into the
        // animation strip so the client reveal always matches the granted item
        // exactly (count-range / random-enchant / loot-table / wear). The plain
        // roll shares the strip reference and would self-sync, but pity/fallback
        // branches replace the winner with a fresh copy — mirror the bulk path
        // (finalizeBulkOpen) and make it unconditional.
        strip.items().set(winningIndex, giveItem.copy());

        OpenBlockGuard.block(guardKey, player.level().getGameTime(), OpenBlockGuard.DEFAULT_COOLDOWN_TICKS);

        ItemStack stampedGive = giveItem.copy();
        stampedGive.set(ItemCsgoBox.GRADE.get(), finalGrade);

        if (giveToPlayer) {
            ItemStack toGive = stampedGive;
            boolean added = player.getInventory().add(toGive);
            if (!added && !toGive.isEmpty()) {
                player.drop(toGive, false);
            }
        }
        // Creative mode is fully free (parity with tryConsumeKeys).
        if (consumeBox && !player.getAbilities().instabuild) {
            box.shrink(1);
        }

        // Record the successful open for max_per_player / cooldown.
        BoxConstraintTracker.recordOpen(trackerKey, boxId.toString(), player.level().getGameTime());

        // v2.0.1 pity: advance the miss streak only after the item was
        // actually given (rejected/aborted opens never count). Uses the
        // ROLLED grade (pityRollGrade), not the resolved/final one.
        if (pity != null) {
            PityTracker.recordOpen(trackerKey, boxId.toString(),
                    pityRollGrade, pity.targetLevel());
        }

        if (player instanceof ServerPlayer sp) {
            sp.awardStat(CsgoBox.OPENED_BOXES_STAT, 1);
            if (CsgoBox.CONFIG.enableAchievements()) {
                OpenedBoxTrigger.INSTANCE.trigger(sp, finalGrade);
            }
        }

        NeoForge.EVENT_BUS.post(new BoxOpenedEvent(player, boxId, giveItem.copy(), finalGrade, false));

        return new Outcome(
                finalGrade,
                winningIndex,
                strip.items(),
                strip.grades(),
                stampedGive
        );
    }

    private record Availability(int boxes, int keys) {
    }

    private static Availability countAvailability(Player player, ItemStack box, ResourceLocation keyId) {
        boolean noKey = keyId == null || keyId.equals(ResourceLocation.parse("minecraft:air"));
        boolean countKeys = !noKey && !player.getAbilities().instabuild;
        int boxes = 0;
        int keys = countKeys ? 0 : Integer.MAX_VALUE;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof ItemCsgoBox) {
                if (ItemStack.isSameItemSameComponents(stack, box)) {
                    boxes += stack.getCount();
                }
            } else if (countKeys && keyId.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                keys += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.getItem() instanceof ItemCsgoBox) {
                if (ItemStack.isSameItemSameComponents(stack, box)) {
                    boxes += stack.getCount();
                }
            } else if (countKeys && keyId.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                keys += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
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
                float wear = rng.nextFloat();
                // v2.0.1-fix: every follow-up result needs its OWN seed — a
                // constant 0L made the spec resolve (count-range / enchant /
                // loot-table) in finalizeBulkOpen deterministic and identical
                // for every non-first item across players and batches.
                int rolledGrade = Mth.clamp(g, 1, 5);
                out.add(new BulkOpenResult(s, rolledGrade, SECURE_RANDOM.nextLong(), -1, List.of(), List.of(), wear, fallback, rolledGrade));
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

        // Get keyId ONCE from templateBox to avoid race conditions if player's hand changes
        ResourceLocation keyId = ItemCsgoBox.getKey(templateBox);

        // Re-validate after async compute; inventory might have changed.
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
        // grade, or its UI colour is wrong. Box 1's animation strip shows the
        // same winner, so patch that slot too.
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
                    r.winningIndex(), r.animationItems(), r.animationGrades(), r.wear(), true, r.pityGrade());
            truncated.set(i, fixed);
            if (i == 0 && fixed.winningIndex() >= 0 && fixed.winningIndex() < fixed.animationGrades().size()) {
                fixed.animationGrades().set(fixed.winningIndex(), realGrade);
            }
        }

        // v2.0.1: resolve count-range / random-enchant / loot-table specs on
        // every result, on the main thread (the resolver needs a ServerLevel).
        for (int i = 0; i < truncated.size(); i++) {
            BulkOpenResult r = truncated.get(i);
            if (r.resultItem().isEmpty()) {
                continue;
            }
            ItemStack resolved = BoxItemResolver.resolve(
                    r.resultItem(), sp.serverLevel(), new Random(r.serverSeed() ^ 0x5DEECE66DL));
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
                if (r.wear() > 0F && r.resultItem().isDamageableItem()) {
                    PacketCsgoProgress.applyWearDamage(r.resultItem(), r.wear());
                    if (!r.animationItems().isEmpty()
                            && r.winningIndex() >= 0
                            && r.winningIndex() < r.animationItems().size()) {
                        ItemStack animWinner = r.animationItems().get(r.winningIndex());
                        if (!animWinner.isEmpty() && animWinner.isDamageableItem()) {
                            PacketCsgoProgress.applyWearDamage(animWinner, r.wear());
                        }
                    }
                }
            }
        }

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

        // v2.0.1: record successful opens for max_per_player / cooldown
        // (one entry per granted item).
        long nowTicks = sp.level().getGameTime();
        for (BulkOpenResult r : truncated) {
            if (r.resultItem().isEmpty()) {
                continue;
            }
            BoxConstraintTracker.recordOpen(sp.getStringUUID(), snapshot.boxId().toString(), nowTicks);
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
    public static void handleBulkOpen(PacketCsgoBulkProgress message, IPayloadContext context) {
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
                if (player instanceof ServerPlayer sp) {
                    PacketCsgoProgress.sendRejected(sp, message.requestId());
                }
                return;
            }
            ResourceLocation boxId = ItemCsgoBox.getBoxId(templateBox);
            if (boxId == null) {
                if (player instanceof ServerPlayer sp) {
                    PacketCsgoProgress.sendRejected(sp, message.requestId());
                }
                return;
            }
            BoxDefinition def = boxId == null ? null : BoxRegistry.get(boxId);
            if (def != null && def.isTerminal()) {
                if (player instanceof ServerPlayer sp) {
                    PacketCsgoProgress.sendRejected(sp, message.requestId());
                }
                return;
            }
            if (player instanceof ServerPlayer sp && (sp.isRemoved() || !sp.isAlive())) {
                return;
            }
            if (OpenBlockGuard.isBlocked(player.getUUID(), player.level().getGameTime())) {
                if (player instanceof ServerPlayer sp) {
                    PacketCsgoProgress.sendRejected(sp, message.requestId());
                }
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
                if (player instanceof ServerPlayer sp) {
                    PacketCsgoProgress.sendRejected(sp, message.requestId());
                }
                return;
            }
            int[] weights = ItemCsgoBox.getRandom(templateBox);
            if (weights.length == 0 || !BoxOdds.hasOpenableWeights(weights)) {
                // v2.0.1-fix(all-zero weights): unopenable crate — refuse.
                if (player instanceof ServerPlayer sp) {
                    PacketCsgoProgress.sendRejected(sp, message.requestId());
                }
                return;
            }

            // v2.0.1 constraints (batch-level): per-player cap / cooldown /
            // permission are checked once for the whole batch.
            BoxDefinition constraintDef = BoxRegistry.get(boxId);
            if (constraintDef != null) {
                if (!BoxConstraintTracker.underPerPlayerCap(
                        player.getStringUUID(), boxId.toString(), constraintDef.maxPerPlayer())) {
                    if (player instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(Component.translatable(
                                "commands.csgobox.constraint.capped", constraintDef.name(), constraintDef.maxPerPlayer()));
                        PacketCsgoProgress.sendRejected(sp, message.requestId());
                    }
                    return;
                }
                if (!BoxConstraintTracker.cooldownElapsed(player.getStringUUID(), boxId.toString(),
                        constraintDef.cooldownSeconds(), player.level().getGameTime())) {
                    if (player instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(Component.translatable(
                                "commands.csgobox.constraint.cooldown", constraintDef.name()));
                        PacketCsgoProgress.sendRejected(sp, message.requestId());
                    }
                    return;
                }
                if (!constraintDef.permission().isBlank() && player instanceof ServerPlayer sp
                        && !CsgoBox.PERMISSION_GATE.test(sp, constraintDef.permission())) {
                    sp.sendSystemMessage(Component.translatable(
                            "commands.csgobox.constraint.permission", constraintDef.name()));
                    PacketCsgoProgress.sendRejected(sp, message.requestId());
                    return;
                }
            }

            Availability avail = countAvailability(player, templateBox, ItemCsgoBox.getKey(templateBox));
            int availableBoxes = avail.boxes();
            int availableKeys = avail.keys();
            int K = Math.min(availableBoxes, availableKeys);
            if (K <= 0) {
                if (player instanceof ServerPlayer sp) {
                    PacketCsgoProgress.sendRejected(sp, message.requestId());
                }
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
            if (constraintDef != null && constraintDef.maxPerPlayer() > 0) {
                int allowance = constraintDef.maxPerPlayer()
                        - BoxConstraintTracker.openCount(player.getStringUUID(), boxId.toString());
                K = Math.min(K, Math.max(0, allowance));
            }
            if (K <= 0) {
                if (player instanceof ServerPlayer sp) {
                    PacketCsgoProgress.sendRejected(sp, message.requestId());
                }
                return;
            }

            // Policy hook (batch-level): scripts may veto the whole batch
            // before any roll or consumption. count = K (the server-authorized
            // batch size). Canceling aborts the batch without setting cooldown.
            BoxOpeningEvent opening = new BoxOpeningEvent(player, boxId, true, K);
            NeoForge.EVENT_BUS.post(opening);
            if (opening.isCanceled()) {
                if (player instanceof ServerPlayer sp) {
                    PacketCsgoProgress.sendRejected(sp, message.requestId());
                }
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
            PityPolicy pity = constraintDef != null ? constraintDef.pity().orElse(null) : null;
            final int pityStreak = pity != null
                    ? PityTracker.startStreak(player.getStringUUID(), boxId.toString())
                    : 0;

            try {
                final Player playerFinal = player;
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
                // v2.0.2-fix: the bounded queue overflowed - instead of dropping
                // silently (client would wait out the full 10s screen timeout),
                // reject the batch like every other refusal path. Nothing was
                // consumed: OpenBlockGuard is still the only side effect.
                CsgoBox.LOGGER.warn("[csgo-bulk] compute queue full, batch rejected (player={} K={})", player.getName().getString(), requestedK);
                if (player instanceof ServerPlayer sp) {
                    PacketCsgoProgress.sendRejected(sp, message.requestId());
                }
            }
        });
    }
}