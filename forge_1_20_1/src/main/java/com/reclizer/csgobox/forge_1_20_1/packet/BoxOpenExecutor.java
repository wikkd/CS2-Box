package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.box.BoxStripGenerator;
import com.reclizer.csgobox.logic.BoxConstraintTracker;
import com.reclizer.csgobox.logic.GradeMapCache;
import com.reclizer.csgobox.logic.OddsCalculator;
import com.reclizer.csgobox.logic.OpenBlockGuard;
import com.reclizer.csgobox.logic.PityPolicy;
import com.reclizer.csgobox.logic.PityTracker;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.BoxItemResolver;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.event.BoxOpeningEvent;
import com.reclizer.csgobox.forge_1_20_1.event.BoxOpenedEvent;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.UUID;

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
            ItemStack giveItem,
            /** Plain prize copy without the grade stamp (client animation data). */
            ItemStack animItem
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
     * ({@code create.DeployerBoxOpenRecipeSearch}) passes
     * {@code consumeKey=false}: Create owns the held-item consumption there,
     * so the executor must not also shrink a key from the fake player's
     * inventory.
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
        BoxOpeningEvent.BUS.post(opening);
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
        giveItem = BoxItemResolver.resolve(giveItem, (net.minecraft.server.level.ServerLevel) player.level(), rng);
        if (giveItem == null || giveItem.isEmpty()) {
            return null;
        }

        // Consume keys (from anywhere: items, armor, offhand) only after the
        // whole roll is validated — a broken definition must never eat a key.
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

        // Keep a plain copy for the client animation (no grade stamp, matching
        // the pre-v2.2.0 player-data payload).
        ItemStack animItem = giveItem.copy();

        ItemStack stampedGive = giveItem.copy();
        ItemCsgoBox.setGrade(stampedGive, finalGrade);

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

        player.awardStat(CsgoBox.OPENED_BOXES_STAT, 1);
        if (CsgoBox.CONFIG.enableAchievements() && player instanceof ServerPlayer sp) {
            OpenedBoxTrigger.INSTANCE.trigger(sp, finalGrade);
        }

        BoxOpenedEvent.BUS.post(new BoxOpenedEvent(player, boxId, giveItem.copy(), finalGrade, false));

        return new Outcome(
                finalGrade,
                winningIndex,
                strip.items(),
                strip.grades(),
                stampedGive,
                animItem
        );
    }
}