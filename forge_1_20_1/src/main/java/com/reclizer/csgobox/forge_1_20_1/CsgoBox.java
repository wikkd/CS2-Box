package com.reclizer.csgobox.forge_1_20_1;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.BoxFileWatcher;
import com.reclizer.csgobox.forge_1_20_1.advancement.ModLoadedTrigger;
import com.reclizer.csgobox.forge_1_20_1.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.forge_1_20_1.box.BoxJsonLoader;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.config.CsboxClothConfigScreen;
import com.reclizer.csgobox.forge_1_20_1.config.CsboxConfig;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ItemTerminal;
import com.reclizer.csgobox.forge_1_20_1.item.ModItems;
import com.reclizer.csgobox.forge_1_20_1.menu.ModMenus;
import com.reclizer.csgobox.forge_1_20_1.sounds.ModSounds;
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
import net.minecraftforge.fml.loading.FMLEnvironment;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Mod(CsgoBox.MODID)
public class CsgoBox {

    public static final String MODID = "csgobox";
    public static String MODVERSION = "unknown";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Whether a mod with the given id is loaded. Used by the box JSON loader
     *  to tell "item id typo" from "target mod not installed" (v2.1.0). */
    public static boolean isModLoaded(String modId) {
        if (modId == null || modId.isBlank()) {
            return false;
        }
        return net.minecraftforge.fml.ModList.get().isLoaded(modId);
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
    public static final ForgeConfigSpec CONFIG_SPEC;
    public static Stat<ResourceLocation> OPENED_BOXES_STAT;
    public static final ResourceLocation TERMINAL_BUYS_STAT_ID =
            new ResourceLocation(CsgoBox.MODID, "terminal_buys");
    public static Stat<ResourceLocation> TERMINAL_BUYS_STAT;

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

        // Cloth Config GUI (optional client mod). Registered only on the client
        // with Cloth present; the reference is intentionally inside the
        // Dist.CLIENT branch so dedicated servers never resolve the
        // client-only screen classes.
        if (FMLEnvironment.dist == Dist.CLIENT
                && net.minecraftforge.fml.ModList.get().isLoaded("cloth_config")) {
            ModLoadingContext.get().registerExtensionPoint(
                    net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                            (minecraft, screen) -> CsboxClothConfigScreen.create(screen)));
        }

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::resolveOpenedBoxesStat);
        modEventBus.addListener(this::resolveTerminalBuysStat);
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

        // Register advancement triggers — CriteriaTriggers.register() may be
        // inaccessible in some Forge 1.20.1 builds (NoSuchMethodError at runtime
        // due to SRG / MojMap mismatch).  Wrap in try-catch so the mod still
        // loads; the triggers themselves work for programmatic use (trigger()).
        try {
            net.minecraft.advancements.CriteriaTriggers.register(OpenedBoxTrigger.INSTANCE);
            net.minecraft.advancements.CriteriaTriggers.register(ModLoadedTrigger.INSTANCE);
        } catch (NoSuchMethodError | Exception e) {
            LOGGER.warn("Could not register advancement triggers via CriteriaTriggers.register(): {}", e.getMessage());
        }

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
            // Load box definitions early (registry phase is done, all items —
            // including TACZ guns — are registered). This populates BoxRegistry
            // before the creative tab is first built, so dynamic boxes always
            // appear there even before a server/world has started. onServerStarting
            // re-runs loadAll() per world, which is idempotent (re-register).
            BoxJsonLoader.loadAll();
            Networking.registerMessages();
            if (CONFIG.enableHotReload()) {
                startBoxWatcher();
            }
            // Optional: register the The One Probe providers through TOP's IMC
            // entry point. The call itself must be guarded: merely loading
            // CsgoBoxTopPlugin for the call pulls in the TOP provider classes
            // during class verification, so without this guard an uninstalled
            // TOP throws NoClassDefFoundError (see 2.1.0 crash reports).
            if (isModLoaded("theoneprobe")) {
                com.reclizer.csgobox.forge_1_20_1.top.CsgoBoxTopPlugin.registerIfLoaded();
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
        com.reclizer.csgobox.forge_1_20_1.packet.PacketSyncBoxDefinitions packet =
                com.reclizer.csgobox.forge_1_20_1.packet.PacketSyncBoxDefinitions.ofAll();
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            com.reclizer.csgobox.forge_1_20_1.packet.Networking.sendToPlayer(packet, player);
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
    // lives in the csgobox:box_id NBT tag plus the server-synced BoxRegistry.
    // Consequences (all intended):
    //   * client and server registries are identical whenever the mod version
    //     matches — this failure class cannot come back;
    //   * config/csbox/ may be edited, added to or hot-reloaded freely without
    //     restarting and without syncing folders between players;
    //   * custom boxes are handed out with /csbox give (generic csgo_box /
    //     terminal item + NBT) instead of a dedicated registry id.

    public static boolean debug() {
        return false;
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
            // Client-side tutorial download: a dedicated-server player's client
            // never fires ServerStartingEvent, so without this they get no local
            // copy. Same-JVM duplicates (integrated server) are safe — the
            // single-threaded executor + synchronized idempotent download.
            BoxJsonLoader.downloadTutorialsAsync();
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
