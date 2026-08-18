package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ItemTerminal;
import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.forge_1_20_1.terminal.TerminalSession;
import com.reclizer.csgobox.forge_1_20_1.terminal.TerminalSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketTerminalReject {

    private final int round;

    public PacketTerminalReject(int round) {
        this.round = round;
    }

    public PacketTerminalReject(FriendlyByteBuf buf) {
        this.round = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(round);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleServer(this, ctx.get()));
        ctx.get().setPacketHandled(true);
    }

    public static void handleServer(final PacketTerminalReject message, final NetworkEvent.Context context) {
        ServerPlayer sp = context.getSender();
        if (sp == null) {
            return;
        }
        ItemStack held = sp.getMainHandItem();
        if (!(held.getItem() instanceof ItemTerminal)) {
            return;
        }
        if (!TerminalSessionManager.isOpenBinding(sp.getStringUUID(), ItemCsgoBox.getTerminalUid(held))) {
            return;
        }
        TerminalSession session = TerminalSessionManager.getByUid(sp, ItemCsgoBox.getTerminalUid(held));
        if (session == null || session.isFinished()
                || session.model().round() != message.round) {
            return;
        }
        long worldMs = sp.level().getGameTime() * 50L;
        if (worldMs >= session.model().countdownDeadlineMs()
                || worldMs - session.model().roundStartMs() < NegotiationModel.TYPING_MS) {
            return;
        }
        session.model().rejectForced(worldMs);
        if (session.model().status() == NegotiationModel.Status.FAILED) {
            TerminalSessionManager.removeByUid(sp.getStringUUID(), ItemCsgoBox.getTerminalUid(held));
            TerminalSessionManager.clearOpenIf(sp.getStringUUID(), ItemCsgoBox.getTerminalUid(held));
            held.setCount(0);
            sp.sendSystemMessage(Component.translatable("csgobox.terminal.sys.broke"));
        } else {
            TerminalSessionManager.markDirty();
        }
    }
}
