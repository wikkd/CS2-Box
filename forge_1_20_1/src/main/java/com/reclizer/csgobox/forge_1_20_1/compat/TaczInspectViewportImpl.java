package com.reclizer.csgobox.forge_1_20_1.compat;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.animation.statemachine.ItemAnimationStateContext;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.reclizer.csgobox.forge_1_20_1.gui.CsLookItemScreen;
import com.reclizer.csgobox.forge_1_20_1.utils.AnimRenderOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Axis;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * TACZ (Timeless & Classics Guns: Zero, official 1.20.1) inspect viewport
 * integration for {@link CsLookItemScreen}. Renders the drawn gun's TACZ
 * model + inspect animation inside the look screen, driven through TACZ's
 * public renderer/state-machine API (no held-item requirement).
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
    /** Precomputed viewport pose rotations (constant angles). */
    private static final Quaternionf VIEWPORT_PITCH = Axis.XP.rotationDegrees(DISPLAY_PITCH);
    private static final Quaternionf VIEWPORT_YAW = Axis.YP.rotationDegrees(DISPLAY_YAW);
    /** Identity render stack: the viewport pose is applied via RenderSystem's
     * model-view stack (matching AnimRenderOps.renderItem3D), so the model's
     * bone transforms accumulate on an identity stack while the outer
     * translate/rotate/scale lives in the shader's model-view matrix. */
    private static final PoseStack RENDER_STACK = new PoseStack();

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
        BedrockGunModel model = TimelessAPI.getGunDisplay(stack)
                .map(GunDisplayInstance::getGunModel)
                .orElse(null);
        if (stateMachine == null || model == null || !stateMachine.isInitialized()) {
            return false;
        }
        advanceStateMachine(renderer, stack, player, partialTicks);

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        // Vanilla GUI item frame: center in display area, flip Y, 16px per block unit.
        pose.translate(centerX, centerY, 150.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        pose.mulPose(VIEWPORT_PITCH);
        pose.mulPose(VIEWPORT_YAW);
        float pixelsPerUnit = 16.0F * scale;
        pose.scale(pixelsPerUnit, pixelsPerUnit, pixelsPerUnit);
        // Pivot on the model's geometric centre instead of the root-node
        // origin (which sits far outside the gun's geometry), and keep the
        // pose above as the only placement control: BedrockGunModel.render
        // applies no internal frame transform of its own.
        Vector3f center = AnimRenderOps.gunModelCenter(model);
        pose.translate(-center.x, -center.y, -center.z);

        // Apply the viewport pose to RenderSystem's model-view stack and render
        // the bedrock model through an identity stack: cube vertices are
        // multiplied by the bone transforms only, and the shader's model-view
        // matrix carries the outer placement. Passing the GUI pose directly to
        // model.render double-transforms every vertex and renders nothing.
        Lighting.setupForEntityInInventory();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        RENDER_STACK.setIdentity();
        // Must use the BedrockGunModel render overload that takes the
        // ItemStack: it fills currentAttachmentItem (laser/grip/scope…) from
        // the stack before walking the part tree. The inherited 5-arg
        // BedrockModel.render skips that, leaving the attachment map empty and
        // NPE-ing in handguardTacticalRender for guns with a tactical rail.
        try {
            model.render(RENDER_STACK, stack, ItemDisplayContext.GUI,
                    RenderType.entityCutout(renderer.getTextureLocation(stack)),
                    0xF000F0, OverlayTexture.NO_OVERLAY);
            model.cleanAnimationTransform();
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        } catch (Throwable t) {
            LOGGER.warn("TACZ inspect viewport model.render failed", t);
            return false;
        } finally {
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            pose.popPose();
        }
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
