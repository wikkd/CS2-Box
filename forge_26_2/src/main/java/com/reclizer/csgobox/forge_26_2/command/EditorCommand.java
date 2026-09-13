package com.reclizer.csgobox.forge_26_2.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.reclizer.csgobox.forge_26_2.CsgoBox;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.net.URI;

@Mod.EventBusSubscriber(modid = CsgoBox.MODID)
public final class EditorCommand {
    private EditorCommand() {
    }

    /** Web box editor entry. Keep in sync across the six platform modules. */
    public static final String EDITOR_URL = "https://wikkd.github.io/CS2-Box/";

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("csbox")
                        .then(Commands.literal("editor")
                                .executes(EditorCommand::openEditor)));
    }

    private static int openEditor(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Component link = Component.literal(EDITOR_URL)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(EDITOR_URL)))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("commands.csgobox.editor.hover"))));
        source.sendSuccess(() -> Component.translatable("commands.csgobox.editor.message").append(link), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.editor.hint"), false);
        return Command.SINGLE_SUCCESS;
    }
}
