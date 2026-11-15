package com.reclizer.csgobox.v26_1_2.gui.pip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.reclizer.csgobox.utils.Quat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;

/**
 * Renders an {@link EntityIcon3DRenderState} into the GUI's per-PIP render
 * target using a full 3D PoseStack — the entity mirror of
 * {@link Icon3DRenderer}, so spawn eggs, armor stands, summon containers and
 * same-id entities spin with exactly the drag feel the item previews use.
 *
 * <p>The PoseStack pipeline matches {@link Icon3DRenderer}:
 * {@code translate(-centre)} -&gt; {@code scale(1, -1, -1)} (PIP texture UV
 * compensation) -&gt; {@code mulPose(rotation)}, after which the parent class
 * applies {@code renderState.scale * guiScale}. The entity itself is
 * submitted through the vanilla {@link EntityRenderDispatcher#submit} state
 * path with a default (identity) camera — preview entities are parked facing
 * the viewer (yRot 180) and stay fully visible (invisible starves
 * LivingEntity renderers; the PIP target has no ground plane, so no shadow
 * pass to suppress).</p>
 */
public class EntityIcon3DRenderer extends PictureInPictureRenderer<EntityIcon3DRenderState> {

    public EntityIcon3DRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<EntityIcon3DRenderState> getRenderStateClass() {
        return EntityIcon3DRenderState.class;
    }

    /** Same rationale as {@link Icon3DRenderer#getTranslateY}: origin at the
     *  texture centre, matching the item renderer and the 1.21.1 reference
     *  path. */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "csgo-box-entity-3d-icon";
    }

    @Override
    protected void renderToTexture(EntityIcon3DRenderState renderState, PoseStack poseStack) {
        poseStack.translate(
                -renderState.modelCenterX(),
                -renderState.modelCenterY(),
                -renderState.modelCenterZ());

        // (1, -1, -1) Y/Z flip compensates for the ortho projection's
        // invertY=true, identical to the item icon renderer.
        poseStack.scale(1.0F, -1.0F, -1.0F);

        // User-driven rotation, same frame as the item preview (flip first,
        // then mulPose — see Icon3DRenderer for the full comment).
        Quat q = renderState.rotation();
        poseStack.mulPose(new Quaternionf(q.x(), q.y(), q.z(), q.w()).normalize());

        // Entity-in-UI lighting (vanilla GuiEntityRenderer's choice).
        Minecraft.getInstance().gameRenderer.getLighting()
                .setupFor(Lighting.Entry.ENTITY_IN_UI);

        FeatureRenderDispatcher dispatcher =
                Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
        EntityRenderDispatcher entityDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        // Identity camera: the preview entity is parked facing the viewer.
        entityDispatcher.submit(renderState.entityRenderState(), new CameraRenderState(),
                0.0D, 0.0D, 0.0D, poseStack, dispatcher.getSubmitNodeStorage());
        dispatcher.renderAllFeatures();
    }
}
