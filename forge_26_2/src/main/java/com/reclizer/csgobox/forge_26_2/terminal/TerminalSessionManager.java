package com.reclizer.csgobox.forge_26_2.terminal;

import com.reclizer.csgobox.forge_26_2.box.BoxDefinition;
import com.reclizer.csgobox.forge_26_2.box.BoxRegistry;
import com.reclizer.csgobox.forge_26_2.item.ItemCsgoBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
     * Per-player terminal locks keyed by {@code player-uuid:terminal-uid}:
     * each terminal owns its own negotiation. Sessions survive logout and
     * screen close; released by a completed buy, five rejects, or timeout.
     * Persisted via {@link TerminalStateStore}; expired terminals stay dead
     * across restarts, live ones stay locked to their owner.
     */
public final class TerminalSessionManager {

    private static final Map<String, TerminalSession> SESSIONS = new ConcurrentHashMap<>();

    /**
     * Uids whose negotiation timed out without a deal. The terminal may
     * still exist somewhere; the next open destroys it. Ordered (FIFO) so
     * eviction at capacity drops the oldest entry first.
     */
    private static final Set<String> DESTROYED_UIDS = Collections.synchronizedSet(new LinkedHashSet<>());

    /**
     * Server-authoritative binding of the uid whose negotiation screen is
     * open per player. Buy/reject are only accepted while the main-hand uid
     * matches, so a hotbar switch mid-screen cannot operate another terminal.
     * Screen state only; never persisted.
     */
    private static final Map<String, String> OPEN_UID = new ConcurrentHashMap<>();

    private static final int DESTROYED_UID_CAP = 8192;

    /** Server bound on start; mutations flushed on the 1 Hz tick and server stop. */
    private static MinecraftServer SERVER;

    /** Persisted structure changed; {@link #flush()} writes once per change. */
    private static boolean dirty;

    private TerminalSessionManager() {
    }

    /** Bind the server and reload the persisted sessions / destroyed uids. */
    public static void bindServer(MinecraftServer server) {
        SERVER = server;
        TerminalStateStore.load(server);
    }

    /** Drop server and all in-memory state on stop (after {@link #saveNow()}); must not outlive the world. */
    public static void unbindServer() {
        SERVER = null;
        SESSIONS.clear();
        DESTROYED_UIDS.clear();
        OPEN_UID.clear();
        dirty = false;
    }

    /** Active session for the player's terminal, creating one when none exists. */
    public static TerminalSession getOrCreate(ServerPlayer player, ItemStack terminalStack) {
        Identifier boxId = ItemCsgoBox.getBoxId(terminalStack);
        if (boxId == null) {
            return null;
        }
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            return null;
        }
        // Sessions bind to the terminal's own uid, never to the box type.
        String uid = ItemCsgoBox.ensureTerminalUid(terminalStack);
        String key = player.getStringUUID() + ":" + uid;
        TerminalSession existing = SESSIONS.get(key);
        if (existing != null && !existing.isFinished()) {
            return existing;
        }
        // Deadline in world time (game ticks × 50): expires only while the world runs.
        long worldMs = player.level().getGameTime() * 50L;
        TerminalSession created = TerminalSession.create(player.getStringUUID(), uid, boxId, def, worldMs);
        ItemCsgoBox.stampTerminalOwner(terminalStack, player.getPlainTextName());
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

    /** Gate for buy/reject/close: the open screen must be bound to this exact uid. */
    public static boolean isOpenBinding(String playerUuid, String terminalUid) {
        return terminalUid != null && terminalUid.equals(OPEN_UID.get(playerUuid));
    }

    /**
     * Display name of the owner locking this terminal, or null when the uid
     * is free. Online player first (handles renames), then the owner stamp,
     * then the raw uuid.
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
                    return online.getPlainTextName();
                }
            }
        } catch (IllegalArgumentException e) {
            // corrupted persisted uuid — fall through to the item stamp
        }
        String stamped = ItemCsgoBox.getTerminalOwner(terminalStack);
        return stamped != null ? stamped : playerUuid;
    }

    /** The player's session for one terminal (by uid), or null. */
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

    /** 1 Hz tick: advance countdowns, drop finished/expired sessions. */
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

    /** Timeout self-destruct: destroy the terminal if the owner is online; else remember the uid for the next open. */
    private static void destroyTerminal(MinecraftServer server, TerminalSession session) {
        // Corrupted persisted uuid must not crash the tick.
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
                // Confirmed destroyed — clean the uid so the next open starts fresh.
                player.sendSystemMessage(Component.translatable("csgobox.terminal.sys.destroyed"));
                DESTROYED_UIDS.remove(session.uid());
                OPEN_UID.remove(session.playerUuid());
                dirty = true;
                return;
            }
        }
        // Not confirmed — keep the uid so the next open destroys it wherever it is.
        DESTROYED_UIDS.add(session.uid());
        OPEN_UID.remove(session.playerUuid());
        if (DESTROYED_UIDS.size() > DESTROYED_UID_CAP) {
            // FIFO: the head is the oldest entry; never evict an unconfirmed uid at random.
            DESTROYED_UIDS.remove(DESTROYED_UIDS.iterator().next());
        }
        dirty = true;
    }

    // ---- persistence (TerminalStateStore) ----

    /** Live (unfinished) sessions — for the world save. */
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

    /** Persist pending mutations; dirty clears only after a successful write, so a failed save is retried. */
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
