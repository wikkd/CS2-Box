package com.reclizer.csgobox.v26_2.utils;

import com.reclizer.csgobox.v26_2.gui.pip.Icon3DRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Single per-platform adaptation point for animation rendering primitives.
 * Screens and logic helpers (IconListTools / GuiItemMove) must call ONLY
 * through this class; version-varying render API lives here and nowhere else.
 * era: decoupled
 */
public final class AnimRenderOps {

    private AnimRenderOps() {
    }

    /** Decoupled rendering runs through RenderPipelines, which carry their
     *  own blend state - no flush/state juggling needed (GuiGraphicsExtractor
     *  has no flush() at all in 26.1.2). */
    public static void blitTextured(GuiGraphicsExtractor gg, Identifier tex, int x, int y, int w, int h) {
        blitTextured(gg, tex, x, y, w, h, w, h);
    }

    /** Variant carrying the texture's real pixel size. 26.1.2's blit treats
     *  the trailing args as texW/texH (not UVs), so callers like
     *  IconListTools.blitGoldItemAspect (gold_item.png is 32x24) must pass
     *  them explicitly or the UV window is wrong. */
    public static void blitTextured(GuiGraphicsExtractor gg, Identifier tex, int x, int y, int w, int h, int texW, int texH) {
        gg.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0F, 0F, w, h, texW, texH);
    }

    public static void fill(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1, int color) {
        gg.fill(x0, y0, x1, y1, color);
    }

    public static void fillGradient(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1, int c0, int c1) {
        gg.fillGradient(x0, y0, x1, y1, c0, c1);
    }

    public static void scissor(GuiGraphicsExtractor gg, int x, int y, int w, int h) {
        gg.enableScissor(x, y, x + w, y + h);
    }

    public static void scissorDisable(GuiGraphicsExtractor gg) {
        gg.disableScissor();
    }

    /** No-op: decoupled pipelines own their blend state. */
    public static void setBlendNormal(GuiGraphicsExtractor gg) {
    }

    /** No-op: GuiGraphicsExtractor has no flush() in 26.1.2. */
    public static void flush(GuiGraphicsExtractor gg) {
    }

    /** 26.1.2 screens blur via the extractBlurredBackground stratum hook;
     *  the facade forwards to it. */
    public static void renderBlurredBackground(GuiGraphicsExtractor gg) {
        gg.blurBeforeThisStratum();
    }

    /** 2D item icon centred at (x, y), scaled (16px per block unit), with
     *  per-item model-bounds centering (mirrors IconListTools.renderGuiItem). */
    public static void renderItem2D(LivingEntity entity, GuiGraphicsExtractor guiGraphics, ItemStack itemStack,
                                    float pX, float pY, float scale) {
        if (itemStack == null || itemStack.isEmpty() || entity == null) return;
        int seed = (int) (entity.getUUID().getLeastSignificantBits() & 0x7FFFFFFFL);
        // Per-item visual baseline (P1-3): measure the model's true extents
        // and offset the draw so the visual centre of every item (swords,
        // tools, armour, boxes) lands on the same pixel, instead of letting
        // the model's own asymmetry float it within the slot.
        Minecraft mc = Minecraft.getInstance();
        float offsetX = 0;
        float offsetY = 0;
        if (mc != null) {
            try {
                TrackingItemStackRenderState tracked = new TrackingItemStackRenderState();
                mc.getItemModelResolver().updateForLiving(tracked, itemStack, ItemDisplayContext.GUI, entity);
                AABB bounds = tracked.getModelBoundingBox();
                if (bounds != null) {
                    // Model space is roughly -8..+8 for a 16px item. Shift so
                    // the measured centre sits at the slot centre.
                    offsetX = -((float) ((bounds.minX + bounds.maxX) * 0.5D));
                    offsetY = -((float) ((bounds.minY + bounds.maxY) * 0.5D));
                }
            } catch (Throwable ignored) {
                // Model measurement is best-effort; fall back to the previous
                // top-left anchored draw on any resolver hiccup.
            }
        }
        // Anchor-relative scale: translate to the target pixel first, then
        // apply the scale so that drawing at (0,0) lands at the original (pX,pY).
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(pX, pY);
        if (scale != 1.0F) guiGraphics.pose().scale(scale, scale);
        guiGraphics.pose().translate(offsetX, offsetY);
        guiGraphics.item(entity, itemStack, 0, 0, seed);
        guiGraphics.pose().popMatrix();
    }

    /** 3D rotating item preview (drag-to-rotate). Angle params are radians;
     *  callers pass exactly what GuiItemMove.renderRotAngleX/Y produce.
     *  Decoupled path: the item re-routes through the PictureInPicture
     *  renderer (Icon3DRenderState) to restore 1.21.1's drag-to-spin. */
    public static void renderItem3D(GuiGraphicsExtractor guiGraphics, ItemStack item, LivingEntity player,
                                    int cx, int cy, float angleXComponent, float angleYComponent, float scale) {
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
        // identity for texture caching across frames (matches OversizedItemRenderer).
        TrackingItemStackRenderState trackedState = new TrackingItemStackRenderState();
        ItemModelResolver resolver = mc.getItemModelResolver();
        resolver.updateForLiving(trackedState, item, ItemDisplayContext.GUI, player);
        AABB bounds = trackedState.getModelBoundingBox();
        float modelSpan = (float) Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
        float modelCenterX = (float) ((bounds.minX + bounds.maxX) * 0.5D);
        float modelCenterY = (float) ((bounds.minY + bounds.maxY) * 0.5D);
        float modelCenterZ = (float) ((bounds.minZ + bounds.maxZ) * 0.5D);

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

        guiGraphics.submitPictureInPictureRenderState(pipState);
    }

    public static boolean supports3D() {
        return true;
    }

    private static float radiansToDegrees(float radians) {
        return radians * (180.0F / (float) Math.PI);
    }
}
