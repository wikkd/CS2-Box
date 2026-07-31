package com.reclizer.csgobox.v1_21_10.utils;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Utilities for rendering items that follow mouse movement.
 * Adapted for NeoForge 21.8.x / MC 1.21.8 - uses PoseStack with Matrix3x2fStack support.
 */
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
        if (item == null || item.isEmpty()) return;
        int seed = (int) (player.getUUID().getLeastSignificantBits() & 0x7FFFFFFFL);
        Minecraft mc = Minecraft.getInstance();
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(x, y, 100.0F);
        poseStack.translate(8.0F * scale, 8.0F * scale, 0.0F);
        poseStack.scale(1.0F, -1.0F, 1.0F);
        poseStack.mulPose(Axis.XP.rotation(angleYComponent));
        poseStack.mulPose(Axis.YP.rotation(angleXComponent));
        poseStack.scale(16.0F * scale, 16.0F * scale, 16.0F * scale);
        if (mc.gameRenderer != null && mc.gameRenderer.getLighting() != null) {
            mc.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
        }
        guiGraphics.renderItem(player, item, 0, 0, seed);
        poseStack.popPose();
    }
}
