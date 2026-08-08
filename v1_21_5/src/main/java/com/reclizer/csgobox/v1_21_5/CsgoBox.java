package com.reclizer.csgobox.v1_21_5;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.box.BoxFileWatcher;
import com.reclizer.csgobox.v1_21_5.box.BoxJsonLoader;
import com.reclizer.csgobox.v1_21_5.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_5.capability.ModCapability;
import com.reclizer.csgobox.v1_21_5.config.CsboxConfig;
import com.reclizer.csgobox.v1_21_5.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_5.item.ModItems;
import com.reclizer.csgobox.v1_21_5.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.v1_21_5.advancement.ModLoadedTrigger;
import com.reclizer.csgobox.v1_21_5.packet.PacketBoxBulkResult;
import com.reclizer.csgobox.v1_21_5.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.v1_21_5.packet.PacketCsgoBulkProgress;
import com.reclizer.csgobox.v1_21_5.packet.PacketCsgoProgress;
import com.reclizer.csgobox.v1_21_5.packet.PacketRequestBoxItems;
import com.reclizer.csgobox.v1_21_5.packet.PacketSyncBoxItems;
import com.reclizer.csgobox.v1_21_5.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
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
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.client.event.ConfigureMainRenderTargetEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
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
    public static final ModConfigSpec CONFIG_SPEC;
    public static Stat<ResourceLocation> OPENED_BOXES_STAT;

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
        modEventBus.addListener(this::registerDynamicBoxItems);
        modEventBus.addListener((ConfigureMainRenderTargetEvent event) -> event.enableStencil());
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
        registrar.playToServer(PacketCsgoBulkProgress.TYPE, PacketCsgoBulkProgress.STREAM_CODEC, PacketCsgoBulkProgress::handleServer);
        registrar.playToClient(PacketBoxOpenResult.TYPE, PacketBoxOpenResult.STREAM_CODEC, PacketBoxOpenResult::handle);
        registrar.playToClient(PacketBoxBulkResult.TYPE, PacketBoxBulkResult.STREAM_CODEC, PacketBoxBulkResult::handle);
        registrar.playToServer(PacketRequestBoxItems.TYPE, PacketRequestBoxItems.STREAM_CODEC, PacketRequestBoxItems::handle);
        registrar.playToClient(PacketSyncBoxItems.TYPE, PacketSyncBoxItems.STREAM_CODEC, PacketSyncBoxItems::handle);
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
     * Scan {@code config/csbox/*.json} and register one dynamic
     * {@link ItemCsgoBox} per file so vanilla {@code /give} can address any
     * box by its file name. The dynamic item's {@code box_id} is pre-set to
     * the same id as the file name, so {@code /give @p csgobox:weapon_supply_box 5}
     * hands the player 5 ready-to-open boxes.
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
     * which fires AFTER the item registry has frozen and therefore crashed
     * with {@code IllegalStateException: Registry is already frozen}.</p>
     */
    @SuppressWarnings("unchecked")
    private static <T> void copyComponent(DataComponentMap.Builder builder, TypedDataComponent<?> component) {
        builder.set((DataComponentType<T>) component.type(), (T) component.value());
    }

    private void registerDynamicBoxItems(final RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }
        Path configDir = FMLPaths.CONFIGDIR.get().resolve("csbox");
        if (!Files.isDirectory(configDir)) {
            return;
        }
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
                // Reuse the base csgo_box item model so dynamic boxes
                // (registered from config/csbox/*.json filenames) render
                // with a real icon instead of the missing-texture
                // checkerboard. ITEM_MODEL is resolved against
                // assets/csgobox/items/<id>.json in 1.21.5+.
                //
                // Item.Properties.buildAndValidateComponents() forcibly sets
                // ITEM_MODEL = own registry id at Item construction, so
                // neither Properties.component() nor a getDefaultInstance()
                // override can change it. ItemStack is built from
                // Item.components(), which is polymorphic — override it to
                // swap ITEM_MODEL for the shared csgo_box model.
                event.register(Registries.ITEM, itemId, () -> new ItemCsgoBox(new Item.Properties()) {
                    private DataComponentMap boxModelComponents;

                    @Override
                    public DataComponentMap components() {
                        if (this.boxModelComponents == null) {
                            DataComponentMap base = super.components();
                            DataComponentMap.Builder builder = DataComponentMap.builder();
                            for (TypedDataComponent<?> component : base) {
                                copyComponent(builder, component);
                            }
                            builder.set(DataComponents.ITEM_MODEL, ResourceLocation.parse(MODID + ":csgo_box"));
                            this.boxModelComponents = builder.build();
                        }
                        return this.boxModelComponents;
                    }

                    @Override
                    public ItemStack getDefaultInstance() {
                        ItemStack stack = super.getDefaultInstance();
                        ItemCsgoBox.setBoxId(boxId, stack);
                        return stack;
                    }
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

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("CS2 Box client setup complete");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
