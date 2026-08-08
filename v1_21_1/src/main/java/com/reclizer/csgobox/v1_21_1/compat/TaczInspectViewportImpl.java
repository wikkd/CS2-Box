package com.reclizer.csgobox.v1_21_1.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.animation.statemachine.ItemAnimationStateContext;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.reclizer.csgobox.v1_21_1.gui.CsLookItemScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Axis;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * TACZ (Timeless & Classics Guns: Zero, unofficial 1.21.1 port) inspect
 * viewport integration for {@link CsLookItemScreen}. Renders the drawn gun's
 * TACZ model + inspect animation inside the look screen, driven through
 * TACZ's public renderer/state-machine API (no held-item requirement).
 *
 * <p>Optional dependency discipline: TACZ is compileOnly. Every public entry
 * point gates on {@link ModList#isLoaded} BEFORE touching any TACZ class
 * (JVM resolves lazily per method invocation) and wraps the TACZ-touching
 * part in {@code catch (Throwable)} so a missing/incompatible TACZ degrades
 * to a silent no-op.
 */
public final class TaczInspectViewportImpl {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TACZ_MOD_ID = "tacz";

    /** Fixed display pose of the gun in the viewport (degrees). */
    private static final float DISPLAY_YAW = -35F;
    private static final float DISPLAY_PITCH = 10F;
    /** Vertical model-space offset so the gun sits in the display area center. */
    private static final float MODEL_Y_OFFSET = -1.5F;

    private TaczInspectViewportImpl() {
    }

    /**
     * True when the inspect viewport can be offered for this stack: TACZ is
     * loaded, the stack is a TACZ gun and its gun display carries an
     * animation state machine.
     */
    public static boolean isAvailable(ItemStack stack) {
        if (!isTaczLoaded()) {
            return false;
        }
        try {
            return isGunWithAnimation(stack);
        } catch (Throwable t) {
            LOGGER.warn("TACZ inspect availability check failed, treating as unavailable", t);
            return false;
        }
    }

    /**
     * Enter the viewport: initialize the gun's animation state machine when
     * needed, trigger the inspect input and play TACZ's inspect sound.
     * Returns false when the viewport cannot be started (caller keeps 2D).
     */
    public static boolean enter(ItemStack stack, LocalPlayer player) {
        if (!isTaczLoaded()) {
            return false;
        }
        try {
            return enterInternal(stack, player);
        } catch (Throwable t) {
            LOGGER.warn("TACZ inspect viewport enter failed, falling back to 2D", t);
            return false;
        }
    }

    /**
     * Enter display-only mode: initialize the animation state machine when
     * needed WITHOUT triggering the inspect input, so the gun renders as a
     * static 3D model (idle pose). Used as the default look for TACZ guns,
     * since TACZ's own GUI item rendering only draws the flat slot texture.
     * Returns false when the viewport cannot be started (caller keeps 2D).
     */
    public static boolean enterDisplay(ItemStack stack, LocalPlayer player) {
        if (!isTaczLoaded()) {
            return false;
        }
        try {
            AnimateGeoItemRenderer<?, ?> renderer = taczRenderer(stack);
            if (renderer == null) {
                return false;
            }
            return initStateMachineIfNeeded(renderer, stack, player);
        } catch (Throwable t) {
            LOGGER.warn("TACZ display viewport enter failed, falling back to 2D", t);
            return false;
        }
    }

    /**
     * Replay the inspect animation + sound inside an already-active viewport.
     * No-op when the viewport cannot be driven.
     */
    public static void triggerInspect(ItemStack stack, LocalPlayer player) {
        if (!isTaczLoaded()) {
            return;
        }
        try {
            enterInternal(stack, player);
        } catch (Throwable t) {
            LOGGER.warn("TACZ inspect replay failed", t);
        }
    }

    /**
     * Render one frame of the viewport into the look screen's display area.
     * Returns false when nothing was rendered (caller falls back to the 2D
     * item icon for this frame).
     */
    public static boolean renderViewport(GuiGraphics guiGraphics, ItemStack stack, LocalPlayer player,
                                         float partialTicks, int centerX, int centerY, float scale) {
        if (!isTaczLoaded()) {
            return false;
        }
        try {
            return renderInternal(guiGraphics, stack, player, partialTicks, centerX, centerY, scale);
        } catch (Throwable t) {
            LOGGER.warn("TACZ inspect viewport render failed this frame, falling back to 2D", t);
            return false;
        }
    }

    /** Leave the viewport: stop gun sounds and exit the state machine. */
    public static void exit(ItemStack stack) {
        if (!isTaczLoaded()) {
            return;
        }
        try {
            exitInternal(stack);
        } catch (Throwable t) {
            LOGGER.warn("TACZ inspect viewport exit cleanup failed", t);
        }
    }

    private static boolean isTaczLoaded() {
        return ModList.get().isLoaded(TACZ_MOD_ID);
    }

    private static boolean isGunWithAnimation(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IGun)) {
            return false;
        }
        return TimelessAPI.getGunDisplay(stack)
                .map(display -> display.getAnimationStateMachine() != null)
                .orElse(false);
    }

    @Nullable
    private static AnimateGeoItemRenderer<?, ?> taczRenderer(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IGun)) {
            return null;
        }
        if (IClientItemExtensions.of(stack).getCustomRenderer() instanceof AnimateGeoItemRenderer<?, ?> renderer) {
            return renderer;
        }
        return null;
    }

    private static boolean enterInternal(ItemStack stack, LocalPlayer player) {
        AnimateGeoItemRenderer<?, ?> renderer = taczRenderer(stack);
        if (renderer == null) {
            return false;
        }
        if (!initStateMachineIfNeeded(renderer, stack, player)) {
            return false;
        }
        renderer.triggerAnimation(stack, GunAnimationConstant.INPUT_INSPECT);
        playInspectSound(stack, player);
        return true;
    }

    /**
     * Initialize the state machine without triggering TACZ's draw input: the
     * viewport plays inspect directly from idle, so no pull-out animation
     * would be visible anyway. Raw types because the renderer's context
     * generic is unknowable at this layer.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean initStateMachineIfNeeded(AnimateGeoItemRenderer renderer, ItemStack stack, LocalPlayer player) {
        LuaAnimationStateMachine stateMachine = renderer.getStateMachine(stack);
        if (stateMachine == null) {
            return false;
        }
        if (!stateMachine.isInitialized()) {
            stateMachine.setContext(renderer.initContext(stack, player, 0F));
            stateMachine.initialize();
        }
        return true;
    }

    /** Mirrors LocalPlayerInspect's no-ammo detection for the sound variant. */
    private static void playInspectSound(ItemStack stack, LocalPlayer player) {
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
        if (display == null) {
            return;
        }
        GunData gunData = TimelessAPI.getClientGunIndex(iGun.getGunId(stack))
                .map(ClientGunIndex::getGunData)
                .orElse(null);
        if (gunData == null) {
            return;
        }
        boolean noAmmo;
        if (gunData.getBolt() == Bolt.OPEN_BOLT) {
            noAmmo = iGun.getCurrentAmmoCount(stack) <= 0;
        } else {
            noAmmo = !iGun.hasBulletInBarrel(stack);
        }
        SoundPlayManager.stopPlayGunSound();
        SoundPlayManager.playInspectSound(player, display, noAmmo);
    }

    private static boolean renderInternal(GuiGraphics guiGraphics, ItemStack stack, LocalPlayer player,
                                          float partialTicks, int centerX, int centerY, float scale) {
        AnimateGeoItemRenderer<?, ?> renderer = taczRenderer(stack);
        if (renderer == null) {
            return false;
        }
        LuaAnimationStateMachine<?> stateMachine = renderer.getStateMachine(stack);
        BedrockAnimatedModel model = renderer.getModel(stack);
        if (stateMachine == null || model == null || !stateMachine.isInitialized()) {
            return false;
        }
        advanceStateMachine(renderer, stack, player, partialTicks);

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        // Vanilla GUI item frame: center in display area, flip Y, 16px per block unit.
        pose.translate(centerX, centerY, 150.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        pose.mulPose(Axis.XP.rotationDegrees(DISPLAY_PITCH));
        pose.mulPose(Axis.YP.rotationDegrees(DISPLAY_YAW));
        float pixelsPerUnit = 16.0F * scale;
        pose.scale(pixelsPerUnit, pixelsPerUnit, pixelsPerUnit);
        // Bedrock model alignment, mirroring AnimateGeoItemRenderer.renderByItem
        // (Y sign flipped because the GUI frame above already inverts Y).
        pose.translate(0.5F, MODEL_Y_OFFSET, 0.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(180F));

        model.render(pose, ItemDisplayContext.GUI,
                RenderType.entityCutout(renderer.getTextureLocation(stack)),
                0xF000F0, OverlayTexture.NO_OVERLAY);
        model.cleanAnimationTransform();
        pose.popPose();
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void advanceStateMachine(AnimateGeoItemRenderer renderer, ItemStack stack,
                                            LocalPlayer player, float partialTicks) {
        LuaAnimationStateMachine stateMachine = renderer.getStateMachine(stack);
        if (stateMachine == null) {
            return;
        }
        stateMachine.processContextIfExist(context ->
                renderer.updateContext((ItemAnimationStateContext) context, stack, player, partialTicks));
        stateMachine.update();
    }

    private static void exitInternal(ItemStack stack) {
        SoundPlayManager.stopPlayGunSound();
        AnimateGeoItemRenderer<?, ?> renderer = taczRenderer(stack);
        if (renderer == null) {
            return;
        }
        LuaAnimationStateMachine<?> stateMachine = renderer.getStateMachine(stack);
        if (stateMachine != null && stateMachine.isInitialized()) {
            stateMachine.exit();
        }
    }
}
