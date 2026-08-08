package com.reclizer.csgobox.v1_21_11.event;

import com.reclizer.csgobox.v1_21_11.CsgoBox;
import com.reclizer.csgobox.v1_21_11.gui.CsboxBulkOverviewScreen;
import com.reclizer.csgobox.v1_21_11.gui.CsboxScreen;
import com.reclizer.csgobox.v1_21_11.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_11.item.ModItems;
import com.reclizer.csgobox.v1_21_11.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = CsgoBox.MODID)
public final class ClickEvent {
    private ClickEvent() {
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player = event.getEntity();
        if (!player.level().isClientSide() || !(player instanceof LocalPlayer)) {
            return;
        }

        ItemStack heldItem = player.getMainHandItem();

        if (heldItem.getItem() instanceof ItemCsgoBox) {

            float vol = CsgoBox.CONFIG.openSoundVolume() / 100F;
            if (vol > 0) {
                player.playSound(ModSounds.CS_OPEN.get(), vol * 10F, 1F);
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                boolean shift = false; // 1.0.6 屏蔽批量开箱（1.0.7 恢复）
                mc.execute(() -> {
                    if (shift) {
                        mc.setScreen(new CsboxBulkOverviewScreen());
                    } else {
                        mc.setScreen(new CsboxScreen());
                    }
                });
            }
        }
    }
}
