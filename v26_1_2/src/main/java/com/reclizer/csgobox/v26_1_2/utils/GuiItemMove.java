package com.reclizer.csgobox.v26_1_2.utils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class GuiItemMove {
    private GuiItemMove() {
    }

    public static float renderRotAngleY(double mouseDelta, float itemRot) {
        // 1.21.1 used radian ATAN clamping; here we keep the same numeric
        // shape so caller-side mouse-drag accumulation doesn't drift.
        float delta = (float) Math.atan(mouseDelta / 40.0F);
        return Math.clamp(delta + itemRot, -1.5F, 1.5F);
    }

    public static float renderRotAngleX(double mouseDelta, float itemRot) {
        float delta = (float) Math.atan(mouseDelta / 40.0F);
        return Math.clamp(delta + itemRot, -3.0F, 3.0F);
    }

    /**
     * Renders an item preview with user-driven 3D rotation. The mouse-drag
     * angles (radians) are passed to the facade, which converts them to
     * degrees for the PictureInPicture renderer path.
     */
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
        AnimRenderOps.renderItem3D(guiGraphics, item, player, x, y, angleXComponent, angleYComponent, scale);
    }
}
