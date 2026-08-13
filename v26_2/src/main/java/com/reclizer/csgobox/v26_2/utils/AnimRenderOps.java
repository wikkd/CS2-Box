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

    /** Decoupled rendering runs through RenderPipelines, which carry their
     *  own blend state - no flush/state juggling needed (GuiGraphicsExtractor
     *  has no flush() at all in 26.1.2). */
    public static void blitTextured(GuiGraphicsExtractor gg, Identifier tex, int x, int y, int w, int h) {
        blitTextured(gg, tex, x, y, w, h, w, h);
    }

    /** Variant carrying the texture's real pixel size. 26.1.2's convenience
     *  blit(RenderPipeline, tex, x, y, u, v, w, h, texW, texH) treats the
     *  SOURCE UV window as width=w/height=h (srcWidth=width internally), so
     *  passing a target size larger than the texture (gold_item.png is 32x24,
     *  drawn at ~169x127 in the opening strip) would push the UV window past
     *  1.0 and wrap/stretch the icon. The 12-arg overload takes the source
     *  window explicitly: srcWidth=texW/srcHeight=texH keeps UV = [0,1] while
     *  width/height stay the free-form target size. */
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
                    // item() renders the model into a 16x16 GUI icon with the
                    // model ORIGIN pinned to pixel (8,8) of that icon and one
                    // block unit = 16px (GuiItemAtlas.drawToSlot translates to
                    // the slot centre then scales by 16). The visual centre
                    // therefore lands at (8 + 16*centreX, 8 + 16*centreY);
                    // shift by the negated value so it pins onto (pX,pY).
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
                // Model measurement is best-effort; centre the draw so icons
                // never drift toward the bottom-right on a resolver hiccup.
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

    /** 3D rotating item preview (drag-to-rotate). The rotation is the raw
     *  unit quaternion produced by {@link ItemDrag3D} — the drag-feel scheme
     *  (One-Euro + arcball + damped spring) works in quaternion space, so we
     *  pass it through unchanged instead of projecting onto two euler angles.
     *  Decoupled path: the item re-routes through the PictureInPicture
     *  renderer (Icon3DRenderState) to restore 1.21.1's drag-to-spin. */
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

        // Extract the item model into a render state the PIP renderer can submit.
        // TrackingItemStackRenderState is the subclass that registers a model
        // identity for texture caching across frames (matches OversizedItemRenderer).
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
            // Some flat items (armour leggings etc.) report no bounds; fall
            // back to a unit model so the PIP renderer still centres it
            // (mirrors renderItem2D's null-bounds handling).
            modelSpan = 1.0F;
            modelCenterX = 0.0F;
            modelCenterY = 0.0F;
            modelCenterZ = 0.0F;
        }

        // (cx, cy) is the TOP-LEFT of the preview square, matching the
        // 1.21.1 reference renderItem3D and every 26.x caller (CsboxScreen /
        // TerminalBootScreen pass previewPixelX/Y, BulkOverview passes
        // centre - size/2). The PIP renderer centres the model inside its
        // target rect, so the square spans (cx, cy) .. (cx+size, cy+size)
        // and the model centre lands on (cx+half, cy+half).
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
