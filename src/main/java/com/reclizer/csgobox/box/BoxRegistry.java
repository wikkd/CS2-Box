package com.reclizer.csgobox.box;

import com.reclizer.csgobox.CsgoBox;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class BoxRegistry {
    private BoxRegistry() {
    }

    private static final Map<ResourceLocation, BoxDefinition> BOX_REGISTRY = new LinkedHashMap<>();

    public static void register(BoxDefinition definition) {
        BOX_REGISTRY.put(definition.id(), definition);
        CsgoBox.LOGGER.debug("Registered box: {}", definition.id());
    }

    public static BoxDefinition get(ResourceLocation id) {
        return BOX_REGISTRY.get(id);
    }

    public static Collection<BoxDefinition> getAll() {
        return Collections.unmodifiableCollection(BOX_REGISTRY.values());
    }

    public static Set<ResourceLocation> getIds() {
        return Collections.unmodifiableSet(BOX_REGISTRY.keySet());
    }

    public static int size() {
        return BOX_REGISTRY.size();
    }

    public static void clear() {
        BOX_REGISTRY.clear();
    }

    public static boolean contains(ResourceLocation id) {
        return BOX_REGISTRY.containsKey(id);
    }

    public static void remove(ResourceLocation id) {
        BOX_REGISTRY.remove(id);
    }
}
