package com.reclizer.csgobox.v1_21_1.event;

import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ModItems;
import com.reclizer.csgobox.v1_21_1.packet.PacketCsgoProgress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Random;

@EventBusSubscriber(modid = CsgoBox.MODID)
public final class ModEvents {
    private static final Random RANDOM = new Random();

    private ModEvents() {
    }

    /** Rolls each matching box definition independently when a configured entity dies. */
    @SubscribeEvent
    public static void livingDeath(LivingDeathEvent event) {
        LivingEntity mob = event.getEntity();
        ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        float lootingMultiplier = lootingMultiplier(event, mob);

        for (BoxDefinition def : BoxRegistry.getAll()) {
            if (!def.dropEntities().contains(entityType)) {
                continue;
            }

            float effectiveRate = def.getDropRateForEntity(entityType) * lootingMultiplier;
            effectiveRate *= CsgoBox.CONFIG.globalDropRatePercent() / 100F;
            effectiveRate = Math.min(effectiveRate, 1.0F);

            if (effectiveRate > 0 && RANDOM.nextFloat() < effectiveRate) {
                ItemStack stack = new ItemStack(ModItems.boxItemFor(def).get());
                ItemCsgoBox.setBoxId(def.id(), stack);
                mob.spawnAtLocation(stack);
            }
        }
    }

    private static float lootingMultiplier(LivingDeathEvent event, LivingEntity mob) {
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return 1.0F;
        }

        ItemStack weapon = player.getMainHandItem();
        var enchantmentRegistry = mob.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var lootingHolder = enchantmentRegistry.getHolderOrThrow(Enchantments.LOOTING);
        int lootingLevel = weapon.getEnchantmentLevel(lootingHolder);
        return lootingLevel > 0 ? 1.0F + lootingLevel * 0.5F : 1.0F;
    }

    /** Fires ModLoadedTrigger so csgobox:root criteria is satisfied on world join. */
    @SubscribeEvent
    public static void playerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (CsgoBox.CONFIG.enableAchievements()
                && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.reclizer.csgobox.v1_21_1.advancement.ModLoadedTrigger.INSTANCE.trigger(sp);
        }
    }

    /**
     * Periodically prunes expired open-cooldown entries from
     * {@link PacketCsgoProgress#tickOpenBlockMap(long)} so the map stays bounded.
     */
    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Pre event) {
        if (event.getServer().getTickCount() % 100 == 0) {
            PacketCsgoProgress.tickOpenBlockMap(event.getServer().overworld().getGameTime());
        }
        // 1 Hz authoritative terminal countdown on the WORLD clock (game ticks
        // × 50) — it advances only while the world runs, and the deadline
        // survives restarts exactly (game time is part of the world save).
        if (event.getServer().getTickCount() % 20 == 0) {
            com.reclizer.csgobox.v1_21_1.terminal.TerminalSessionManager.tickSessions(
                    event.getServer(), event.getServer().overworld().getGameTime() * 50L);
        }
    }
}
