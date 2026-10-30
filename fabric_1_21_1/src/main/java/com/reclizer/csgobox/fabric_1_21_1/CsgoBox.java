package com.reclizer.csgobox.fabric_1_21_1;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.box.BoxFileWatcher;
import com.reclizer.csgobox.fabric_1_21_1.advancement.ModLoadedTrigger;
import com.reclizer.csgobox.fabric_1_21_1.advancement.OpenedBoxTrigger;
import com.reclizer.csgobox.fabric_1_21_1.advancement.TerminalBrokeTrigger;
import com.reclizer.csgobox.fabric_1_21_1.advancement.TerminalDealTrigger;
import com.reclizer.csgobox.fabric_1_21_1.block.ModBlocks;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxJsonLoader;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.fabric_1_21_1.command.CsboxCommand;
import com.reclizer.csgobox.fabric_1_21_1.command.EditorCommand;
import com.reclizer.csgobox.fabric_1_21_1.config.CsboxConfig;
import com.reclizer.csgobox.fabric_1_21_1.event.ModEvents;
import com.reclizer.csgobox.fabric_1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.fabric_1_21_1.item.ModItems;
import com.reclizer.csgobox.fabric_1_21_1.menu.ModMenus;
import com.reclizer.csgobox.fabric_1_21_1.net.Networking;
import com.reclizer.csgobox.fabric_1_21_1.packet.PacketSyncBoxDefinitions;
import com.reclizer.csgobox.fabric_1_21_1.sounds.ModSounds;
import com.reclizer.csgobox.fabric_1_21_1.villager.ModVillagers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Registry;

/**
 * Fabric entry point. Registration happens directly in {@link #onInitialize()}
 * (Fabric has no mod event bus); the memoized suppliers in the Mod* classes
 * keep the same call-site shape as the other platforms.
 */
public class CsgoBox implements ModInitializer {

    public static final String MODID = "csgobox";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Whether a mod with the given id is loaded (v2.0.1: box JSON missing-mod
     *  detection). Fabric uses {@code FabricLoader.getInstance().isLoaded}. */
    public static boolean isModLoaded(String modId) {
        if (modId == null || modId.isBlank()) {
            return false;
        }
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /** v2.0.1 permission gate for the {@code permission} box config field.
     *  Defaults to allow-all; a modpack wires a permission backend at startup. */
    public static java.util.function.BiPredicate<ServerPlayer, String> PERMISSION_GATE =
            (player, node) -> true;

    public static final CsboxConfig CONFIG = new CsboxConfig();
    public static Stat<ResourceLocation> OPENED_BOXES_STAT;
    public static final ResourceLocation TERMINAL_BUYS_STAT_ID =
            ResourceLocation.fromNamespaceAndPath(MODID, "terminal_buys");
    public static Stat<ResourceLocation> TERMINAL_BUYS_STAT;

    /** Background pool for {@code PacketCsgoBulkProgress} rolls (2 daemon
     *  threads; further requests queue). Same shape as the other platforms. */
    public static final ExecutorService BULK_COMPUTE_POOL = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            // Bounded work queue (v2.2.0-fix): overflow rethrows after logging
            // so CompletableFuture.supplyAsync() surfaces
            // RejectedExecutionException at the submit site.
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
            (r, e) -> {
                LOGGER.warn("[csgo-bulk] bulk compute queue full (64); batch rejected");
                throw new RejectedExecutionException("csgo-bulk queue full");
            });

    /** Watches {@code config/csbox/} for JSON changes (debounced reload);
     *  created in {@link #onInitialize()}, shut down in {@link #onServerStopping()}. */
    private static BoxFileWatcher boxWatcher;

    /** Current server — the Fabric stand-in for NeoForge's
     *  {@code ServerLifecycleHooks.getCurrentServer()}. */
    private static volatile MinecraftServer currentServer;

    public static MinecraftServer currentServer() {
        return currentServer;
    }

