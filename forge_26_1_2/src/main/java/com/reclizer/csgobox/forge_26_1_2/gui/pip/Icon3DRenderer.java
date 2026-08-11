package com.reclizer.csgobox.forge_26_1_2.gui.pip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Renders an Icon3DRenderState into the GUI's per-PIP render target using a
 * full 3D PoseStack. Restores the 1.21.1 "drag-to-rotate" GUI affordance for
 * the held-box preview and the won-item preview.
 *
 * <p>The PoseStack pipeline matches the 1.21.1 reference path:
 * {@code translate(8*scale, 8*scale)} -&gt; {@code scale(1, -1, 1)} -&gt;
 * {@code mulPose} rotations, after which the parent class applies
 * {@code renderState.scale * guiScale}. The (1, -1, -1) Y/Z flip in
 * {@link #renderToTexture} compensates for the PIP texture's flipped UVs,
 * and the preview is centred from the model's measured bounding box rather
 * than a fixed magic offset.</p>
 *
 * <p>Note: deliberately omits the {@code @OnlyIn(Dist.CLIENT)} annotation;
 * see {@link Icon3DRenderState} for the rationale.</p>
 */
public class Icon3DRenderer extends PictureInPictureRenderer<Icon3DRenderState> {

    public Icon3DRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<Icon3DRenderState> getRenderStateClass() {
        return Icon3DRenderState.class;
    }

    /**
     * Override the parent's default {@code getTranslateY() = height} (which
     * places the posestack origin at the texture's bottom-centre). We want
     * the origin at the texture centre, matching {@link OversizedItemRenderer}
     * and the 1.21.1 reference path where the slot centre is the model /
     * rotation anchor. Without this override the model is rendered offset
     * toward the top of the slot and clipped on the bottom.
     */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "csgo-box-3d-icon";
    }

    @Override
    protected void renderToTexture(Icon3DRenderState renderState, PoseStack poseStack) {
        poseStack.translate(
                -renderState.modelCenterX(),
                -renderState.modelCenterY(),
                -renderState.modelCenterZ());

        // (1, -1, -1) Y/Z flip compensates for the ortho projection's
        //    invertY=true, same as OversizedItemRenderer.
        poseStack.scale(1.0F, -1.0F, -1.0F);

        // User-driven rotation. Done AFTER the flip so the rotation lives
        //    in the same posestack frame as 1.21.1's
        //    scale(1, -1, 1) -> mulPose -> mulPose order. This keeps the
        //    horizontal-drag -> "model spins one way" mapping consistent with
        //    the 1.21.1 reference behaviour the rest of the GUI was tuned
        //    against.
        if (renderState.rotXDeg() != 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(renderState.rotXDeg()));
        }
        if (renderState.rotYDeg() != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(renderState.rotYDeg()));
        }
        if (renderState.rotZDeg() != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(renderState.rotZDeg()));
        }

        // 3D lighting path (matches the visual character of the 1.21.1 preview).
        Lighting.Entry lightingEntry = itemUsesFlatLight(renderState.itemStackRenderState())
                ? Lighting.Entry.ITEMS_FLAT
                : Lighting.Entry.ITEMS_3D;
        Minecraft.getInstance().gameRenderer.getLighting().setupFor(lightingEntry);

        FeatureRenderDispatcher dispatcher =
                Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
        renderState.itemStackRenderState().submit(
                poseStack,
                dispatcher.getSubmitNodeStorage(),
                15728880,
                OverlayTexture.NO_OVERLAY,
                0);
        dispatcher.renderAllFeatures();
    }

    /** Mirrors OversizedItemRenderer's flat-detection logic. */
    private static boolean itemUsesFlatLight(TrackingItemStackRenderState state) {
        return !state.usesBlockLight();
    }
}
