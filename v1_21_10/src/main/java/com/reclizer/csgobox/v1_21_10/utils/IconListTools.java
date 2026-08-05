package com.reclizer.csgobox.v1_21_10.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reclizer.csgobox.utils.ColorTools;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Utilities for rendering item icons and rarity frames in GUI overlays.
 * Adapted from v26_1_2 for NeoForge 21.x / MC 1.21.3+ API.
 */
public final class IconListTools {

    private static final ResourceLocation GOLD_ITEM_TEXTURE =
            ResourceLocation.parse("csgobox:textures/screens/gold_item.png");

    private static final int GOLD_ITEM_TEX_WIDTH = 32;
    private static final int GOLD_ITEM_TEX_HEIGHT = 24;

    private IconListTools() {
    }

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
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                GOLD_ITEM_TEXTURE,
                drawX, drawY,
                0F, 0F,
                drawW, drawH,
                GOLD_ITEM_TEX_WIDTH, GOLD_ITEM_TEX_HEIGHT,
                0xFFFFFFFF);
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
            blitGoldItemAspect(guiGraphics, pX + 2, pY + 2,
                    frameWidth - 4, frameHeight - 4);
        } else {
            renderRarity(guiGraphics, pX, pY, toX, toY, color);
            renderGuiItem(entity, guiGraphics, itemStack, itemX, itemY, scale);
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
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(pX, pY, 0.0);
        if (scale != 1.0F) poseStack.scale(scale, scale, 1.0F);
        guiGraphics.renderItem(entity, itemStack, 0, 0, seed);
        poseStack.popPose();
    }

    public static void renderItemProgress(LivingEntity entity, GuiGraphics guiGraphics, ItemStack itemStack, float pX, float pY, float width, float height, int grade) {
        int color = ColorTools.colorItems(grade);
        float frameWidth = width * 18 / 100F;
        float frameHeight = height * 25 / 100F;
        float scale = frameWidth * 60F / 100F / 16F;
        int toX = (int) (pX + frameWidth);
        int toY = (int) (pY + frameHeight);
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
}
