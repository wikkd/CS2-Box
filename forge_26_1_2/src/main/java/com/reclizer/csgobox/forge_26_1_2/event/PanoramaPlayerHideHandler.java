package com.reclizer.csgobox.forge_26_1_2.event;

import com.reclizer.csgobox.forge_26_1_2.CsgoBox;
import com.reclizer.csgobox.forge_26_1_2.gui.CsLookItemScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * While the look screen owns the render camera (panorama background), the
 * local player's entity render is canceled entirely. Invisibility alone only
 * skips the body model — vanilla still draws the equipment layers (held item,
 * armor) of invisible entities, which would float in the panorama view. The
 * 26.x render states carry no entity reference, so the local player is
 * matched by its exact interpolated render position (the same
 * {@code Mth.lerp} the state extraction uses, so equality is exact).
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = CsgoBox.MODID)
public final class PanoramaPlayerHideHandler {
    /** Position match slack around an otherwise exact equality (both sides
     *  run the identical lerp with the identical partial tick). */
    private static final double POSITION_EPSILON = 1.0E-4;

    private PanoramaPlayerHideHandler() {
    }

    /** Returning true cancels the render (Forge 26.x listener contract for
     *  cancellable events). */
    @SubscribeEvent
    public static boolean onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CsLookItemScreen screen && screen.isSceneryRigMounted()
                && mc.player != null) {
            LivingEntityRenderState state = event.getRenderState();
            if (state.entityType == EntityType.PLAYER) {
                LocalPlayer player = mc.player;
                double partial = state.partialTick;
                double px = Mth.lerp(partial, player.xo, player.getX());
                double py = Mth.lerp(partial, player.yo, player.getY());
                double pz = Mth.lerp(partial, player.zo, player.getZ());
                if (Math.abs(state.x - px) < POSITION_EPSILON
                        && Math.abs(state.y - py) < POSITION_EPSILON
                        && Math.abs(state.z - pz) < POSITION_EPSILON) {
                    return true;
                }
            }
        }
        return false;
    }
}
