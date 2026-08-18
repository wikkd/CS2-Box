package com.reclizer.csgobox.v26_2.utils;

import com.reclizer.csgobox.utils.Quat;
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

    /** RenderPipelines carry their own blend state — no flush/state juggling
     *  (GuiGraphicsExtractor has no flush() in 26.1.2). */
    public static void blitTextured(GuiGraphicsExtractor gg, Identifier tex, int x, int y, int w, int h) {
        blitTextured(gg, tex, x, y, w, h, w, h);
    }

    /** Variant carrying the texture's real pixel size: the convenience blit
     *  treats the source UV window as w×h, so an enlarged draw (gold_item.png
     *  32x24 at ~169x127) would push UV past 1.0. The 12-arg overload keeps
     *  UV = [0,1] and uses width/height as the free-form target size. */
    public static void blitTextured(GuiGraphicsExtractor gg, Identifier tex, int x, int y, int w, int h, int texW, int texH) {
        gg.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0F, 0F, w, h, texW, texH, texW, texH);
    }

    /** Sprite-sheet variant: draws a UV window (u,v,uw,vh) of a texW x texH
     *  texture with an ARGB tint (26.x blit takes the tint as its last arg;
     *  the decoupled pipeline carries the blend state itself). */
    public static void blitTextured(GuiGraphicsExtractor gg, Identifier tex, int x, int y, int w, int h,
                                    int u, int v, int uw, int vh, int texW, int texH, int tint) {
        gg.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, uw, vh, texW, texH, tint);
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
        // Measure the model's true extents and offset the draw so every
        // item's visual centre lands on the same pixel.
        Minecraft mc = Minecraft.getInstance();
        float offsetX = 0;
        float offsetY = 0;
        if (mc != null) {
            try {
                TrackingItemStackRenderState tracked = new TrackingItemStackRenderState();
                mc.getItemModelResolver().updateForLiving(tracked, itemStack, ItemDisplayContext.GUI, entity);
                AABB bounds = tracked.getModelBoundingBox();
                if (bounds != null) {
                    // The 16x16 GUI icon pins the model origin at (8,8) with
                    // 1 block unit = 16px, so the visual centre lands at
                    // (8 + 16*centreX, 8 + 16*centreY); negate to pin (pX,pY).
                    offsetX = -16F * (float) ((bounds.minX + bounds.maxX) * 0.5D) - 8F;
                    offsetY = -16F * (float) ((bounds.minY + bounds.maxY) * 0.5D) - 8F;
                } else {
                    // Some flat items (armour leggings etc.) report no bounds;
                    // centre the top-left-anchored 16px GUI draw instead of
                    // letting it float to the bottom-right of the target.
                    offsetX = -8F;
                    offsetY = -8F;
                }
            } catch (Throwable ignored) {
                // Best-effort measurement; centre so icons never drift bottom-right.
                offsetX = -8F;
                offsetY = -8F;
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

    /** 3D rotating item preview (drag-to-rotate). The raw quaternion from
     *  {@link ItemDrag3D} passes through unchanged — the drag scheme works in
     *  quaternion space. Decoupled path re-routes through the PIP renderer
     *  (Icon3DRenderState) to restore 1.21.1's drag-to-spin. */
    public static void renderItem3D(GuiGraphicsExtractor guiGraphics, ItemStack item, LivingEntity player,
                                    int cx, int cy, Quat rotation, float scale) {
        if (item == null || item.isEmpty() || player == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        int textureSize = Math.max(1, Math.round(16.0F * scale));

        // TrackingItemStackRenderState registers a model identity so the PIP
        // renderer caches the texture across frames (matches OversizedItemRenderer).
        TrackingItemStackRenderState trackedState = new TrackingItemStackRenderState();
        ItemModelResolver resolver = mc.getItemModelResolver();
        resolver.updateForLiving(trackedState, item, ItemDisplayContext.GUI, player);
        AABB bounds = trackedState.getModelBoundingBox();
        float modelSpan;
        float modelCenterX;
        float modelCenterY;
        float modelCenterZ;
        if (bounds != null) {
            modelSpan = (float) Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
            modelCenterX = (float) ((bounds.minX + bounds.maxX) * 0.5D);
            modelCenterY = (float) ((bounds.minY + bounds.maxY) * 0.5D);
            modelCenterZ = (float) ((bounds.minZ + bounds.maxZ) * 0.5D);
        } else {
            // Flat items may report no bounds; a unit model keeps them centred.
            modelSpan = 1.0F;
            modelCenterX = 0.0F;
            modelCenterY = 0.0F;
            modelCenterZ = 0.0F;
        }

        // (cx, cy) is the TOP-LEFT of the preview square (all 26.x callers
        // pass top-left; the PIP renderer centres the model inside it).
        Icon3DRenderState pipState = new Icon3DRenderState(
                trackedState,
                rotation,
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
}
