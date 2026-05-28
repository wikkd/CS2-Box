package com.reclizer.csgobox.item;

import com.reclizer.csgobox.gui.CsgoBoxCraftMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

public class ItemOpenBox extends Item {
    public ItemOpenBox() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entity, InteractionHand hand) {
        InteractionResultHolder<ItemStack> ar = super.use(world, entity, hand);

        if (world.isClientSide) {
            return ar;
        }

        if (entity instanceof ServerPlayer serverPlayer) {
            BlockPos pos = entity.blockPosition();
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, player) ->
                    new CsgoBoxCraftMenu(id, inventory, null), Component.literal("csgo_box_craft")));
        }

        return ar;
    }
}