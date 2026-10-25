package com.tacz.guns.api;

import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * COMPILE-ONLY API STUB for the forge_1_18_2 module (built by
 * scripts/build-tacz-stub-1182.sh into local-repo as
 * com.tacz:tacz:1.1.8-api-1182). The real TACZ 1.18.2 production jar is
 * SRG-mapped and cannot sit on a Mojang-mapped dev classpath, so the module
 * compiles against these signature stubs instead; at runtime the real mod
 * provides the classes (everything is behind ModList.isLoaded("tacz")).
 * Signatures verified against TACZ's 1.18.2 branch source. Bodies never run.
 */
public final class TimelessAPI {
    public TimelessAPI() {
    }

    public static Set<Map.Entry<ResourceLocation, CommonGunIndex>> getAllCommonGunIndex() {
        return null;
    }

    public static Set<Map.Entry<ResourceLocation, CommonAmmoIndex>> getAllCommonAmmoIndex() {
        return null;
    }

    public static Set<Map.Entry<ResourceLocation, CommonAttachmentIndex>> getAllCommonAttachmentIndex() {
        return null;
    }

    public static Optional<CommonGunIndex> getCommonGunIndex(ResourceLocation id) {
        return Optional.empty();
    }

    public static Optional<CommonAmmoIndex> getCommonAmmoIndex(ResourceLocation id) {
        return Optional.empty();
    }

    public static Optional<CommonAttachmentIndex> getCommonAttachmentIndex(ResourceLocation id) {
        return Optional.empty();
    }
}
