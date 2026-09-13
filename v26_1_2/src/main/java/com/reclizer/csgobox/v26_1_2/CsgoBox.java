package com.reclizer.csgobox.v26_1_2;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.BoxFileWatcher;
import com.reclizer.csgobox.v26_1_2.box.BoxJsonLoader;
import com.reclizer.csgobox.v26_1_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_1_2.config.CsboxClothConfigScreen;
import com.reclizer.csgobox.v26_1_2.config.CsboxConfig;
import com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_1_2.item.ItemTerminal;
import com.reclizer.csgobox.v26_1_2.item.ModItems;
import com.reclizer.csgobox.v26_1_2.menu.ModMenus;
import com.reclizer.csgobox.v26_1_2.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.v26_1_2.advancement.ModLoadedTrigger;
import com.reclizer.csgobox.v26_1_2.advancement.TerminalBrokeTrigger;
import com.reclizer.csgobox.v26_1_2.advancement.TerminalDealTrigger;
import com.reclizer.csgobox.v26_1_2.packet.PacketBoxBulkResult;
import com.reclizer.csgobox.v26_1_2.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.v26_1_2.packet.PacketCsgoBulkProgress;
import com.reclizer.csgobox.v26_1_2.packet.PacketCsgoProgress;
import com.reclizer.csgobox.v26_1_2.packet.PacketRequestBoxItems;
import com.reclizer.csgobox.v26_1_2.packet.PacketSyncBoxItems;
import com.reclizer.csgobox.v26_1_2.packet.PacketTerminalBuy;
import com.reclizer.csgobox.v26_1_2.packet.PacketTerminalBuyResult;
import com.reclizer.csgobox.v26_1_2.packet.PacketTerminalClose;
import com.reclizer.csgobox.v26_1_2.packet.PacketTerminalOpen;
import com.reclizer.csgobox.v26_1_2.packet.PacketTerminalReject;
import com.reclizer.csgobox.v26_1_2.packet.PacketTerminalState;
import com.reclizer.csgobox.v26_1_2.packet.PacketSyncBoxDefinitions;
import com.reclizer.csgobox.v26_1_2.sounds.ModSounds;
import com.reclizer.csgobox.v26_1_2.block.ModBlocks;
import com.reclizer.csgobox.v26_1_2.villager.ModVillagers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Mod(CsgoBox.MODID)
public class CsgoBox {

    public static final String MODID = "csgobox";
    /** Mod version from {@code ModContainer}; consumed by the tutorial download
     *  which fires after this constructor. "unknown" only if loaded pre-init. */
    public static String MODVERSION = "unknown";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Whether a mod with the given id is loaded. Used by the box JSON loader
     *  to tell "item id typo" from "target mod not installed" (v2.1.0). */
    public static boolean isModLoaded(String modId) {
        if (modId == null || modId.isBlank()) {
            return false;
        }
        return net.neoforged.fml.ModList.get().isLoaded(modId);
    }

    /**
     * v2.1.0 permission gate for the {@code permission} box config field.
     * Defaults to allow-all; a modpack wires this to its permission backend
     * (e.g. {@code (player) -> LuckPerms.api().getUserManager().getUser(...)})
     * once at startup. The gate receives the player and the permission node
     * from the box JSON.
     */
    public static java.util.function.BiPredicate<net.minecraft.server.level.ServerPlayer, String> PERMISSION_GATE =
            (player, node) -> true;

