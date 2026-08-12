package com.reclizer.csgobox.v26_1_2.utils;

import com.reclizer.csgobox.utils.ColorTools;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    private static void blitGoldItemAspect(GuiGraphicsExtractor guiGraphics,
                                           int x, int y, int availW, int availH, int alpha) {
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
        // Tint variant (full UV window) so the gold gem fades with the frame.
        AnimRenderOps.blitTextured(guiGraphics, GOLD_ITEM_TEXTURE,
                drawX, drawY,
                drawW, drawH,
                0, 0,
                GOLD_ITEM_TEX_WIDTH, GOLD_ITEM_TEX_HEIGHT,
                GOLD_ITEM_TEX_WIDTH, GOLD_ITEM_TEX_HEIGHT,
                ColorTools.withAlpha(0xFFFFFFFF, alpha));
    }

    private static void renderRarity(GuiGraphicsExtractor guiGraphics, int pX0, int pY0, int toX, int toY, int color, int alpha) {
        AnimRenderOps.fillGradient(guiGraphics, pX0, pY0, toX, toY,
                ColorTools.withAlpha(0xFF696969, alpha), ColorTools.withAlpha(0xFFD3D3D3, alpha));
        AnimRenderOps.fill(guiGraphics, pX0, pY0, pX0 + 2, toY, ColorTools.withAlpha(color, alpha));
    }

    /** {@code alpha} (0..255) fades the whole frame (gradient, rarity bar,
     *  gold texture); the item icon itself has no alpha channel in the
     *  render pipeline and slides with the frame instead. */
    public static void renderItemFrame(LivingEntity entity, GuiGraphicsExtractor guiGraphics, ItemStack itemStack, int pX, int pY, int width, int height, int grade, int alpha) {
        int color = ColorTools.colorItems(grade);

        int frameWidth = width * 8 / 100;
        int frameHeight = height * 11 / 100;
        float scale = frameWidth * 60F / 100F / 16F;
        int toX = pX + frameWidth;
        int toY = pY + frameHeight;
        // 26.x renderItem2D takes the ICON CENTRE (1.21.1 took the top-left
        // and added 8*scale internally, so the old 20%/10% offsets happened to
        // centre it there). Pass the slot centre directly, otherwise the icon
        // floats toward the top-left corner of the frame.
        int itemX = pX + frameWidth / 2;
        int itemY = pY + frameHeight / 2;
        if (grade == 5) {
            AnimRenderOps.fillGradient(guiGraphics, pX, pY, toX, toY,
                    ColorTools.withAlpha(0xFF533c00, alpha), ColorTools.withAlpha(0xFFb69008, alpha));
            AnimRenderOps.fill(guiGraphics, pX, pY, pX + 2, toY, ColorTools.withAlpha(color, alpha));
            // 26.1.2 changed blit: must pass RenderPipeline explicitly and
            // last two ints are textureWidth/textureHeight (pixels), not UVs.
            blitGoldItemAspect(guiGraphics, pX + 2, pY + 2,
                    frameWidth - 4, frameHeight - 4, alpha);
        } else {
            renderRarity(guiGraphics, pX, pY, toX, toY, color, alpha);
            AnimRenderOps.renderItem2D(entity, guiGraphics, itemStack, itemX, itemY, scale);
        }
    }

    public static void renderRewardCell(LivingEntity entity, GuiGraphicsExtractor guiGraphics, ItemStack itemStack, int pX, int pY, int width, int height, int grade) {
        int color = ColorTools.colorItems(grade);
        int pad = Math.max(3, Math.min(8, width / 10));
        int iconW = Math.max(8, width - pad * 2);
        // 26.x renderItem2D takes the ICON CENTRE: pin the visual centre on
        // the cell centre instead of the icon-box top-left.
        int itemX = pX + width / 2;
        int itemY = pY + height / 2;
        if (grade == 5) {
            AnimRenderOps.fillGradient(guiGraphics, pX, pY, pX + width, pY + height, 0xFF533c00, 0xFFb69008);
            AnimRenderOps.fill(guiGraphics, pX, pY, pX + 2, pY + height, color);
            blitGoldItemAspect(guiGraphics, pX + 2, pY + 2, width - 4, height - 4, 255);
        } else {
            AnimRenderOps.fillGradient(guiGraphics, pX, pY, pX + width, pY + height, 0xFF696969, 0xFFD3D3D3);
            AnimRenderOps.fill(guiGraphics, pX, pY, pX + 2, pY + height, color);
            AnimRenderOps.renderItem2D(entity, guiGraphics, itemStack, itemX, itemY, iconW / 16F);
        }
    }

    public static void renderGuiItem(LivingEntity entity, GuiGraphicsExtractor guiGraphics, ItemStack itemStack, float pX, float pY, float scale) {
        AnimRenderOps.renderItem2D(entity, guiGraphics, itemStack, pX, pY, scale);
    }

    public static void renderItemProgress(LivingEntity entity, GuiGraphicsExtractor guiGraphics, ItemStack itemStack, float pX, float pY, float width, float height, int grade) {
        int color = ColorTools.colorItems(grade);
        float frameWidth = width * 18 / 100F;
        float frameHeight = height * 25 / 100F;
        float scale = frameWidth * 60F / 100F / 16F;
        int toX = (int)(pX + frameWidth);
        int toY = (int)(pY + frameHeight);
        float itemX = pX + frameWidth / 2F;
        float itemY = pY + frameHeight / 2F;
        if (grade == 5) {
            AnimRenderOps.fillGradient(guiGraphics, (int) pX, (int) pY, toX, toY, 0xFF533c00, 0xFFb69008);
            blitGoldItemAspect(guiGraphics, (int) (pX + 2F), (int) (pY + 2F),
                    (int) (frameWidth - 4F), (int) (frameHeight - 4F), 255);
            AnimRenderOps.fill(guiGraphics, (int) pX, toY, toX, toY + 2, color);
        } else {
            AnimRenderOps.fillGradient(guiGraphics, (int) pX, (int) pY, toX, toY, 0xFF696969, 0xFFA9A9A9);
            AnimRenderOps.fillGradient(guiGraphics, (int) pX, (int) (pY + frameHeight * 2 / 3), toX, toY,
                    ColorTools.argbColor(0, 128, 128, 128), ColorTools.deepColor(color));
            AnimRenderOps.renderItem2D(entity, guiGraphics, itemStack, itemX, itemY, scale);
            AnimRenderOps.fill(guiGraphics, (int) pX, toY, toX, toY + 2, color);
        }
    }

