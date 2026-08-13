package com.reclizer.csgobox.v26_2.packet;

import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.v26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_2.item.ItemTerminal;
import com.reclizer.csgobox.v26_2.terminal.TerminalSession;
import com.reclizer.csgobox.v26_2.terminal.TerminalSessionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server: the terminal screen opened. The server returns the
 * player's locked session (creating a fresh negotiation on first open, or
 * after a completed buy / five rejects) so the screen resumes exactly where
 * the player left off.
 */
public record PacketTerminalOpen(ItemStack terminalStack, long requestId) implements CustomPacketPayload {

    public static final Type<PacketTerminalOpen> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "terminal_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTerminalOpen> STREAM_CODEC = StreamCodec.of(
            PacketTerminalOpen::write,
            PacketTerminalOpen::read
    );

    private static void write(RegistryFriendlyByteBuf buf, PacketTerminalOpen packet) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.terminalStack);
        buf.writeLong(packet.requestId);
    }

    private static PacketTerminalOpen read(RegistryFriendlyByteBuf buf) {
        return new PacketTerminalOpen(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final PacketTerminalOpen message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) {
                return;
            }
            ItemStack held = sp.getMainHandItem();
            if (!(held.getItem() instanceof ItemTerminal)) {
                // The held item is no longer the terminal the screen opened
                // with (e.g. the player swapped hotbar slots mid-open): reply
                // a FAILED snapshot so the screen shows a retry notice instead
                // of hanging forever waiting for a state.
                context.reply(PacketTerminalState.unreachable(
                        ItemCsgoBox.getBoxId(message.terminalStack()),
                        ItemCsgoBox.getTerminalUid(message.terminalStack()),
                        message.requestId()));
                return;
            }
            String uid = ItemCsgoBox.getTerminalUid(held);
            if (uid != null && TerminalSessionManager.consumeDestroyedUid(uid)) {
                // This terminal already timed out — destroy it on the spot and
                // let the open screen show the failure instead of hanging.
                held.setCount(0);
                sp.sendSystemMessage(Component.translatable("csgobox.terminal.sys.destroyed"));
                context.reply(PacketTerminalState.destroyed(ItemCsgoBox.getBoxId(held), uid, message.requestId()));
                return;
            }
            String ownerName = uid == null ? null
                    : TerminalSessionManager.activeOwnerName(uid, sp.getStringUUID(), held);
            if (ownerName != null) {
                // Opened but not expired and locked to another player's live
                // negotiation — the dealer refuses, no session is created.
                context.reply(PacketTerminalState.locked(
                        ItemCsgoBox.getBoxId(held), uid, message.requestId(), ownerName));
                return;
            }
            TerminalSession session = TerminalSessionManager.getOrCreate(sp, held);
            if (session != null) {
                // Pin this open to the terminal's uid: buy/reject/close are
                // only accepted while the main-hand uid matches this binding.
                TerminalSessionManager.bindOpen(sp.getStringUUID(), session.uid());
                context.reply(PacketTerminalState.fromSession(session, message.requestId()));
            } else {
                // No definition (e.g. the terminal is still an unconfigured
                // first-run box): reply a FAILED snapshot instead of staying
                // silent — otherwise the screen hangs at IDLE forever.
                context.reply(PacketTerminalState.empty(ItemCsgoBox.getBoxId(held),
                        ItemCsgoBox.getTerminalUid(held), message.requestId()));
            }
        });
    }
}
