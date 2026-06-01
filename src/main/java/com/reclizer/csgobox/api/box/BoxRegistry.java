package com.reclizer.csgobox.api.box;

import com.reclizer.csgobox.CsgoBox;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class BoxRegistry {

    private static final Map<ResourceLocation, BoxDefinition> BOXES = new LinkedHashMap<>();

    public static void register(BoxDefinition definition) {
        BOXES.put(definition.id(), definition);
        CsgoBox.LOGGER.debug("Registered box: {}", definition.id());
    }

    public static BoxDefinition get(ResourceLocation id) {
        return BOXES.get(id);
    }

    public static Collection<BoxDefinition> getAll() {
        return Collections.unmodifiableCollection(BOXES.values());
    }

    public static Set<ResourceLocation> getIds() {
        return Collections.unmodifiableSet(BOXES.keySet());
    }

    public static int size() {
        return BOXES.size();
    }

    public static void clear() {
        BOXES.clear();
    }

    public static boolean contains(ResourceLocation id) {
        return BOXES.containsKey(id);
    }
}
