package com.reclizer.csgobox.v1_21_1.packet;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.box.BoxStripGenerator;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.box.GradeGroup;
import com.reclizer.csgobox.v1_21_1.event.BoxOpeningEvent;
import com.reclizer.csgobox.v1_21_1.event.BoxOpenedEvent;
import com.reclizer.csgobox.v1_21_1.box.BoxItemResolver;
import com.reclizer.csgobox.logic.BoxConstraintTracker;
import com.reclizer.csgobox.logic.GradeMapCache;
import com.reclizer.csgobox.logic.OddsCalculator;
import com.reclizer.csgobox.logic.OpenBlockGuard;
import com.reclizer.csgobox.logic.PityPolicy;
import com.reclizer.csgobox.logic.PityTracker;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ItemTerminal;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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

import java.util.List;
import java.util.Random;

/** Client-to-server request to open the currently held box. The request id
 *  only matches the later client animation result; never trusted by the server. */
public record PacketCsgoProgress(long requestId) implements CustomPacketPayload {


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

            // v2.2.0: the whole headless pipeline (guards, BoxOpeningEvent,
            // constraints, pity, roll, keys, grant, records) moved to
            // BoxOpenExecutor, shared with the Create deployer compat so a
            // mechanical deployer holding the key opens crates with identical
            // rules. Note the prize is now granted slightly before the
            // animation packet is sent (same tick, no visible change).
            BoxOpenExecutor.Outcome outcome = BoxOpenExecutor.execute(player, box, true, true);
            if (outcome == null) {
                if (player instanceof ServerPlayer sp) {
                    sendRejected(sp, message.requestId());
                }
                return;
            }

            if (player instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, new PacketBoxOpenResult(
                        outcome.grade(),
                        outcome.winningIndex(),
                        message.requestId(),
                        outcome.animationItems(),
                        outcome.animationGrades()
                ));
            }
        });
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

    /** Resolves the fallback item's tier level (1..tier count) against the box definition. */
    static int resolveGrade(ItemStack item, ResourceLocation boxId, int fallback) {
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def != null) {
            List<GradeGroup> tiers = def.grades();
            for (int i = 0; i < tiers.size(); i++) {
                for (ItemStack candidate : tiers.get(i).items()) {
                    if (ItemStack.isSameItemSameComponents(item, candidate)) {
                        return Mth.clamp(BoxGrades.levelAt(tiers.size(), i), 1, Math.max(1, tiers.size()));
                    }
                }
            }
        }
        return Mth.clamp(fallback, 1, com.reclizer.csgobox.box.BoxGrades.MAX_GRADES);
    }

    /**
     * Consume up to {@code count} keys from anywhere (items, armor, offhand);
     * true only when fully consumed (or no key required).
     */
    static boolean tryConsumeKeys(Player entity, ItemStack box, int count) {
        java.util.List<String> acceptedKeys = ItemCsgoBox.getAcceptedKeys(box);
        if (acceptedKeys.isEmpty()) {
            return true;
        }
        for (String acceptedKey : acceptedKeys) {
            if (tryConsumeKeys(entity, ResourceLocation.parse(acceptedKey), count)) {
                return true;
            }
        }
        return false;
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

    /**
     * Count-first, all-or-nothing key consumption across the box's full
     * accepted-keys list (mirrors {@code BoxOpenExecutor.countAvailability}'s
     * union count). The any-of/single-id overloads consumed as they scanned,
     * so a partial match (e.g. 1 primary + 3 extra keys against a 4-box batch)
     * destroyed the keys it found and THEN failed — the bulk finalizer
     * refunded boxes, but those keys were gone for good. This variant counts
     * availability across ALL accepted ids first and consumes nothing unless
     * the total covers {@code count}; both passes run back-to-back on the
     * server thread, so nothing can interleave between them.
     */
    static boolean tryConsumeKeysExact(Player entity, java.util.List<String> acceptedKeys, int count) {
        if (acceptedKeys == null || acceptedKeys.isEmpty() || count <= 0
                || entity.getAbilities().instabuild) {
            return true;
        }
        java.util.List<ResourceLocation> ids = new java.util.ArrayList<>(acceptedKeys.size());
        int total = 0;
        for (String acceptedKey : acceptedKeys) {
            ResourceLocation id = ResourceLocation.parse(acceptedKey);
            if (id.toString().equals("minecraft:air")) {
                return true;
            }
            ids.add(id);
            total += countKeys(entity, id);
        }
        if (total < count) {
            return false;
        }
        int remaining = count;
        for (ResourceLocation id : ids) {
            if (remaining <= 0) {
                break;
            }
            remaining = consumeFromList(entity.getInventory().items, id, null, remaining);
            if (remaining > 0) remaining = consumeFromList(entity.getInventory().armor, id, null, remaining);
            if (remaining > 0) remaining = consumeFromList(entity.getInventory().offhand, id, null, remaining);
        }
        return remaining == 0;
    }

    /** Availability count of one key id across items + armor + offhand (box stacks excluded). */
    private static int countKeys(Player entity, ResourceLocation keyId) {
        int n = 0;
        for (ItemStack stack : entity.getInventory().items) {
            if (!stack.isEmpty() && !(stack.getItem() instanceof ItemCsgoBox)
                    && keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                n += stack.getCount();
            }
        }
        for (ItemStack stack : entity.getInventory().armor) {
            if (!stack.isEmpty() && !(stack.getItem() instanceof ItemCsgoBox)
                    && keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                n += stack.getCount();
            }
        }
        for (ItemStack stack : entity.getInventory().offhand) {
            if (!stack.isEmpty() && !(stack.getItem() instanceof ItemCsgoBox)
                    && keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                n += stack.getCount();
            }
        }
        return n;
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
