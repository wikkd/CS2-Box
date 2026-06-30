package com.reclizer.csgobox.v26_1_2.box;

import com.reclizer.csgobox.v26_1_2.CsgoBox;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class BoxRegistry {
    private BoxRegistry() {
    }

    private static final Map<Identifier, BoxDefinition> BOX_REGISTRY = new LinkedHashMap<>();

    public static void register(BoxDefinition definition) {
        BOX_REGISTRY.put(definition.id(), definition);
        CsgoBox.LOGGER.debug("Registered box: {}", definition.id());
    }

    public static BoxDefinition get(Identifier id) {
        return BOX_REGISTRY.get(id);
    }

    public static Collection<BoxDefinition> getAll() {
        return Collections.unmodifiableCollection(BOX_REGISTRY.values());
    }

    public static Set<Identifier> getIds() {
        return Collections.unmodifiableSet(BOX_REGISTRY.keySet());
    }

    public static int size() {
        return BOX_REGISTRY.size();
    }

    public static void clear() {
        BOX_REGISTRY.clear();
    }

    public static boolean contains(Identifier id) {
        return BOX_REGISTRY.containsKey(id);
    }

    public static void remove(Identifier id) {
        BOX_REGISTRY.remove(id);
    }
}
