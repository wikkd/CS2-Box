package com.reclizer.csgobox.v1_21_1.utils;

import com.reclizer.csgobox.utils.ItemDrag3D;
import com.reclizer.csgobox.utils.Quat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * GUI-side adapter for the 3D item preview drag. The drag math itself lives
 * in {@link ItemDrag3D} (common, pure math): screens own an {@code ItemDrag3D}
 * instance, feed it raw pointer deltas in {@code mouseDragged}, advance it per
 * frame in their render pass, and render the resulting quaternion through
 * {@link #renderItemInInventoryFollowsMouse}.
 */
public final class GuiItemMove {
    private GuiItemMove() {
    }

    /**
     * Renders an item preview with user-driven 3D rotation. The rotation is
     * the unit quaternion produced by {@link ItemDrag3D}; the facade applies
     * it in the GUI pose stack (1.21.1 reference path).
     */
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
}
