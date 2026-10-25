package com.reclizer.csgobox.forge_1_18_2.utils;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.utils.Quat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Single per-platform adaptation point for animation rendering primitives.
 * 1.18.2 edition: MC has no GuiGraphics — everything is PoseStack-based, and
 * the model-view stack is a PoseStack (pushPose/mulPoseMatrix) instead of the
 * 1.20.1+ Matrix4fStack. era: legacy
 *
 * <p>1.18.2 notes: menu blur does not exist yet ({@link #renderBlurredBackground}
 * is a no-op); TACZ gun 3D render branches are not ported (TACZ 1.1.4's client
 * model API differs from 1.1.8 — box stacks fall back to the vanilla path,
 * identical to every platform without TACZ); scissor uses RenderSystem with
 * explicit GUI-to-framebuffer coordinate conversion.</p>
 */
public final class AnimRenderOps extends net.minecraft.client.gui.GuiComponent {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Set once the first supports3D() call has logged the render environment. */
    private static boolean logged3DEnvironment;
    private static final PoseStack REUSABLE_POSE_STACK = new PoseStack();
    private static final Quaternion SCRATCH_QUAT = new Quaternion(0, 0, 0, 1);
    private static final Object NO_CENTER = new Object();
    private static final float PREVIEW_FILL = 0.82F;

    private record ModelBounds(Vector3f center, float span) {
    }

    private static final Map<BakedModel, Object> ITEM_MODEL_BOUNDS = new WeakHashMap<>();

    private AnimRenderOps() {
    }

    /** Immediate-mode blit: 1.18.2 has no texture-carrying blit — bind first. */
    public static void blitTextured(PoseStack pose, ResourceLocation tex, int x, int y, int w, int h) {
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        blit(pose, x, y, w, h, 0F, 0F, w, h, w, h);
    }

    public static void blitTextured(PoseStack pose, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        blit(pose, x, y, w, h, 0F, 0F, texW, texH, texW, texH);
    }

    public static void blitTextured(PoseStack pose, ResourceLocation tex, int x, int y, int w, int h,
                                    int u, int v, int uw, int vh, int texW, int texH, int tint) {
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        RenderSystem.setShaderColor(((tint >> 16) & 0xFF) / 255F,
                ((tint >> 8) & 0xFF) / 255F, (tint & 0xFF) / 255F, ((tint >> 24) & 0xFF) / 255F);
        blit(pose, x, y, w, h, u, v, uw, vh, texW, texH);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /** 1.18.2 has no GuiGraphics.enableScissor — convert GUI coords to the
     *  framebuffer window ourselves (GUI y grows down, GL y grows up). */
    /** 1.18.2: GuiComponent.fillGradient is protected — the facade re-exposes
     *  it under a distinct name (inherited via the GuiComponent base class). */
    public static void fillGradientOp(PoseStack pose, int x0, int y0, int x1, int y1, int c0, int c1) {
        fillGradient(pose, x0, y0, x1, y1, c0, c1, 0);
    }

    public static void scissor(PoseStack pose, int x, int y, int w, int h) {
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int fbY = window.getHeight() - (int) Math.round((y + h) * scale);
        RenderSystem.enableScissor(
                (int) Math.round(x * scale), fbY,
                (int) Math.round(w * scale), (int) Math.round(h * scale));
    }

    public static void scissorDisable(PoseStack pose) {
        RenderSystem.disableScissor();
    }

    public static void setBlendNormal(PoseStack pose) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
    }

    /** 1.18.2 renders in immediate mode — nothing to flush. */
    public static void flush(PoseStack pose) {
    }

    /** MC 1.18.2 has no menu blur (added in 1.20.2+) — silent no-op. */
    public static void renderBlurredBackground(PoseStack pose) {
    }

    /** 2D item icon centred at (x, y), scaled (16px per block unit). */
    public static void renderItem2D(LivingEntity entity, PoseStack pose, ItemStack stack, float x, float y, float scale) {
        BakedModel model = Minecraft.getInstance().getItemRenderer()
                .getModel(stack, entity.level, entity, 0);
        pose.pushPose();
        pose.translate(x, y, 2F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 0F);
        pose.scale(16.0F * scale, 16.0F * scale, 0F);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean flat = !model.usesBlockLight();
        if (flat) Lighting.setupForFlatItems();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        Minecraft.getInstance().getItemRenderer().render(stack,
                ItemTransforms.TransformType.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    /** 3D rotating item preview (drag-to-rotate). */
    public static void renderItem3D(PoseStack pose, ItemStack item, LivingEntity player,
                                    int cx, int cy, Quat rotation, float scale) {
        if (item == null || item.isEmpty() || player == null) return;
        // Shader-mod fallback (Iris/Oculus): degrade 3D previews to the 2D icon
        // (see docs/SHADER-COMPAT.md).
        if (!supports3D()) {
            renderItem2D(player, pose, item, cx, cy, scale);
            return;
        }
        BakedModel model = Minecraft.getInstance().getItemRenderer()
                .getModel(item, player.level, player, 0);
        float fitScale = fitScaleFor(itemModelSpan(model), scale);
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        // 1.18.2 Quaternion has no value accessors — normalize via the common
        // Quat components before constructing the Mojang instance.
        float qx = rotation.x(), qy = rotation.y(), qz = rotation.z(), qw = rotation.w();
        float qlen = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        if (qlen > 1.0E-6F) {
            qx /= qlen; qy /= qlen; qz /= qlen; qw /= qlen;
        }
        pose.mulPose(new Quaternion(qx, qy, qz, qw));
        Lighting.setupForEntityInInventory();
        pose.scale(16.0F * fitScale, 16.0F * fitScale, 16.0F * fitScale);
        Vector3f center = itemModelCenter(model);
        if (center != null) {
            pose.translate(-center.x(), -center.y(), -center.z());
        }
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean flat = !model.usesBlockLight();
        if (flat) Lighting.setupForFlatItems();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        Minecraft.getInstance().getItemRenderer().render(item,
                ItemTransforms.TransformType.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private static Vector3f itemModelCenter(BakedModel model) {
        return modelBounds(model).center();
    }

    private static float itemModelSpan(BakedModel model) {
        return modelBounds(model).span();
    }

    private static float fitScaleFor(float modelSpan, float scale) {
        if (modelSpan <= 1.0F || modelSpan <= 0.0F) {
            return scale;
        }
        return scale * PREVIEW_FILL / modelSpan;
    }

    private static final ModelBounds NO_BOUNDS = new ModelBounds(new Vector3f(), 1.0F);

    /** Bounding box centre + largest-axis span of the model's rendered geometry. */
    private static ModelBounds modelBounds(BakedModel model) {
        Object cached = ITEM_MODEL_BOUNDS.get(model);
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_CENTER) {
            return NO_BOUNDS;
        }
        if (model.isCustomRenderer()) {
            ITEM_MODEL_BOUNDS.put(model, NO_CENTER);
            return NO_BOUNDS;
        }
        ItemTransform gui = model.getTransforms().gui;
        Matrix4f renderer = new Matrix4f();
        if (gui != ItemTransform.NO_TRANSFORM) {
            renderer.multiply(Matrix4f.createTranslateMatrix(
                    gui.translation.x(), gui.translation.y(), gui.translation.z()));
            renderer.multiply(Quaternion.fromXYZ(
                    (float) Math.toRadians(gui.rotation.x()),
                    (float) Math.toRadians(gui.rotation.y()),
                    (float) Math.toRadians(gui.rotation.z())));
            renderer.multiply(Matrix4f.createScaleMatrix(
                    gui.scale.x(), gui.scale.y(), gui.scale.z()));
        }
        renderer.multiply(Matrix4f.createTranslateMatrix(-0.5F, -0.5F, -0.5F));
        Random random = new Random();
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
                    transformPoint(renderer, p);
                    minX = Math.min(minX, p.x());
                    minY = Math.min(minY, p.y());
                    minZ = Math.min(minZ, p.z());
                    maxX = Math.max(maxX, p.x());
                    maxY = Math.max(maxY, p.y());
                    maxZ = Math.max(maxZ, p.z());
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
                transformPoint(renderer, p);
                minX = Math.min(minX, p.x());
                minY = Math.min(minY, p.y());
                minZ = Math.min(minZ, p.z());
                maxX = Math.max(maxX, p.x());
                maxY = Math.max(maxY, p.y());
                maxZ = Math.max(maxZ, p.z());
                any = true;
            }
        }
        if (!any || !(minX < maxX && minY < maxY && minZ < maxZ)) {
            ITEM_MODEL_BOUNDS.put(model, NO_CENTER);
            return NO_BOUNDS;
        }
        ModelBounds bounds = new ModelBounds(
                new Vector3f(
                        (minX + maxX) * 0.5F,
                        (minY + maxY) * 0.5F,
                        (minZ + maxZ) * 0.5F),
                Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)));
        ITEM_MODEL_BOUNDS.put(model, bounds);
        return bounds;
    }

    /** 1.18.2 Matrix4f has no point-transform method — multiply manually via
     *  store(FloatBuffer) (GL column-major: translation lands at indices
     *  12/13/14, rows are 4 elements apart). */
    private static void transformPoint(Matrix4f m, Vector3f p) {
        java.nio.FloatBuffer buf = java.nio.ByteBuffer.allocateDirect(16 * 4).asFloatBuffer();
        m.store(buf);
        float x = p.x(), y = p.y(), z = p.z();
        float nx = buf.get(0) * x + buf.get(4) * y + buf.get(8) * z + buf.get(12);
        float ny = buf.get(1) * x + buf.get(5) * y + buf.get(9) * z + buf.get(13);
        float nz = buf.get(2) * x + buf.get(6) * y + buf.get(10) * z + buf.get(14);
        p.set(nx, ny, nz);
    }

    /** Whether 3D item previews can render in this client (Iris/Oculus force
     *  the 2D fallback; Modern UI deliberately does NOT — see
     *  docs/MODERN-UI-COMPAT.md). */
    public static boolean supports3D() {
        if (!logged3DEnvironment) {
            logged3DEnvironment = true;
            LOGGER.info("[csgobox] 3D preview env: supports3D={} iris={} oculus={} modernui={}",
                    !isShaderModActive(), ModList.get().isLoaded("iris"),
                    ModList.get().isLoaded("oculus"), ModList.get().isLoaded("modernui"));
        }
        return !isShaderModActive();
    }

    private static boolean isShaderModActive() {
        return ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");
    }
}
