package com.reclizer.csgobox.event;

import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.gui.client.CsboxScreen;
import com.reclizer.csgobox.item.ItemCsgoBox;
import com.reclizer.csgobox.item.ModItems;
import com.reclizer.csgobox.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = CsgoBox.MODID)
public class ClickEvent {
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.getSide().isServer()) {
            return;
        }

        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();

        if (heldItem.getItem() == ModItems.ITEM_CSGOBOX.get() && event.getHand() == InteractionHand.MAIN_HAND) {

            player.playSound(ModSounds.CS_OPEN.get(), 10F, 1F);

            ItemCsgoBox.BoxInfo info = ItemCsgoBox.getBoxInfo(heldItem);
            if (info != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.setScreen(new CsboxScreen());
                }
            }
        }
    }
}