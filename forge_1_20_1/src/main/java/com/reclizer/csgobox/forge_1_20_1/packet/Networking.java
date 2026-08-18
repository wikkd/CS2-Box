package com.reclizer.csgobox.forge_1_20_1.packet;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

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
                PacketCsgoProgress::encode, PacketCsgoProgress::new, PacketCsgoProgress::handle);
        INSTANCE.registerMessage(id++, PacketCsgoBulkProgress.class,
                PacketCsgoBulkProgress::encode, PacketCsgoBulkProgress::new, PacketCsgoBulkProgress::handle);
        INSTANCE.registerMessage(id++, PacketRequestBoxItems.class,
                PacketRequestBoxItems::encode, PacketRequestBoxItems::new, PacketRequestBoxItems::handle);
        INSTANCE.registerMessage(id++, PacketTerminalBuy.class,
                PacketTerminalBuy::encode, PacketTerminalBuy::new, PacketTerminalBuy::handle);
        INSTANCE.registerMessage(id++, PacketTerminalOpen.class,
                PacketTerminalOpen::encode, PacketTerminalOpen::new, PacketTerminalOpen::handle);
        INSTANCE.registerMessage(id++, PacketTerminalReject.class,
                PacketTerminalReject::encode, PacketTerminalReject::new, PacketTerminalReject::handle);
        INSTANCE.registerMessage(id++, PacketTerminalClose.class,
                PacketTerminalClose::encode, PacketTerminalClose::new, PacketTerminalClose::handle);

        // Clientbound packets (server -> client)
        INSTANCE.registerMessage(id++, PacketBoxOpenResult.class,
                PacketBoxOpenResult::encode, PacketBoxOpenResult::new, PacketBoxOpenResult::handle);
        INSTANCE.registerMessage(id++, PacketBoxBulkResult.class,
                PacketBoxBulkResult::encode, PacketBoxBulkResult::new, PacketBoxBulkResult::handle);
        INSTANCE.registerMessage(id++, PacketSyncBoxItems.class,
                PacketSyncBoxItems::encode, PacketSyncBoxItems::new, PacketSyncBoxItems::handle);
        INSTANCE.registerMessage(id++, PacketTerminalBuyResult.class,
                PacketTerminalBuyResult::encode, PacketTerminalBuyResult::new, PacketTerminalBuyResult::handle);
        INSTANCE.registerMessage(id++, PacketTerminalState.class,
                PacketTerminalState::encode, PacketTerminalState::new, PacketTerminalState::handle);
    }

    public static void sendToPlayer(Object msg, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }
}
