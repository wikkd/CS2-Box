package com.reclizer.csgobox.forge_26_2;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.BoxFileWatcher;
import com.reclizer.csgobox.forge_26_2.box.BoxJsonLoader;
import com.reclizer.csgobox.forge_26_2.box.BoxRegistry;
import com.reclizer.csgobox.forge_26_2.config.CsboxConfig;
import com.reclizer.csgobox.forge_26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_26_2.item.ItemTerminal;
import com.reclizer.csgobox.forge_26_2.item.ModItems;
import com.reclizer.csgobox.forge_26_2.menu.ModMenus;
import com.reclizer.csgobox.forge_26_2.packet.Networking;
import com.reclizer.csgobox.forge_26_2.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.forge_26_2.advancement.ModLoadedTrigger;
import com.reclizer.csgobox.forge_26_2.packet.PacketCsgoBulkProgress;
import com.reclizer.csgobox.forge_26_2.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Mod(CsgoBox.MODID)
public class CsgoBox {

    public static final String MODID = "csgobox";
    /** Mod version from {@code ModContainer}; consumed by the tutorial download
     *  which fires after this constructor. "unknown" only if loaded pre-init. */
    public static String MODVERSION = "unknown";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CsboxConfig CONFIG;
    public static final ForgeConfigSpec CONFIG_SPEC;
    public static Stat<Identifier> OPENED_BOXES_STAT;

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
        var pair = new ForgeConfigSpec.Builder()
                .configure(CsboxConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    public CsgoBox(FMLJavaModLoadingContext context) {
        BusGroup modEventBus = context.getModBusGroup();
        try {
            MODVERSION = ModLoadingContext.get().getContainer()
                    .getModInfo().getVersion().toString();
        } catch (Exception e) {
            LOGGER.warn("Could not read mod version from container: {}", e.getMessage());
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC, "csgobox.toml");

        FMLCommonSetupEvent.getBus(modEventBus).addListener(this::commonSetup);
        Networking.registerMessages();
        FMLCommonSetupEvent.getBus(modEventBus).addListener(this::resolveOpenedBoxesStat);
        RegisterEvent.getBus(modEventBus).addListener(this::registerDynamicBoxItems);
        ModConfigEvent.Reloading.getBus(modEventBus).addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == CONFIG_SPEC) {
                LOGGER.info("CS2 Box config reloaded");
            }
        });
        RegisterEvent.getBus(modEventBus).addListener((RegisterEvent event) -> {
            ResourceKey<?> registryKey = event.getRegistryKey();
            if (registryKey.equals(Registries.CUSTOM_STAT)) {
                event.register(Registries.CUSTOM_STAT, OpenedBoxTrigger.STAT_ID, () -> OpenedBoxTrigger.STAT_ID);
            } else if (registryKey.equals(Registries.TRIGGER_TYPE)) {
                event.register(Registries.TRIGGER_TYPE, OpenedBoxTrigger.ID, () -> OpenedBoxTrigger.INSTANCE);
                event.register(Registries.TRIGGER_TYPE, ModLoadedTrigger.ID, () -> ModLoadedTrigger.INSTANCE);
            }
        });

        ModSounds.SOUNDS.register(modEventBus);
        ItemCsgoBox.registerDataComponents(modEventBus);
        ModItems.register(modEventBus);
        ModItems.registerTab(modEventBus);
        com.reclizer.csgobox.forge_26_2.block.ModBlocks.register(modEventBus);
        ModMenus.register(modEventBus);
        com.reclizer.csgobox.forge_26_2.villager.ModVillagers.register(modEventBus);

        // Forge's EVENT_BUS validates @SubscribeEvent on every method of a
        // registered object (strict mode); the un-annotated lifecycle handlers
        // would be rejected. Register on the per-event static buses instead.
        ServerStartingEvent.BUS.addListener(this::onServerStarting);
        ServerStoppingEvent.BUS.addListener(this::onServerStopping);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        if (CONFIG.enableHotReload()) {
            event.enqueueWork(this::startBoxWatcher);
        }
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
        // Default terminal.json must exist before the scan (BoxJsonLoader
        // writes it only at server start, after the registry freezes).
        // Pre-v1.0.8 files (no "type") are upgraded here first.
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
                Identifier itemId;
                try {
                    itemId = Identifier.fromNamespaceAndPath(MODID, idStr);
                } catch (Exception e) {
                    LOGGER.warn("[csgo-dynamic-items] skip invalid filename '{}': {}", filename, e.getMessage());
                    continue;
                }
                if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                    skipped++;
                    continue;
                }
                // Static items (ModItems) must never be re-registered by the
                // dynamic path. On Forge 26.2 a duplicate registration is an
                // owner-less registry override and GameData.sync() aborts the
                // freeze ("One of more entry values did not copy to the
                // correct id"). DeferredRegister entries can land after this
                // listener, so containsKey alone is order-dependent — check
                // the declared static set explicitly (order-independent).
                if (ModItems.ITEMS.getEntries().stream()
                        .anyMatch(entry -> entry.getId().equals(itemId))) {
                    skipped++;
                    continue;
                }
                final Identifier boxId = itemId;
                final ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, itemId);
                // "type" is the single source of truth (v1.0.8): terminal
                // registers an ItemTerminal (client dispatch is by instanceof,
                // remote clients never see the type field); everything else a
                // plain ItemCsgoBox.
                //
                // Reuse the base csgo_box model so dynamic boxes render a real
                // icon (ITEM_MODEL resolves against items/<id>.json in 26.x);
                // terminal-type boxes keep the terminal model instead.
                final boolean isTerminal = "terminal".equals(BoxJsonLoader.readType(file));
                event.register(Registries.ITEM, itemId, () -> {
                    ItemCsgoBox item;
                    if (isTerminal) {
                        // No stacksTo() here: it writes a MAX_STACK_SIZE
                        // initializer that runs after ItemTerminal's, which
                        // would override its stacksTo(1) — terminals must stay
                        // unstackable (one uid/lock per terminal).
                        item = new ItemTerminal(new Item.Properties().setId(itemKey)) {
                            @Override
                            public ItemStack getDefaultInstance() {
                                ItemStack stack = super.getDefaultInstance();
                                ItemCsgoBox.setBoxId(boxId, stack);
                                stack.set(DataComponents.ITEM_MODEL,
                                        Identifier.parse(MODID + ":" + (isTerminal ? "terminal" : "csgo_box")));
                                return stack;
                            }
                        };
                    } else {
                        item = new ItemCsgoBox(new Item.Properties().stacksTo(16).setId(itemKey)) {
                            @Override
                            public ItemStack getDefaultInstance() {
                                ItemStack stack = super.getDefaultInstance();
                                ItemCsgoBox.setBoxId(boxId, stack);
                                stack.set(DataComponents.ITEM_MODEL,
                                        Identifier.parse(MODID + ":" + (isTerminal ? "terminal" : "csgo_box")));
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
        com.reclizer.csgobox.forge_26_2.terminal.TerminalSessionManager.bindServer(event.getServer());
        LOGGER.info("CS2 Box server started with {} box definitions", BoxRegistry.size());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        com.reclizer.csgobox.forge_26_2.terminal.TerminalSessionManager.saveNow();
        com.reclizer.csgobox.forge_26_2.terminal.TerminalSessionManager.unbindServer();
        if (boxWatcher != null) {
            boxWatcher.stop();
            boxWatcher = null;
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.BOTH)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("CS2 Box client setup complete");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
            event.enqueueWork(() ->
                    net.minecraft.client.gui.screens.MenuScreens.register(
                            ModMenus.ARMORY_RECYCLER.get(),
                            com.reclizer.csgobox.forge_26_2.gui.ArmoryRecyclerScreen::new));
        }

        /**
         * Register {@link com.reclizer.csgobox.forge_26_2.gui.pip.Icon3DRenderer}
         * for {@link com.reclizer.csgobox.forge_26_2.gui.pip.Icon3DRenderState};
         * without it the PIP renderer map has no entry and 3D rotation draws
         * nothing.
         */
        @SubscribeEvent
        public static void onRegisterPictureInPictureRenderers(RegisterPictureInPictureRendererEvent event) {
            // 26.2 dropped the BufferSource parameter (the parent class is
            // annotation-only now); register the renderer instance directly.
            event.register(
                    new com.reclizer.csgobox.forge_26_2.gui.pip.Icon3DRenderer());
        }
    }
}
