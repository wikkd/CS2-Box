package com.reclizer.csgobox.v26_1_2.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.reclizer.csgobox.box.EditorPack;
import com.reclizer.csgobox.box.EditorPackStats;
import com.reclizer.csgobox.v26_1_2.CsgoBox;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.net.URI;

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
        // Embed the current config/csbox files plus the instance's item/entity
        // registry ids in the URL fragment when they fit (EditorPack: deflate +
        // base64url); the website inflates the fragment, auto-imports each box
        // as a workspace draft and feeds the registries into its autocomplete
        // (box items can be picked in the browser without copying ids/NBT
        // in-game). Any failure or oversize pack falls back to the plain editor
        // URL — over the cap the registry lists alone are dropped first. The
        // fragment is never sent to any server, so configs stay client-side.
        String packed = EditorPack.buildUrl(
                FMLPaths.CONFIGDIR.get().resolve("csbox"), EDITOR_URL, "26.1.2",
                BuiltInRegistries.ITEM.keySet().stream().map(Identifier::toString).toList(),
                BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Identifier::toString).toList());
        final boolean embedded = packed != null;
        final String clickUrl = embedded ? packed : EDITOR_URL;
        Component link = Component.literal(EDITOR_URL)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(clickUrl)))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("commands.csgobox.editor.hover"))));
        source.sendSuccess(() -> Component.translatable("commands.csgobox.editor.message").append(link), false);
        if (embedded) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.editor.embedded"), false);
        }
        EditorPackStats.Summary stats = EditorPackStats.summarize(FMLPaths.CONFIGDIR.get().resolve("csbox"));
        if (!stats.empty()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.editor.stats",
                    stats.boxes(), stats.items(), stats.unpriced()), false);
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.editor.hint"), false);
        return Command.SINGLE_SUCCESS;
    }
}
