package com.reclizer.csgobox.v26_1_2.utils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class IconListTools {

    private static final Identifier GOLD_ITEM_TEXTURE =
            Identifier.parse("csgobox:textures/screens/gold_item.png");

    private IconListTools() {
    }

    public static void renderRarity(GuiGraphicsExtractor guiGraphics, int pX0, int pY0, int toX, int toY, int color) {
        guiGraphics.fillGradient(pX0, pY0, toX, toY, 0xFF696969, 0xFFD3D3D3);
        guiGraphics.fill(pX0, pY0, pX0 + 2, toY, color);
    }

    public static void renderItemFrame(LivingEntity entity, GuiGraphicsExtractor guiGraphics, ItemStack itemStack, int pX, int pY, int width, int height, int grade) {
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
            guiGraphics.blit(GOLD_ITEM_TEXTURE, pX + 2, pY + 2, 0, 0,
                    frameWidth - 4, frameHeight - 4, frameWidth - 4, frameHeight - 4);
        } else {
            renderRarity(guiGraphics, pX, pY, toX, toY, color);
            renderGuiItem(entity, guiGraphics, itemStack, itemX, itemY, scale);
        }
    }

    public static void renderGuiItem(LivingEntity entity, GuiGraphicsExtractor guiGraphics, ItemStack itemStack, float pX, float pY, float scale) {
        if (itemStack == null || itemStack.isEmpty() || entity == null) return;
        int pixelX = Math.round(pX);
        int pixelY = Math.round(pY);
        int seed = (int)(entity.getUUID().getLeastSignificantBits() & 0x7FFFFFFFL);
        guiGraphics.pose().pushMatrix();
        if (scale != 1.0F) {
            guiGraphics.pose().scale(scale, scale);
        }
        guiGraphics.item(entity, itemStack, pixelX, pixelY, seed);
        guiGraphics.pose().popMatrix();
    }

    public static void renderItemProgress(LivingEntity entity, GuiGraphicsExtractor guiGraphics, ItemStack itemStack, float pX, float pY, float width, float height, int grade) {
        int color = ColorTools.colorItems(grade);
        float frameWidth = width * 18 / 100;
        float frameHeight = height * 25 / 100;
        float scale = frameWidth * 60F / 100F / 16F;
        float toX = pX + frameWidth;
        float toY = pY + frameHeight;
        float itemX = pX + frameWidth * 20 / 100;
        float itemY = pY + frameHeight * 10 / 100;
        if (grade == 5) {
            guiGraphics.fillGradient((int) pX, (int) pY, (int) toX, (int) toY, 0xFF533c00, 0xFFb69008);
            guiGraphics.blit(GOLD_ITEM_TEXTURE, (int) (pX + 2F), (int) (pY + 2), 0, 0,
                    (int) (frameWidth - 4), (int) (frameHeight - 4),
                    (int) (frameWidth - 4), (int) (frameHeight - 4));
            guiGraphics.fill((int) pX, (int) toY, (int) toX, (int) (toY + 2), color);
        } else {
            guiGraphics.fillGradient((int) pX, (int) pY, (int) toX, (int) toY, 0xFF696969, 0xFFA9A9A9);
            guiGraphics.fillGradient((int) pX, (int) (pY + frameHeight * 2 / 3), (int) toX, (int) toY,
                    ColorTools.argbColor(0, 128, 128, 128), ColorTools.deepColor(color));
            renderGuiItem(entity, guiGraphics, itemStack, itemX, itemY, scale);
            guiGraphics.fill((int) pX, (int) toY, (int) toX, (int) (toY + 2), color);
        }
    }
}
