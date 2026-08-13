package com.reclizer.csgobox.v1_21_1.packet;

import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ItemTerminal;
import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.v1_21_1.terminal.TerminalSession;
import com.reclizer.csgobox.v1_21_1.terminal.TerminalSessionManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server: the player rejected the current offer. Fire-and-forget —
 * the server commits the advance (next round, or FAILED on round 5) into the
 * locked session; the client's busy animation runs locally and needs no reply.
 */
public record PacketTerminalReject(int round) implements CustomPacketPayload {

    public static final Type<PacketTerminalReject> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "terminal_reject"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTerminalReject> STREAM_CODEC = StreamCodec.of(
            PacketTerminalReject::write,
            PacketTerminalReject::read
    );

    private static void write(RegistryFriendlyByteBuf buf, PacketTerminalReject packet) {
        buf.writeVarInt(packet.round);
    }

    private static PacketTerminalReject read(RegistryFriendlyByteBuf buf) {
        return new PacketTerminalReject(buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final PacketTerminalReject message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) {
                return;
            }
            ItemStack held = sp.getMainHandItem();
            if (!(held.getItem() instanceof ItemTerminal)) {
                return;
            }
            // The rejected negotiation must be the one whose screen is open:
            // a hotbar switch mid-screen would otherwise reject (and on round
            // 5, destroy) a DIFFERENT terminal the player is not looking at.
            if (!TerminalSessionManager.isOpenBinding(sp.getStringUUID(), ItemCsgoBox.getTerminalUid(held))) {
                return;
            }
            TerminalSession session = TerminalSessionManager.getByUid(sp, ItemCsgoBox.getTerminalUid(held));
            if (session == null || session.isFinished()
                    || session.model().round() != message.round()) {
                return;
            }
            // World clock (game ticks × 50): the typing window is
            // server-authoritative — a crafted reject before the 1100ms
            // reveal elapsed (or after the countdown expired) is refused.
            long worldMs = sp.level().getGameTime() * 50L;
            if (worldMs >= session.model().countdownDeadlineMs()
                    || worldMs - session.model().roundStartMs() < NegotiationModel.TYPING_MS) {
                return;
            }
            session.model().rejectForced(worldMs);
            if (session.model().status() == NegotiationModel.Status.FAILED) {
                // 谈崩（第 5 轮拒绝）与超时一致：终端机物品本身销毁并立即释放锁。
                TerminalSessionManager.removeByUid(sp.getStringUUID(), ItemCsgoBox.getTerminalUid(held));
                TerminalSessionManager.clearOpenIf(sp.getStringUUID(), ItemCsgoBox.getTerminalUid(held));
                held.setCount(0);
                sp.sendSystemMessage(Component.translatable("csgobox.terminal.sys.broke"));
            } else {
                TerminalSessionManager.markDirty();
            }
        });
    }
}
