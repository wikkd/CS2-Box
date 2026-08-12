package com.reclizer.csgobox.forge_26_1_2.event;

import com.reclizer.csgobox.forge_26_1_2.CsgoBox;
import com.reclizer.csgobox.forge_26_1_2.item.ItemCsgoBox;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = CsgoBox.MODID)
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

        // Box kind decides the screen: ItemTerminal opens the terminal boot
        // screen, every other box opens the classic crate UI (Shift → bulk).
        if (heldItem.getItem() instanceof ItemCsgoBox boxItem) {
            boxItem.openScreen(heldItem);
        }
    }
}
