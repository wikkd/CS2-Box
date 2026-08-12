package com.reclizer.csgobox.v1_21_1.utils;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.reclizer.csgobox.utils.Quat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Single per-platform adaptation point for animation rendering primitives.
 * Screens and logic helpers (IconListTools / GuiItemMove) must call ONLY
 * through this class; version-varying render API lives here and nowhere else.
 * era: legacy
 */
public final class AnimRenderOps {
    private static final PoseStack REUSABLE_POSE_STACK = new PoseStack();

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
     *  pass it through unchanged instead of projecting onto two euler angles. */
    public static void renderItem3D(GuiGraphics gg, ItemStack item, LivingEntity player,
                                    int cx, int cy, Quat rotation, float scale) {
        if (item == null || item.isEmpty() || player == null) return;
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(item, player.level(), player, 0);
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        pose.mulPose(new Quaternionf(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize());
        Lighting.setupForEntityInInventory();
        pose.scale(16.0F * scale, 16.0F * scale, 16.0F * scale);
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

    public static boolean supports3D() {
        return true;
    }
}
