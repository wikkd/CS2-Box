package com.reclizer.csgobox.forge_1_20_1;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.BoxFileWatcher;
import com.reclizer.csgobox.forge_1_20_1.advancement.ModLoadedTrigger;
import com.reclizer.csgobox.forge_1_20_1.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.forge_1_20_1.box.BoxJsonLoader;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.config.CsboxConfig;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ItemTerminal;
import com.reclizer.csgobox.forge_1_20_1.item.ModItems;
import com.reclizer.csgobox.forge_1_20_1.menu.ModMenus;
import com.reclizer.csgobox.forge_1_20_1.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import com.reclizer.csgobox.forge_1_20_1.packet.Networking;
import com.reclizer.csgobox.forge_1_20_1.terminal.TerminalSessionManager;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Mod(CsgoBox.MODID)
public class CsgoBox {

    public static final String MODID = "csgobox";
    public static String MODVERSION = "unknown";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CsboxConfig CONFIG;
    public static final ForgeConfigSpec CONFIG_SPEC;
    public static Stat<ResourceLocation> OPENED_BOXES_STAT;
    public static final ResourceLocation TERMINAL_BUYS_STAT_ID =
            new ResourceLocation(CsgoBox.MODID, "terminal_buys");
    public static Stat<ResourceLocation> TERMINAL_BUYS_STAT;

    public static final ExecutorService BULK_COMPUTE_POOL = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "csgobox-bulk-compute-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private static BoxFileWatcher boxWatcher;

