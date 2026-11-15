package com.reclizer.csgobox.forge_1_20_1.event;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.gui.CsLookItemScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * While the look screen owns the render camera (panorama background), the
 * local player's entity render is canceled entirely. Invisibility alone only
 * skips the body model — vanilla still draws the equipment layers (held item,
 * armor) of invisible entities, which would float in the panorama view.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = CsgoBox.MODID)
public final class PanoramaPlayerHideHandler {
    private PanoramaPlayerHideHandler() {
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CsLookItemScreen screen && screen.isSceneryRigMounted()
                && event.getEntity() == mc.player) {
            event.setCanceled(true);
        }
    }
}
