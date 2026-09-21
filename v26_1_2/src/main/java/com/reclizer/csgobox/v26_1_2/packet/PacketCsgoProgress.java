package com.reclizer.csgobox.v26_1_2.packet;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.box.BoxStripGenerator;
import com.reclizer.csgobox.logic.BoxConstraintTracker;
import com.reclizer.csgobox.logic.GradeMapCache;
import com.reclizer.csgobox.logic.OddsCalculator;
import com.reclizer.csgobox.logic.OpenBlockGuard;
import com.reclizer.csgobox.logic.PityPolicy;
import com.reclizer.csgobox.logic.PityTracker;
import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.v26_1_2.event.BoxOpeningEvent;
import com.reclizer.csgobox.v26_1_2.event.BoxOpenedEvent;
import com.reclizer.csgobox.v26_1_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_1_2.box.BoxItemResolver;
import com.reclizer.csgobox.v26_1_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_1_2.box.GradeGroup;
import com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_1_2.item.ItemTerminal;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

/** Client-to-server request to open the currently held box. The request id
 *  only matches the later client animation result; never trusted by the server. */
public record PacketCsgoProgress(long requestId) implements CustomPacketPayload {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
    static void sendRejected(IPayloadContext context, long requestId) {
        context.reply(new PacketBoxOpenResult(
                1,
                0,
                requestId,
                List.of(),
                List.of()
        ));
    }

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
    static int resolveGrade(ItemStack item, Identifier boxId, int fallback) {
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
    static boolean tryConsumeKeys(Player entity, ItemStack box, int count) {
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
    static boolean tryConsumeBoxes(Player entity, ItemStack box, int count) {
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
