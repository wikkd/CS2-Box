package com.reclizer.csgobox.kubejs;

import com.reclizer.csgobox.CsgoBox;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;

public class KubeJsPlugin implements KubeJSPlugin {

    public static final EventGroup CSBOX_GROUP = EventGroup.of("CsboxEvents");

    public static final EventHandler REGISTRY = CSBOX_GROUP.startup("registry", () -> CsboxRegistryEventJS.class);

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(CSBOX_GROUP);
        CsgoBox.LOGGER.info("CS2 Box KubeJS integration enabled");
    }
}