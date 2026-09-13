package com.reclizer.csgobox.forge_26_1_2;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.box.BoxFileWatcher;
import com.reclizer.csgobox.forge_26_1_2.box.BoxJsonLoader;
import com.reclizer.csgobox.forge_26_1_2.box.BoxRegistry;
import com.reclizer.csgobox.forge_26_1_2.config.CsboxClothConfigScreen;
import com.reclizer.csgobox.forge_26_1_2.config.CsboxConfig;
import com.reclizer.csgobox.forge_26_1_2.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_26_1_2.item.ModItems;
import com.reclizer.csgobox.forge_26_1_2.menu.ModMenus;
import com.reclizer.csgobox.forge_26_1_2.packet.Networking;
import com.reclizer.csgobox.forge_26_1_2.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.forge_26_1_2.advancement.ModLoadedTrigger;
import com.reclizer.csgobox.forge_26_1_2.advancement.TerminalBrokeTrigger;
import com.reclizer.csgobox.forge_26_1_2.advancement.TerminalDealTrigger;
import com.reclizer.csgobox.forge_26_1_2.packet.PacketCsgoBulkProgress;
import com.reclizer.csgobox.forge_26_1_2.sounds.ModSounds;
import net.minecraft.core.registries.Registries;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;

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
        return net.minecraftforge.fml.ModList.isLoaded(modId);
    }

    /**
     * v2.1.0 permission gate for the {@code permission} box config field.
     * Defaults to allow-all; a modpack wires this to its permission backend
     * (e.g. (player) -> LuckPerms...() ) once at startup. The gate receives the
     * player and the permission node from the box JSON.
     */
    public static java.util.function.BiPredicate<net.minecraft.server.level.ServerPlayer, String> PERMISSION_GATE =
            (player, node) -> true;

    public static final CsboxConfig CONFIG;
    public static final ForgeConfigSpec CONFIG_SPEC;
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

        // Cloth Config GUI (optional client mod). Registered only on the client
        // with Cloth present; the reference is intentionally inside the
        // Dist.CLIENT branch so dedicated servers never resolve the
        // client-only screen classes.
        if (FMLEnvironment.dist == Dist.CLIENT
                && net.minecraftforge.fml.ModList.isLoaded("cloth_config")) {
            ModLoadingContext.get().registerExtensionPoint(
                    net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                            (minecraft, screen) -> CsboxClothConfigScreen.create(screen)));
        }

        FMLCommonSetupEvent.getBus(modEventBus).addListener(this::commonSetup);
        Networking.registerMessages();
        FMLCommonSetupEvent.getBus(modEventBus).addListener(this::resolveOpenedBoxesStat);
        ModConfigEvent.Reloading.getBus(modEventBus).addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == CONFIG_SPEC) {
                LOGGER.info("CS2 Box config reloaded");
            }
        });
        RegisterEvent.getBus(modEventBus).addListener((RegisterEvent event) -> {
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
        com.reclizer.csgobox.forge_26_1_2.block.ModBlocks.register(modEventBus);
        ModMenus.register(modEventBus);
        com.reclizer.csgobox.forge_26_1_2.villager.ModVillagers.register(modEventBus);

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
                () -> {
                    BoxJsonLoader.reloadPreserving();
                    net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
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
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        com.reclizer.csgobox.forge_26_1_2.packet.PacketSyncBoxDefinitions packet =
                com.reclizer.csgobox.forge_26_1_2.packet.PacketSyncBoxDefinitions.ofAll();
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            com.reclizer.csgobox.forge_26_1_2.packet.Networking.sendToPlayer(packet, player);
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
    // only) the registries diverged and joins died with Forge's misleading
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
    //     terminal item + component) instead of a dedicated registry id.

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (CONFIG.loadDefaultBoxes()) {
            BoxJsonLoader.loadAll();
        }
        com.reclizer.csgobox.forge_26_1_2.terminal.TerminalSessionManager.bindServer(event.getServer());
        LOGGER.info("CS2 Box server started with {} box definitions", BoxRegistry.size());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        com.reclizer.csgobox.forge_26_1_2.terminal.TerminalSessionManager.saveNow();
        com.reclizer.csgobox.forge_26_1_2.terminal.TerminalSessionManager.unbindServer();
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
            // Client-side tutorial download: a dedicated-server player's client
            // never fires ServerStartingEvent, so without this they get no local
            // copy. Same-JVM duplicates (integrated server) are safe — the
            // single-threaded executor + synchronized idempotent download.
            BoxJsonLoader.downloadTutorialsAsync();
                        event.enqueueWork(() ->
                    net.minecraft.client.gui.screens.MenuScreens.register(
                            ModMenus.ARMORY_RECYCLER.get(),
                            com.reclizer.csgobox.forge_26_1_2.gui.ArmoryRecyclerScreen::new));
        }

        /**
         * Register {@link com.reclizer.csgobox.forge_26_1_2.gui.pip.Icon3DRenderer}
         * for {@link com.reclizer.csgobox.forge_26_1_2.gui.pip.Icon3DRenderState};
         * without it the PIP renderer map has no entry and 3D rotation draws
         * nothing.
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
