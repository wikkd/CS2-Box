package com.reclizer.csgobox.event;

import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.box.BoxDefinition;
import com.reclizer.csgobox.box.BoxRegistry;
import com.reclizer.csgobox.item.ItemCsgoBox;
import com.reclizer.csgobox.item.ModItems;
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
            if (CsgoBox.CONFIG != null) {
                effectiveRate *= CsgoBox.CONFIG.general.globalDropRatePercent / 100F;
            }
            effectiveRate = Math.min(effectiveRate, 1.0F);

            if (effectiveRate > 0 && RANDOM.nextFloat() < effectiveRate) {
                ItemStack stack = new ItemStack(ModItems.ITEM_CSGOBOX.get());
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
}
