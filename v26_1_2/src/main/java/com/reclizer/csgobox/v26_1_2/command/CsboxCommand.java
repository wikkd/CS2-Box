package com.reclizer.csgobox.v26_1_2.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_1_2.box.BoxItemCodec;
import com.reclizer.csgobox.v26_1_2.box.BoxJsonLoader;
import com.reclizer.csgobox.v26_1_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_1_2.box.GradeGroup;
import com.reclizer.csgobox.v26_1_2.box.LoadError;
import com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_1_2.item.ModItems;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@EventBusSubscriber(modid = CsgoBox.MODID)
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
                .then(Commands.literal("help")
                        .executes(CsboxCommand::showHelp))
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
                .then(Commands.literal("validate")
                        .requires(CsboxCommand::isGameMaster)
                        .executes(CsboxCommand::validateAll)
                        .then(Commands.argument("box", IdentifierArgument.id())
                                .suggests(BOX_SUGGESTIONS)
                                .executes(ctx -> validateBox(ctx, IdentifierArgument.getId(ctx, "box")))))
                .then(Commands.literal("give")
                        .requires(CsboxCommand::isGameMaster)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("box", IdentifierArgument.id())
                                        .suggests(BOX_SUGGESTIONS)
                                        .executes(ctx -> giveBox(ctx, 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 6400))
                                                .executes(ctx -> giveBox(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(Commands.literal("nbt")
                        .then(Commands.literal("hand")
                                .executes(CsboxCommand::showHandNbt)))
        );
    }

    // --- Query handlers ---

    private static boolean isGameMaster(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** Help is visible to everyone; carries clickable links to the web editor, the local tutorial folder and the online tutorial mirror. */
    private static int showHelp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.title"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.info"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.reload"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.give_vanilla"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.give"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.nbt"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.editor")
                .append(editorLink()), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.tutorial")
                .append(tutorialFolderLink()).append("  ").append(tutorialOnlineLink()), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.footer"), false);
        return Command.SINGLE_SUCCESS;
    }
    /** Folder opened by /csbox help (path is relative to the client game directory). */
    private static final String TUTORIAL_DIR = "config/csbox";
    /** Online tutorial mirror, kept in sync with docs/tutorials/ in the repo. */
    private static final String TUTORIAL_URL = "https://gitee.com/hou-xiangling/CS2-Box/tree/main/docs/tutorials";

    /** Web editor link shown by /csbox help — same URL as the /csbox editor subcommand. */
    private static Component editorLink() {
        return Component.literal(EditorCommand.EDITOR_URL)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(EditorCommand.EDITOR_URL)))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("commands.csgobox.editor.hover"))));
    }

    /** Opens the local config/csbox folder, where the bundled tutorials are written. */
    private static Component tutorialFolderLink() {
        return Component.translatable("commands.csgobox.help.tutorial.folder")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.OpenFile(TUTORIAL_DIR))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("commands.csgobox.help.tutorial.folder_hover"))));
    }

    /** Opens the online tutorial mirror in the player's browser. */
    private static Component tutorialOnlineLink() {
        return Component.translatable("commands.csgobox.help.tutorial.online")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(TUTORIAL_URL)))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("commands.csgobox.help.tutorial.online_hover"))));
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
        showNamespaceSummary(source, ids);
        return showLoadErrors(source);
    }

    /**
     * 来源模组统计：把所有箱子的全部档位物品按注册表命名空间分组计数，帮助
     * 整合包作者一眼看出「这套箱子开得出哪些模组的货」（联动数据包核对用）。
     */
    private static void showNamespaceSummary(CommandSourceStack source, Set<Identifier> ids) {
        Map<String, Integer> byNamespace = new TreeMap<>();
        for (Identifier id : ids) {
            BoxDefinition def = BoxRegistry.get(id);
            if (def == null) {
                continue;
            }
            for (GradeGroup grade : def.grades()) {
                for (ItemStack item : grade.items()) {
                    if (item.isEmpty()) {
                        continue;
                    }
                    String ns = BuiltInRegistries.ITEM.getKey(item.getItem()).getNamespace();
                    byNamespace.merge(ns, 1, Integer::sum);
                }
            }
        }
        if (byNamespace.isEmpty()) {
            return;
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.list.mod_summary_header"), false);
        byNamespace.forEach((ns, count) ->
                source.sendSuccess(() -> Component.translatable("commands.csgobox.list.mod_summary_entry",
                        ns, String.valueOf(count)), false));
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
            boolean weighted = grade.positiveItemWeightSum() != grade.items().size();
            source.sendSuccess(() -> Component.translatable("commands.csgobox.info.grade_entry",
                    grade.id(), String.valueOf(grade.weight()), String.valueOf(grade.items().size())), false);
            List<ItemStack> displayItems = grade.items().stream().limit(5).toList();
            for (int j = 0; j < displayItems.size(); j++) {
                final int itemIndex = j;
                ItemStack item = displayItems.get(j);
                if (weighted) {
                    source.sendSuccess(() -> Component.translatable("commands.csgobox.info.item_entry_weighted",
                            String.valueOf(itemIndex + 1), item.getHoverName().getString(),
                            String.valueOf(grade.itemWeightAt(itemIndex))), false);
                } else {
                    source.sendSuccess(() -> Component.translatable("commands.csgobox.info.item_entry",
                            String.valueOf(itemIndex + 1), item.getHoverName().getString(), String.valueOf(item.getCount())), false);
                }
            }
            if (grade.items().size() > 5) {
                source.sendSuccess(() -> Component.translatable("commands.csgobox.info.items_more",
                        String.valueOf(grade.items().size() - 5)), false);
            }
        }
        // v2.0.1 config flags.
        if (!def.enabled()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.info.flag_disabled"), false);
        }
        for (String req : def.requires()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.info.flag_requires", req), false);
        }
        def.icon().ifPresent(icon -> source.sendSuccess(
                () -> Component.translatable("commands.csgobox.info.flag_icon", icon), false));
        if (def.discount() > 0F) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.info.flag_discount",
                    String.format("%.0f", def.discount() * 100)), false);
        }
        if (def.stock() >= 0) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.info.flag_stock",
                    String.valueOf(def.stock()), String.valueOf(def.restockMinutes())), false);
        }
        if (def.maxPerPlayer() >= 0) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.info.flag_max_per_player",
                    String.valueOf(def.maxPerPlayer())), false);
        }
        if (def.cooldownSeconds() > 0) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.info.flag_cooldown",
                    String.valueOf(def.cooldownSeconds())), false);
        }
        if (!def.permission().isBlank()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.info.flag_permission",
                    def.permission()), false);
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
        com.reclizer.csgobox.v26_1_2.CsgoBox.broadcastBoxDefinitions();
        source.sendSuccess(() -> Component.translatable("commands.csgobox.reload.success", BoxRegistry.size()), false);
        return BoxRegistry.size();
    }

    // --- v2.0.1 dry-run validation ---

    /** Validates every config/csbox JSON without registering anything. */
    private static int validateAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        java.nio.file.Path dir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("csbox");
        int ok = 0;
        int bad = 0;
        try (var stream = java.nio.file.Files.newDirectoryStream(dir, "*.json")) {
            for (java.nio.file.Path p : stream) {
                String name = p.getFileName().toString();
                if (name.startsWith("_")) continue;
                BoxJsonLoader.ValidateResult r = BoxJsonLoader.validateFile(p);
                if (r.ok()) {
                    ok++;
                } else {
                    bad++;
                    source.sendSuccess(() -> Component.translatable("commands.csgobox.validate.fail",
                            name), false);
                    for (LoadError err : r.errors()) {
                        source.sendSuccess(err::toChatMessage, false);
                    }
                }
            }
        } catch (java.io.IOException e) {
            String msg = e.getMessage();
            source.sendSuccess(() -> Component.translatable("commands.csgobox.validate.dir_error",
                    msg), false);
            return 0;
        }
        // v2.0.1+: the central price table (config/csbox/_prices.json) is part
        // of the validate surface; a malformed table is reported like a box.
        BoxJsonLoader.ValidateResult priceTable = BoxJsonLoader.validatePriceTable();
        if (!priceTable.ok()) {
            bad++;
            source.sendSuccess(() -> Component.translatable("commands.csgobox.validate.fail",
                    "_prices"), false);
            for (LoadError err : priceTable.errors()) {
                source.sendSuccess(err::toChatMessage, false);
            }
        }
        final int okFinal = ok;
        final int badFinal = bad;
        source.sendSuccess(() -> Component.translatable("commands.csgobox.validate.summary",
                String.valueOf(okFinal), String.valueOf(badFinal)), false);
        return bad == 0 ? ok : -bad;
    }

    /** Validates one box file (by box id → {@code config/csbox/<id>.json}). */
    private static int validateBox(CommandContext<CommandSourceStack> ctx, Identifier boxId)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        java.nio.file.Path dir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("csbox");
        java.nio.file.Path file = dir.resolve(boxId.getPath() + ".json").normalize();
        if (!file.startsWith(dir.normalize()) || !java.nio.file.Files.exists(file)) {
            throw new SimpleCommandExceptionType(
                    Component.translatable("commands.csgobox.validate.not_found", boxId.getPath())).create();
        }
        BoxJsonLoader.ValidateResult r = BoxJsonLoader.validateFile(file);
        if (r.ok()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.validate.ok",
                    boxId.getPath()), false);
            for (LoadError err : r.errors()) {
                source.sendSuccess(err::toChatMessage, false);
            }
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.validate.fail",
                boxId.getPath()), false);
        for (LoadError err : r.errors()) {
            source.sendSuccess(err::toChatMessage, false);
        }
        return 0;
    }

    /**
     * v2.0.1: hands out a box definition. Registry entries are a fixed set now
     * (a box is data, not an item id), so this is the supported way to obtain
     * a box — it picks the fixed item when the id ships with the mod and the
     * generic {@code csgo_box} / {@code terminal} item otherwise, then stamps
     * {@code csgobox:box_id} onto the stack.
     */
    private static int giveBox(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Identifier boxId = IdentifierArgument.getId(ctx, "box");
        BoxDefinition def = getBoxOrThrow(boxId);
        Item item = ModItems.itemForBox(boxId, def.isTerminal());
        ItemStack stack = new ItemStack(item, count);
        ItemCsgoBox.setBoxId(boxId, stack);
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : players) {
            ItemStack give = stack.copy();
            if (!player.getInventory().add(give)) {
                player.drop(give, false);
            }
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.give.success",
                String.valueOf(count), boxId.toString(), String.valueOf(players.size())), true);
        return players.size();
    }

    /** Force a refresh of the JAR-bundled tutorial markdown files (offline re-copy). */
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
            source.sendSuccess(() -> copyable(
                    Component.translatable("commands.csgobox.nbt.hand.copy_button")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE),
                    json), false);
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
