package com.reclizer.csgobox.forge_26_2.utils;

import com.reclizer.csgobox.forge_26_2.gui.pip.Icon3DRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

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
     * angles are converted from the 1.21.1 radian-space "atan" accumulation
     * to degrees for the PIP PoseStack rotation.
     *
     * <p>The item model is resolved into a {@link TrackingItemStackRenderState}
     * and submitted through the GUI's picture-in-picture renderer
     * ({@link Icon3DRenderState}), which renders it into a per-PIP texture at
     * preview resolution with a full 3D PoseStack. This restores the 1.21.1
     * "drag to spin the held item" behaviour and keeps the preview crisp —
     * the old {@code GuiGraphicsExtractor#item} path rasterised through the
     * GUI item atlas at slot size (guiScale px) and the pose scale then
     * blew that tiny texture up, making the crate look like a flat pixel
     * sprite instead of a 3D model.</p>
     */
    public static void renderItemInInventoryFollowsMouse(
            GuiGraphicsExtractor guiGraphics,
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

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        int textureSize = Math.max(1, Math.round(16.0F * scale));

        // Extract the item model into a render state the PIP renderer can submit.
        // TrackingItemStackRenderState is the subclass that registers a model
        // identity for texture caching across frames (matches GuiItemAtlas).
        TrackingItemStackRenderState trackedState = new TrackingItemStackRenderState();
        ItemModelResolver resolver = mc.getItemModelResolver();
        resolver.updateForLiving(trackedState, item, ItemDisplayContext.GUI, player);
        AABB bounds = trackedState.getModelBoundingBox();
        float modelSpan = (float) Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
        float modelCenterX = (float) ((bounds.minX + bounds.maxX) * 0.5D);
        float modelCenterY = (float) ((bounds.minY + bounds.maxY) * 0.5D);
        float modelCenterZ = (float) ((bounds.minZ + bounds.maxZ) * 0.5D);

        // (cx, cy) is the TOP-LEFT of the preview square, matching every
        // caller (CsboxScreen / CsLookItemScreen pass previewPixelX/Y,
        // BulkOverview passes centre - size/2). The PIP renderer centres the
        // model inside its target rect, so the square spans (cx, cy) ..
        // (cx+textureSize, cy+textureSize) and the model centre lands on
        // (cx+half, cy+half).
        Icon3DRenderState pipState = new Icon3DRenderState(
                trackedState,
                radiansToDegrees(angleXComponent),
                radiansToDegrees(angleYComponent),
                0.0F,
                textureSize,
                modelSpan,
                modelCenterX,
                modelCenterY,
                modelCenterZ,
                cx,
                cy,
                cx + textureSize,
                cy + textureSize);

        guiGraphics.getRenderState().addPicturesInPictureState(pipState);
    }

    private static float radiansToDegrees(float radians) {
        return radians * (180.0F / (float) Math.PI);
    }
}
