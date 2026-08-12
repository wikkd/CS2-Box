package com.reclizer.csgobox.v26_1_2.event;

import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.box.BoxJsonLoader;
import com.reclizer.csgobox.v26_1_2.box.LoadError;
import com.reclizer.csgobox.v26_1_2.config.CsboxConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Pushes {@link LoadError} entries accumulated by {@link BoxJsonLoader#loadAll()}
 * to a player the first time they join a world.
 *
 * <p>Only OP players receive the messages by default; set
 * {@code advanced.jsonErrorAudience = EVERYONE} in {@code csgobox.toml} to push
 * to everyone.</p>
 */
@EventBusSubscriber(modid = CsgoBox.MODID)
public final class LoadErrorAnnouncer {
    private LoadErrorAnnouncer() {
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        if (!BoxJsonLoader.hasLoadErrors()) return;

        CsboxConfig.ErrorChatAudience audience = CsgoBox.CONFIG.jsonErrorAudience();
        if (audience == CsboxConfig.ErrorChatAudience.OP_ONLY
                && !sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return;
        }

        sp.sendSystemMessage(Component.translatable("commands.csgobox.errors.announce",
                        BoxJsonLoader.getLastLoadErrors().size())
                .withStyle(s -> s.withColor(ChatFormatting.YELLOW)));

        for (LoadError err : BoxJsonLoader.getLastLoadErrors()) {
            sp.sendSystemMessage(err.toChatMessage());
        }
    }
}
