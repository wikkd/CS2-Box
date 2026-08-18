package com.reclizer.csgobox.forge_26_1_2.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.forge_26_1_2.CsgoBox;
import com.reclizer.csgobox.forge_26_1_2.box.BoxDefinition;
import com.reclizer.csgobox.forge_26_1_2.box.BoxItemCodec;
import com.reclizer.csgobox.forge_26_1_2.box.BoxJsonLoader;
import com.reclizer.csgobox.forge_26_1_2.box.BoxRegistry;
import com.reclizer.csgobox.forge_26_1_2.box.GradeGroup;
import com.reclizer.csgobox.forge_26_1_2.box.LoadError;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = CsgoBox.MODID)
public final class CsboxCommand {
    private CsboxCommand() {
    }

    // --- Command exceptions ---

    private static final DynamicCommandExceptionType BOX_NOT_FOUND = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.csgobox.info.not_found", id)
    );
    private static final SimpleCommandExceptionType NBT_EMPTY = new SimpleCommandExceptionType(
            Component.translatable("commands.csgobox.nbt.hand.empty")
    );

    // --- Constants & suggestions ---

    private static final int MAX_NBT_CHARS = 20000;

    private static final SuggestionProvider<CommandSourceStack> BOX_SUGGESTIONS = (context, builder) -> {
        SharedSuggestionProvider.suggestResource(BoxRegistry.getIds(), builder);
        return builder.buildFuture();
    };

    // --- Command tree ---

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("csbox")
                .executes(CsboxCommand::showHelp)
                .then(Commands.literal("info")
                        .requires(CsboxCommand::isGameMaster)
                        .executes(CsboxCommand::showInfoOverview)
                        .then(Commands.argument("box", IdentifierArgument.id())
                                .suggests(BOX_SUGGESTIONS)
                                .executes(ctx -> showBoxInfo(ctx, IdentifierArgument.getId(ctx, "box"))))
                        .then(Commands.literal("error")
                                .executes(ctx -> showLoadErrors(ctx.getSource()))))
                .then(Commands.literal("reload")
                        .requires(CsboxCommand::isGameMaster)
                        .executes(CsboxCommand::reloadBoxes)
                        .then(Commands.literal("tutorial")
                                .executes(CsboxCommand::refreshTutorials)))
                .then(Commands.literal("nbt")
                        .then(Commands.literal("hand")
                                .executes(CsboxCommand::showHandNbt)))
        );
    }

    // --- Query handlers ---

    private static boolean isGameMaster(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** Requires permission level 2; invisible for non-OP players. */
    private static int showHelp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!isGameMaster(source)) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.csgobox.help.need_op")).create();
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.title"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.info"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.reload"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.give_vanilla"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.nbt"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.footer"), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Requires permission level 2; lists all boxes then any load errors. */
    private static int showInfoOverview(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Set<Identifier> ids = BoxRegistry.getIds();
        if (ids.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.list.empty"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.list.header",
                    String.valueOf(ids.size())), false);
            for (Identifier id : ids) {
                BoxDefinition def = BoxRegistry.get(id);
                if (def != null) {
                    int gradeCount = def.grades().size();
                    int itemCount = def.grades().stream().mapToInt(g -> g.items().size()).sum();
                    source.sendSuccess(() -> Component.translatable("commands.csgobox.list.entry",
                            id.toString(), def.name().getString(), String.valueOf(gradeCount), String.valueOf(itemCount)), false);
                }
            }
        }
        return showLoadErrors(source);
    }

    /** Requires permission level 2. */
    private static int showBoxInfo(CommandContext<CommandSourceStack> ctx, Identifier boxId) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        BoxDefinition def = getBoxOrThrow(boxId);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.info.header",
                def.id().toString(), def.name().getString()), false);
        if (!def.isTerminal()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.info.key",
                    def.keyItem().toString()), false);
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.info.drop_rate",
                String.format("%.0f", def.dropRate() * 100)), false);
        if (!def.entityDropRates().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.info.entity_drop_rates_header"), false);
            def.entityDropRates().forEach((entity, rate) ->
                source.sendSuccess(() -> Component.translatable("commands.csgobox.info.entity_drop_rate_entry",
                        entity.toString(), String.format("%.0f", rate * 100)), false)
            );
        }
        if (!def.dropEntities().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int maxShow = 5;
            for (int i = 0; i < Math.min(def.dropEntities().size(), maxShow); i++) {
                if (i > 0) sb.append(", ");
                sb.append(def.dropEntities().get(i));
            }
            if (def.dropEntities().size() > maxShow) {
                sb.append(", ... (").append(def.dropEntities().size()).append(" total)");
            }
            source.sendSuccess(() -> Component.translatable("commands.csgobox.info.drop_entities", sb.toString()), false);
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.info.grades_header",
                String.valueOf(def.grades().size())), false);
        for (int i = 0; i < def.grades().size(); i++) {
            GradeGroup grade = def.grades().get(i);
            source.sendSuccess(() -> Component.translatable("commands.csgobox.info.grade_entry",
                    grade.id(), String.valueOf(grade.weight()), String.valueOf(grade.items().size())), false);
            List<ItemStack> displayItems = grade.items().stream().limit(5).toList();
            for (int j = 0; j < displayItems.size(); j++) {
                final int itemIndex = j;
                ItemStack item = displayItems.get(j);
                source.sendSuccess(() -> Component.translatable("commands.csgobox.info.item_entry",
                        String.valueOf(itemIndex + 1), item.getHoverName().getString(), String.valueOf(item.getCount())), false);
            }
            if (grade.items().size() > 5) {
                source.sendSuccess(() -> Component.translatable("commands.csgobox.info.items_more",
                        String.valueOf(grade.items().size() - 5)), false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Prints the last box JSON load errors, or a green confirmation when none. */
    private static int showLoadErrors(CommandSourceStack source) {
        List<LoadError> errors = BoxJsonLoader.getLastLoadErrors();
        if (errors.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.errors.none")
                    .withStyle(s -> s.withColor(ChatFormatting.GREEN)), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.errors.header",
                String.valueOf(errors.size()))
                .withStyle(s -> s.withColor(ChatFormatting.YELLOW)), false);
        for (LoadError err : errors) {
            source.sendSuccess(err::toChatMessage, false);
        }
        return errors.size();
    }

    // --- Admin handlers ---

    private static int reloadBoxes(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        BoxJsonLoader.reloadPreserving();
        source.sendSuccess(() -> Component.translatable("commands.csgobox.reload.success", BoxRegistry.size()), false);
        return BoxRegistry.size();
    }

    /** Force re-download of the tutorial markdown files. */
    private static int refreshTutorials(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        BoxDefaults.refreshTutorials(FMLPaths.CONFIGDIR.get().resolve("csbox"));
        source.sendSuccess(() -> Component.translatable("commands.csgobox.tutorial.refresh.success"), false);
        return Command.SINGLE_SUCCESS;
    }

    // --- Item tool ---

    /** Any player; prints the main hand item's serialized JSON. */
    private static int showHandNbt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ItemStack item = source.getPlayerOrException().getMainHandItem();
        if (item.isEmpty()) {
            throw NBT_EMPTY.create();
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.nbt.hand.header",
                item.getHoverName().getString()), false);
        try {
            String json = BoxItemCodec.gson().toJson(BoxItemCodec.serializeItemStack(item));
            if (json.length() > MAX_NBT_CHARS) {
                source.sendSuccess(() -> copyable(Component.literal(json.substring(0, MAX_NBT_CHARS)), json), false);
                source.sendSuccess(() -> copyable(Component.translatable("commands.csgobox.nbt.hand.truncated",
                        String.valueOf(json.length())), json), false);
            } else {
                source.sendSuccess(() -> copyable(Component.literal(json), json), false);
            }
        } catch (Exception e) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.nbt.hand.error",
                    e.getMessage()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    // --- Private helpers ---

    /** Wraps a chat component so clicking it copies {@code clipboard} to the player's clipboard. */
    private static Component copyable(Component component, String clipboard) {
        return component.copy().withStyle(s -> s
                .withClickEvent(new ClickEvent.CopyToClipboard(clipboard))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.translatable("commands.csgobox.nbt.hand.click_copy"))));
    }

    private static BoxDefinition getBoxOrThrow(Identifier boxId) throws CommandSyntaxException {
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            throw BOX_NOT_FOUND.create(boxId.toString());
        }
        return def;
    }
}
