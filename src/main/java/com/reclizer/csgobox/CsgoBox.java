package com.reclizer.csgobox;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.api.box.BoxJsonLoader;
import com.reclizer.csgobox.api.box.BoxRegistry;
import com.reclizer.csgobox.capability.ModCapability;
import com.reclizer.csgobox.config.CsboxConfig;
import com.reclizer.csgobox.item.ItemCsgoBox;
import com.reclizer.csgobox.item.ModItems;
import com.reclizer.csgobox.packet.PacketCsgoProgress;
import com.reclizer.csgobox.packet.PacketGiveItem;
import com.reclizer.csgobox.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.sounds.ModSounds;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(CsgoBox.MODID)
public class CsgoBox {

    public static final String MODID = "csgobox";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static CsboxConfig CONFIG;

    public CsgoBox(IEventBus modEventBus) {
        CONFIG = AutoConfig.register(CsboxConfig.class, Toml4jConfigSerializer::new).getConfig();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

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
        registrar.playToServer(PacketGiveItem.TYPE, PacketGiveItem.STREAM_CODEC, PacketGiveItem::handle);
        registrar.playToClient(PacketBoxOpenResult.TYPE, PacketBoxOpenResult.STREAM_CODEC, PacketBoxOpenResult::handle);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(BoxJsonLoader::loadAll);
        LOGGER.info("CS2 Box initialized successfully");
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