public static void renderItemProgressFocus(LivingEntity entity, GuiGraphicsExtractor guiGraphics, ItemStack itemStack, float pX, float pY, float width, float height, int grade, float focusScale) {
        int color = ColorTools.colorItems(grade);
        float frameWidth = width * 18 / 100F * focusScale;
        float frameHeight = height * 25 / 100F * focusScale;
        float scale = frameWidth * 92F / 100F / 16F;
        int toX = (int)(pX + frameWidth);
        int toY = (int)(pY + frameHeight);
        float itemX = pX + frameWidth / 2F;
        float itemY = pY + frameHeight / 2F;
        if (grade == 5) {
            AnimRenderOps.fillGradient(guiGraphics, (int) pX, (int) pY, toX, toY, 0xFF533c00, 0xFFb69008);
            blitGoldItemAspect(guiGraphics, (int) (pX + 2F), (int) (pY + 2F),
                    (int) (frameWidth - 4F), (int) (frameHeight - 4F), 255);
            AnimRenderOps.fill(guiGraphics, (int) pX, toY, toX, toY + 2, color);
        } else {
            AnimRenderOps.fillGradient(guiGraphics, (int) pX, (int) pY, toX, toY, 0xFF696969, 0xFFA9A9A9);
            AnimRenderOps.fillGradient(guiGraphics, (int) pX, (int) (pY + frameHeight * 2 / 3), toX, toY,
                    ColorTools.argbColor(0, 128, 128, 128), ColorTools.deepColor(color));
            AnimRenderOps.renderItem2D(entity, guiGraphics, itemStack, itemX, itemY, scale);
            AnimRenderOps.fill(guiGraphics, (int) pX, toY, toX, toY + 2, color);
        }
        int bx0 = (int) pX;
        int by0 = (int) pY;
        // Focus tint: periwinkle/blue gradient lit up inside the focused card
        // (mirrors the CS:GO inspect highlight), strengthening with focus.
        float focus = (focusScale - 1.0F) / (FOCUS_PEAK_SCALE - 1.0F);
        int tintA = (int) (70F * (0.4F + 0.6F * focus));
        int tintTop = ColorTools.argbColor(tintA, 176, 140, 255);
        int tintBottom = ColorTools.argbColor(tintA - 12, 48, 80, 255);
        AnimRenderOps.fillGradient(guiGraphics, bx0 + 4, by0 + 4, toX - 4, toY - 4, tintTop, tintBottom);
    }
}
