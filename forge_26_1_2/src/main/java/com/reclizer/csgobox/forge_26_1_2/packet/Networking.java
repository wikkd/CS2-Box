package com.reclizer.csgobox.forge_26_1_2.packet;

import com.reclizer.csgobox.forge_26_1_2.CsgoBox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkProtocol;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Forge SimpleChannel networking hub for CS2 Box.
 * Registers all packet types and provides convenience send methods.
 */
public final class Networking {
    private Networking() {
    }

    public static SimpleChannel INSTANCE;

    public static void registerMessages() {
        INSTANCE = ChannelBuilder.named(Identifier.fromNamespaceAndPath(CsgoBox.MODID, "network"))
                .networkProtocolVersion(1)
                .simpleChannel();

        // Serverbound packets (client → server)
        INSTANCE.messageBuilder(PacketCsgoProgress.class, NetworkProtocol.PLAY)
                .direction(PacketFlow.SERVERBOUND)
                .codec(PacketCsgoProgress.STREAM_CODEC)
                .consumer(PacketCsgoProgress::handleServer)
                .add();
        INSTANCE.messageBuilder(PacketCsgoBulkProgress.class, NetworkProtocol.PLAY)
                .direction(PacketFlow.SERVERBOUND)
                .codec(PacketCsgoBulkProgress.STREAM_CODEC)
                .consumer(PacketCsgoBulkProgress::handleServer)
                .add();
        INSTANCE.messageBuilder(PacketRequestBoxItems.class, NetworkProtocol.PLAY)
                .direction(PacketFlow.SERVERBOUND)
                .codec(PacketRequestBoxItems.STREAM_CODEC)
                .consumer(PacketRequestBoxItems::handle)
                .add();
        INSTANCE.messageBuilder(PacketTerminalBuy.class, NetworkProtocol.PLAY)
                .direction(PacketFlow.SERVERBOUND)
                .codec(PacketTerminalBuy.STREAM_CODEC)
                .consumer(PacketTerminalBuy::handleServer)
                .add();

        // Clientbound packets (server → client)
        INSTANCE.messageBuilder(PacketBoxOpenResult.class, NetworkProtocol.PLAY)
                .direction(PacketFlow.CLIENTBOUND)
                .codec(PacketBoxOpenResult.STREAM_CODEC)
                .consumer(PacketBoxOpenResult::handle)
                .add();
        INSTANCE.messageBuilder(PacketBoxBulkResult.class, NetworkProtocol.PLAY)
                .direction(PacketFlow.CLIENTBOUND)
                .codec(PacketBoxBulkResult.STREAM_CODEC)
                .consumer(PacketBoxBulkResult::handle)
                .add();
        INSTANCE.messageBuilder(PacketSyncBoxItems.class, NetworkProtocol.PLAY)
                .direction(PacketFlow.CLIENTBOUND)
                .codec(PacketSyncBoxItems.STREAM_CODEC)
                .consumer(PacketSyncBoxItems::handle)
                .add();
        INSTANCE.messageBuilder(PacketTerminalBuyResult.class, NetworkProtocol.PLAY)
                .direction(PacketFlow.CLIENTBOUND)
                .codec(PacketTerminalBuyResult.STREAM_CODEC)
                .consumer(PacketTerminalBuyResult::handle)
                .add();

        INSTANCE.build();
    }

    public static void sendToPlayer(Object msg, ServerPlayer player) {
        INSTANCE.send(msg, PacketDistributor.PLAYER.with(player));
    }

    public static void sendToServer(Object msg) {
        INSTANCE.send(msg, PacketDistributor.SERVER.noArg());
    }
}
