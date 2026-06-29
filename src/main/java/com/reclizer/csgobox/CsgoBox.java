package com.reclizer.csgobox;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.box.BoxJsonLoader;
import com.reclizer.csgobox.box.BoxRegistry;
import com.reclizer.csgobox.capability.ModCapability;
import com.reclizer.csgobox.config.CsboxConfig;
import com.reclizer.csgobox.item.ItemCsgoBox;
import com.reclizer.csgobox.item.ModItems;
import com.reclizer.csgobox.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.advancement.ModLoadedTrigger;
import com.reclizer.csgobox.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.packet.PacketCsgoProgress;
import com.reclizer.csgobox.packet.PacketRequestBoxItems;
import com.reclizer.csgobox.packet.PacketSyncBoxItems;
import com.reclizer.csgobox.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

@Mod(CsgoBox.MODID)
public class CsgoBox {

    public static final String MODID = "csgobox";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CsboxConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;
    public static Stat<ResourceLocation> OPENED_BOXES_STAT;

    static {
        var pair = new ModConfigSpec.Builder()
                .configure(CsboxConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    public CsgoBox(IEventBus modEventBus) {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC, "csgobox.toml");

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::resolveOpenedBoxesStat);
        modEventBus.addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == CONFIG_SPEC) {
                LOGGER.info("CS2 Box config reloaded");
            }
        });
        modEventBus.addListener((RegisterEvent event) -> {
            ResourceKey<?> registryKey = event.getRegistryKey();
            if (registryKey.equals(Registries.CUSTOM_STAT)) {
                event.register(Registries.CUSTOM_STAT, OpenedBoxTrigger.STAT_ID, () -> OpenedBoxTrigger.STAT_ID);
            } else if (registryKey.equals(Registries.TRIGGER_TYPE)) {
                event.register(Registries.TRIGGER_TYPE, OpenedBoxTrigger.ID, () -> OpenedBoxTrigger.INSTANCE);
                event.register(Registries.TRIGGER_TYPE, ModLoadedTrigger.ID, () -> ModLoadedTrigger.INSTANCE);
            }
        });

        ModSounds.SOUNDS.register(modEventBus);
        ModCapability.ATTACHMENT_TYPES.register(modEventBus);
        ItemCsgoBox.registerDataComponents(modEventBus);
        ModItems.register(modEventBus);
        ModItems.registerTab(modEventBus);

        NeoForge.EVENT_BUS.register(this);
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MODID);
        registrar.playToServer(PacketCsgoProgress.TYPE, PacketCsgoProgress.STREAM_CODEC, PacketCsgoProgress::handleServer);
        registrar.playToClient(PacketBoxOpenResult.TYPE, PacketBoxOpenResult.STREAM_CODEC, PacketBoxOpenResult::handle);
        registrar.playToServer(PacketRequestBoxItems.TYPE, PacketRequestBoxItems.STREAM_CODEC, PacketRequestBoxItems::handle);
        registrar.playToClient(PacketSyncBoxItems.TYPE, PacketSyncBoxItems.STREAM_CODEC, PacketSyncBoxItems::handle);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        if (CONFIG.loadDefaultBoxes()) {
            event.enqueueWork(BoxJsonLoader::loadAll);
        }
        LOGGER.info("CS2 Box initialized successfully");
    }

    private void resolveOpenedBoxesStat(final FMLCommonSetupEvent event) {
        OPENED_BOXES_STAT = Stats.CUSTOM.get(OpenedBoxTrigger.STAT_ID);
        if (OPENED_BOXES_STAT == null) {
            throw new IllegalStateException(
                    "Custom stat " + OpenedBoxTrigger.STAT_ID + " not registered — CUSTOM_STAT registry missing entry");
        }
        LOGGER.info("Resolved custom stat {} -> {}", OpenedBoxTrigger.STAT_ID, OPENED_BOXES_STAT);
    }

    public static boolean debug() {
        return CONFIG.enableDebugLogging();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("CS2 Box server started with {} box definitions", BoxRegistry.size());
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("CS2 Box client setup complete");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
