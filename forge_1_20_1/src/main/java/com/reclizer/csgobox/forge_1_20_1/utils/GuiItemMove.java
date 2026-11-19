package com.reclizer.csgobox.forge_1_20_1.utils;

import com.reclizer.csgobox.utils.Quat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class GuiItemMove {
    private GuiItemMove() {
    }

    public static float renderRotAngleY(double mouseDelta, float itemRot) {
        float delta = (float) Math.atan(mouseDelta / 40.0F);
        return Math.max(-1.5F, Math.min(1.5F, delta + itemRot));
    }

    public static float renderRotAngleX(double mouseDelta, float itemRot) {
        float delta = (float) Math.atan(mouseDelta / 40.0F);
        return Math.max(-3.0F, Math.min(3.0F, delta + itemRot));
    }

    public static void renderItemInInventoryFollowsMouse(
            GuiGraphics guiGraphics,
            int cx,
            int cy,
            float angleXComponent,
            float angleYComponent,
            ItemStack item,
            LivingEntity player,
            float scale
    ) {
        if (item == null || item.isEmpty() || player == null) {
            return;
        }
        Quat rotation = Quat.mul(Quat.fromAxisAngle(1F, 0F, 0F, angleXComponent),
                Quat.fromAxisAngle(0F, 1F, 0F, angleYComponent));
        AnimRenderOps.renderItem3D(guiGraphics, item, player, cx, cy, rotation, scale);
    }

    public static void renderItemInInventoryFollowsMouse(
            GuiGraphics guiGraphics,
            int x,
            int y,
            Quat rotation,
            ItemStack item,
            LivingEntity player,
            float scale
    ) {
        AnimRenderOps.renderItem3D(guiGraphics, item, player, x, y, rotation, scale);
    }

    /** 检视页方块特效（BE 动画 + 粒子）会话 begin/end 转发：AnimRenderOps 侧
     *  保持包私有（不进公开 op 表面），跨包入口走这里。 */
    public static void beginInspectEffects(ItemStack item) {
        AnimRenderOps.beginInspectEffects(item);
    }

    public static void endInspectEffects() {
        AnimRenderOps.endInspectEffects();
    }
}
