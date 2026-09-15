package com.reclizer.csgobox.v26_1_2.top;

import com.reclizer.csgobox.v26_1_2.CsgoBox;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ITheOneProbe;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.ModList;

import java.util.function.Function;

/**
 * The One Probe integration: registers the CS2-Box probe providers through
 * TOP's documented inter-mod-communication entry point
 * ({@code InterModComms.sendTo("theoneprobe", "getTheOneProbe", ...)}).
 *
 * <p>Only invoked when TOP is loaded (guarded by
 * {@link #registerIfLoaded()}); the provider classes reference TOP API, so the
 * integration is inert when TOP is absent.</p>
 */
public final class CsgoBoxTopPlugin {

    private CsgoBoxTopPlugin() {
    }

    /** Called from the mod's common setup; no-op when TOP is not installed. */
    public static void registerIfLoaded() {
        if (!ModList.get().isLoaded("theoneprobe")) {
            return;
        }
        InterModComms.sendTo("theoneprobe", "getTheOneProbe", () ->
                (Function<ITheOneProbe, Void>) probe -> {
                    probe.registerEntityProvider(new BoxItemEntityProvider());
                    probe.registerProvider(new RecyclerBlockProvider());
                    return null;
                });
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(CsgoBox.MODID, path);
    }
}