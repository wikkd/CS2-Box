package com.reclizer.csgobox.forge_1_20_1.terminal;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.packet.PacketTerminalState;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class TerminalStateStore {

    private static final byte[] MAGIC = {'C', 'S', 'G', 'B', 'T', 'E', 'R', 'M'};
    private static final int VERSION = 3;

    private TerminalStateStore() {
    }

    public static void load(MinecraftServer server) {
        Path file = path(server);
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            byte[] magic = in.readNBytes(MAGIC.length);
            if (!Arrays.equals(magic, MAGIC) || in.readInt() != VERSION) {
                return;
            }
            byte[] payload = in.readAllBytes();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
            long worldMs = server.overworld().getGameTime() * 50L;
            int sessionCount = buf.readVarInt();
            if (sessionCount < 0 || sessionCount > 65536) {
                throw new IOException("implausible session count " + sessionCount);
            }
            for (int i = 0; i < sessionCount; i++) {
                try {
                    String playerUuid = buf.readUtf();
                    int len = buf.readVarInt();
                    if (len < 0 || len > 1_048_576) {
                        throw new IOException("implausible session length " + len);
                    }
                    byte[] bytes = new byte[len];
                    buf.readBytes(bytes);
                    PacketTerminalState state = new PacketTerminalState(
                            new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)));
                    if (state.boxId() != null && !state.boxId().isEmpty()
                            && BoxRegistry.get(new ResourceLocation(state.boxId())) != null) {
                        TerminalSessionManager.restore(TerminalSession.fromState(playerUuid, state, worldMs));
                    }
                } catch (IOException | RuntimeException e) {
                    CsgoBox.LOGGER.warn("[csgo-terminal] failed to load session {} (skipped): {}", i, e.getMessage());
                }
            }
            int destroyedCount = buf.readVarInt();
            if (destroyedCount < 0 || destroyedCount > 65536) {
                throw new IOException("implausible destroyed-uid count " + destroyedCount);
            }
            for (int i = 0; i < destroyedCount; i++) {
                try {
                    TerminalSessionManager.restoreDestroyedUid(buf.readUtf());
                } catch (RuntimeException e) {
                    CsgoBox.LOGGER.warn("[csgo-terminal] failed to load destroyed uid {} (skipped): {}", i, e.getMessage());
                }
            }
        } catch (IOException | RuntimeException e) {
            CsgoBox.LOGGER.warn("[csgo-terminal] failed to load terminal state: {}", e.getMessage());
        }
    }

    public static boolean save(MinecraftServer server) {
        try {
            Path file = path(server);
            Files.createDirectories(file.getParent());
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            List<TerminalSession> sessions = TerminalSessionManager.allSessions();
            buf.writeVarInt(sessions.size());
            for (TerminalSession session : sessions) {
                buf.writeUtf(session.playerUuid());
                FriendlyByteBuf sbuf = new FriendlyByteBuf(Unpooled.buffer());
                PacketTerminalState.fromSession(session, 0L).encode(sbuf);
                buf.writeVarInt(sbuf.writerIndex());
                buf.writeBytes(sbuf, sbuf.readerIndex(), sbuf.writerIndex());
            }
            Set<String> destroyed = TerminalSessionManager.destroyedUids();
            buf.writeVarInt(destroyed.size());
            for (String uid : destroyed) {
                buf.writeUtf(uid);
            }
            byte[] payload = new byte[buf.writerIndex()];
            buf.getBytes(0, payload);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(tmp))) {
                out.write(MAGIC);
                out.writeInt(VERSION);
                out.write(payload);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            CsgoBox.LOGGER.warn("[csgo-terminal] failed to save terminal state: {}", e.getMessage());
            return false;
        }
    }

    private static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("csgobox").resolve("terminal_state.bin");
    }
}