    static {
        var pair = new ForgeConfigSpec.Builder()
                .configure(CsboxConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    public CsgoBox() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        try {
            MODVERSION = ModLoadingContext.get().getContainer()
                    .getModInfo().getVersion().toString();
        } catch (Exception e) {
            LOGGER.warn("Could not read mod version from container: {}", e.getMessage());
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC, "csgobox.toml");

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::resolveOpenedBoxesStat);
        modEventBus.addListener(this::resolveTerminalBuysStat);
        modEventBus.addListener(this::registerDynamicBoxItems);
        modEventBus.addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == CONFIG_SPEC) {
                LOGGER.info("CS2 Box config reloaded");
            }
        });
        modEventBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(Registries.CUSTOM_STAT)) {
                event.register(Registries.CUSTOM_STAT, helper -> {
                    // ResourceLocation 重载：String 重载会再拼一次 modid（csgobox:csgobox:opened_boxes 非法）
                    helper.register(OpenedBoxTrigger.STAT_ID, OpenedBoxTrigger.STAT_ID);
                    helper.register(TERMINAL_BUYS_STAT_ID, TERMINAL_BUYS_STAT_ID);
                });
            }
        });

        // Register advancement triggers directly via CriteriaTriggers (1.20.1 pattern)
        net.minecraft.advancements.CriteriaTriggers.register(OpenedBoxTrigger.INSTANCE);
        net.minecraft.advancements.CriteriaTriggers.register(ModLoadedTrigger.INSTANCE);

        ModSounds.SOUNDS.register(modEventBus);
        ModItems.register(modEventBus);
        ModItems.registerTab(modEventBus);
        com.reclizer.csgobox.forge_1_20_1.block.ModBlocks.register(modEventBus);
        ModMenus.register(modEventBus);
        com.reclizer.csgobox.forge_1_20_1.villager.ModVillagers.register(modEventBus);

        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Networking.registerMessages();
            if (CONFIG.enableHotReload()) {
                startBoxWatcher();
            }
        });
        com.reclizer.csgobox.forge_1_20_1.villager.ModVillagers.registerTrades();
        LOGGER.info("CS2 Box initialized successfully");
    }

    private void startBoxWatcher() {
        if (boxWatcher != null) {
            return;
        }
        Path boxesDir = FMLPaths.CONFIGDIR.get().resolve("csbox");
        boxWatcher = BoxFileWatcher.start(
                boxesDir,
                BoxJsonLoader::reloadPreserving,
                msg -> LOGGER.info("[BoxFileWatcher] {}", msg),
                (msg, err) -> LOGGER.error("[BoxFileWatcher] {}", msg, err));
    }

    private void resolveOpenedBoxesStat(final FMLCommonSetupEvent event) {
        OPENED_BOXES_STAT = Stats.CUSTOM.get(OpenedBoxTrigger.STAT_ID);
        if (OPENED_BOXES_STAT == null) {
            throw new IllegalStateException(
                    "Custom stat " + OpenedBoxTrigger.STAT_ID + " not registered — CUSTOM_STAT registry missing entry");
        }
        LOGGER.info("Resolved custom stat {} -> {}", OpenedBoxTrigger.STAT_ID, OPENED_BOXES_STAT);
    }

    private void resolveTerminalBuysStat(final FMLCommonSetupEvent event) {
        TERMINAL_BUYS_STAT = Stats.CUSTOM.get(TERMINAL_BUYS_STAT_ID);
        if (TERMINAL_BUYS_STAT == null) {
            throw new IllegalStateException(
                    "Custom stat " + TERMINAL_BUYS_STAT_ID + " not registered — CUSTOM_STAT registry missing entry");
        }
        LOGGER.info("Resolved custom stat {} -> {}", TERMINAL_BUYS_STAT_ID, TERMINAL_BUYS_STAT);
    }

    public static boolean debug() {
        return CONFIG.enableDebugLogging();
    }

    private void registerDynamicBoxItems(final RegisterEvent event) {
        // 注意：必须用 Registries.ITEM（ResourceKey）；ForgeRegistries.ITEMS 是 registry 对象，equals 永假
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }
        Path configDir = FMLPaths.CONFIGDIR.get().resolve("csbox");
        if (!Files.isDirectory(configDir)) {
            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                LOGGER.warn("[csgo-dynamic-items] cannot create {}: {}", configDir, e.getMessage());
                return;
            }
        }
        BoxDefaults.upgradeLegacyTerminalConfig(configDir);
        BoxDefaults.writeDefaultTerminalIfMissing(configDir);
        int registered = 0;
        int skipped = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "*.json")) {
            for (Path file : stream) {
                String filename = file.getFileName().toString();
                if (filename.startsWith("_") || !filename.endsWith(".json")) {
                    continue;
                }
                String idStr = filename.substring(0, filename.length() - 5);
                if (idStr.isEmpty()) {
                    continue;
                }
                ResourceLocation itemId;
                try {
                    itemId = new ResourceLocation(MODID, idStr);
                } catch (Exception e) {
                    LOGGER.warn("[csgo-dynamic-items] skip invalid filename '{}': {}", filename, e.getMessage());
                    continue;
                }
                if (ForgeRegistries.ITEMS.containsKey(itemId)) {
                    skipped++;
                    continue;
                }
                if (ModItems.ITEMS.getEntries().stream()
                        .anyMatch(entry -> entry.getId().equals(itemId))) {
                    skipped++;
                    continue;
                }
                final ResourceLocation boxId = itemId;
                final boolean isTerminal = "terminal".equals(BoxJsonLoader.readType(file));
                event.register(Registries.ITEM, helper -> {
                    ItemCsgoBox item;
                    if (isTerminal) {
                        item = new ItemTerminal(new Item.Properties()) {
                            @Override
                            public ItemStack getDefaultInstance() {
                                ItemStack stack = super.getDefaultInstance();
                                ItemCsgoBox.setBoxId(boxId, stack);
                                return stack;
                            }
                        };
                    } else {
                        item = new ItemCsgoBox(new Item.Properties().stacksTo(16)) {
                            @Override
                            public ItemStack getDefaultInstance() {
                                ItemStack stack = super.getDefaultInstance();
                                ItemCsgoBox.setBoxId(boxId, stack);
                                return stack;
                            }
                        };
                    }
                    helper.register(itemId, item);
                });
                registered++;
            }
        } catch (IOException e) {
            LOGGER.warn("[csgo-dynamic-items] scan of {} failed: {}", configDir, e.getMessage());
            return;
        }
        if (registered > 0 || skipped > 0) {
            LOGGER.info("[csgo-dynamic-items] registered {} dynamic box item(s) from config/csbox/ ({} skipped as already registered)",
                    registered, skipped);
        }
    }

    public void onServerStarting(ServerStartingEvent event) {
        if (CONFIG.loadDefaultBoxes()) {
            BoxJsonLoader.loadAll();
        }
        TerminalSessionManager.bindServer(event.getServer());
        LOGGER.info("CS2 Box server started with {} box definitions", BoxRegistry.size());
    }

    public void onServerStopping(ServerStoppingEvent event) {
        TerminalSessionManager.saveNow();
        TerminalSessionManager.unbindServer();
        if (boxWatcher != null) {
            boxWatcher.stop();
            boxWatcher = null;
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("CS2 Box client setup complete");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
            event.enqueueWork(() -> {
                // 1.20.1 屏幕注册：MenuScreens.register 必须在主线程执行（FMLClientSetupEvent.enqueueWork）
                net.minecraft.client.gui.screens.MenuScreens.register(
                        ModMenus.ARMORY_RECYCLER.get(),
                        com.reclizer.csgobox.forge_1_20_1.gui.ArmoryRecyclerScreen::new);
            });
        }

        /**
         * Dynamic box items have no model file, so vanilla bakes them to the
         * missing model. 1.20.1 has no per-stack ITEM_MODEL component (a 26.x
         * API), so remap csgobox items stuck with the missing model to the
         * baked csgo_box model; terminal-type items (ItemTerminal) go to the
         * terminal model instead so every terminal config file renders as a
         * terminal. Static items keep their real model.
         */
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onModelBaking(ModelEvent.ModifyBakingResult event) {
            Map<ResourceLocation, BakedModel> models = event.getModels();
            ModelResourceLocation base = new ModelResourceLocation(
                    new ResourceLocation(CsgoBox.MODID, "csgo_box"), "inventory");
            ModelResourceLocation terminalLoc = new ModelResourceLocation(
                    new ResourceLocation(CsgoBox.MODID, "terminal"), "inventory");
            BakedModel baseModel = models.get(base);
            BakedModel terminalModel = models.get(terminalLoc);
            if (baseModel == null) {
                LOGGER.warn("[csgo-model] csgo_box model missing at bake time; dynamic box remap skipped");
                return;
            }
            // Missing-model instance: the same baked model vanilla assigns to
            // items with no models/item/<id>.json (BlockModel.MISSING baked).
            BakedModel missingModel = models.get(ModelBakery.MISSING_MODEL_LOCATION);
            ResourceLocation missingSprite = MissingTextureAtlasSprite.getLocation();
            int swapped = 0;
            for (ResourceLocation itemId : ForgeRegistries.ITEMS.getKeys()) {
                if (!CsgoBox.MODID.equals(itemId.getNamespace())) {
                    continue;
                }
                ModelResourceLocation loc = new ModelResourceLocation(itemId, "inventory");
                BakedModel current = models.get(loc);
                // Missing-model dynamic boxes: either the entry is absent, is
                // the vanilla missing-model instance, or its particle is the
                // missing sprite. Items with a real model are left untouched.
                boolean isMissing = current == null;
                if (current != null) {
                    isMissing = current == missingModel
                            || current.getParticleIcon().contents().name().equals(missingSprite);
                }
                if (!isMissing) {
                    continue;
                }
                Item item = ForgeRegistries.ITEMS.getValue(itemId);
                BakedModel replacement = item instanceof ItemTerminal && terminalModel != null
                        ? terminalModel : baseModel;
                models.put(loc, replacement);
                swapped++;
                LOGGER.info("[csgo-model] remapped {} -> {}", itemId, replacement == terminalModel ? "terminal" : "csgo_box");
            }
            if (swapped > 0) {
                LOGGER.info("[csgo-model] remapped {} dynamic box item model(s)", swapped);
            }
        }
    }
}
