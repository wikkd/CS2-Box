package com.reclizer.csgobox.v1_21_1.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = CsgoBox.MODID)
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
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, EDITOR_URL))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.csgobox.editor.hover"))));
        source.sendSuccess(() -> Component.translatable("commands.csgobox.editor.message").append(link), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.editor.hint"), false);
        return Command.SINGLE_SUCCESS;
    }
}
