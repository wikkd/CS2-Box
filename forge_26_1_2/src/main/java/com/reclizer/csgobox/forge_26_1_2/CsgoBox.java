package com.reclizer.csgobox.forge_26_1_2;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.BoxFileWatcher;
import com.reclizer.csgobox.forge_26_1_2.box.BoxJsonLoader;
import com.reclizer.csgobox.forge_26_1_2.box.BoxRegistry;
import com.reclizer.csgobox.forge_26_1_2.capability.ModCapability;
import com.reclizer.csgobox.forge_26_1_2.config.CsboxConfig;
import com.reclizer.csgobox.forge_26_1_2.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_26_1_2.item.ItemTerminal;
import com.reclizer.csgobox.forge_26_1_2.item.ModItems;
import com.reclizer.csgobox.forge_26_1_2.packet.Networking;
import com.reclizer.csgobox.forge_26_1_2.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.forge_26_1_2.advancement.ModLoadedTrigger;
import com.reclizer.csgobox.forge_26_1_2.packet.PacketBoxBulkResult;
import com.reclizer.csgobox.forge_26_1_2.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.forge_26_1_2.packet.PacketCsgoBulkProgress;
import com.reclizer.csgobox.forge_26_1_2.packet.PacketCsgoProgress;
import com.reclizer.csgobox.forge_26_1_2.packet.PacketRequestBoxItems;
import com.reclizer.csgobox.forge_26_1_2.packet.PacketSyncBoxItems;
import com.reclizer.csgobox.forge_26_1_2.sounds.ModSounds;
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
    /**
     * Mod version, set during construction from {@code ModContainer}.
     * Consumed by {@code BoxDefaults.writeTutorialIfMissing} — the load
     * runs in {@code FMLCommonSetupEvent.enqueueWork} (1.21.1) or
     * {@code ServerStartingEvent} (26.x), both of which fire AFTER this
     * constructor completes. Default {@code "unknown"} is therefore only
     * observable if the class is loaded before mod init, which is not a
     * normal code path.
     */
    public static String MODVERSION = "unknown";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CsboxConfig CONFIG;
    public static final ForgeConfigSpec CONFIG_SPEC;
    public static Stat<Identifier> OPENED_BOXES_STAT;

    /**
     * Background thread pool used by {@code PacketCsgoBulkProgress} to compute
     * K random results off the main thread. Two daemon threads are enough for
     * concurrent bulk requests from two operators; further requests queue.
     * Shut down on mod unload via {@link FMLCommonSetupEvent} shutdown hook.
     */
    public static final ExecutorService BULK_COMPUTE_POOL = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "csgobox-bulk-compute-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    /**
     * Watches {@code config/csbox/} for JSON changes and triggers a debounced
     * reload. Created during {@link #commonSetup} when {@code enableHotReload}
     * is true, shut down during {@link #onServerStopping}.
     */
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

        // Forge 26.1's MinecraftForge.EVENT_BUS is an EventBusMigrationHelper that
        // validates @SubscribeEvent on every method of a registered object (strict
        // mode), so registering `this` would reject the un-annotated lifecycle
        // handlers (commonSetup etc.). Register the game-event handlers on the
        // per-event static buses instead.
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
     * so vanilla {@code /give} can address any box by its file name. Boxes
     * whose JSON declares {@code "type": "terminal"} are registered as
     * {@link ItemTerminal} (they open the terminal UI); everything else is a
     * plain {@link ItemCsgoBox}. The dynamic item's {@code box_id} is pre-set
     * to the same id as the file name, so
     * {@code /give @p csgobox:weapon_supply_box 5} hands the player 5
     * ready-to-open boxes.
     *
     * <p>Trade-off: items without a matching model file
     * ({@code assets/csgobox/models/item/<name>.json}) render as missing-texture.
     * Functional behavior (open, RNG, give) is unaffected. Drop a model file
     * to fix the icon.</p>
     *
     * <p>Registered via {@link RegisterEvent} using deferred suppliers so the
     * {@link Item} instances are constructed during registry finalization,
     * <em>before</em> the registry freezes. The previous implementation
     * scheduled this work through {@code FMLCommonSetupEvent.enqueueWork},
     * which in MC 26.1.2 fires AFTER the item registry has frozen and therefore
     * crashed with {@code IllegalStateException: Registry is already frozen}.</p>
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
        // The terminal is a dynamic item like every other box, so the default
        // terminal.json must exist before the scan or csgobox:terminal would
        // not be registered on a fresh install. BoxJsonLoader.loadAll() also
        // writes it, but that runs at server start, AFTER the item registry
        // has frozen.
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
                final Identifier boxId = itemId;
                final ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, itemId);
                // Boxes with "type": "terminal" in their JSON are registered as
                // ItemTerminal so the client can dispatch to the terminal UI
                // (ClickEvent / PacketTerminalBuy match instanceof
                // ItemTerminal) without needing the box definition — remote
                // clients never receive the type field. Everything else stays
                // a plain ItemCsgoBox.
                //
                // Reuse the base csgo_box item model so dynamic boxes
                // (registered from config/csbox/*.json filenames) render
                // with a real icon instead of the missing-texture
                // checkerboard. ITEM_MODEL is resolved against
                // assets/csgobox/items/<id>.json in 26.x; terminal-type boxes
                // keep the terminal model instead.
                final boolean isTerminal = "terminal".equals(BoxJsonLoader.readType(file));
                event.register(Registries.ITEM, itemId, () -> {
                    ItemCsgoBox item;
                    if (isTerminal) {
                        item = new ItemTerminal(new Item.Properties().stacksTo(16).setId(itemKey)) {
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
        LOGGER.info("CS2 Box server started with {} box definitions", BoxRegistry.size());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
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
        }

        /**
         * Register the {@link com.reclizer.csgobox.forge_26_1_2.gui.pip.Icon3DRenderer}
         * for our custom {@link com.reclizer.csgobox.forge_26_1_2.gui.pip.Icon3DRenderState}.
         *
         * <p>Without this listener, every {@code submitPictureInPictureRenderState}
         * call from {@code GuiItemMove} hits a renderer map that has no entry
         * keyed by {@code Icon3DRenderState.class}, so the 3D rotation in
         * {@code CsboxScreen} / {@code CsLookItemScreen} silently draws nothing
         * — only the 2D fallback slot background is visible. The original
         * 1.0.6 release forgot this listener; this commit closes that gap.</p>
         */
        @SubscribeEvent
        public static void onRegisterPictureInPictureRenderers(RegisterPictureInPictureRendererEvent event) {
            // 26.1.2's PictureInPictureRenderer constructor takes a BufferSource;
            // the registration factory therefore must accept one. 26.2 dropped
            // the parameter and switched to a Supplier-based register signature.
            event.register(
                    new com.reclizer.csgobox.forge_26_1_2.gui.pip.Icon3DRenderer(event.getBufferSource()));
        }
    }
}
