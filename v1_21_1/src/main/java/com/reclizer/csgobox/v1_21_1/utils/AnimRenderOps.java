package com.reclizer.csgobox.v1_21_1.utils;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.utils.Quat;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Single per-platform adaptation point for animation rendering primitives.
 * Screens and logic helpers (IconListTools / GuiItemMove) must call ONLY
 * through this class; version-varying render API lives here and nowhere else.
 * era: legacy
 */
public final class AnimRenderOps {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PoseStack REUSABLE_POSE_STACK = new PoseStack();
    /** Cached model-space centre of each TACZ gun's default-pose bounding
     *  box (block units). Gun displays are singletons per gun id and are
     *  replaced on resource reload, so a WeakHashMap lets old models go. */
    private static final Map<BedrockGunModel, Vector3f> GUN_MODEL_CENTERS = new WeakHashMap<>();

    /** Cached centre (block units, in the drag-rotation space) of each baked
     *  item model's geometry. Models are stable instances owned by the model
     *  manager and are replaced on resource reload, so a WeakHashMap lets old
     *  models go. */
    private static final Map<BakedModel, Vector3f> ITEM_MODEL_CENTERS = new WeakHashMap<>();

    /** Screen.renderBlurredBackground is protected in this MC version and
     *  depends on the screen's own {@code minecraft} instance, so the facade
     *  bridges it via reflection (invokes the very same method). */
    private static final Method SCREEN_RENDER_BLURRED_BACKGROUND;

    static {
        Method m;
        try {
            m = Screen.class.getDeclaredMethod("renderBlurredBackground", float.class);
            m.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Unable to resolve Screen.renderBlurredBackground", e);
        }
        SCREEN_RENDER_BLURRED_BACKGROUND = m;
    }

    private AnimRenderOps() {
    }

    /** Immediate-mode blit. Forces SRC_ALPHA: the 8-arg blit inherits whatever
     *  blend func is current, so translucent textures (spot glow, lens
     *  vignette) would otherwise render as hard opaque discs. */
    public static void blitTextured(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        gg.blit(tex, x, y, 0, 0, w, h, w, h);
    }

    /** Variant carrying the texture's real pixel size. 1.21.1's convenience
     *  blit(tex, x, y, u, v, w, h, texW, texH) treats the SOURCE UV window as
     *  width=w/height=h (uWidth=width internally), so passing a target size
     *  larger than the texture (gold_item.png is 32x24, drawn at ~169x127 in
     *  the opening strip) would push the UV window past 1.0 and wrap/stretch
     *  the icon. The 11-arg overload takes the source window explicitly:
     *  uWidth=texW/vHeight=texH keeps UV = [0,1] while width/height stay the
     *  free-form target size. */
    public static void blitTextured(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        gg.blit(tex, x, y, w, h, 0F, 0F, texW, texH, texW, texH);
    }

    /** Sprite-sheet variant: draws a UV window (u,v,uw,vh) of a texW x texH
     *  texture with an ARGB tint applied via shader color. Legacy immediate
     *  mode does NOT auto-reset the shader color, so this facade restores
     *  (1,1,1,1) itself before returning — callers never leak the tint. */
    public static void blitTextured(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h,
                                    int u, int v, int uw, int vh, int texW, int texH, int tint) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        RenderSystem.setShaderColor(((tint >> 16) & 0xFF) / 255F,
                ((tint >> 8) & 0xFF) / 255F, (tint & 0xFF) / 255F, ((tint >> 24) & 0xFF) / 255F);
        gg.blit(tex, x, y, w, h, u, v, uw, vh, texW, texH);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    public static void fill(GuiGraphics gg, int x0, int y0, int x1, int y1, int color) {
        gg.fill(x0, y0, x1, y1, color);
    }

    public static void fillGradient(GuiGraphics gg, int x0, int y0, int x1, int y1, int c0, int c1) {
        gg.fillGradient(x0, y0, x1, y1, c0, c1);
    }

    public static void scissor(GuiGraphics gg, int x, int y, int w, int h) {
        gg.enableScissor(x, y, x + w, y + h);
    }

    public static void scissorDisable(GuiGraphics gg) {
        gg.disableScissor();
    }

