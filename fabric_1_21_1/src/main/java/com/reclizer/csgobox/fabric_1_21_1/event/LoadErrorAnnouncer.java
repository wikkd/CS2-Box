package com.reclizer.csgobox.fabric_1_21_1.event;

import com.reclizer.csgobox.config.ErrorChatAudience;
import com.reclizer.csgobox.fabric_1_21_1.CsgoBox;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxJsonLoader;
import com.reclizer.csgobox.fabric_1_21_1.box.LoadError;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pushes {@link LoadError} entries accumulated by {@link BoxJsonLoader#loadAll()}
 * to a player the first time they join a world.
 *
 * <p>Only OP players receive the messages by default; set
 * {@code advanced.jsonErrorAudience = EVERYONE} to push to everyone.</p>
 */
public final class LoadErrorAnnouncer {
    private LoadErrorAnnouncer() {
    }

    /** Called from the ServerPlayConnectionEvents.JOIN wiring in {@link ModEvents}. */
    public static void onPlayerJoin(ServerPlayer sp) {
        if (!BoxJsonLoader.hasLoadErrors()) return;

        ErrorChatAudience audience = CsgoBox.CONFIG.jsonErrorAudience();
        if (audience == ErrorChatAudience.OP_ONLY && !sp.hasPermissions(2)) {
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
