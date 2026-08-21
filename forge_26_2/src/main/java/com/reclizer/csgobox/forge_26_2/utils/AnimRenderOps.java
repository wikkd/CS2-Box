package com.reclizer.csgobox.forge_26_2.utils;

import com.reclizer.csgobox.utils.Quat;
import com.reclizer.csgobox.forge_26_2.gui.pip.Icon3DRenderState;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
        // Forge's Icon3DRenderState carries Euler angles (degrees) instead of
        // the common Quat; decompose in the same XYZ order the PIP renderer
        // applies (mulPose X, then Y, then Z) so the visual matches the
        // decoupled reference.
        Quaternionf jq = new Quaternionf(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize();
        Vector3f euler = jq.getEulerAnglesXYZ(new Vector3f());
        float rotXDeg = euler.x * (180.0F / (float) Math.PI);
        float rotYDeg = euler.y * (180.0F / (float) Math.PI);
        float rotZDeg = euler.z * (180.0F / (float) Math.PI);

        Icon3DRenderState pipState = new Icon3DRenderState(
                trackedState,
                rotXDeg,
                rotYDeg,
                rotZDeg,
                textureSize,
                modelSpan,
                modelCenterX,
                modelCenterY,
                modelCenterZ,
                cx,
                cy,
                cx + textureSize,
                cy + textureSize);

        guiGraphics.getRenderState().addPicturesInPictureState(pipState);
    }

    public static boolean supports3D() {
        return true;
    }

    // ---- HD rounded rect / pill (9-slice, no bitmap down-scaling) ----

    /** 8x8 four-quadrant corner mask (rounded_corner.png): each 4x4 quadrant
     *  is one corner, drawn at original size; the border ring blits them 1.5x
     *  and the edges are 1px fill rects. */
    private static final Identifier TEX_ROUNDED_CORNER = Identifier.parse("csgobox:textures/gui/terminal/rounded_corner.png");
    /** 16x8 pill end-cap mask (terminal_cap.png): left half = left cap, right
     *  half = mirrored right cap; drawn at ~diameter h (8 -> 7/9 px). */
    public static final Identifier TEX_PILL_CAP = Identifier.parse("csgobox:textures/gui/terminal/terminal_cap.png");

    /** Rounded rectangle with a fixed ~3.5px corner radius, crisp at any
     *  size: the 4x4 corner quadrants render at original size (1:1), the
     *  border ring at 1.5x + 1px rect edges, the body as fill rects. */
    public static void drawRoundedRect(GuiGraphicsExtractor gg, int x, int y, int w, int h,
                                       int fill, int border) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (w < 8 || h < 8) {
            // Too small for a 4px corner: plain bordered rect.
            fill(gg, x - 1, y - 1, x + w + 1, y + h + 1, border);
            fill(gg, x, y, x + w, y + h, fill);
            return;
        }
        int c = 4;
        int bc = 6;
        // border ring: corners 1.5x, edges 1px
        blitTextured(gg, TEX_ROUNDED_CORNER, x - 1, y - 1, bc, bc, 0, 0, 4, 4, 8, 8, border);
        blitTextured(gg, TEX_ROUNDED_CORNER, x + w + 1 - bc, y - 1, bc, bc, 4, 0, 4, 4, 8, 8, border);
        blitTextured(gg, TEX_ROUNDED_CORNER, x - 1, y + h + 1 - bc, bc, bc, 0, 4, 4, 4, 8, 8, border);
        blitTextured(gg, TEX_ROUNDED_CORNER, x + w + 1 - bc, y + h + 1 - bc, bc, bc, 4, 4, 4, 4, 8, 8, border);
        fill(gg, x + 3, y - 1, x + w - 3, y + 1, border);
        fill(gg, x + 3, y + h - 1, x + w - 3, y + h + 1, border);
        fill(gg, x - 1, y + 3, x + 1, y + h - 3, border);
        fill(gg, x + w - 1, y + 3, x + w + 1, y + h - 3, border);
        // fill: corners 1:1 + body
        blitTextured(gg, TEX_ROUNDED_CORNER, x, y, c, c, 0, 0, 4, 4, 8, 8, fill);
        blitTextured(gg, TEX_ROUNDED_CORNER, x + w - c, y, c, c, 4, 0, 4, 4, 8, 8, fill);
        blitTextured(gg, TEX_ROUNDED_CORNER, x, y + h - c, c, c, 0, 4, 4, 4, 8, 8, fill);
        blitTextured(gg, TEX_ROUNDED_CORNER, x + w - c, y + h - c, c, c, 4, 4, 4, 4, 8, 8, fill);
        fill(gg, x + c, y, x + w - c, y + h, fill);
        fill(gg, x, y + c, x + w, y + h - c, fill);
    }

    /** Pill/capsule: two semicircle end caps from the 16x8 cap texture plus a
     *  fill-rect body. Caps render at diameter h (8 -> 7/9 px, <=25% error)
     *  instead of the old 32 -> h circle down-scale that aliased badly. */
    public static void drawPill(GuiGraphicsExtractor gg, int x, int y, int w, int h,
                                int fill, int border) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (h < 5 || w < h) {
            // Too small for the cap texture (or degenerate): plain bordered rect.
            fill(gg, x - 1, y - 1, x + w + 1, y + h + 1, border);
            fill(gg, x, y, x + w, y + h, fill);
            return;
        }
        int bd = h + 2;
        // border ring: caps 1px larger + body rect
        blitTextured(gg, TEX_PILL_CAP, x - 1, y - 1, bd, bd, 0, 0, 8, 8, 16, 8, border);
        blitTextured(gg, TEX_PILL_CAP, x + w + 1 - bd, y - 1, bd, bd, 8, 0, 8, 8, 16, 8, border);
        fill(gg, x + h / 2, y - 1, x + w - h / 2, y + h + 1, border);
        // fill: caps at diameter h + body
        blitTextured(gg, TEX_PILL_CAP, x, y, h, h, 0, 0, 8, 8, 16, 8, fill);
        blitTextured(gg, TEX_PILL_CAP, x + w - h, y, h, h, 8, 0, 8, 8, 16, 8, fill);
        fill(gg, x + h / 2, y, x + w - h / 2, y + h, fill);
    }
}
