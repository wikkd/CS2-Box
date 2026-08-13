package com.reclizer.csgobox.v26_2.packet;

import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.v26_2.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.v26_2.capability.CsboxPlayerData;
import com.reclizer.csgobox.v26_2.capability.ModCapability;
import com.reclizer.csgobox.v26_2.event.BoxOpenedEvent;
import com.reclizer.csgobox.v26_2.box.BoxDefinition;
import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxStripGenerator;
import com.reclizer.csgobox.v26_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_2.box.GradeGroup;
import com.reclizer.csgobox.logic.GradeMap;
import com.reclizer.csgobox.logic.GradeMapCache;
import com.reclizer.csgobox.logic.OpenBlockGuard;
import com.reclizer.csgobox.v26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_2.item.ItemTerminal;
import net.minecraft.core.component.DataComponents;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

/**
 * Client-to-server request to open the currently held box.
 *
 * <p>The request id is for matching the later client animation result only. The
 * server never trusts it for authorization.</p>
 */
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
            var player = context.player();
            var box = player.getMainHandItem();
            if (!(box.getItem() instanceof ItemCsgoBox)) {
                return;
            }
            // Strict separation (v1.0.8): terminals are only buyable through
            // the terminal negotiation protocol — never through the classic
            // crate pipeline, which would open them for free (no key, no
            // Armory Points). A crafted packet holding a terminal is refused.
            if (box.getItem() instanceof ItemTerminal) {
                sendRejected(context, message.requestId());
                return;
            }

            if (player instanceof ServerPlayer sp && (sp.isRemoved() || !sp.isAlive())) {
                sendRejected(context, message.requestId());
                return;
            }

            if (OpenBlockGuard.isBlocked(player.getUUID(), player.level().getGameTime())) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(context, message.requestId());
                }
                return;
            }

            var boxId = ItemCsgoBox.getBoxId(box);
            if (boxId == null) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(context, message.requestId());
                }
                return;
            }
            // Same guard for a crafted box_id component pointing at a
            // terminal definition from a plain ItemCsgoBox stack.
            if (BoxRegistry.get(boxId) != null && BoxRegistry.get(boxId).isTerminal()) {
                sendRejected(context, message.requestId());
                return;
            }

            int[] weights = ItemCsgoBox.getRandom(box);
            if (weights.length == 0) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(context, message.requestId());
                }
                return;
            }

            long serverSeed = SECURE_RANDOM.nextLong();
            var rng = new Random(serverSeed);

            // The grade pool is definition-derived and immutable, so it is
            // built once per box id (same cache the bulk path uses) instead
            // of re-copied on every single open. GradeMapCache is invalidated
            // by BoxRegistry on reload, so a config change can never serve a
            // stale pool. pickRandom always returns ItemStack::copy results,
            // so callers may mutate the returned stack freely.
            var gradeMap = GradeMapCache.get(boxId.toString(),
                    () -> GradeMap.build(ItemCsgoBox.getItemGroup(box), stack -> !stack.isEmpty(), ItemStack::copy));
            if (gradeMap.isEmpty()) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(context, message.requestId());
                }
                return;
            }

            var strip = BoxStripGenerator.generate(gradeMap, weights, rng, ItemStack.EMPTY);
            int winningIndex = strip.winningIndex();
            if (winningIndex < 0) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(context, message.requestId());
                }
                return;
            }

            ItemStack giveItem = strip.items().get(winningIndex);
            int finalGrade = strip.grades().get(winningIndex);

            if (giveItem.isEmpty()) {
                giveItem = gradeMap.findFallback(1);
                if (giveItem == null) giveItem = ItemStack.EMPTY;
                if (giveItem.isEmpty()) {
                    if (player instanceof ServerPlayer sp) {
                        sendRejected(context, message.requestId());
                    }
                    return;
                }
                finalGrade = resolveGrade(giveItem, boxId, 1);
                strip.items().set(winningIndex, giveItem.copy());
                strip.grades().set(winningIndex, finalGrade);
            }

            // Keys are consumed only after the whole roll is validated (box id,
            // weights, grade pool, winning index, fallback). A broken or
            // hot-reloaded-empty definition must never eat a key: every failure
            // above replies sendRejected before any consumption happens.
            if (!tryConsumeKeys(player, box, 1)) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(context, message.requestId());
                }
                return;
            }

            float wear = 0F;
            if (CsgoBox.CONFIG.damageItemByWear() && giveItem.getMaxDamage() > 0) {
                wear = rng.nextFloat();
                applyWearDamage(giveItem, wear);
            }

            OpenBlockGuard.block(player.getUUID(), player.level().getGameTime(), OpenBlockGuard.DEFAULT_COOLDOWN_TICKS);

            player.setData(ModCapability.PLAYER_DATA,
                    new CsboxPlayerData(serverSeed, 0, giveItem.copy(), finalGrade));

            context.reply(new PacketBoxOpenResult(
                    finalGrade,
                    winningIndex,
                    message.requestId(),
                    strip.items(),
                    strip.grades()
            ));

            ItemStack toGive = giveItem.copy();
            boolean added = player.getInventory().add(toGive);
            if (!added && !toGive.isEmpty()) {
                player.drop(toGive, false);
            }
            // Creative mode is fully free: keys, Armory Points and now boxes
            // (parity with tryConsumeKeys / PacketTerminalBuy).
            if (!player.getAbilities().instabuild) {
                box.shrink(1);
            }

            if (player instanceof ServerPlayer sp) {
                sp.awardStat(CsgoBox.OPENED_BOXES_STAT, 1);
                if (CsgoBox.CONFIG.enableAchievements()) {
                    OpenedBoxTrigger.INSTANCE.trigger(sp);
                }
            }

            NeoForge.EVENT_BUS.post(new BoxOpenedEvent(player, boxId, giveItem.copy(), finalGrade, false));
        });
    }


    static void sendRejected(IPayloadContext context, long requestId) {
        context.reply(new PacketBoxOpenResult(
                1,
                0,
                requestId,
                List.of(),
                List.of()
        ));
    }

    /**
     * Damages a durable item stack by a fraction of its max durability
     * proportional to the wear value (0..1). Clamped so the item never breaks
     * (damage is at most maxDamage - 1) and never goes negative.
     */
    static void applyWearDamage(ItemStack stack, float wear) {
        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) {
            return;
        }
        int damage = Math.max(0, Math.min(Math.round(wear * maxDamage), maxDamage - 1));
        stack.set(DataComponents.DAMAGE, damage);
    }

    /**
     * Resolves the grade (1..5) of an item produced by the fallback path. The
     * box definition is the source of truth; the per-open item list no longer
     * exists now that the grade pool is cached.
     */
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

    /**
     * Consume up to {@code count} boxes matching the template (same item,
     * same components) from anywhere in the player's inventory (items, armor,
     * offhand). Returns true only when the full count was consumed.
     */
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
