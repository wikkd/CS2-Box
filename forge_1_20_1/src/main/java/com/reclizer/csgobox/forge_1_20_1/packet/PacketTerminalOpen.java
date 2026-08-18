package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ItemTerminal;
import com.reclizer.csgobox.forge_1_20_1.terminal.TerminalSession;
import com.reclizer.csgobox.forge_1_20_1.terminal.TerminalSessionManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketTerminalOpen {

    private final ItemStack terminalStack;
    private final long requestId;

    public PacketTerminalOpen(ItemStack terminalStack, long requestId) {
        this.terminalStack = terminalStack == null ? ItemStack.EMPTY : terminalStack.copy();
        this.requestId = requestId;
    }

    public PacketTerminalOpen(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        this.terminalStack = tag == null ? ItemStack.EMPTY : ItemStack.of(tag);
        this.requestId = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(terminalStack.save(new CompoundTag()));
        buf.writeLong(requestId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleServer(this, ctx.get()));
        ctx.get().setPacketHandled(true);
    }

    public ItemStack getTerminalStack() { return terminalStack; }
    public long getRequestId() { return requestId; }

    public static void handleServer(final PacketTerminalOpen message, final NetworkEvent.Context context) {
        ServerPlayer sp = context.getSender();
        if (sp == null) {
            return;
        }
        ItemStack held = sp.getMainHandItem();
        if (!(held.getItem() instanceof ItemTerminal)) {
            Networking.sendToPlayer(PacketTerminalState.unreachable(
                    ItemCsgoBox.getBoxId(message.terminalStack),
                    ItemCsgoBox.getTerminalUid(message.terminalStack),
                    message.requestId), sp);
            return;
        }
        String uid = ItemCsgoBox.getTerminalUid(held);
        if (uid != null && TerminalSessionManager.consumeDestroyedUid(uid)) {
            held.setCount(0);
            sp.sendSystemMessage(Component.translatable("csgobox.terminal.sys.destroyed"));
            Networking.sendToPlayer(PacketTerminalState.destroyed(ItemCsgoBox.getBoxId(held), uid,
                    message.requestId), sp);
            return;
        }
        String ownerName = uid == null ? null
                : TerminalSessionManager.activeOwnerName(uid, sp.getStringUUID(), held);
        if (ownerName != null) {
            Networking.sendToPlayer(PacketTerminalState.locked(ItemCsgoBox.getBoxId(held), uid,
                    message.requestId, ownerName), sp);
            return;
        }
        TerminalSession session = TerminalSessionManager.getOrCreate(sp, held);
        if (session != null) {
            TerminalSessionManager.bindOpen(sp.getStringUUID(), session.uid());
            Networking.sendToPlayer(PacketTerminalState.fromSession(session, message.requestId), sp);
        } else {
            Networking.sendToPlayer(PacketTerminalState.empty(ItemCsgoBox.getBoxId(held),
                    ItemCsgoBox.getTerminalUid(held), message.requestId), sp);
        }
    }
}