    public static void setBlendNormal(GuiGraphics gg) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
    }

    public static void flush(GuiGraphics gg) {
        gg.flush();
    }

    public static void renderBlurredBackground(Screen screen, GuiGraphics gg, float partialTicks) {
        try {
            SCREEN_RENDER_BLURRED_BACKGROUND.invoke(screen, partialTicks);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Unable to render blurred background", e);
        }
    }

    /** 2D item icon centred at (x, y), scaled (16px per block unit). */
    public static void renderItem2D(LivingEntity entity, GuiGraphics gg, ItemStack stack, float x, float y, float scale) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), entity, 0);
        // TACZ guns without a loaded display instance have no model to draw;
        // their custom renderer falls back to the missing-texture slot icon,
        // painting a magenta checkerboard across the card. Skip the draw so
        // the card frame alone shows until the display is available.
        if (stack.getItem() instanceof IGun && TimelessAPI.getGunDisplay(stack).isEmpty()) {
            return;
        }
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(x, y, 2F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 0F);
        pose.scale(16.0F * scale, 16.0F * scale, 0F);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean flat = !model.usesBlockLight();
        if (flat) Lighting.setupForFlatItems();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** 3D rotating item preview (drag-to-rotate). The rotation is the raw
     *  unit quaternion produced by {@link ItemDrag3D} — the drag-feel scheme
     *  (One-Euro + arcball + damped spring) works in quaternion space, so we
     *  pass it through unchanged instead of projecting onto two euler angles.
     *  TACZ guns bypass the vanilla ItemRenderer: TACZ's GUI branch only
     *  draws the flat slot texture, so the bedrock gun model is rendered
     *  directly (see {@link #renderGunModel3D}). */
    public static void renderItem3D(GuiGraphics gg, ItemStack item, LivingEntity player,
                                    int cx, int cy, Quat rotation, float scale) {
        if (item == null || item.isEmpty() || player == null) return;
        if (item.getItem() instanceof IGun) {
            Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(item);
            // No loaded display -> nothing to draw (TACZ would fall back to the
            // missing-texture slot icon, painting a magenta checkerboard).
            if (display.isEmpty()) {
                return;
            }
            BedrockGunModel gunModel = display.get().getGunModel();
            if (gunModel == null) {
                return;
            }
            renderGunModel3D(gg, item, cx, cy, rotation, scale, gunModel, display.get());
            return;
        }
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(item, player.level(), player, 0);
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        pose.mulPose(new Quaternionf(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize());
        Lighting.setupForEntityInInventory();
        pose.scale(16.0F * scale, 16.0F * scale, 16.0F * scale);
        // Rotate around the model's geometric centre, not the model origin:
        // block-style models span 0..1 with the origin at a corner, so an
        // origin pivot would swing the box around a point outside its body.
        // Same convention as the TACZ gun branch (and the 26.x PIP renderer):
        // translate(-centre) in block units AFTER the 16px scale.
        Vector3f center = itemModelCenter(model);
        if (center != null) {
            pose.translate(-center.x, -center.y, -center.z);
        }
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean flat = !model.usesBlockLight();
        if (flat) Lighting.setupForFlatItems();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        Minecraft.getInstance().getItemRenderer().render(item, ItemDisplayContext.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** Centre of the RENDERED geometry of a baked item model, in the space
     *  the drag rotation is applied (block units), or {@code null} for models
     *  without quads (custom renderers). Every quad vertex is pushed through
     *  the exact chain {@code ItemRenderer.render} applies ({@code
     *  ItemTransform.apply} then the {@code -0.5} shift) and the centre of
     *  the resulting bounding box is taken — the same semantics as 26.x's
     *  {@code getModelBoundingBox} centre, and more accurate than pushing
     *  just the raw diagonal midpoint through the transform (rotation turns
     *  the AABB into an OBB, so the two no longer coincide). Computed once
     *  per model instance and cached. */
    private static Vector3f itemModelCenter(BakedModel model) {
        Vector3f cached = ITEM_MODEL_CENTERS.get(model);
        if (cached != null) {
            return cached;
        }
        // Compose the renderer's model placement: ItemTransform.apply is
        // translate(translation) -> rotationXYZ -> scale, then render() adds
        // translate(-0.5, -0.5, -0.5) so 0..1 block geometry centres at origin.
        ItemTransform gui = model.getTransforms().gui;
        Matrix4f renderer = new Matrix4f();
        if (gui != ItemTransform.NO_TRANSFORM) {
            renderer.translate(gui.translation.x(), gui.translation.y(), gui.translation.z());
            renderer.rotate(new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(gui.rotation.x()),
                    (float) Math.toRadians(gui.rotation.y()),
                    (float) Math.toRadians(gui.rotation.z())));
            renderer.scale(gui.scale.x(), gui.scale.y(), gui.scale.z());
        }
        renderer.translate(-0.5F, -0.5F, -0.5F);
        RandomSource random = RandomSource.create();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        boolean any = false;
        Vector3f p = new Vector3f();
        for (Direction direction : Direction.values()) {
            for (BakedQuad quad : model.getQuads(null, direction, random)) {
                int[] vertices = quad.getVertices();
                for (int i = 0; i < 4; i++) {
                    p.set(Float.intBitsToFloat(vertices[i * 8]),
                            Float.intBitsToFloat(vertices[i * 8 + 1]),
                            Float.intBitsToFloat(vertices[i * 8 + 2]));
                    renderer.transformPosition(p);
                    minX = Math.min(minX, p.x);
                    minY = Math.min(minY, p.y);
                    minZ = Math.min(minZ, p.z);
                    maxX = Math.max(maxX, p.x);
                    maxY = Math.max(maxY, p.y);
                    maxZ = Math.max(maxZ, p.z);
                    any = true;
                }
            }
        }
        for (BakedQuad quad : model.getQuads(null, null, random)) {
            int[] vertices = quad.getVertices();
            for (int i = 0; i < 4; i++) {
                p.set(Float.intBitsToFloat(vertices[i * 8]),
                        Float.intBitsToFloat(vertices[i * 8 + 1]),
                        Float.intBitsToFloat(vertices[i * 8 + 2]));
                renderer.transformPosition(p);
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
                minZ = Math.min(minZ, p.z);
                maxX = Math.max(maxX, p.x);
                maxY = Math.max(maxY, p.y);
                maxZ = Math.max(maxZ, p.z);
                any = true;
            }
        }
        if (!any || !(minX < maxX && minY < maxY && minZ < maxZ)) {
            return null;
        }
        // Midpoint of the rendered bounding box (block units).
        Vector3f center = new Vector3f(
                (minX + maxX) * 0.5F,
                (minY + maxY) * 0.5F,
                (minZ + maxZ) * 0.5F);
        ITEM_MODEL_CENTERS.put(model, center);
        return center;
    }

    /** TACZ gun 3D render: same outer pose convention as the vanilla branch
     *  (anchor = top-left of the preview square, Y-flip, drag quaternion,
     *  16px per block unit) but the model is a {@link BedrockGunModel} drawn
     *  on an identity stack with the placement in RenderSystem's model-view
     *  matrix — TACZ's GUI item rendering draws only the flat slot texture,
     *  so the vanilla ItemRenderer path cannot carry the drag rotation. */
    private static void renderGunModel3D(GuiGraphics gg, ItemStack item, int cx, int cy,
                                         Quat rotation, float scale,
                                         BedrockGunModel gunModel, GunDisplayInstance display) {
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx + 8.0F * scale, cy + 8.0F * scale, 100.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        pose.mulPose(new Quaternionf(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize());
        pose.scale(16.0F * scale, 16.0F * scale, 16.0F * scale);
        // Rotate around the model's geometric centre, not the model origin:
        // the gun's root node sits far from its geometry, so a pivot at the
        // origin would swing the gun around a point outside the model.
        Vector3f center = gunModelCenter(gunModel);
        pose.translate(-center.x, -center.y, -center.z);
        Lighting.setupForEntityInInventory();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        REUSABLE_POSE_STACK.setIdentity();
        RenderType renderType = display.enablesTransparency()
                ? RenderType.entityTranslucent(display.getModelTexture())
                : RenderType.entityCutout(display.getModelTexture());
        try {
            gunModel.render(REUSABLE_POSE_STACK, item, ItemDisplayContext.GUI, renderType,
                    0xF000F0, OverlayTexture.NO_OVERLAY);
            gunModel.cleanAnimationTransform();
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        } catch (Throwable t) {
            LOGGER.warn("TACZ gun model render failed in 3D preview", t);
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            pose.popPose();
        }
    }

    /** Model-space centre of the gun's default-pose bounding box (block
     *  units), computed once per model instance by walking the part tree with
     *  the base bone rotations ({@code xRot/yRot/zRot} are the static bedrock
     *  pose — {@code cleanAnimationTransform} resets offsets/quaternions/scales,
     *  not these). */
    public static Vector3f gunModelCenter(BedrockGunModel model) {
        Vector3f cached = GUN_MODEL_CENTERS.get(model);
        if (cached != null) {
            return cached;
        }
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        BedrockPart root = model.getRootNode();
        if (root != null) {
            collectPartBounds(root, new Matrix4f(), min, max);
        }
        Vector3f center;
        if (min[0] == Float.MAX_VALUE) {
            center = new Vector3f();
        } else {
            center = new Vector3f((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2);
        }
        GUN_MODEL_CENTERS.put(model, center);
        return center;
    }

    private static void collectPartBounds(BedrockPart part, Matrix4f parent, float[] min, float[] max) {
        if (!part.visible) {
            return;
        }
        // Mirrors BedrockPart.translateAndRotateAndScale with the animation
        // state cleared (offset/quaternion/scales are identity by default).
        Matrix4f m = new Matrix4f(parent);
        m.translate(part.offsetX, part.offsetY, part.offsetZ);
        m.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
        if (part.zRot != 0.0F) {
            m.rotateZ(part.zRot);
        }
        if (part.yRot != 0.0F) {
            m.rotateY(part.yRot);
        }
        if (part.xRot != 0.0F) {
            m.rotateX(part.xRot);
        }
        // Identity quaternion is a harmless no-op; JOML 1.10.5 has no isIdentity().
        m.rotate(part.additionalQuaternion);
        m.scale(part.xScale, part.yScale, part.zScale);
        for (BedrockCube cube : part.cubes) {
            // Cube coordinates are in 1/16-block pixels; all 8 corners are
            // needed because the base bone rotations turn boxes into OBBs.
            if (cube instanceof BedrockCubeBox box) {
                addBoxCorners(m, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, min, max);
            } else if (cube instanceof BedrockCubePerFace face) {
                addBoxCorners(m, face.minX, face.minY, face.minZ, face.maxX, face.maxY, face.maxZ, min, max);
            }
        }
        for (BedrockPart child : part.children) {
            collectPartBounds(child, m, min, max);
        }
    }

    private static void addBoxCorners(Matrix4f m, float minX, float minY, float minZ,
                                      float maxX, float maxY, float maxZ, float[] min, float[] max) {
        addCorner(m, minX / 16.0F, minY / 16.0F, minZ / 16.0F, min, max);
        addCorner(m, minX / 16.0F, minY / 16.0F, maxZ / 16.0F, min, max);
        addCorner(m, minX / 16.0F, maxY / 16.0F, minZ / 16.0F, min, max);
        addCorner(m, minX / 16.0F, maxY / 16.0F, maxZ / 16.0F, min, max);
        addCorner(m, maxX / 16.0F, minY / 16.0F, minZ / 16.0F, min, max);
        addCorner(m, maxX / 16.0F, minY / 16.0F, maxZ / 16.0F, min, max);
        addCorner(m, maxX / 16.0F, maxY / 16.0F, minZ / 16.0F, min, max);
        addCorner(m, maxX / 16.0F, maxY / 16.0F, maxZ / 16.0F, min, max);
    }

    private static void addCorner(Matrix4f m, float x, float y, float z, float[] min, float[] max) {
        Vector4f v = m.transform(new Vector4f(x, y, z, 1.0F));
        if (v.x < min[0]) min[0] = v.x;
        if (v.y < min[1]) min[1] = v.y;
        if (v.z < min[2]) min[2] = v.z;
        if (v.x > max[0]) max[0] = v.x;
        if (v.y > max[1]) max[1] = v.y;
        if (v.z > max[2]) max[2] = v.z;
    }

    public static boolean supports3D() {
        return true;
    }
}
