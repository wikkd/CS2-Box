package com.reclizer.csgobox;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.capability.ModCapability;
import com.reclizer.csgobox.config.CsgoBoxManage;
import com.reclizer.csgobox.gui.RecModMenus;
import com.reclizer.csgobox.item.ModItems;
import com.reclizer.csgobox.packet.PacketCsgoProgress;
import com.reclizer.csgobox.packet.PacketGiveItem;
import com.reclizer.csgobox.packet.PacketUpdateMode;
import com.reclizer.csgobox.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(CsgoBox.MODID)
public class CsgoBox {

    public static final String MODID = "csgobox";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, MODID);

    public CsgoBox(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        ModSounds.SOUNDS.register(modEventBus);
        ModCapability.ATTACHMENT_TYPES.register(modEventBus);
        RecModMenus.register(modEventBus);
        ModItems.register(modEventBus);
        ModItems.registerTab(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MODID);
        registrar.playToServer(PacketCsgoProgress.TYPE, PacketCsgoProgress.STREAM_CODEC, PacketCsgoProgress::handleServer);
        registrar.playToServer(PacketGiveItem.TYPE, PacketGiveItem.STREAM_CODEC, PacketGiveItem::handle);
        registrar.playToServer(PacketUpdateMode.TYPE, PacketUpdateMode.STREAM_CODEC, PacketUpdateMode::handle);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        Path configPath = FMLPaths.CONFIGDIR.get();
        Path folderPath = configPath.resolve("csbox");
        try {
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }
            String content =
                    """
                    {
                      "name": "Weapons Supply Box",
                      "key": "csgobox:csgo_key0",
                      "drop": 0.12,
                      "random": [
                        2,
                        5,
                        6,
                        20,
                        625
                      ],
                      "entity": [
                        "minecraft:zombie",
                        "minecraft:skeleton"
                      ],
                      "grade1": [
                        "{\\"id\\":\\"minecraft:stone_sword\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:iron_axe\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:iron_shovel\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:iron_pickaxe\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:iron_axe\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:iron_hoe\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:iron_sword\\",\\"count\\":1}"
                      ],
                      "grade2": [
                        "{\\"id\\":\\"minecraft:golden_sword\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:golden_axe\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:golden_axe\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:golden_pickaxe\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:golden_shovel\\",\\"count\\":1}"
                      ],
                      "grade3": [
                        "{\\"id\\":\\"minecraft:diamond_shovel\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:diamond_pickaxe\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:diamond_hoe\\",\\"count\\":1}"
                      ],
                      "grade4": [
                        "{\\"id\\":\\"minecraft:diamond_axe\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:diamond_sword\\",\\"count\\":1}"
                      ],
                      "grade5": [
                        "{\\"id\\":\\"minecraft:netherite_sword\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:netherite_axe\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:netherite_pickaxe\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:netherite_shovel\\",\\"count\\":1}",
                        "{\\"id\\":\\"minecraft:netherite_hoe\\",\\"count\\":1}"
                      ]
                    }""";

            if (!Files.exists(folderPath.resolve("default.json"))) {
                Path filePath = folderPath.resolve("default.json");
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath.toFile()), StandardCharsets.UTF_8))) {
                    writer.write(content);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        event.enqueueWork(() -> {
            try {
                CsgoBoxManage.loadConfigBox();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}