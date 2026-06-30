package com.reclizer.csgobox.v26_1_2.utils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
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
            GuiGraphicsExtractor guiGraphics,
            int x,
            int y,
            float angleXComponent,
            float angleYComponent,
            ItemStack item,
            LivingEntity player,
            float scale
    ) {
        if (item == null || item.isEmpty() || player == null) return;
        int pixelX = x;
        int pixelY = y;
        int seed = (int)(player.getUUID().getLeastSignificantBits() & 0x7FFFFFFFL);
        guiGraphics.pose().pushMatrix();
        if (scale != 1.0F) {
            guiGraphics.pose().scale(scale, scale);
        }
        guiGraphics.item(player, item, pixelX, pixelY, seed);
        guiGraphics.pose().popMatrix();
    }
}