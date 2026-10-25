package com.reclizer.csgobox.forge_1_20_1.utils;

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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Single per-platform adaptation point for animation rendering primitives.
 * Screens and logic helpers (IconListTools / GuiItemMove) must call ONLY
 * through this class; version-varying render API lives here and nowhere else.
 * era: legacy (1.20.1)
 */
public final class AnimRenderOps {

    /** TACZ is a compileOnly optional dependency: every TACZ class touch in
     *  this file must sit behind an {@code isLoaded} gate first, so the JVM
     *  never resolves IGun/TimelessAPI when the mod is absent at runtime
     *  (same discipline as BoxItemCodec / TaczInspectViewportImpl). */
    private static final String TACZ_MOD_ID = "tacz";
    /** Superb Warfare (卓越前线) item namespace. Unlike TACZ no ModList gate
     *  is needed: the 3D branch only matches a registered item's namespace,
     *  and such an item cannot exist unless the mod is loaded. No Superb
     *  Warfare class is ever referenced. */
    private static final String SUPERB_WARFARE_MOD_ID = "superbwarfare";

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Set once the first supports3D() call has logged the render environment. */
    private static boolean logged3DEnvironment;
    /** Identity render stack for model rendering: bone transforms accumulate
     *  here while the outer placement lives in RenderSystem's model-view
     *  matrix. Safe to reuse on the render thread. */
    private static final PoseStack REUSABLE_POSE_STACK = new PoseStack();
    /** Scratch quaternion for the drag rotation: {@code mulPose} copies the
     *  value immediately, so a single static instance is safe on the render
     *  thread and avoids one allocation per 3D item per frame. */
    private static final Quaternionf SCRATCH_QUAT = new Quaternionf();
    /** Cached model-space centre of each TACZ gun's default-pose bounding
     *  box (block units). Gun displays are singletons per gun id and are
     *  replaced on resource reload, so a WeakHashMap lets old models go. */
    private static final Map<BedrockGunModel, Vector3f> GUN_MODEL_CENTERS = new WeakHashMap<>();
    /** Cached largest-axis span (block units) of each TACZ gun model, keyed
     *  and lifecycle-managed like {@link #GUN_MODEL_CENTERS}. */
    private static final Map<BedrockGunModel, Float> GUN_MODEL_SPANS = new WeakHashMap<>();
    /** Sentinel cached for models without usable geometry (custom renderers
     *  or degenerate bounds): distinguishes "not computed" from "nothing to
     *  centre", so those models are not rescanned every frame. */
    private static final Object NO_CENTER = new Object();
    /** Largest projected size of a preview square, as a fraction of its edge:
     *  leaves margin for the drag-rotation tilt so an oversized model cannot
     *  overflow the square (same 0.82 constant as the 26.x PIP path). */
    private static final float PREVIEW_FILL = 0.82F;
    /** Cached centre + largest-axis span (block units, in the drag-rotation
     *  space) of each baked item model's geometry. Models are stable
     *  instances owned by the model manager and are replaced on resource
     *  reload, so a WeakHashMap lets old models go. */
    private record ModelBounds(Vector3f center, float span) {
    }

    private static final Map<BakedModel, Object> ITEM_MODEL_BOUNDS = new WeakHashMap<>();

    private AnimRenderOps() {
    }

    /** Immediate-mode blit. Forces SRC_ALPHA: the convenience blit inherits
     *  whatever blend func is current, so translucent textures (spot glow,
     *  lens vignette) would otherwise render as hard opaque discs. */
    public static void blitTextured(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h) {
        blitTextured(gg, tex, x, y, w, h, w, h);
    }

    /** Variant carrying the texture's real pixel size: the convenience blit
     *  treats the source UV window as w×h, so an enlarged draw (gold_item.png
     *  32x24 at ~169x127) would push UV past 1.0. The whole texture maps
     *  onto the free-form w×h target rect with UV kept in [0,1]. */
    public static void blitTextured(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        gg.blit(tex, x, y, w, h, 0F, 0F, texW, texH, texW, texH);
    }

