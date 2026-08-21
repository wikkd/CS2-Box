package com.reclizer.csgobox.v1_21_1;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.BoxFileWatcher;
import com.reclizer.csgobox.v1_21_1.box.BoxJsonLoader;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.capability.ModCapability;
import com.reclizer.csgobox.v1_21_1.config.CsboxConfig;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ItemTerminal;
import com.reclizer.csgobox.v1_21_1.item.ModItems;
import com.reclizer.csgobox.v1_21_1.menu.ModMenus;
import com.reclizer.csgobox.v1_21_1.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.v1_21_1.advancement.ModLoadedTrigger;
import com.reclizer.csgobox.v1_21_1.block.ModBlocks;
import com.reclizer.csgobox.v1_21_1.villager.ModVillagers;
import com.reclizer.csgobox.v1_21_1.packet.PacketBoxBulkResult;
import com.reclizer.csgobox.v1_21_1.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.v1_21_1.packet.PacketCsgoBulkProgress;
import com.reclizer.csgobox.v1_21_1.packet.PacketCsgoProgress;
import com.reclizer.csgobox.v1_21_1.packet.PacketRequestBoxItems;
import com.reclizer.csgobox.v1_21_1.packet.PacketSyncBoxItems;
import com.reclizer.csgobox.v1_21_1.packet.PacketTerminalBuy;
import com.reclizer.csgobox.v1_21_1.packet.PacketTerminalBuyResult;
import com.reclizer.csgobox.v1_21_1.packet.PacketTerminalClose;
import com.reclizer.csgobox.v1_21_1.packet.PacketTerminalOpen;
import com.reclizer.csgobox.v1_21_1.packet.PacketTerminalReject;
import com.reclizer.csgobox.v1_21_1.packet.PacketTerminalState;
import com.reclizer.csgobox.v1_21_1.packet.PacketSyncBoxDefinitions;
import com.reclizer.csgobox.v1_21_1.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Mod(CsgoBox.MODID)
public class CsgoBox {

    /** Items statically registered in {@code ModItems}; never re-added from config JSON. */
    private static final Set<String> STATIC_ITEM_IDS = Set.of(
            "csgo_box", "csgo_key0", "csgo_key1", "csgo_key2", "csgo_key3",
            "armory_point", "terminal");