    @Override
    public void onInitialize() {

        registerCustomStatsAndTriggers();
        ModSounds.register();
        ItemCsgoBox.registerDataComponents();
        ModItems.register();
        ModItems.registerTab();
        ModBlocks.register();
        ModMenus.register();
        ModVillagers.register();

        Networking.register();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            currentServer = server;
            onServerStarting(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> onServerStopping());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CsboxCommand.register(dispatcher);
            EditorCommand.register(dispatcher);
        });

        ModEvents.registerListeners();

        // commonSetup equivalent: load box definitions after our own items are
        // registered so the creative tab is populated. onServerStarting re-runs
        // loadAll() per world (idempotent), and CsgoBoxClient loads once more
        // after every mod's main init has finished (other mods' items present).
        BoxJsonLoader.loadAll();
        if (CONFIG.enableHotReload()) {
            startBoxWatcher();
        }
        LOGGER.info("CS2 Box initialized successfully (Fabric)");
    }

    /** Persists {@code config/csgobox.json} — the Cloth Config screen's
     *  saving runnable calls this (Fabric has no CONFIG_SPEC to save). */
    public static void saveConfig() {
        CONFIG.save();
    }

    private static void registerCustomStatsAndTriggers() {
        Registry.register(BuiltInRegistries.CUSTOM_STAT, OpenedBoxTrigger.STAT_ID, OpenedBoxTrigger.STAT_ID);
        Registry.register(BuiltInRegistries.CUSTOM_STAT, TERMINAL_BUYS_STAT_ID, TERMINAL_BUYS_STAT_ID);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, OpenedBoxTrigger.ID, OpenedBoxTrigger.INSTANCE);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, ModLoadedTrigger.ID, ModLoadedTrigger.INSTANCE);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, TerminalDealTrigger.ID, TerminalDealTrigger.INSTANCE);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, TerminalBrokeTrigger.ID, TerminalBrokeTrigger.INSTANCE);
        OPENED_BOXES_STAT = Stats.CUSTOM.get(OpenedBoxTrigger.STAT_ID);
        TERMINAL_BUYS_STAT = Stats.CUSTOM.get(TERMINAL_BUYS_STAT_ID);
        LOGGER.info("Resolved custom stats {} / {}", OPENED_BOXES_STAT, TERMINAL_BUYS_STAT);
    }

    private static void onServerStarting(MinecraftServer server) {
        if (CONFIG.loadDefaultBoxes()) {
            BoxJsonLoader.loadAll();
        }
        com.reclizer.csgobox.fabric_1_21_1.terminal.TerminalSessionManager.bindServer(server);
        LOGGER.info("CS2 Box server started with {} box definitions", BoxRegistry.size());
    }

    private static void onServerStopping() {
        com.reclizer.csgobox.fabric_1_21_1.terminal.TerminalSessionManager.saveNow();
        com.reclizer.csgobox.fabric_1_21_1.terminal.TerminalSessionManager.unbindServer();
        com.reclizer.csgobox.logic.BoxConstraintTracker.reset();
        com.reclizer.csgobox.logic.PityTracker.reset();
        com.reclizer.csgobox.terminal.TerminalStockManager.reset();
        com.reclizer.csgobox.logic.OpenBlockGuard.reset();
        if (boxWatcher != null) {
            boxWatcher.stop();
            boxWatcher = null;
        }
    }

    private static void startBoxWatcher() {
        if (boxWatcher != null) {
            return;
        }
        Path boxesDir = FabricLoader.getInstance().getConfigDir().resolve("csbox");
        boxWatcher = BoxFileWatcher.start(
                boxesDir,
                () -> {
                    BoxJsonLoader.reloadPreserving();
                    var server = currentServer;
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
        var server = currentServer;
        if (server == null) {
            return;
        }
        PacketSyncBoxDefinitions packet = PacketSyncBoxDefinitions.ofAll();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, packet);
        }
    }

    public static boolean debug() {
        return false;
    }
}
