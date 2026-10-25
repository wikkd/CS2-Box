package com.reclizer.csgobox.fabric_1_21_1.event;

import com.reclizer.csgobox.fabric_1_21_1.item.ItemCsgoBox;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Client right-click-with-item hook (Fabric edition of
 * {@code PlayerInteractEvent.RightClickItem}): a box item in the main hand
 * opens its screen. Box kind decides the screen: ItemTerminal opens the
 * terminal boot screen, every other box opens the classic crate UI
 * (Shift → bulk).
 */
public final class ClickEvent {
    private ClickEvent() {
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResultHolder.pass(held);
            }
            if (!world.isClientSide() || !(player instanceof LocalPlayer)) {
                return InteractionResultHolder.pass(held);
            }
            if (held.getItem() instanceof ItemCsgoBox boxItem) {
                boxItem.openScreen(held);
                return InteractionResultHolder.success(held);
            }
            return InteractionResultHolder.pass(held);
        });
    }
}
