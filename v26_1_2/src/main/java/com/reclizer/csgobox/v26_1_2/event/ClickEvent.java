package com.reclizer.csgobox.v26_1_2.event;

import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.gui.CsboxBulkOverviewScreen;
import com.reclizer.csgobox.v26_1_2.gui.CsboxScreen;
import com.reclizer.csgobox.v26_1_2.item.ModItems;
import com.reclizer.csgobox.v26_1_2.sounds.ModSounds;
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

        if (heldItem.getItem() == ModItems.ITEM_CSGOBOX.get()) {

            float vol = CsgoBox.CONFIG.openSoundVolume() / 100F;
            if (vol > 0) {
                player.playSound(ModSounds.CS_OPEN.get(), vol * 10F, 1F);
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                boolean shift = mc.options.keyShift.isDown();
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
