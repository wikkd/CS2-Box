package com.reclizer.csgobox.utils;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4fStack;

public final class IconListTools {

    private static final ResourceLocation GOLD_ITEM_TEXTURE =
            ResourceLocation.parse("csgobox:textures/screens/gold_item.png");

    private IconListTools() {
    }

    public static void renderRarity(GuiGraphics guiGraphics, int pX0, int pY0, int toX, int toY, int color) {
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
            guiGraphics.blit(GOLD_ITEM_TEXTURE, pX + 2, pY + 2, 0, 0,
                    frameWidth - 4, frameHeight - 4, frameWidth - 4, frameHeight - 4);
        } else {
            renderRarity(guiGraphics, pX, pY, toX, toY, color);
            renderGuiItem(entity, entity.level(), guiGraphics, itemStack, itemX, itemY, scale);
        }
    }

    public static void renderGuiItem(LivingEntity entity, Level world, GuiGraphics guiGraphics, ItemStack itemStack, float pX, float pY, float scale) {
        renderGuiItem(guiGraphics.pose(), itemStack, pX, pY, Minecraft.getInstance().getItemRenderer().getModel(itemStack, world, entity, 0), scale);
    }

    private static void renderGuiItem(PoseStack poseStack, ItemStack itemStack, float pX, float pY, BakedModel bakedModel, float scale) {
        poseStack.pushPose();
        poseStack.translate(pX, pY, 2F);
        poseStack.translate(8.0F * scale, 8.0F * scale, 0.0F);
        poseStack.scale(1.0F, -1.0F, 0F);
        poseStack.scale(16.0F * scale, 16.0F * scale, 0);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean useFlatLighting = !bakedModel.usesBlockLight();
        if (useFlatLighting) {
            Lighting.setupForFlatItems();
        }

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();

        Minecraft.getInstance().getItemRenderer().render(itemStack, ItemDisplayContext.GUI, false,
                new PoseStack(), bufferSource, 15728880, OverlayTexture.NO_OVERLAY, bakedModel);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (useFlatLighting) {
            Lighting.setupFor3DItems();
        }

        poseStack.popPose();
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    public static void renderItemProgress(LivingEntity entity, GuiGraphics guiGraphics, ItemStack itemStack, float pX, float pY, float width, float height, int grade) {
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
            renderGuiItem(entity, entity.level(), guiGraphics, itemStack, itemX, itemY, scale);
            RenderSystem.enableBlend();
            guiGraphics.fill((int) pX, (int) toY, (int) toX, (int) (toY + 2), color);
        }
    }
}
