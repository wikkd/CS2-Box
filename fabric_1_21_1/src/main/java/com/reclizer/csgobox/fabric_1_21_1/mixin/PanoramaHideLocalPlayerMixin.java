package com.reclizer.csgobox.fabric_1_21_1.mixin;

import com.reclizer.csgobox.fabric_1_21_1.gui.CsLookItemScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the look screen owns the render camera (panorama background), the
 * local player's entity render is canceled entirely. Invisibility alone only
 * skips the body model — vanilla still draws the equipment layers (held item,
 * armor) of invisible entities, which would float in the panorama view.
 * Fabric has no living-render cancellation event, so this is the one place a
 * mixin is needed; the camera rig mechanism itself stays mixin-free.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class PanoramaHideLocalPlayerMixin {
    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void csgobox$hideLocalPlayerInPanorama(LivingEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CsLookItemScreen screen && screen.isSceneryRigMounted()
                && entity == mc.player) {
            ci.cancel();
        }
    }
}
