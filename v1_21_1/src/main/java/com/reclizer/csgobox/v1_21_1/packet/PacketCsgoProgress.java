package com.reclizer.csgobox.v1_21_1.packet;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxStripGenerator;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.v1_21_1.capability.CsboxPlayerData;
import com.reclizer.csgobox.v1_21_1.capability.ModCapability;
import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.box.GradeGroup;
import com.reclizer.csgobox.v1_21_1.event.BoxOpeningEvent;
import com.reclizer.csgobox.v1_21_1.event.BoxOpenedEvent;
import com.reclizer.csgobox.logic.GradeMap;
import com.reclizer.csgobox.logic.GradeMapCache;
import com.reclizer.csgobox.logic.OpenBlockGuard;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ItemTerminal;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

/** Client-to-server request to open the currently held box. The request id
 *  only matches the later client animation result; never trusted by the server. */
public record PacketCsgoProgress(long requestId) implements CustomPacketPayload {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
            // Terminals are only buyable via the negotiation protocol; the
            // classic crate pipeline would open them for free. Refuse crafted packets.
            if (box.getItem() instanceof ItemTerminal) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(sp, message.requestId());
                }
                return;
            }

            if (player instanceof ServerPlayer sp && (sp.isRemoved() || !sp.isAlive())) {
                sendRejected(sp, message.requestId());
                return;
            }

            if (OpenBlockGuard.isBlocked(player.getUUID(), player.level().getGameTime())) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(sp, message.requestId());
                }
                return;
            }

var boxId = ItemCsgoBox.getBoxId(box);
            if (boxId == null) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(sp, message.requestId());
                }
                return;
            }
            // Same guard for a box_id pointing at a terminal from a plain box stack.
            if (BoxRegistry.get(boxId) != null && BoxRegistry.get(boxId).isTerminal()) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(sp, message.requestId());
                }
                return;
            }

            // Mods may veto the open before any roll or consumption.
            BoxOpeningEvent opening = new BoxOpeningEvent(player, boxId, false, 1);
            NeoForge.EVENT_BUS.post(opening);
            if (opening.isCanceled()) {
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

            long serverSeed = SECURE_RANDOM.nextLong();
            var rng = new Random(serverSeed);

            // Grade pool is immutable per box id (shared cache with the bulk
            // path, invalidated on reload). pickRandom returns copies, so
            // callers may mutate freely.
            var gradeMap = GradeMapCache.get(boxId.toString(),
                    () -> GradeMap.build(ItemCsgoBox.getItemGroup(box), stack -> !stack.isEmpty(), ItemStack::copy));
            if (gradeMap.isEmpty()) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(sp, message.requestId());
                }
                return;
            }

            var strip = BoxStripGenerator.generate(gradeMap, weights, rng, ItemStack.EMPTY);
            int winningIndex = strip.winningIndex();
            if (winningIndex < 0) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(sp, message.requestId());
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
                        sendRejected(sp, message.requestId());
                    }
                    return;
                }
                finalGrade = resolveGrade(giveItem, boxId, 1);
                strip.items().set(winningIndex, giveItem.copy());
                strip.grades().set(winningIndex, finalGrade);
            }

            // Consume keys only after the whole roll is validated — a broken
            // definition must never eat a key.
            if (!tryConsumeKeys(player, box, 1)) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(sp, message.requestId());
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

            if (player instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, new PacketBoxOpenResult(
                        finalGrade,
                        winningIndex,
                        message.requestId(),
                        strip.items(),
                        strip.grades()
                ));
            }

            ItemStack toGive = giveItem.copy();
            boolean added = player.getInventory().add(toGive);
            if (!added && !toGive.isEmpty()) {
                player.drop(toGive, false);
            }
            // Creative mode is fully free (parity with tryConsumeKeys).
            if (!player.getAbilities().instabuild) {
                box.shrink(1);
            }

            if (player instanceof ServerPlayer sp) {
                sp.awardStat(CsgoBox.OPENED_BOXES_STAT, 1);
                if (CsgoBox.CONFIG.enableAchievements()) {
                    OpenedBoxTrigger.INSTANCE.trigger(sp);
                }
            }

            NeoForge.EVENT_BUS.post(new BoxOpenedEvent(player, boxId, giveItem.copy(), finalGrade, false));        });
    }



    static void sendRejected(ServerPlayer player, long requestId) {
        PacketDistributor.sendToPlayer(player, new PacketBoxOpenResult(
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
        stack.set(DataComponents.MAX_DAMAGE, maxDamage); // 1.21.x 过渡期原版剑无此组件，补写以保证 isDamageableItem 可检测
        stack.set(DataComponents.DAMAGE, damage);
    }

    /** Resolves the grade (1..5) of a fallback item against the box definition. */
    static int resolveGrade(ItemStack item, ResourceLocation boxId, int fallback) {
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
     * true only when fully consumed (or no key required).
     */
    static boolean tryConsumeKeys(Player entity, ItemStack box, int count) {
        ResourceLocation keyId = ItemCsgoBox.getKey(box);
        return tryConsumeKeys(entity, keyId, count);
    }

    /** Consume keys by id directly; avoids lookups that could change if the hand switches mid-call. */
    static boolean tryConsumeKeys(Player entity, ResourceLocation keyId, int count) {
        if (keyId == null || keyId.toString().equals("minecraft:air")) {
            return true;
        }
        if (count <= 0) {
            return true;
        }
        if (entity.getAbilities().instabuild) {
            return true;
        }
        int remaining = count;
        remaining = consumeFromList(entity.getInventory().items, keyId, null, remaining);
        if (remaining > 0) remaining = consumeFromList(entity.getInventory().armor, keyId, null, remaining);
        if (remaining > 0) remaining = consumeFromList(entity.getInventory().offhand, keyId, null, remaining);
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
        remaining = consumeFromList(entity.getInventory().items, null, box, remaining);
        if (remaining > 0) remaining = consumeFromList(entity.getInventory().armor, null, box, remaining);
        if (remaining > 0) remaining = consumeFromList(entity.getInventory().offhand, null, box, remaining);
        return remaining == 0;
    }

    /** Shrinks matching stacks until the count is satisfied or the slice exhausted;
     *  returns what's left. Keys match by id, boxes by item + components. */
    private static int consumeFromList(java.util.List<ItemStack> stacks,
                                       ResourceLocation keyId,
                                       ItemStack boxTemplate,
                                       int remaining) {
        for (ItemStack stack : stacks) {
            if (remaining <= 0) {
                return 0;
            }
            if (stack.isEmpty()) {
                continue;
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
}