    public static final CsboxConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;
    public static Stat<Identifier> OPENED_BOXES_STAT;
    public static final Identifier TERMINAL_BUYS_STAT_ID =
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "terminal_buys");
    public static Stat<Identifier> TERMINAL_BUYS_STAT;

    /** Background pool for {@code PacketCsgoBulkProgress} rolls (2 daemon
     *  threads; further requests queue). Shut down on mod unload. */
    public static final ExecutorService BULK_COMPUTE_POOL = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            // Bounded work queue (v2.2.0-fix): an unbounded LinkedBlockingQueue
            // let flood requests pile up without limit. Dropping with a log is
            // safe — the per-player 10-tick open guard already rate-limits,
            // and a dropped batch simply stays unopened.
            new ArrayBlockingQueue<>(64),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "csgobox-bulk-compute-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            (r, e) -> LOGGER.warn("[csgo-bulk] bulk compute queue full (64); batch request dropped"));

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

        // Cloth Config GUI (optional client mod). Registered only on the client
        // with Cloth present; the reference is intentionally inside the
        // Dist.CLIENT branch so dedicated servers never resolve the
        // client-only screen classes.
        if (FMLEnvironment.getDist() == Dist.CLIENT
                && net.neoforged.fml.ModList.get().isLoaded("cloth_config")) {
            ModLoadingContext.get().getActiveContainer().registerExtensionPoint(
                    net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    (modContainer, screen) -> CsboxClothConfigScreen.create(screen));
        }

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::resolveOpenedBoxesStat);
        modEventBus.addListener(this::resolveTerminalBuysStat);
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
                event.register(Registries.TRIGGER_TYPE, TerminalDealTrigger.ID, () -> TerminalDealTrigger.INSTANCE);
                event.register(Registries.TRIGGER_TYPE, TerminalBrokeTrigger.ID, () -> TerminalBrokeTrigger.INSTANCE);
            }
        });

        ModSounds.SOUNDS.register(modEventBus);
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
        if (CONFIG.enableHotReload()) {
            event.enqueueWork(this::startBoxWatcher);
        }
        // Optional: register the The One Probe providers through TOP's IMC
        // entry point. The call itself must be guarded: merely loading
        // CsgoBoxTopPlugin for the call pulls in the TOP provider classes
        // during class verification, so without this guard an uninstalled
        // TOP throws NoClassDefFoundError (see 2.1.0 crash reports).
        if (isModLoaded("theoneprobe")) {
            com.reclizer.csgobox.v26_1_2.top.CsgoBoxTopPlugin.registerIfLoaded();
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
        return false;
    }

    // ===== Box items are a compile-time constant (v2.1.0 registry hotfix) =====
    // Up to 2.1.0 every config/csbox/<name>.json was turned into its own item
    // (csgobox:<name>) during RegisterEvent. The item registry is synced over
    // the network and frozen before login, so as soon as the client and server
    // config folders differed (or a `requires` mod was installed on one side
    // only) the registries diverged and joins died with NeoForge's misleading
    // "Failed to synchronize registry data from server / mod versions do not
    // match" screen.
    //
    // Item registration therefore no longer reads the config folder: the mod
    // ships a fixed item set (see ModItems#fixedBoxItem) and a box's identity
    // lives in the csgobox:box_id data component plus the server-synced
    // BoxRegistry. Consequences (all intended):
    //   * client and server registries are identical whenever the mod version
    //     matches — this failure class cannot come back;
    //   * config/csbox/ may be edited, added to or hot-reloaded freely without
    //     restarting and without syncing folders between players;
    //   * custom boxes are handed out with /csbox give (generic csgo_box /
    //     terminal item + box_id component) instead of a dedicated registry id.

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (CONFIG.loadDefaultBoxes()) {
            BoxJsonLoader.loadAll();
        }
        com.reclizer.csgobox.v26_1_2.terminal.TerminalSessionManager.bindServer(event.getServer());
        LOGGER.info("CS2 Box server started with {} box definitions", BoxRegistry.size());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        com.reclizer.csgobox.v26_1_2.terminal.TerminalSessionManager.saveNow();
        com.reclizer.csgobox.v26_1_2.terminal.TerminalSessionManager.unbindServer();
        if (boxWatcher != null) {
            boxWatcher.stop();
            boxWatcher = null;
        }
    }

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("CS2 Box client setup complete");
            // Client-side tutorial download: a dedicated-server player's client
            // never fires ServerStartingEvent, so without this they get no local
            // copy. Same-JVM duplicates (integrated server) are safe — the
            // single-threaded executor + synchronized idempotent download.
            BoxJsonLoader.downloadTutorialsAsync();
                    }

        /**
         * Register {@link com.reclizer.csgobox.v26_1_2.gui.pip.Icon3DRenderer}
         * for {@link com.reclizer.csgobox.v26_1_2.gui.pip.Icon3DRenderState};
         * without it the PIP renderer map has no entry and 3D rotation draws
         * nothing.
         */
        @SubscribeEvent
        public static void onRegisterPictureInPictureRenderers(RegisterPictureInPictureRenderersEvent event) {
            // 26.1.2's PIP renderer constructor takes a BufferSource; 26.2
            // dropped it for a Supplier-based signature.
            event.register(
                    com.reclizer.csgobox.v26_1_2.gui.pip.Icon3DRenderState.class,
                    bufferSource -> new com.reclizer.csgobox.v26_1_2.gui.pip.Icon3DRenderer(bufferSource));
        }

        /**
         * Map the recycler {@code MenuType} to its screen. Fired by
         * {@code MenuScreens.init()} on the mod bus at client start.
         */
        @SubscribeEvent
        public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
            event.register(com.reclizer.csgobox.v26_1_2.menu.ModMenus.ARMORY_RECYCLER.get(),
                    com.reclizer.csgobox.v26_1_2.gui.ArmoryRecyclerScreen::new);
        }
    }
}
