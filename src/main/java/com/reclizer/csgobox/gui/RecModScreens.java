package com.reclizer.csgobox.gui;

import com.reclizer.csgobox.CsgoBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CsgoBox.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RecModScreens {

    @SubscribeEvent
    public static void clientLoad(RegisterMenuScreensEvent event) {
        event.register(RecModMenus.CSGO_BOX_CRAFT.get(), CsgoBoxCraftScreen::new);
    }
}