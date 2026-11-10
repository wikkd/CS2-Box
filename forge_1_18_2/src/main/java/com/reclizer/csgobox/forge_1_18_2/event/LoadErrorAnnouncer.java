package com.reclizer.csgobox.forge_1_18_2.event;

import com.reclizer.csgobox.config.ErrorChatAudience;
import com.reclizer.csgobox.forge_1_18_2.CsgoBox;
import com.reclizer.csgobox.forge_1_18_2.box.BoxJsonLoader;
import com.reclizer.csgobox.forge_1_18_2.box.LoadError;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;

@Mod.EventBusSubscriber(modid = CsgoBox.MODID)
public final class LoadErrorAnnouncer {
    private LoadErrorAnnouncer() {
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        if (!BoxJsonLoader.hasLoadErrors()) return;

        ErrorChatAudience audience = CsgoBox.CONFIG.jsonErrorAudience();
        if (audience == ErrorChatAudience.OP_ONLY
                && !sp.createCommandSourceStack().hasPermission(2)) {
            return;
        }

        sp.displayClientMessage(new TranslatableComponent("commands.csgobox.errors.announce",
                        BoxJsonLoader.getLastLoadErrors().size())
                .withStyle(s -> s.withColor(ChatFormatting.YELLOW)), false);

        for (LoadError err : BoxJsonLoader.getLastLoadErrors()) {
            sp.displayClientMessage(err.toChatMessage(), false);
        }
    }
}
