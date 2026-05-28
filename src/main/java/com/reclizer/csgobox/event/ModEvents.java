package com.reclizer.csgobox.event;

import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.config.CsgoBoxManage;
import com.reclizer.csgobox.item.ItemCsgoBox;
import com.reclizer.csgobox.item.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Random;

@EventBusSubscriber(modid = CsgoBox.MODID)
public class ModEvents {

    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void LivingDeadEvents(LivingDeathEvent event) {
        LivingEntity mob = event.getEntity();
        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();

        if (CsgoBoxManage.BOX == null) {
            return;
        }

        for (ItemCsgoBox.BoxInfo info : CsgoBoxManage.BOX) {
            if (info.dropEntity == null) {
                continue;
            }
            if (info.dropRandom > 0) {
                if (info.dropRandom > (1.00F - RANDOM.nextFloat(1)) && info.dropEntity.contains(entityType)) {
                    ItemStack stack = new ItemStack(ModItems.ITEM_CSGOBOX.get());
                    ItemCsgoBox.setBoxInfo(info, stack);
                    mob.spawnAtLocation(stack);
                }
            }
        }
    }
}