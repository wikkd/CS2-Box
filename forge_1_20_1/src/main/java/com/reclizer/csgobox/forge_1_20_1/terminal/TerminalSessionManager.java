package com.reclizer.csgobox.forge_1_20_1.terminal;

import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
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

public final class TerminalSessionManager {

    private static final Map<String, TerminalSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Set<String> DESTROYED_UIDS = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final Map<String, String> OPEN_UID = new ConcurrentHashMap<>();
    private static final int DESTROYED_UID_CAP = 8192;
    private static MinecraftServer SERVER;
    private static boolean dirty;

    private TerminalSessionManager() {
    }

    public static void bindServer(MinecraftServer server) {
        SERVER = server;
        TerminalStateStore.load(server);
    }

    public static void unbindServer() {
        SERVER = null;
        SESSIONS.clear();
        DESTROYED_UIDS.clear();
        OPEN_UID.clear();
        dirty = false;
    }

    public static TerminalSession getOrCreate(ServerPlayer player, ItemStack terminalStack) {
        ResourceLocation boxId = ItemCsgoBox.getBoxId(terminalStack);
        if (boxId == null) {
            return null;
        }
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            return null;
        }
        String uid = ItemCsgoBox.ensureTerminalUid(terminalStack);
        String key = player.getStringUUID() + ":" + uid;
        TerminalSession existing = SESSIONS.get(key);
        if (existing != null && !existing.isFinished()) {
            return existing;
        }
        long worldMs = player.level().getGameTime() * 50L;
        TerminalSession created = TerminalSession.create(player.getStringUUID(), uid, boxId, def, worldMs);
        ItemCsgoBox.stampTerminalOwner(terminalStack, player.getName().getString());
        SESSIONS.put(key, created);
        dirty = true;
        return created;
    }

    public static void bindOpen(String playerUuid, String terminalUid) {
        if (terminalUid != null) {
            OPEN_UID.put(playerUuid, terminalUid);
        }
    }

    public static void clearOpenIf(String playerUuid, String terminalUid) {
        if (terminalUid != null && terminalUid.equals(OPEN_UID.get(playerUuid))) {
            OPEN_UID.remove(playerUuid);
        }
    }

    public static void clearOpen(String playerUuid) {
        OPEN_UID.remove(playerUuid);
    }

    public static boolean isOpenBinding(String playerUuid, String terminalUid) {
        return terminalUid != null && terminalUid.equals(OPEN_UID.get(playerUuid));
    }

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

    private static String resolveOwnerName(String playerUuid, ItemStack terminalStack) {
        try {
            UUID id = UUID.fromString(playerUuid);
            if (SERVER != null) {
                ServerPlayer online = SERVER.getPlayerList().getPlayer(id);
                if (online != null) {
                    return online.getName().getString();
                }
            }
        } catch (IllegalArgumentException e) {
            // corrupted persisted uuid
        }
        String stamped = ItemCsgoBox.getTerminalOwner(terminalStack);
        return stamped != null ? stamped : playerUuid;
    }

    public static TerminalSession getByUid(ServerPlayer player, String terminalUid) {
        if (terminalUid == null) {
            return null;
        }
        return SESSIONS.get(player.getStringUUID() + ":" + terminalUid);
    }

    public static void removeByUid(String playerUuid, String terminalUid) {
        if (terminalUid != null) {
            SESSIONS.remove(playerUuid + ":" + terminalUid);
            dirty = true;
        }
    }

    public static void markDirty() {
        dirty = true;
    }

    public static boolean consumeDestroyedUid(String uid) {
        boolean removed = DESTROYED_UIDS.remove(uid);
        if (removed) {
            dirty = true;
        }
        return removed;
    }

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

    private static void destroyTerminal(MinecraftServer server, TerminalSession session) {
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
                player.sendSystemMessage(Component.translatable("csgobox.terminal.sys.destroyed"));
                DESTROYED_UIDS.remove(session.uid());
                OPEN_UID.remove(session.playerUuid());
                dirty = true;
                return;
            }
        }
        DESTROYED_UIDS.add(session.uid());
        OPEN_UID.remove(session.playerUuid());
        if (DESTROYED_UIDS.size() > DESTROYED_UID_CAP) {
            DESTROYED_UIDS.remove(DESTROYED_UIDS.iterator().next());
        }
        dirty = true;
    }

    public static List<TerminalSession> allSessions() {
        return SESSIONS.values().stream().filter(s -> !s.isFinished()).toList();
    }

    public static Set<String> destroyedUids() {
        return Set.copyOf(DESTROYED_UIDS);
    }

    public static void restore(TerminalSession session) {
        SESSIONS.putIfAbsent(session.playerUuid() + ":" + session.uid(), session);
    }

    public static void restoreDestroyedUid(String uid) {
        if (uid != null && !uid.isEmpty()) {
            DESTROYED_UIDS.add(uid);
        }
    }

    public static void flush() {
        if (dirty && SERVER != null) {
            if (TerminalStateStore.save(SERVER)) {
                dirty = false;
            }
        }
    }

    public static void saveNow() {
        dirty = true;
        flush();
    }
}