    /** Sprite-sheet variant: draws a w×h rect with the UV window (u,v,uw,vh)
     *  of a texW x texH texture, ARGB tint applied via shader color. The
     *  shader color is NOT auto-reset by the GUI pipeline, so this facade
     *  restores (1,1,1,1) itself before returning — callers never leak tint. */
    public static void blitTextured(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h,
                                    int u, int v, int uw, int vh, int texW, int texH, int tint) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        RenderSystem.setShaderColor(((tint >> 16) & 0xFF) / 255F,
                ((tint >> 8) & 0xFF) / 255F, (tint & 0xFF) / 255F, ((tint >> 24) & 0xFF) / 255F);
        gg.blit(tex, x, y, w, h, (float) u, (float) v, uw, vh, texW, texH);
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

    public static void renderBlurredBackground(GuiGraphics gg) {
        // 1.20.1 has no menu backdrop blur API (added in 1.20.2) — no-op.
    }

    /** 2D item icon with its top-left corner at (x, y), scaled (16px per
     *  block unit). Drawn through the shared buffer source and submitted
     *  immediately (endBatch) so an active scissor rect clips the icon. */
    public static void renderItem2D(LivingEntity entity, GuiGraphics gg, ItemStack stack, float x, float y, float scale) {
        // TACZ guns without a loaded display instance have no model to draw;
        // their custom renderer falls back to the missing-texture slot icon,
        // painting a magenta checkerboard across the card. Skip the draw so
        // the card frame alone shows until the display is available. The
        // isLoaded gate keeps instanceof IGun from ever executing (and thus
        // from class-loading TACZ) when the optional dependency is absent.
        if (ModList.get().isLoaded(TACZ_MOD_ID)
                && stack.getItem() instanceof IGun && TimelessAPI.getGunDisplay(stack).isEmpty()) {
            return;
        }
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), entity, 0);
        PoseStack pose = gg.pose();
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
        Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    /** 3D rotating item preview (drag-to-rotate). The raw quaternion from
     *  {@code ItemDrag3D} passes through unchanged — the drag scheme works in
     *  quaternion space. TACZ guns bypass the vanilla ItemRenderer (its GUI
     *  branch only draws the flat slot texture); see {@link #renderGunModel3D}. */
    public static void renderItem3D(GuiGraphics gg, ItemStack item, LivingEntity player,
                                    int cx, int cy, Quat rotation, float scale) {
        if (item == null || item.isEmpty() || player == null) return;
        // Shader-mod fallback (Iris/Oculus): degrade 3D previews (incl. TACZ
        // guns) to the 2D icon so nothing renders blank under shader packs
        // (see docs/SHADER-COMPAT.md).
        if (!supports3D()) {
            renderItem2D(player, gg, item, cx, cy, scale);
            return;
        }
        // Optional-dependency gate: without it the instanceof below would
        // class-load TACZ and crash clients that don't have it installed.
        if (ModList.get().isLoaded(TACZ_MOD_ID) && item.getItem() instanceof IGun) {
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
        // Superb Warfare keeps its 3D gun model out of the GUI perspective
        // (separate-transforms routes gui -> flat icon); every other context
        // renders the real model — exactly what an item frame shows. See
        // renderFixedContextModel3D.
        if (isSuperbWarfareItem(item)) {
            renderFixedContextModel3D(gg, item, player, cx, cy, rotation, scale);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(item, player.level(), player, 0);
        // Auto-fit: a model that projects beyond the preview square (oversized
        // datapack models) is shrunk to stay inside it; normal items (span <=
        // 1 block) keep the caller's scale unchanged.
        float fitScale = fitScaleFor(itemModelSpan(model), scale);
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        SCRATCH_QUAT.set(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize();
        pose.mulPose(SCRATCH_QUAT);
        Lighting.setupForEntityInInventory();
        pose.scale(16.0F * fitScale, 16.0F * fitScale, 16.0F * fitScale);
        // Rotate around the model's geometric centre, not the model origin:
        // block-style models span 0..1 with the origin at a corner, so an
        // origin pivot would swing the box around a point outside its body.
        // Same convention as the TACZ gun branch: translate(-centre) in block
        // units AFTER the 16px scale.
        Vector3f center = itemModelCenter(model);
        if (center != null) {
            pose.translate(-center.x, -center.y, -center.z);
        }
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        boolean flat = !model.usesBlockLight();
        if (flat) Lighting.setupForFlatItems();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        mc.getItemRenderer().render(item, ItemDisplayContext.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    /** Centre of the RENDERED geometry of a baked item model, in drag-rotation
     *  space (block units), or {@code null} for models without quads. Pushes
     *  every quad vertex through the exact {@code ItemRenderer.render} chain
     *  ({@code ItemTransform.apply} + {@code -0.5} shift) — rotation turns the
     *  AABB into an OBB, so a raw midpoint is wrong. Computed once per model. */
    private static Vector3f itemModelCenter(BakedModel model) {
        return modelBounds(model).center();
    }

    /** Largest-axis span (block units, in the drag-rotation space) of the
     *  model's rendered geometry, used by {@link #fitScaleFor} to auto-shrink
     *  oversized models so they stay inside the preview square. */
    private static float itemModelSpan(BakedModel model) {
        return modelBounds(model).span();
    }

    /** Auto-fit scale for a model of {@code modelSpan} block units rendered
     *  into a preview square of {@code 16 * scale} px (1 block = 16 px at
     *  scale 1): models that project beyond the square are shrunk so their
     *  largest axis fills only PREVIEW_FILL of it; everything else keeps the
     *  caller's scale. Same shrink rule as the 26.x PIP path
     *  ({@code targetPixelSize * 0.82 / modelSpan} in Icon3DRenderState). */
    private static float fitScaleFor(float modelSpan, float scale) {
        if (modelSpan <= 1.0F || modelSpan <= 0.0F) {
            return scale;
        }
        return scale * PREVIEW_FILL / modelSpan;
    }

    /** Sentinel bounds for models without usable geometry (custom renderers
     *  or degenerate quads): unit span keeps the caller's scale untouched. */
    private static final ModelBounds NO_BOUNDS = new ModelBounds(new Vector3f(), 1.0F);

    /** Bounding box centre + largest-axis span of the RENDERED geometry of a
     *  baked item model, in drag-rotation space (block units). Pushes every
     *  quad vertex through the exact {@code ItemRenderer.render} chain
     *  ({@code ItemTransform.apply} + {@code -0.5} shift) — rotation turns the
     *  AABB into an OBB, so a raw midpoint is wrong. Computed once per model. */
    private static ModelBounds modelBounds(BakedModel model) {
        Object cached = ITEM_MODEL_BOUNDS.get(model);
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_CENTER) {
            return NO_BOUNDS;
        }
        // Custom renderers (TACZ guns routed elsewhere, special items) have no
        // quads to measure; cache the sentinel so they are not rescanned.
        if (model.isCustomRenderer()) {
            ITEM_MODEL_BOUNDS.put(model, NO_CENTER);
            return NO_BOUNDS;
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
            ITEM_MODEL_BOUNDS.put(model, NO_CENTER);
            return NO_BOUNDS;
        }
        // Midpoint + largest axis of the rendered bounding box (block units).
        ModelBounds bounds = new ModelBounds(
                new Vector3f(
                        (minX + maxX) * 0.5F,
                        (minY + maxY) * 0.5F,
                        (minZ + maxZ) * 0.5F),
                Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)));
        ITEM_MODEL_BOUNDS.put(model, bounds);
        return bounds;
    }

    /** True for any Superb Warfare (卓越前线) item. The namespace check alone
     *  proves the optional mod is loaded, and no Superb Warfare class is
     *  touched, so unlike the TACZ gate there is nothing to gate against. */
    private static boolean isSuperbWarfareItem(ItemStack stack) {
        return !stack.isEmpty() && SUPERB_WARFARE_MOD_ID.equals(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    /** Superb Warfare 3D preview: same pose convention as the vanilla branch,
     *  but the render runs under {@code ItemDisplayContext.FIXED} — the
     *  item-frame context. The mod's separate-transforms model routes the gui
     *  perspective to a flat 2D icon and only the other perspectives carry
     *  the 3D gun (a GeckoLib custom renderer behind the per-gun display
     *  settings models), so FIXED is the cheapest context with a calibrated
     *  one-block transform. Quad auto-fit and centring are skipped: the
     *  fixed transform is already frame-fitted and the custom renderer has
     *  no quads to measure. A renderer failure degrades to the 2D icon. */
    private static void renderFixedContextModel3D(GuiGraphics gg, ItemStack item, LivingEntity player,
                                                  int cx, int cy, Quat rotation, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        BakedModel model = mc.getItemRenderer().getModel(item, player.level(), player, 0);
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        SCRATCH_QUAT.set(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize();
        pose.mulPose(SCRATCH_QUAT);
        Lighting.setupForEntityInInventory();
        pose.scale(16.0F * scale, 16.0F * scale, 16.0F * scale);
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        boolean rendered = true;
        try {
            mc.getItemRenderer().render(item, ItemDisplayContext.FIXED, false,
                    renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
            bufferSource.endBatch();
        } catch (Throwable t) {
            LOGGER.warn("Superb Warfare 3D preview render failed; falling back to the flat icon", t);
            rendered = false;
        } finally {
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            pose.popPose();
        }
        if (!rendered) {
            renderItem2D(player, gg, item, cx, cy, scale);
        }
    }

    /** TACZ gun 3D render: same pose convention as the vanilla branch, but
     *  the model is a {@link BedrockGunModel} drawn on an identity stack via
     *  RenderSystem's model-view matrix — the vanilla ItemRenderer path only
     *  draws the flat slot texture. */
    private static void renderGunModel3D(GuiGraphics gg, ItemStack item, int cx, int cy,
                                         Quat rotation, float scale,
                                         BedrockGunModel gunModel, GunDisplayInstance display) {
        // Auto-fit: gun models routinely span 2-3 blocks; shrink so the
        // largest axis stays inside the preview square (normal guns keep
        // their tuned look via the same PREVIEW_FILL rule as vanilla items).
        float fitScale = fitScaleFor(gunModelSpan(gunModel), scale);
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx + 8.0F * scale, cy + 8.0F * scale, 100.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        SCRATCH_QUAT.set(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize();
        pose.mulPose(SCRATCH_QUAT);
        pose.scale(16.0F * fitScale, 16.0F * fitScale, 16.0F * fitScale);
        // Rotate around the model's geometric centre, not the model origin:
        // the gun's root node sits far from its geometry, so a pivot at the
        // origin would swing the gun around a point outside the model.
        Vector3f center = gunModelCenter(gunModel);
        pose.translate(-center.x, -center.y, -center.z);
        Lighting.setupForEntityInInventory();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
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
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            pose.popPose();
        }
    }

    /** Model-space centre of the gun's default-pose bounding box (block
     *  units), computed once per model by walking the part tree with the base
     *  bone rotations ({@code cleanAnimationTransform} does not reset these). */
    public static Vector3f gunModelCenter(BedrockGunModel model) {
        return gunBounds(model).center();
    }

    /** Largest-axis span (block units) of the gun's default-pose bounding
     *  box, used by {@link #fitScaleFor} to auto-shrink oversized gun models
     *  so they stay inside the preview square. */
    private static float gunModelSpan(BedrockGunModel model) {
        return gunBounds(model).span();
    }

    /** Centre + span of the gun's default-pose bounding box, computed once
     *  per model by walking the part tree with the base bone rotations
     *  ({@code cleanAnimationTransform} does not reset these). */
    private static ModelBounds gunBounds(BedrockGunModel model) {
        Vector3f cached = GUN_MODEL_CENTERS.get(model);
        if (cached instanceof Vector3f) {
            return new ModelBounds(cached, GUN_MODEL_SPANS.getOrDefault(model, 1.0F));
        }
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        BedrockPart root = model.getRootNode();
        if (root != null) {
            collectPartBounds(root, new Matrix4f(), min, max);
        }
        ModelBounds bounds;
        if (min[0] == Float.MAX_VALUE) {
            bounds = NO_BOUNDS;
        } else {
            bounds = new ModelBounds(
                    new Vector3f((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2),
                    Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2])));
        }
        GUN_MODEL_CENTERS.put(model, bounds.center());
        GUN_MODEL_SPANS.put(model, bounds.span());
        return bounds;
    }

    private static void collectPartBounds(BedrockPart part, Matrix4f parent, float[] min, float[] max) {
        if (!part.visible) {
            return;
        }
        // Mirrors BedrockPart.translateAndRotateAndScale with the animation
        // state cleared (offset/quaternion/scales are identity by default).
        Matrix4f m = new Matrix4f(parent);
        // Two consecutive translates compose into one; /16 is exact (power of
        // two), so pre-division is lossless.
        m.translate(part.offsetX + part.x / 16.0F,
                part.offsetY + part.y / 16.0F,
                part.offsetZ + part.z / 16.0F);
        if (part.zRot != 0.0F) {
            m.rotateZ(part.zRot);
        }
        if (part.yRot != 0.0F) {
            m.rotateY(part.yRot);
        }
        if (part.xRot != 0.0F) {
            m.rotateX(part.xRot);
        }
        // JOML Matrix4f.rotate has no identity short-circuit and always
        // builds a quat->matrix product; the quaternion is identity in every
        // default-pose model (constructor and cleanAnimationTransform reset
        // it), so skip the full multiply when it is.
        if (part.additionalQuaternion.w != 1.0F || part.additionalQuaternion.x != 0.0F
                || part.additionalQuaternion.y != 0.0F || part.additionalQuaternion.z != 0.0F) {
            m.rotate(part.additionalQuaternion);
        }
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
        float mnX = minX / 16.0F, mnY = minY / 16.0F, mnZ = minZ / 16.0F;
        float mxX = maxX / 16.0F, mxY = maxY / 16.0F, mxZ = maxZ / 16.0F;
        Vector4f v = new Vector4f();
        addCorner(m, mnX, mnY, mnZ, v, min, max);
        addCorner(m, mnX, mnY, mxZ, v, min, max);
        addCorner(m, mnX, mxY, mnZ, v, min, max);
        addCorner(m, mnX, mxY, mxZ, v, min, max);
        addCorner(m, mxX, mnY, mnZ, v, min, max);
        addCorner(m, mxX, mnY, mxZ, v, min, max);
        addCorner(m, mxX, mxY, mnZ, v, min, max);
        addCorner(m, mxX, mxY, mxZ, v, min, max);
    }

    private static void addCorner(Matrix4f m, float x, float y, float z, Vector4f v, float[] min, float[] max) {
        v.set(x, y, z, 1.0F);
        m.transform(v);
        if (v.x < min[0]) min[0] = v.x;
        if (v.y < min[1]) min[1] = v.y;
        if (v.z < min[2]) min[2] = v.z;
        if (v.x > max[0]) max[0] = v.x;
        if (v.y > max[1]) max[1] = v.y;
        if (v.z > max[2]) max[2] = v.z;
    }

    /** Whether 3D item previews (PIP / drag-to-rotate / TACZ guns) can render
     *  in this client. Only shader-pack mods (Iris/Oculus) force the 2D
     *  fallback; Modern UI (modernui) deliberately does NOT: it officially
     *  renders vanilla-GUI-system mods unchanged, so 3D previews stay 3D
     *  under it. If a future Modern UI release is proven to break the 3D
     *  path, add it to isShaderModActive() (docs/MODERN-UI-COMPAT.md). */
    public static boolean supports3D() {
        if (!logged3DEnvironment) {
            logged3DEnvironment = true;
            LOGGER.info("[csgobox] 3D preview env: supports3D={} iris={} oculus={} modernui={}",
                    !isShaderModActive(), ModList.get().isLoaded("iris"),
                    ModList.get().isLoaded("oculus"), ModList.get().isLoaded("modernui"));
        }
        return !isShaderModActive();
    }

    /** True when Iris (NeoForge/Fabric) or Oculus (Forge) is installed: their
     *  shader packs intercept/ignore custom GUI 3D paths, so 3D previews
     *  degrade to 2D for correctness (probabilities/gameplay unaffected).
     *  Modern UI is intentionally excluded — see supports3D(). */
    private static boolean isShaderModActive() {
        return ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");
    }
}
