package com.reclizer.csgobox.v1_21_1.utils;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class GuiItemMove {

    private GuiItemMove() {
    }

    public static float renderRotAngleY(double mouseDelta, float itemRot) {
        float delta = (float) Math.atan(mouseDelta / 40.0F);
        return Math.clamp(delta + itemRot, -1.5F, 1.5F);
    }

    public static float renderRotAngleX(double mouseDelta, float itemRot) {
        float delta = (float) Math.atan(mouseDelta / 40.0F);
        return Math.clamp(delta + itemRot, -3.0F, 3.0F);
    }

    public static void renderItemInInventoryFollowsMouse(
            GuiGraphics guiGraphics,
            int x,
            int y,
            float angleXComponent,
            float angleYComponent,
            ItemStack item,
            LivingEntity player,
            float scale
    ) {
        AnimRenderOps.renderItem3D(guiGraphics, item, player, x, y, angleXComponent, angleYComponent, scale);
    }
}
