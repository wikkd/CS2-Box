package com.reclizer.csgobox.v26_2.box;

import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.box.BoxRegistryStore;
import com.reclizer.csgobox.logic.GradeMapCache;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Set;

/**
 * Platform shell over the common {@link BoxRegistryStore}: supplies the
 * {@link Identifier} key type, the {@link GradeMapCache} invalidation
 * callbacks, and platform logging.
 */
public final class BoxRegistry {

    private static final BoxRegistryStore<Identifier, BoxDefinition> STORE = new BoxRegistryStore<>(
            id -> GradeMapCache.invalidate(id.toString()),
            GradeMapCache::invalidateAll);

    private BoxRegistry() {
    }

    public static void register(BoxDefinition definition) {
        STORE.register(definition.id(), definition);
        CsgoBox.LOGGER.debug("Registered box: {}", definition.id());
    }

    public static BoxDefinition get(Identifier id) {
        return STORE.get(id);
    }

    public static Collection<BoxDefinition> getAll() {
        return STORE.getAll();
    }

    public static Set<Identifier> getIds() {
        return STORE.getIds();
    }

    public static int size() {
        return STORE.size();
    }

    public static void clear() {
        STORE.clear();
    }

    public static void remove(Identifier id) {
        STORE.remove(id);
    }
}
