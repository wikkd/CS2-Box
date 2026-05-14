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

public class IconListTools {

    public static void renderRarity(GuiGraphics guiGraphics, int pX0, int pY0, int toX, int toY, int color) {
        guiGraphics.fillGradient(pX0, pY0, toX, toY, 0xFF696969, 0xFFD3D3D3);
        guiGraphics.fill(pX0, pY0, pX0 + 2, toY, color);
    }

    public static void renderItemFrame(LivingEntity entity, GuiGraphics guiGraphics, ItemStack itemStack, int pX, int pY, int width, int height, int grade) {
        int color = ColorTools.colorItems(grade);

        int FrameWidth = width * 8 / 100;
        int FrameHeight = height * 11 / 100;
        float scale = FrameWidth * 60F / 100F / 16F;
        int toX = pX + FrameWidth;
        int toY = pY + FrameHeight;
        int itemX = pX + FrameWidth * 20 / 100;
        int itemY = pY + FrameHeight * 10 / 100;
        if (grade == 5) {
            guiGraphics.fillGradient(pX, pY, toX, toY, 0xFF533c00, 0xFFb69008);
            guiGraphics.fill(pX, pY, pX + 2, toY, color);
            guiGraphics.blit(ResourceLocation.parse("csgobox:textures/screens/gold_item.png"), pX + 2, pY + 2, 0, 0, FrameWidth - 4, FrameHeight - 4, FrameWidth - 4, FrameHeight - 4);
        } else {
            renderRarity(guiGraphics, pX, pY, toX, toY, color);
            renderGuiItem(entity, entity.level(), guiGraphics, itemStack, itemX, itemY, scale);
        }
    }

    public static void renderGuiItem(LivingEntity entity, Level world, GuiGraphics guiGraphics, ItemStack itemStack, float pX, float pY, float scale) {
        renderGuiItem(guiGraphics.pose(), itemStack, pX, pY, Minecraft.getInstance().getItemRenderer().getModel(itemStack, world, entity, 0), scale);
    }

    protected static void renderGuiItem(PoseStack poseStack, ItemStack itemStack, float pX, float pY, BakedModel bakedModel, float scale) {
        poseStack.pushPose();
        poseStack.translate(pX, pY, 2F);
        poseStack.translate(8.0F * scale, 8.0F * scale, 0.0F);
        poseStack.scale(1.0F, -1.0F, 0F);
        poseStack.scale(16.0F*scale, 16.0F*scale, 0);
        MultiBufferSource.BufferSource multibuffersource$buffersource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean flag = !bakedModel.usesBlockLight();
        if (flag) {
            Lighting.setupForFlatItems();
        }

        Matrix4fStack posestack = RenderSystem.getModelViewStack();
        posestack.pushMatrix();
        posestack.mul(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();

        Minecraft.getInstance().getItemRenderer().render(itemStack, ItemDisplayContext.GUI, false, new PoseStack(), multibuffersource$buffersource, 15728880, OverlayTexture.NO_OVERLAY, bakedModel);
        multibuffersource$buffersource.endBatch();
        RenderSystem.enableDepthTest();
        if (flag) {
            Lighting.setupFor3DItems();
        }

        poseStack.popPose();
        posestack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    public static void renderItemProgress(LivingEntity entity, GuiGraphics guiGraphics, ItemStack itemStack, float pX, float pY, float width, float height, int grade) {
        int color = ColorTools.colorItems(grade);
        float FrameWidth = width * 18 / 100;
        float FrameHeight = height * 25 / 100;
        float scale = FrameWidth * 60F / 100F / 16F;
        float toX = pX + FrameWidth;
        float toY = pY + FrameHeight;
        float itemX = pX + FrameWidth * 20 / 100;
        float itemY = pY + FrameHeight * 10 / 100;
        if (grade == 5) {
            guiGraphics.fillGradient((int)pX, (int)pY, (int)toX, (int)toY, 0xFF533c00, 0xFFb69008);
            guiGraphics.blit(ResourceLocation.parse("csgobox:textures/screens/gold_item.png"), (int)(pX + 2F), (int)(pY + 2), 0, 0, (int)(FrameWidth - 4), (int)(FrameHeight - 4), (int)(FrameWidth - 4), (int)(FrameHeight - 4));
            guiGraphics.fill((int)pX, (int)toY, (int)toX, (int)(toY + 2), color);
        } else {
            guiGraphics.fillGradient((int)pX, (int)pY, (int)toX, (int)toY, 0xFF696969, 0xFFA9A9A9);
            guiGraphics.fillGradient((int)pX, (int)(pY + FrameHeight * 2 / 3), (int)toX, (int)toY, ColorTools.argbColor(0, 128, 128, 128), ColorTools.deepColor(color));
            renderGuiItem(entity, entity.level(), guiGraphics, itemStack, itemX, itemY, scale);
            RenderSystem.enableBlend();
            guiGraphics.fill((int)pX, (int)toY, (int)toX, (int)(toY + 2), color);
        }
    }
}