    public static final String MODID = "csgobox";
    /** Mod version from {@code ModContainer}; consumed by the tutorial download
     *  which fires after this constructor. "unknown" only if loaded pre-init. */
    public static String MODVERSION = "unknown";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CsboxConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;
    public static Stat<ResourceLocation> OPENED_BOXES_STAT;
    public static final ResourceLocation TERMINAL_BUYS_STAT_ID =
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "terminal_buys");
    public static Stat<ResourceLocation> TERMINAL_BUYS_STAT;

    /** Background pool for {@code PacketCsgoBulkProgress} rolls (2 daemon
     *  threads; further requests queue). Shut down on mod unload. */
    public static final ExecutorService BULK_COMPUTE_POOL = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "csgobox-bulk-compute-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    /** Watches {@code config/csbox/} for JSON changes (debounced reload);
     *  created in {@link #commonSetup}, shut down in {@link #onServerStopping}. */
    private static BoxFileWatcher boxWatcher;

    static {
        var pair = new ModConfigSpec.Builder()
                .configure(CsboxConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    public CsgoBox(IEventBus modEventBus) {
        try {
            MODVERSION = ModLoadingContext.get().getActiveContainer()
                    .getModInfo().getVersion().toString();
        } catch (Exception e) {
            LOGGER.warn("Could not read mod version from container: {}", e.getMessage());
        }
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC, "csgobox.toml");

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::resolveOpenedBoxesStat);
        modEventBus.addListener(this::resolveTerminalBuysStat);
        modEventBus.addListener(this::registerDynamicBoxItems);
        modEventBus.addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == CONFIG_SPEC) {
                LOGGER.info("CS2 Box config reloaded");
            }
        });
        modEventBus.addListener((RegisterEvent event) -> {
            ResourceKey<?> registryKey = event.getRegistryKey();
            if (registryKey.equals(Registries.CUSTOM_STAT)) {
                event.register(Registries.CUSTOM_STAT, OpenedBoxTrigger.STAT_ID, () -> OpenedBoxTrigger.STAT_ID);
                event.register(Registries.CUSTOM_STAT, TERMINAL_BUYS_STAT_ID, () -> TERMINAL_BUYS_STAT_ID);
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
        ModBlocks.register(modEventBus);
        ModMenus.register(modEventBus);
        ModVillagers.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MODID);
        registrar.playToServer(PacketCsgoProgress.TYPE, PacketCsgoProgress.STREAM_CODEC, PacketCsgoProgress::handleServer);
        registrar.playToServer(PacketCsgoBulkProgress.TYPE, PacketCsgoBulkProgress.STREAM_CODEC, PacketCsgoBulkProgress::handleServer);
        registrar.playToClient(PacketBoxOpenResult.TYPE, PacketBoxOpenResult.STREAM_CODEC, PacketBoxOpenResult::handle);
        registrar.playToClient(PacketBoxBulkResult.TYPE, PacketBoxBulkResult.STREAM_CODEC, PacketBoxBulkResult::handle);
        registrar.playToServer(PacketRequestBoxItems.TYPE, PacketRequestBoxItems.STREAM_CODEC, PacketRequestBoxItems::handle);
        registrar.playToClient(PacketSyncBoxItems.TYPE, PacketSyncBoxItems.STREAM_CODEC, PacketSyncBoxItems::handle);
        registrar.playToServer(PacketTerminalBuy.TYPE, PacketTerminalBuy.STREAM_CODEC, PacketTerminalBuy::handleServer);
        registrar.playToClient(PacketTerminalBuyResult.TYPE, PacketTerminalBuyResult.STREAM_CODEC, PacketTerminalBuyResult::handle);
        registrar.playToServer(PacketTerminalOpen.TYPE, PacketTerminalOpen.STREAM_CODEC, PacketTerminalOpen::handleServer);
        registrar.playToClient(PacketTerminalState.TYPE, PacketTerminalState.STREAM_CODEC, PacketTerminalState::handle);
        registrar.playToServer(PacketTerminalReject.TYPE, PacketTerminalReject.STREAM_CODEC, PacketTerminalReject::handleServer);
        registrar.playToServer(PacketTerminalClose.TYPE, PacketTerminalClose.STREAM_CODEC, PacketTerminalClose::handleServer);
        registrar.playToClient(PacketSyncBoxDefinitions.TYPE, PacketSyncBoxDefinitions.STREAM_CODEC, PacketSyncBoxDefinitions::handle);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Load box definitions early (registry phase is done, all items —
            // including TACZ guns — are registered). This populates BoxRegistry
            // before the creative tab is first built, so dynamic boxes always
            // appear there even before a server/world has started. onServerStarting
            // re-runs loadAll() per world, which is idempotent (re-register).
            BoxJsonLoader.loadAll();
            if (CONFIG.enableHotReload()) {
                startBoxWatcher();
            }
        });
        LOGGER.info("CS2 Box initialized successfully");
    }

    private void startBoxWatcher() {
        if (boxWatcher != null) {
            return;
        }
        Path boxesDir = FMLPaths.CONFIGDIR.get().resolve("csbox");
        boxWatcher = BoxFileWatcher.start(
                boxesDir,
                () -> {
                    BoxJsonLoader.reloadPreserving();
                    var server = ServerLifecycleHooks.getCurrentServer();
                    if (server != null) {
                        server.execute(CsgoBox::broadcastBoxDefinitions);
                    }
                },
                msg -> LOGGER.info("[BoxFileWatcher] {}", msg),
                (msg, err) -> LOGGER.error("[BoxFileWatcher] {}", msg, err));
    }

    /** Broadcasts the box registry to all players so client registries follow
     *  server state after reloads. No-op on the client or without a server. */
    public static void broadcastBoxDefinitions() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        PacketSyncBoxDefinitions packet = PacketSyncBoxDefinitions.ofAll();
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, packet);
        }
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

    /**
     * Scan {@code config/csbox/*.json} and register one dynamic item per file
     * so {@code /give} can address any box by its file name: "terminal" type
     * becomes {@link ItemTerminal}, everything else a plain {@link ItemCsgoBox}
     * with {@code box_id} preset. Items without a model file render as
     * missing-texture (function unaffected). Registered via {@link RegisterEvent}
     * deferred suppliers, before the registry freezes — enqueueWork fires
     * after freeze and would crash.
     */
    private void registerDynamicBoxItems(final RegisterEvent event) {
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
        // Pre-v2.0.0 files (no "type") are upgraded here first. Since 2.0.0
        // the terminal ships unconfigured — no default terminal.json is
        // generated (empty crate, same as the default box).
        BoxDefaults.upgradeLegacyTerminalConfig(configDir);
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
                // Statically-registered items (ModItems) are never re-added.
                if (STATIC_ITEM_IDS.contains(idStr)) {
                    skipped++;
                    continue;
                }
                ResourceLocation itemId;
                try {
                    itemId = ResourceLocation.fromNamespaceAndPath(MODID, idStr);
                } catch (Exception e) {
                    LOGGER.warn("[csgo-dynamic-items] skip invalid filename '{}': {}", filename, e.getMessage());
                    continue;
                }
                if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                    skipped++;
                    continue;
                }
                final ResourceLocation boxId = itemId;
                // "type" is the single source of truth (v2.0.0): terminal
                // registers an ItemTerminal (client dispatch is by instanceof,
                // remote clients never see the type field); everything else a
                // plain ItemCsgoBox. The terminal model resolves by registry
                // id to common models/item/terminal.json.
                final boolean isTerminal = "terminal".equals(BoxJsonLoader.readType(file));
                event.register(Registries.ITEM, itemId, () -> {
                    ItemCsgoBox item;
                    if (isTerminal) {
                        item = new ItemTerminal() {
                            @Override
                            public ItemStack getDefaultInstance() {
                                ItemStack stack = super.getDefaultInstance();
                                ItemCsgoBox.setBoxId(boxId, stack);
                                return stack;
                            }
                        };
                    } else {
                        item = new ItemCsgoBox() {
                            @Override
                            public ItemStack getDefaultInstance() {
                                ItemStack stack = super.getDefaultInstance();
                                ItemCsgoBox.setBoxId(boxId, stack);
                                return stack;
                            }
                        };
                    }
                    return item;
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

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (CONFIG.loadDefaultBoxes()) {
            BoxJsonLoader.loadAll();
        }
        com.reclizer.csgobox.v1_21_1.terminal.TerminalSessionManager.bindServer(event.getServer());
        LOGGER.info("CS2 Box server started with {} box definitions", BoxRegistry.size());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        com.reclizer.csgobox.v1_21_1.terminal.TerminalSessionManager.saveNow();
        com.reclizer.csgobox.v1_21_1.terminal.TerminalSessionManager.unbindServer();
        if (boxWatcher != null) {
            boxWatcher.stop();
            boxWatcher = null;
        }
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("CS2 Box client setup complete");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }

        /**
         * Dynamic box items have no model file, so vanilla bakes them to the
         * missing model. 1.21.1 has no per-stack ITEM_MODEL component (a 26.x
         * API), so remap csgobox items stuck with the missing model to the
         * baked csgo_box model; terminal-type items (ItemTerminal) go to the
         * terminal model instead so every terminal config file renders as a
         * terminal. Static items keep their real model.
         */
        @SubscribeEvent
        public static void onModelBaking(ModelEvent.ModifyBakingResult event) {
            Map<ModelResourceLocation, BakedModel> models = event.getModels();
            ModelResourceLocation base = new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "csgo_box"), "inventory");
            ModelResourceLocation terminalLoc = new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "terminal"), "inventory");
            BakedModel baseModel = models.get(base);
            BakedModel terminalModel = models.get(terminalLoc);
            if (baseModel == null) {
                return;
            }
            ResourceLocation missingSprite = MissingTextureAtlasSprite.getLocation();
            for (ResourceLocation itemId : BuiltInRegistries.ITEM.keySet()) {
                if (!CsgoBox.MODID.equals(itemId.getNamespace())) {
                    continue;
                }
                ModelResourceLocation loc = new ModelResourceLocation(itemId, "inventory");
                BakedModel current = models.get(loc);
                // Swap missing-model dynamic boxes to the csgo_box model
                // (terminals to the terminal model); items with a real model
                // are left untouched.
                if (current == null
                        || current.getParticleIcon().contents().name().equals(missingSprite)) {
                    Item item = BuiltInRegistries.ITEM.get(itemId);
                    BakedModel replacement = item instanceof ItemTerminal && terminalModel != null
                            ? terminalModel : baseModel;
                    models.put(loc, replacement);
                }
            }
        }

        /**
         * Map the recycler {@code MenuType} to its screen. Fired by
         * {@code MenuScreens.init()} on the mod bus at client start.
         */
        @SubscribeEvent
        public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
            event.register(com.reclizer.csgobox.v1_21_1.menu.ModMenus.ARMORY_RECYCLER.get(),
                    com.reclizer.csgobox.v1_21_1.gui.ArmoryRecyclerScreen::new);
        }
    }
}
