package com.reclizer.csgobox.v26_1_2.terminal;

import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_1_2.packet.PacketTerminalState;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * World-save persistence for {@link TerminalSessionManager}: every live
 * terminal session (owner, negotiation state, sampled offers/items, slot
 * item) plus the destroyed-uid set. Sessions are written with the same
 * {@link PacketTerminalState} codec used over the wire (items need a
 * {@link RegistryFriendlyByteBuf} with the server's registry access).
 *
 * <p>File: {@code <world>/csgobox/terminal_state.bin}. Loaded on server start
 * after the box definitions are registered; saved after every mutation and on
 * server stop. A corrupt/unknown-version file is ignored (fresh state).</p>
 */
public final class TerminalStateStore {

    private static final byte[] MAGIC = {'C', 'S', 'G', 'B', 'T', 'E', 'R', 'M'};
    // v3: SystemEntry history entries carry translatable args (terminal owner
    // name in the locked refusal); older v2 files are ignored as corrupt.
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
            RegistryFriendlyByteBuf buf = wrap(payload, server);
            long worldMs = server.overworld().getGameTime() * 50L;
            int sessionCount = buf.readVarInt();
            for (int i = 0; i < sessionCount; i++) {
                String playerUuid = buf.readUtf();
                int len = buf.readVarInt();
                byte[] bytes = new byte[len];
                buf.readBytes(bytes);
                PacketTerminalState state = PacketTerminalState.STREAM_CODEC.decode(wrap(bytes, server));
                // A session whose box definition no longer exists is dropped.
                if (state.boxId() != null && !state.boxId().isEmpty()
                        && BoxRegistry.get(Identifier.parse(state.boxId())) != null) {
                    TerminalSessionManager.restore(TerminalSession.fromState(playerUuid, state, worldMs));
                }
            }
            int destroyedCount = buf.readVarInt();
            for (int i = 0; i < destroyedCount; i++) {
                TerminalSessionManager.restoreDestroyedUid(buf.readUtf());
            }
        } catch (IOException | RuntimeException e) {
            CsgoBox.LOGGER.warn("[csgo-terminal] failed to load terminal state: {}", e.getMessage());
        }
    }

    public static void save(MinecraftServer server) {
        try {
            Path file = path(server);
            Files.createDirectories(file.getParent());
            RegistryFriendlyByteBuf buf = buffer(server);
            List<TerminalSession> sessions = TerminalSessionManager.allSessions();
            buf.writeVarInt(sessions.size());
            for (TerminalSession session : sessions) {
                buf.writeUtf(session.playerUuid());
                RegistryFriendlyByteBuf sbuf = buffer(server);
                PacketTerminalState.STREAM_CODEC.encode(sbuf, PacketTerminalState.fromSession(session, 0L));
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
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
                out.write(MAGIC);
                out.writeInt(VERSION);
                out.write(payload);
            }
        } catch (IOException | RuntimeException e) {
            CsgoBox.LOGGER.warn("[csgo-terminal] failed to save terminal state: {}", e.getMessage());
        }
    }

    private static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("csgobox").resolve("terminal_state.bin");
    }

    private static RegistryFriendlyByteBuf wrap(byte[] bytes, MinecraftServer server) {
        return new RegistryFriendlyByteBuf(
                new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)), server.registryAccess());
    }

    private static RegistryFriendlyByteBuf buffer(MinecraftServer server) {
        return new RegistryFriendlyByteBuf(new FriendlyByteBuf(Unpooled.buffer()), server.registryAccess());
    }
}
