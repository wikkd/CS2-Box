package com.reclizer.csgobox.utils;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

public class GuiItemMove {

    public static float renderRotAngleY(double pMouse, float itemRot) {
        float f1 = (float) Math.atan((double) (pMouse / 40.0F));
        float angle = f1 + itemRot;
        if (angle > 1.5F) {
            angle = 1.5F;
        } else if (angle < -1.5F) {
            angle = -1.5F;
        }
        return angle;
    }

    public static float renderRotAngleX(double pMouse, float itemRot) {
        float f1 = (float) Math.atan((double) (pMouse / 40.0F));
        float angle = f1 + itemRot;
        if (angle > 3F) {
            angle = 3F;
        } else if (angle < -3F) {
            angle = -3F;
        }
        return angle;
    }

    public static void renderItemInInventoryFollowsMouse(GuiGraphics pGuiGraphics, int pX, int pY, float angleXComponent, float angleYComponent, ItemStack item, LivingEntity player, float scale) {
        BakedModel bakedModel = Minecraft.getInstance().getItemRenderer().getModel(item, (Level) player.level(), (LivingEntity) player, 0);
        renderItemInInventoryFollowsAngle(pGuiGraphics, pX, pY, angleXComponent, angleYComponent, item, bakedModel, scale);
    }

    public static void renderItemInInventoryFollowsAngle(GuiGraphics pGuiGraphics, int pX, int pY, float angleXComponent, float angleYComponent, ItemStack itemStack, BakedModel bakedModel, float scale) {
        float f = angleXComponent;
        float f1 = angleYComponent;
        Quaternionf quaternionf = (new Quaternionf()).rotateZ((float) Math.PI);
        Quaternionf quaternionf1 = (new Quaternionf()).rotateX(f1 * 20.0F * ((float) Math.PI / 180F));
        quaternionf.mul(quaternionf1);
        renderItemInInventory(pGuiGraphics.pose(), itemStack, pX, pY, bakedModel, f, f1, scale);
    }

    protected static void renderItemInInventory(PoseStack poseStack, ItemStack itemStack, int pX, int pY, BakedModel bakedModel, float angleXComponent, float angleYComponent, float scale) {
        poseStack.pushPose();
        poseStack.translate((float) pX, (float) pY, 100.0F);
        poseStack.translate(8.0F * scale, 8.0F * scale, 0.0F);
        poseStack.scale(1.0F, -1.0F, 1.0F);
        poseStack.mulPose(Axis.XP.rotation(angleYComponent));
        poseStack.mulPose(Axis.YP.rotation(angleXComponent));
        Lighting.setupForEntityInInventory();
        poseStack.scale(16.0F * scale, 16.0F * scale, 16.0F * scale);
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

    public static void renderEntityInInventoryFollowsMouse(GuiGraphics pGuiGraphics, int pX, int pY, int pScale, float pMouseX, float pMouseY, ItemEntity pEntity) {
        float f = (float) Math.atan((double) (pMouseX / 40.0F));
        float f1 = (float) Math.atan((double) (pMouseY / 40.0F));
        renderEntityInInventoryFollowsAngle(pGuiGraphics, pX, pY, pScale, f, f1, pEntity);
    }

    public static void renderEntityInInventoryFollowsAngle(GuiGraphics pGuiGraphics, int pX, int pY, int pScale, float angleXComponent, float angleYComponent, ItemEntity pEntity) {
        float f = angleXComponent;
        float f1 = angleYComponent;
        Quaternionf quaternionf = (new Quaternionf()).rotateZ((float) Math.PI);
        Quaternionf quaternionf1 = (new Quaternionf()).rotateX(f1 * 20.0F * ((float) Math.PI / 180F));
        quaternionf.mul(quaternionf1);
        renderEntityInInventory(pGuiGraphics, pX, pY, pScale, quaternionf, quaternionf1, pEntity);
    }

    public static void renderEntityInInventory(GuiGraphics pGuiGraphics, int pX, int pY, int pScale, Quaternionf pPose, @Nullable Quaternionf pCameraOrientation, ItemEntity pEntity) {
        pGuiGraphics.pose().pushPose();
        pGuiGraphics.pose().translate((double) pX, (double) pY, 50.0D);
        pGuiGraphics.pose().scale((float) pScale, (float) pScale, (float) (-pScale));
        pGuiGraphics.pose().mulPose(pPose);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher entityrenderdispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (pCameraOrientation != null) {
            pCameraOrientation.conjugate();
            entityrenderdispatcher.overrideCameraOrientation(pCameraOrientation);
        }
        entityrenderdispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> {
            entityrenderdispatcher.render(pEntity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, pGuiGraphics.pose(), pGuiGraphics.bufferSource(), 15728880);
        });
        pGuiGraphics.flush();
        entityrenderdispatcher.setRenderShadow(true);
        pGuiGraphics.pose().popPose();
        Lighting.setupFor3DItems();
    }
}
