package com.reclizer.csgobox.v26_2.gui.pip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.reclizer.csgobox.utils.Quat;
import org.joml.Quaternionf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Renders an {@link Icon3DRenderState} into the GUI's per-PIP render target
 * using a full 3D PoseStack. Restores the 1.21.1 "drag-to-rotate" affordance
 * for the held-box preview and the won-item preview.
 *
 * <p>Mirrors the 26.2 vanilla {@code OversizedItemRenderer} shape: the
 * parent {@link PictureInPictureRenderer} is now annotation-only (no
 * constructor) and drives {@code featureRenderDispatcher.renderAllFeatures}
 * itself, so we just submit into the supplied {@link SubmitNodeCollector}
 * and let the parent flush. {@code getTranslateY} override keeps the
 * pose origin at the texture centre (matching the 1.21.1 reference
 * path where the slot centre is the rotation anchor).</p>
 *
 * <p>Omits the {@code @OnlyIn(Dist.CLIENT)} annotation; see
 * {@link Icon3DRenderState} for the rationale. The parent class
 * re-added the annotation in 26.2 but our subclass only runs through
 * a client-side registration guard so the runtime warning would be
 * noise.</p>
 */
public class Icon3DRenderer extends PictureInPictureRenderer<Icon3DRenderState> {

    @Override
    public Class<Icon3DRenderState> getRenderStateClass() {
        return Icon3DRenderState.class;
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "csgo-box-3d-icon";
    }

    @Override
    protected void renderToTexture(Icon3DRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        poseStack.translate(
                -renderState.modelCenterX(),
                -renderState.modelCenterY(),
                -renderState.modelCenterZ());

        // (1, -1, -1) Y/Z flip compensates for the ortho projection's
        //    invertY=true, same as OversizedItemRenderer.
        poseStack.scale(1.0F, -1.0F, -1.0F);

        // User-driven rotation. Done AFTER the flip so the rotation lives
        //    in the same posestack frame as 1.21.1's
        //    scale(1, -1, 1) -> mulPose -> mulPose order. The state carries a
        //    unit quaternion (the drag-feel algorithm's orientation) instead
        //    of two euler angles, so the horizontal-drag -> "model spins one
        //    way" mapping stays exactly as tuned and no gimbal projection
        //    loss occurs.
        Quat q = renderState.rotation();
        poseStack.mulPose(new Quaternionf(q.x(), q.y(), q.z(), q.w()).normalize());

        // 3D lighting path (matches the visual character of the 1.21.1 preview).
        Lighting.Entry lightingEntry = itemUsesFlatLight(renderState.itemStackRenderState())
                ? Lighting.Entry.ITEMS_FLAT
                : Lighting.Entry.ITEMS_3D;
        Minecraft.getInstance().gameRenderer.lighting().setupFor(lightingEntry);

        // 26.2 API: the parent PictureInPictureRenderer owns the
        //    SubmitNodeStorage and calls renderAllFeatures() on the
        //    FeatureRenderDispatcher itself after this method returns.
        //    We just submit into the collector supplied as parameter.
        renderState.itemStackRenderState().submit(
                poseStack,
                submitNodeCollector,
                15728880,
                OverlayTexture.NO_OVERLAY,
                0);
    }

    /** Mirrors OversizedItemRenderer's flat-detection logic. */
    private static boolean itemUsesFlatLight(TrackingItemStackRenderState state) {
        return !state.usesBlockLight();
    }
}