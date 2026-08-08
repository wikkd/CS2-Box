package com.reclizer.csgobox.v1_21_11.utils;

import com.reclizer.csgobox.utils.ColorTools;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class IconListTools {

    private static final Identifier GOLD_ITEM_TEXTURE =
            Identifier.parse("csgobox:textures/screens/gold_item.png");

    // Real pixel dimensions of gold_item.png (verified via `file`).
    // Required as textureWidth / textureHeight in 26.1.2's
    // blit(RenderPipeline, Identifier, x, y, u0, v0, w, h, texW, texH).
    private static final int GOLD_ITEM_TEX_WIDTH = 32;
    private static final int GOLD_ITEM_TEX_HEIGHT = 24;
/**
     * Peak magnification of the card sitting at the golden line during the
     * opening animation (1.0 = no magnification). The card whose left edge is
     * closest to the line scales up toward this factor; neighbors ramp back
     * down to 1.0.
     */
    public static final float FOCUS_PEAK_SCALE = 1.2F;

    /** Focus reaches 1.0 (no magnification) at this many card spacings from the line. */
    public static final float FOCUS_FALLOFF_SPACING = 1.0F;



    private IconListTools() {
    }

    // Letterbox the 32x24 gold_item icon into an arbitrary destination
    // rectangle while preserving its native 4:3 aspect ratio. The grade-5
    // slot is slightly more square than the source on a 16:9 screen, so a
    // plain scale-to-fit stretches the gem vertically and squashes the
    // chain links. The padding that falls out of letterboxing is left as
    // background gradient (drawn by the caller) rather than retinted, so
    // the gold bar at the slot edges stays visible.
    private static void blitGoldItemAspect(GuiGraphics guiGraphics,
                                           int x, int y, int availW, int availH) {
        int drawW;
        int drawH;
        if ((long) availW * GOLD_ITEM_TEX_HEIGHT < (long) availH * GOLD_ITEM_TEX_WIDTH) {
            drawW = availW;
            drawH = Math.round((float) availW * GOLD_ITEM_TEX_HEIGHT / GOLD_ITEM_TEX_WIDTH);
        } else {
            drawH = availH;
            drawW = Math.round((float) availH * GOLD_ITEM_TEX_WIDTH / GOLD_ITEM_TEX_HEIGHT);
        }
        int drawX = x + (availW - drawW) / 2;
        int drawY = y + (availH - drawH) / 2;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GOLD_ITEM_TEXTURE,
                drawX, drawY,
                0F, 0F,
                drawW, drawH,
                GOLD_ITEM_TEX_WIDTH, GOLD_ITEM_TEX_HEIGHT);
    }

    private static void renderRarity(GuiGraphics guiGraphics, int pX0, int pY0, int toX, int toY, int color) {
        guiGraphics.fillGradient(pX0, pY0, toX, toY, 0xFF696969, 0xFFD3D3D3);
        guiGraphics.fill(pX0, pY0, pX0 + 2, toY, color);
    }

    public static void renderItemFrame(LivingEntity entity, GuiGraphics guiGraphics, ItemStack itemStack, int pX, int pY, int width, int height, int grade) {
        int color = ColorTools.colorItems(grade);

        int frameWidth = width * 8 / 100;
        int frameHeight = height * 11 / 100;
        float scale = frameWidth * 60F / 100F / 16F;
        int toX = pX + frameWidth;
        int toY = pY + frameHeight;
        int itemX = pX + frameWidth * 20 / 100;
        int itemY = pY + frameHeight * 10 / 100;
        if (grade == 5) {
            guiGraphics.fillGradient(pX, pY, toX, toY, 0xFF533c00, 0xFFb69008);
            guiGraphics.fill(pX, pY, pX + 2, toY, color);
            // 26.1.2 changed blit: must pass RenderPipeline explicitly and
            // last two ints are textureWidth/textureHeight (pixels), not UVs.
            blitGoldItemAspect(guiGraphics, pX + 2, pY + 2,
                    frameWidth - 4, frameHeight - 4);
        } else {
            renderRarity(guiGraphics, pX, pY, toX, toY, color);
            renderGuiItem(entity, guiGraphics, itemStack, itemX, itemY, scale);
        }
    }

    public static void renderRewardCell(LivingEntity entity, GuiGraphics guiGraphics, ItemStack itemStack, int pX, int pY, int width, int height, int grade) {
        int color = ColorTools.colorItems(grade);
        int pad = Math.max(3, Math.min(8, width / 10));
        int iconW = Math.max(8, width - pad * 2);
        int iconH = Math.max(8, height - pad * 2);
        int itemX = pX + (width - iconW) / 2;
        int itemY = pY + (height - iconH) / 2;
        if (grade == 5) {
            guiGraphics.fillGradient(pX, pY, pX + width, pY + height, 0xFF533c00, 0xFFb69008);
            guiGraphics.fill(pX, pY, pX + 2, pY + height, color);
            blitGoldItemAspect(guiGraphics, pX + 2, pY + 2, width - 4, height - 4);
        } else {
            guiGraphics.fillGradient(pX, pY, pX + width, pY + height, 0xFF696969, 0xFFD3D3D3);
            guiGraphics.fill(pX, pY, pX + 2, pY + height, color);
            renderGuiItem(entity, guiGraphics, itemStack, itemX, itemY, iconW / 16F);
        }
    }

    public static void renderGuiItem(LivingEntity entity, GuiGraphics guiGraphics, ItemStack itemStack, float pX, float pY, float scale) {
        if (itemStack == null || itemStack.isEmpty() || entity == null) return;
        int seed = (int) (entity.getUUID().getLeastSignificantBits() & 0x7FFFFFFFL);
        // Per-item visual baseline (P1-3): measure the model's true extents
        // and offset the draw so the visual centre of every item (swords,
        // tools, armour, boxes) lands on the same pixel.
        Minecraft mc = Minecraft.getInstance();
        float offsetX = 0;
        float offsetY = 0;
        if (mc != null) {
            try {
                TrackingItemStackRenderState tracked = new TrackingItemStackRenderState();
                mc.getItemModelResolver().updateForLiving(tracked, itemStack, ItemDisplayContext.GUI, entity);
                AABB bounds = tracked.getModelBoundingBox();
                if (bounds != null) {
                    offsetX = -((float) ((bounds.minX + bounds.maxX) * 0.5D));
                    offsetY = -((float) ((bounds.minY + bounds.maxY) * 0.5D));
                }
            } catch (Throwable ignored) {
                // Model measurement is best-effort; fall back to the previous
                // top-left anchored draw on any resolver hiccup.
            }
        }
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(pX, pY);
        if (scale != 1.0F) guiGraphics.pose().scale(scale, scale);
        guiGraphics.renderItem(entity, itemStack, 0, 0, seed);
        guiGraphics.pose().popMatrix();
    }

    public static void renderItemProgress(LivingEntity entity, GuiGraphics guiGraphics, ItemStack itemStack, float pX, float pY, float width, float height, int grade) {
        int color = ColorTools.colorItems(grade);
        float frameWidth = width * 18 / 100F;
        float frameHeight = height * 25 / 100F;
        float scale = frameWidth * 60F / 100F / 16F;
        int toX = (int)(pX + frameWidth);
        int toY = (int)(pY + frameHeight);
        float itemX = pX + frameWidth * 20 / 100F;
        float itemY = pY + frameHeight * 10 / 100F;
        if (grade == 5) {
            guiGraphics.fillGradient((int) pX, (int) pY, toX, toY, 0xFF533c00, 0xFFb69008);
            blitGoldItemAspect(guiGraphics, (int) (pX + 2F), (int) (pY + 2F),
                    (int) (frameWidth - 4F), (int) (frameHeight - 4F));
            guiGraphics.fill((int) pX, toY, toX, toY + 2, color);
        } else {
            guiGraphics.fillGradient((int) pX, (int) pY, toX, toY, 0xFF696969, 0xFFA9A9A9);
            guiGraphics.fillGradient((int) pX, (int) (pY + frameHeight * 2 / 3), toX, toY,
                    ColorTools.argbColor(0, 128, 128, 128), ColorTools.deepColor(color));
            renderGuiItem(entity, guiGraphics, itemStack, itemX, itemY, scale);
            guiGraphics.fill((int) pX, toY, toX, toY + 2, color);
        }
    }

