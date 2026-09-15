package com.reclizer.csgobox.forge_1_20_1.top;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ITheOneProbe;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModList;

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

    static ResourceLocation id(String path) {
        return new ResourceLocation(CsgoBox.MODID, path);
    }
}