package com.reclizer.csgobox.forge_1_18_2.packet;

import com.reclizer.csgobox.forge_1_18_2.CsgoBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class Networking {
    private Networking() {
    }

    public static SimpleChannel INSTANCE;

    private static int id = 0;

    public static void registerMessages() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(CsgoBox.MODID, "network"),
                () -> "1",
                "1"::equals,
                "1"::equals
        );

        // Serverbound packets (client -> server)
        INSTANCE.registerMessage(id++, PacketCsgoProgress.class,
                PacketCsgoProgress::encode, PacketCsgoProgress::new, PacketCsgoProgress::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, PacketCsgoBulkProgress.class,
                PacketCsgoBulkProgress::encode, PacketCsgoBulkProgress::new, PacketCsgoBulkProgress::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, PacketRequestBoxItems.class,
                PacketRequestBoxItems::encode, PacketRequestBoxItems::new, PacketRequestBoxItems::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, PacketTerminalBuy.class,
                PacketTerminalBuy::encode, PacketTerminalBuy::new, PacketTerminalBuy::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, PacketTerminalOpen.class,
                PacketTerminalOpen::encode, PacketTerminalOpen::new, PacketTerminalOpen::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, PacketTerminalReject.class,
                PacketTerminalReject::encode, PacketTerminalReject::new, PacketTerminalReject::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, PacketTerminalClose.class,
                PacketTerminalClose::encode, PacketTerminalClose::new, PacketTerminalClose::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Clientbound packets (server -> client)
        INSTANCE.registerMessage(id++, PacketBoxOpenResult.class,
                PacketBoxOpenResult::encode, PacketBoxOpenResult::new, PacketBoxOpenResult::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++, PacketBoxBulkResult.class,
                PacketBoxBulkResult::encode, PacketBoxBulkResult::new, PacketBoxBulkResult::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++, PacketSyncBoxItems.class,
                PacketSyncBoxItems::encode, PacketSyncBoxItems::new, PacketSyncBoxItems::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++, PacketSyncBoxDefinitions.class,
                PacketSyncBoxDefinitions::encode, PacketSyncBoxDefinitions::new, PacketSyncBoxDefinitions::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++, PacketTerminalBuyResult.class,
                PacketTerminalBuyResult::encode, PacketTerminalBuyResult::new, PacketTerminalBuyResult::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++, PacketTerminalState.class,
                PacketTerminalState::encode, PacketTerminalState::new, PacketTerminalState::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToPlayer(Object msg, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }
}
