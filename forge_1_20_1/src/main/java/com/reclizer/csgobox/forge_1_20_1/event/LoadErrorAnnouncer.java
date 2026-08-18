package com.reclizer.csgobox.forge_1_20_1.event;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.box.BoxJsonLoader;
import com.reclizer.csgobox.forge_1_20_1.box.LoadError;
import com.reclizer.csgobox.forge_1_20_1.config.CsboxConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

        CsboxConfig.ErrorChatAudience audience = CsgoBox.CONFIG.jsonErrorAudience();
        if (audience == CsboxConfig.ErrorChatAudience.OP_ONLY
                && !sp.createCommandSourceStack().hasPermission(2)) {
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
