package com.reclizer.csgobox.v1_21_1.box;

import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.box.BoxRegistryStore;
import com.reclizer.csgobox.logic.GradeMapCache;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Set;

/**
 * Platform shell over the common {@link BoxRegistryStore}: supplies the
 * {@link ResourceLocation} key type, the {@link GradeMapCache} invalidation
 * callbacks, and platform logging.
 */
public final class BoxRegistry {

    private static final BoxRegistryStore<ResourceLocation, BoxDefinition> STORE = new BoxRegistryStore<>(
            id -> GradeMapCache.invalidate(id.toString()),
            GradeMapCache::invalidateAll);

    private BoxRegistry() {
    }

    public static void register(BoxDefinition definition) {
        STORE.register(definition.id(), definition);
        CsgoBox.LOGGER.debug("Registered box: {}", definition.id());
    }

    public static BoxDefinition get(ResourceLocation id) {
        return STORE.get(id);
    }

    public static Collection<BoxDefinition> getAll() {
        return STORE.getAll();
    }

    public static Set<ResourceLocation> getIds() {
        return STORE.getIds();
    }

    public static int size() {
        return STORE.size();
    }

    public static void clear() {
        STORE.clear();
    }

    public static void remove(ResourceLocation id) {
        STORE.remove(id);
    }
}
