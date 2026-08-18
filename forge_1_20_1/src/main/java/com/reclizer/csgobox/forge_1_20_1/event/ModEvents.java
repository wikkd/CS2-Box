package com.reclizer.csgobox.forge_1_20_1.event;

import com.reclizer.csgobox.logic.OpenBlockGuard;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Random;

@Mod.EventBusSubscriber(modid = CsgoBox.MODID)
public final class ModEvents {
    private static final Random RANDOM = new Random();

    private ModEvents() {
    }

    @SubscribeEvent
    public static void livingDeath(LivingDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob.level().isClientSide()) {
            return;
        }
        ResourceLocation entityType = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        float lootingMultiplier = lootingMultiplier(mob, event.getSource());

        for (BoxDefinition def : BoxRegistry.getAll()) {
            if (!def.dropEntities().contains(entityType)) {
                continue;
            }

            float effectiveRate = def.getDropRateForEntity(entityType) * lootingMultiplier;
            effectiveRate *= CsgoBox.CONFIG.globalDropRatePercent() / 100F;
            effectiveRate = Math.min(effectiveRate, 1.0F);

            if (effectiveRate > 0 && RANDOM.nextFloat() < effectiveRate) {
                Item item = ForgeRegistries.ITEMS.getValue(def.id());
                if (item == null) {
                    item = ModItems.ITEM_CSGOBOX.get();
                }
                ItemStack stack = new ItemStack(item);
                ItemCsgoBox.setBoxId(def.id(), stack);
                mob.spawnAtLocation(stack);
            }
        }
    }

    private static float lootingMultiplier(LivingEntity mob, DamageSource source) {
        if (!(source.getEntity() instanceof Player player)) {
            return 1.0F;
        }

        ItemStack weapon = player.getMainHandItem();
        int lootingLevel = net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING, weapon);
        return lootingLevel > 0 ? 1.0F + lootingLevel * 0.5F : 1.0F;
    }

    @SubscribeEvent
    public static void playerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (CsgoBox.CONFIG.enableAchievements()
                && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.reclizer.csgobox.forge_1_20_1.advancement.ModLoadedTrigger.INSTANCE.trigger(sp);
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        // TODO: uncomment when terminal/ is ported
        // if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
        //     com.reclizer.csgobox.forge_1_20_1.terminal.TerminalSessionManager.clearOpen(sp.getStringUUID());
        // }
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        if (server.getTickCount() % 100 == 0) {
            OpenBlockGuard.tick(server.overworld().getGameTime());
        }
        // TODO: uncomment when terminal/ is ported
        // if (server.getTickCount() % 20 == 0) {
        //     com.reclizer.csgobox.forge_1_20_1.terminal.TerminalSessionManager.tickSessions(
        //             server, server.overworld().getGameTime() * 50L);
        // }
    }
}
