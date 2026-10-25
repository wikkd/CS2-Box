package com.reclizer.csgobox.fabric_1_21_1.event;

import com.reclizer.csgobox.logic.OpenBlockGuard;
import com.reclizer.csgobox.fabric_1_21_1.CsgoBox;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.fabric_1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.fabric_1_21_1.item.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Random;

/**
 * Server-side event wiring (Fabric edition): box drops on configured mob
 * deaths, per-player definition sync on join, terminal binding cleanup on
 * logout, and the periodic cooldown/terminal countdown ticks.
 */
public final class ModEvents {
    private static final Random RANDOM = new Random(new java.security.SecureRandom().nextLong());

    private ModEvents() {
    }

    /** Wires the Fabric event callbacks; called once from the entry point. */
    public static void registerListeners() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> livingDeath(entity, damageSource));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            playerLoggedIn(handler.player);
            LoadErrorAnnouncer.onPlayerJoin(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> playerLoggedOut(handler.player));
        ServerTickEvents.START_SERVER_TICK.register(ModEvents::serverTick);
    }

    /** Rolls each matching box definition independently when a configured entity dies. */
    public static void livingDeath(LivingEntity mob, DamageSource source) {
        ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        float lootingMultiplier = lootingMultiplier(source, mob);

        for (BoxDefinition def : BoxRegistry.getAll()) {
            if (!def.dropEntities().contains(entityType)) {
                continue;
            }

            float effectiveRate = def.getDropRateForEntity(entityType) * lootingMultiplier;
            effectiveRate *= CsgoBox.CONFIG.globalDropRatePercent() / 100F;
            effectiveRate = Math.min(effectiveRate, 1.0F);

            // v2.0.1 KubeJS: per-drop veto/rate hook — before the RNG roll and
            // before the item spawns. Cancel suppresses the drop, setDropRate
            // overrides the effective chance (clamped, 0 = no roll).
            BoxEntityDropEvent dropEvent = new BoxEntityDropEvent(entityType, mob, def, effectiveRate);
            CsgoBus.post(dropEvent);
            if (dropEvent.isCanceled()) {
                continue;
            }
            effectiveRate = dropEvent.getDropRate();
            if (effectiveRate <= 0) {
                continue;
            }

            if (RANDOM.nextFloat() < effectiveRate) {
                // Box items are a fixed set now (a box is data, not an item id):
                // resolve the shipped fixed item, else the generic item.
                Item item = ModItems.itemForBox(def.id(), def.isTerminal());
                ItemStack stack = new ItemStack(item);
                ItemCsgoBox.setBoxId(def.id(), stack);
                mob.spawnAtLocation(stack);
            }
        }
    }

    private static float lootingMultiplier(DamageSource source, LivingEntity mob) {
        if (!(source.getEntity() instanceof Player player)) {
            return 1.0F;
        }

        ItemStack weapon = player.getMainHandItem();
        var enchantmentRegistry = mob.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var lootingHolder = enchantmentRegistry.getHolderOrThrow(Enchantments.LOOTING);
        int lootingLevel = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(lootingHolder, weapon);
        return lootingLevel > 0 ? 1.0F + lootingLevel * 0.5F : 1.0F;
    }

    /** Fires ModLoadedTrigger so csgobox:root criteria is satisfied on world join. */
    public static void playerLoggedIn(net.minecraft.server.level.ServerPlayer sp) {
        // Bring the joining client's box registry (and JEI category) in sync.
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                com.reclizer.csgobox.fabric_1_21_1.packet.PacketSyncBoxDefinitions.ofAll());
        if (CsgoBox.CONFIG.enableAchievements()) {
            com.reclizer.csgobox.fabric_1_21_1.advancement.ModLoadedTrigger.INSTANCE.trigger(sp);
        }
    }

    /** Drop the player's open-terminal binding on logout (sessions stay). */
    public static void playerLoggedOut(net.minecraft.server.level.ServerPlayer sp) {
        com.reclizer.csgobox.fabric_1_21_1.terminal.TerminalSessionManager.clearOpen(sp.getStringUUID());
    }

    /**
     * Periodically prunes expired open-cooldown entries from
     * {@link OpenBlockGuard#tick(long)} so the map stays bounded, and drives
     * the 1 Hz authoritative terminal countdown on the WORLD clock.
     */
    public static void serverTick(MinecraftServer server) {
        if (server.getTickCount() % 100 == 0) {
            OpenBlockGuard.tick(server.overworld().getGameTime());
        }
        // 1 Hz authoritative terminal countdown on the WORLD clock (game ticks
        // × 50) — it advances only while the world runs, and the deadline
        // survives restarts exactly (game time is part of the world save).
        if (server.getTickCount() % 20 == 0) {
            com.reclizer.csgobox.fabric_1_21_1.terminal.TerminalSessionManager.tickSessions(
                    server, server.overworld().getGameTime() * 50L);
        }
    }
}