public static void renderItemProgressFocus(LivingEntity entity, GuiGraphics guiGraphics, ItemStack itemStack, float pX, float pY, float width, float height, int grade, float focusScale) {
        int color = ColorTools.colorItems(grade);
        float frameWidth = width * 18 / 100F * focusScale;
        float frameHeight = height * 25 / 100F * focusScale;
        float scale = frameWidth * 92F / 100F / 16F;
        int toX = (int)(pX + frameWidth);
        int toY = (int)(pY + frameHeight);
        float itemX = pX + (frameWidth - scale * 16F) / 2F;
        float itemY = pY + (frameHeight - scale * 16F) / 2F;
        if (grade == 5) {
            guiGraphics.fillGradient((int) pX, (int) pY, toX, toY, 0xFF533c00, 0xFFb69008);
            blitGoldItemAspect(guiGraphics, (int) (pX + 2F), (int) (pY + 2F),
                    (int) (frameWidth - 4F), (int) (frameHeight - 4F));
            guiGraphics.fill((int) pX, toY, toX, toY + 2, color);
        } else {
            guiGraphics.fillGradient((int) pX, (int) pY, toX, toY, 0xFF696969, 0xFFA9A9A9);
            guiGraphics.fillGradient((int) pX, (int) (pY + frameHeight * 2 / 3), toX, toY,
                    ColorTools.argbColor(0, 128, 128, 128), ColorTools.deepColor(color));
            renderGuiItem(entity, guiGraphics, itemStack, itemX, itemY, scale);
            guiGraphics.fill((int) pX, toY, toX, toY + 2, color);
        }
        int bx0 = (int) pX;
        int by0 = (int) pY;
        // Focus tint: periwinkle/blue gradient lit up inside the focused card
        // (mirrors the CS:GO inspect highlight), strengthening with focus.
        float focus = (focusScale - 1.0F) / (FOCUS_PEAK_SCALE - 1.0F);
        int tintA = (int) (70F * (0.4F + 0.6F * focus));
        int tintTop = ColorTools.argbColor(tintA, 176, 140, 255);
        int tintBottom = ColorTools.argbColor(tintA - 12, 48, 80, 255);
        guiGraphics.fillGradient(bx0 + 4, by0 + 4, toX - 4, toY - 4, tintTop, tintBottom);
    }
}
