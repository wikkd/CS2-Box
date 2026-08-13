package com.reclizer.csgobox.v1_21_1.terminal;

import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the per-player terminal locks. Keyed by {@code player-uuid:terminal-uid}
 * so every individual terminal (not just every terminal type) owns its own
 * negotiation — an opened terminal resumes where it left off, a never-opened
 * terminal starts fresh, and two terminals of the same type never share state.
 * Sessions survive logout and server-side screen close; they are released only
 * by a completed buy, five rejects, or a timeout self-destruct.
 *
 * <p>State is persisted to the world save (see {@link TerminalStateStore}): an
 * expired terminal stays dead across restarts (and when handed to another
 * player, it is destroyed on their next open), while an opened-but-live
 * terminal stays locked to its owner — anyone else is refused by the dealer,
 * who points at the owner ("去问问xxx吧").</p>
 */
public final class TerminalSessionManager {

    private static final Map<String, TerminalSession> SESSIONS = new ConcurrentHashMap<>();

    /**
     * Terminal uids whose negotiation timed out without a deal. The stack may
     * still exist somewhere (chest / ground / offline inventory / another
     * player); the next time a terminal carrying one of these uids is opened,
     * it is destroyed on the spot. Persisted with the world save. A uid is
     * removed again once the terminal is confirmed destroyed (inventory sweep
     * at timeout or open-time consume).
     *
     * <p>Ordered (FIFO) so the capacity eviction below always drops the
     * OLDEST entry — a random eviction could resurrect a terminal whose
     * destruction was never confirmed.</p>
     */
    private static final Set<String> DESTROYED_UIDS = Collections.synchronizedSet(new LinkedHashSet<>());

    /**
     * Runtime-only binding of the terminal uid whose negotiation screen is
     * currently open for each player (server-authoritative). Set on a
     * successful open, cleared on close / buy / reject-destroy / timeout /
     * logout / server unbind. buy and reject are only accepted while the
     * main-hand uid matches this binding — otherwise a player who switched
     * hotbar slots mid-screen would operate (or destroy) a DIFFERENT
     * terminal's negotiation. Never persisted: it is screen state, not world
     * state.
     */
    private static final Map<String, String> OPEN_UID = new ConcurrentHashMap<>();

    private static final int DESTROYED_UID_CAP = 8192;

    /** The running server — bound on start; mutations are flushed by the
     *  1 Hz tick and on server stop (see {@link #flush()}). */
    private static MinecraftServer SERVER;

    /** Set when a persisted structure changed; {@link #flush()} writes once per change. */
    private static boolean dirty;

    private TerminalSessionManager() {
    }

    /** Bind the server and reload the persisted sessions / destroyed uids. */
    public static void bindServer(MinecraftServer server) {
        SERVER = server;
        TerminalStateStore.load(server);
    }

    /** Drop the server reference and ALL in-memory session state. Called
     *  after {@link #saveNow()} on server stop, so no data is lost: the
     *  collections must not outlive the world — otherwise sessions and
     *  destroyed uids from world A would be ticked against and persisted
     *  into world B in single-player / LAN world switching. */
    public static void unbindServer() {
        SERVER = null;
        SESSIONS.clear();
        DESTROYED_UIDS.clear();
        OPEN_UID.clear();
        dirty = false;
    }

