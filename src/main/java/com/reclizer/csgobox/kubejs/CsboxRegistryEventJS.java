package com.reclizer.csgobox.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;

public class CsboxRegistryEventJS implements KubeEvent {

    public BoxBuilderJS create(String id, String name) {
        return new BoxBuilderJS(id, name);
    }
}