    /** The active session for the player's terminal, creating a fresh one when none exists. */
    public static TerminalSession getOrCreate(ServerPlayer player, ItemStack terminalStack) {
        ResourceLocation boxId = ItemCsgoBox.getBoxId(terminalStack);
        if (boxId == null) {
            return null;
        }
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            return null;
        }
        // Every opened terminal is stamped with its own uid on first use, so
        // the session is bound to THIS terminal — never to the box type.
        String uid = ItemCsgoBox.ensureTerminalUid(terminalStack);
        String key = player.getStringUUID() + ":" + uid;
        TerminalSession existing = SESSIONS.get(key);
        if (existing != null && !existing.isFinished()) {
            return existing;
        }
        // The countdown lives on the WORLD clock (game ticks × 50), so a
        // fresh negotiation's deadline is stamped in world time too — it
        // expires only while the world runs, exactly like restored sessions.
        long worldMs = player.level().getGameTime() * 50L;
        TerminalSession created = TerminalSession.create(player.getStringUUID(), uid, boxId, def, worldMs);
        ItemCsgoBox.stampTerminalOwner(terminalStack, player.getGameProfile().getName());
        SESSIONS.put(key, created);
        dirty = true;
        return created;
    }

    /** Record that the player's open terminal screen is bound to this uid. */
    public static void bindOpen(String playerUuid, String terminalUid) {
        if (terminalUid != null) {
            OPEN_UID.put(playerUuid, terminalUid);
        }
    }

    /** Clear the player's open binding when it still points at this uid. */
    public static void clearOpenIf(String playerUuid, String terminalUid) {
        if (terminalUid != null && terminalUid.equals(OPEN_UID.get(playerUuid))) {
            OPEN_UID.remove(playerUuid);
        }
    }

    /** Clear the player's open binding on logout (the session itself stays). */
    public static void clearOpen(String playerUuid) {
        OPEN_UID.remove(playerUuid);
    }

    /**
     * True when the player's open screen is bound to exactly this terminal
     * uid — the gate buy/reject/close must pass so a hotbar switch mid-screen
     * can never operate a different terminal.
     */
    public static boolean isOpenBinding(String playerUuid, String terminalUid) {
        return terminalUid != null && terminalUid.equals(OPEN_UID.get(playerUuid));
    }

    /**
     * Display name of the player whose LIVE negotiation locks this terminal
     * to another player: an opened-but-not-expired terminal handed over is
     * unusable by anyone but its owner — the dealer refuses and points at the
     * owner. Null when the uid is free (never opened, finished by a buy or
     * five rejects, or timed out). The name comes from the online player
     * first (handles renames), then the terminal's owner stamp, then the raw
     * uuid as a last resort.
     */
    public static String activeOwnerName(String terminalUid, String playerUuid, ItemStack terminalStack) {
        if (terminalUid == null) {
            return null;
        }
        for (TerminalSession session : SESSIONS.values()) {
            if (session.uid().equals(terminalUid)
                    && !session.playerUuid().equals(playerUuid)
                    && !session.isFinished()) {
                return resolveOwnerName(session.playerUuid(), terminalStack);
            }
        }
        return null;
    }

    /** Best-effort display name for a session owner: online player, then the item stamp, then the raw uuid. */
    private static String resolveOwnerName(String playerUuid, ItemStack terminalStack) {
        try {
            UUID id = UUID.fromString(playerUuid);
            if (SERVER != null) {
                ServerPlayer online = SERVER.getPlayerList().getPlayer(id);
                if (online != null) {
                    return online.getGameProfile().getName();
                }
            }
        } catch (IllegalArgumentException e) {
            // corrupted persisted uuid — fall through to the item stamp
        }
        String stamped = ItemCsgoBox.getTerminalOwner(terminalStack);
        return stamped != null ? stamped : playerUuid;
    }

    /** The player's active session for one specific terminal (by uid), or null. */
    public static TerminalSession getByUid(ServerPlayer player, String terminalUid) {
        if (terminalUid == null) {
            return null;
        }
        return SESSIONS.get(player.getStringUUID() + ":" + terminalUid);
    }

    /** Drops a session immediately (buy self-destruct); the next flush persists it. */
    public static void removeByUid(String playerUuid, String terminalUid) {
        if (terminalUid != null) {
            SESSIONS.remove(playerUuid + ":" + terminalUid);
            dirty = true;
        }
    }

    /** A session's model changed (reject / close / insufficient points) — persist on the next flush. */
    public static void markDirty() {
        dirty = true;
    }

    /**
     * True when the terminal uid already self-destructed on timeout. Consumed
     * by {@code PacketTerminalOpen} so a stored-away terminal is destroyed the
     * moment the player tries to open it again; the confirmed destruction is
     * persisted (the expired uid is cleaned up).
     */
    public static boolean consumeDestroyedUid(String uid) {
        boolean removed = DESTROYED_UIDS.remove(uid);
        if (removed) {
            dirty = true;
        }
        return removed;
    }

    /**
     * Server tick pass (1 Hz, driven by the vanilla {@code ServerTickEvent}):
     * advances the authoritative countdown of every locked session and drops
     * sessions that are finished or just expired — an expired negotiation
     * self-destructs exactly like five rejects, so the next open is fresh.
     */
    public static void tickSessions(MinecraftServer server, long nowMs) {
        boolean removed = SESSIONS.entrySet().removeIf(entry -> {
            TerminalSession session = entry.getValue();
            if (session.isFinished()) {
                return true;
            }
            if (session.model().tickServer(nowMs)) {
                destroyTerminal(server, session);
                return true;
            }
            return false;
        });
        if (removed) {
            dirty = true;
        }
        flush();
    }

    /**
     * Timeout self-destruct: when the owner is online and the terminal is
     * found and destroyed right now, the expired uid is cleaned up; otherwise
     * (offline / chest / handed off) the uid is remembered so the terminal is
     * destroyed on its next open wherever it is.
     */
    private static void destroyTerminal(MinecraftServer server, TerminalSession session) {
        // A corrupted persisted player uuid must never crash the server tick.
        ServerPlayer player;
        try {
            player = server.getPlayerList().getPlayer(UUID.fromString(session.playerUuid()));
        } catch (IllegalArgumentException e) {
            player = null;
        }
        if (player != null) {
            boolean destroyedAny = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && session.uid().equals(ItemCsgoBox.getTerminalUid(stack))) {
                    stack.setCount(0);
                    destroyedAny = true;
                }
            }
            if (destroyedAny) {
                // Confirmed gone (all copies the inventory sweep can see) —
                // clean the expired uid so the next open starts fresh.
                player.sendSystemMessage(Component.translatable("csgobox.terminal.sys.destroyed"));
                DESTROYED_UIDS.remove(session.uid());
                OPEN_UID.remove(session.playerUuid());
                dirty = true;
                return;
            }
        }
        // Not confirmed (offline / stored away / handed off) — keep the uid so
        // the next open destroys the terminal wherever it is.
        DESTROYED_UIDS.add(session.uid());
        OPEN_UID.remove(session.playerUuid());
        if (DESTROYED_UIDS.size() > DESTROYED_UID_CAP) {
            // FIFO eviction: the ordered set's head is the oldest entry, so
            // a flood of timeouts can only push out terminals whose expired
            // uid was recorded longest ago — never a random one whose
            // destruction is still unconfirmed.
            DESTROYED_UIDS.remove(DESTROYED_UIDS.iterator().next());
        }
        dirty = true;
    }

    // ---- persistence (TerminalStateStore) ----

    /** All live (unfinished) sessions — for the world save. */
    public static List<TerminalSession> allSessions() {
        return SESSIONS.values().stream().filter(s -> !s.isFinished()).toList();
    }

    /** Destroyed uids — for the world save. */
    public static Set<String> destroyedUids() {
        return Set.copyOf(DESTROYED_UIDS);
    }

    /** Reload a session from the world save (owner may be offline). */
    public static void restore(TerminalSession session) {
        SESSIONS.putIfAbsent(session.playerUuid() + ":" + session.uid(), session);
    }

    /** Reload one destroyed uid from the world save. */
    public static void restoreDestroyedUid(String uid) {
        if (uid != null && !uid.isEmpty()) {
            DESTROYED_UIDS.add(uid);
        }
    }

    /** Persist pending mutations; no-op when nothing changed. The dirty flag
     *  is only cleared after a successful write, so a failed save is retried
     *  on the next flush instead of silently dropping the mutation. */
    public static void flush() {
        if (dirty && SERVER != null) {
            if (TerminalStateStore.save(SERVER)) {
                dirty = false;
            }
        }
    }

    /** Force a write even when nothing changed (server stop). */
    public static void saveNow() {
        dirty = true;
        flush();
    }
}
