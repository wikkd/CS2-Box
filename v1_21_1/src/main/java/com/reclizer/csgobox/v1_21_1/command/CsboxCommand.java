package com.reclizer.csgobox.v1_21_1.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxItemCodec;
import com.reclizer.csgobox.v1_21_1.box.BoxJsonLoader;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.box.GradeGroup;
import com.reclizer.csgobox.v1_21_1.box.LoadError;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ModItems;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
    private static final int TACZ_JSON_CHARS = 20000;
    private static final int TACZ_PAGE_SIZE = 24;

    private static final SuggestionProvider<CommandSourceStack> BOX_SUGGESTIONS = (context, builder) -> {
        SharedSuggestionProvider.suggestResource(BoxRegistry.getIds(), builder);
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> TACZ_SLOT_SUGGESTIONS = (context, builder) -> {
        for (String slot : TaczList.SLOT_NAMES) {
            builder.suggest(slot);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> TACZ_AMMO_SUGGESTIONS = (context, builder) -> {
        for (String id : TaczList.collectAmmoIds()) {
            builder.suggest(id);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> TACZ_GUN_SUGGESTIONS = (context, builder) -> {
        for (String id : TaczList.collectGunIds()) {
            builder.suggest(id);
        }
        return builder.buildFuture();
    };

    // --- Command tree ---

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> csbox = Commands.literal("csbox")
                .executes(CsboxCommand::showHelp)
                .then(Commands.literal("help")
                        .executes(CsboxCommand::showHelp))
                .then(Commands.literal("info")
                        .requires(CsboxCommand::isGameMaster)
                        .executes(CsboxCommand::showInfoOverview)
                        .then(Commands.argument("box", ResourceLocationArgument.id())
                                .suggests(BOX_SUGGESTIONS)
                                .executes(ctx -> showBoxInfo(ctx, ResourceLocationArgument.getId(ctx, "box"))))
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
                        .then(Commands.argument("box", ResourceLocationArgument.id())
                                .suggests(BOX_SUGGESTIONS)
                                .executes(ctx -> validateBox(ctx, ResourceLocationArgument.getId(ctx, "box")))))
                .then(Commands.literal("give")
                        .requires(CsboxCommand::isGameMaster)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("box", ResourceLocationArgument.id())
                                        .suggests(BOX_SUGGESTIONS)
                                        .executes(ctx -> giveBox(ctx, 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 6400))
                                                .executes(ctx -> giveBox(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(Commands.literal("nbt")
                        .then(Commands.literal("hand")
                                .executes(CsboxCommand::showHandNbt)));
        // TACZ registry browser: only exists when TACZ is installed.
        if (ModList.get() != null && ModList.get().isLoaded("tacz")) {
            csbox = csbox.then(taczSubtree());
        }
        dispatcher.register(csbox);
    }

    // --- Query handlers ---

    private static boolean isGameMaster(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    /**
     * v2.0.1: hands out a box definition. Registry entries are a fixed set now
     * (a box is data, not an item id), so this is the supported way to obtain
     * a box — it picks the fixed item when the id ships with the mod and the
     * generic {@code csgo_box} / {@code terminal} item otherwise, then stamps
     * {@code csgobox:box_id} into the stack's data components.
     */
    private static int giveBox(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation boxId = ResourceLocationArgument.getId(ctx, "box");
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            throw BOX_NOT_FOUND.create(boxId.toString());
        }
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

    /** Help is visible to everyone; carries clickable links to the web editor, the local tutorial folder and the online tutorial mirror. */
    private static int showHelp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.title"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.info"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.reload"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.give_vanilla"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.give"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.nbt"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.tacz"), false);
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
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, EditorCommand.EDITOR_URL))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.csgobox.editor.hover"))));
    }

    /** Opens the local config/csbox folder, where the bundled tutorials are written. */
    private static Component tutorialFolderLink() {
        return Component.translatable("commands.csgobox.help.tutorial.folder")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, TUTORIAL_DIR))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.csgobox.help.tutorial.folder_hover"))));
    }

    /** Opens the online tutorial mirror in the player's browser. */
    private static Component tutorialOnlineLink() {
        return Component.translatable("commands.csgobox.help.tutorial.online")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, TUTORIAL_URL))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.csgobox.help.tutorial.online_hover"))));
    }


    /** Requires permission level 2; lists all boxes then any load errors. */
    private static int showInfoOverview(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Set<ResourceLocation> ids = BoxRegistry.getIds();
        if (ids.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.list.empty"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.list.header",
                    String.valueOf(ids.size())), false);
            for (ResourceLocation id : ids) {
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
    private static void showNamespaceSummary(CommandSourceStack source, Set<ResourceLocation> ids) {
        Map<String, Integer> byNamespace = new TreeMap<>();
        for (ResourceLocation id : ids) {
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
    private static int showBoxInfo(CommandContext<CommandSourceStack> ctx, ResourceLocation boxId) throws CommandSyntaxException {
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
                    .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN)), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.errors.header",
                String.valueOf(errors.size()))
                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW)), false);
        for (LoadError err : errors) {
            source.sendSuccess(err::toChatMessage, false);
        }
        return errors.size();
    }

    // --- Admin handlers ---

    /** v2.0.1 dry-run: validates every config/csbox JSON without registering anything. */
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

    /** v2.0.1 dry-run: validates one box file (by box id → config/csbox/<id>.json). */
    private static int validateBox(CommandContext<CommandSourceStack> ctx, ResourceLocation boxId)
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

    private static int reloadBoxes(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        BoxJsonLoader.reloadPreserving();
        com.reclizer.csgobox.v1_21_1.CsgoBox.broadcastBoxDefinitions();
        source.sendSuccess(() -> Component.translatable("commands.csgobox.reload.success", BoxRegistry.size()), false);
        return BoxRegistry.size();
    }

    /** Force a refresh of the JAR-bundled tutorial markdown files (offline re-copy). */
    private static int refreshTutorials(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        BoxDefaults.refreshTutorials(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("csbox"));
        source.sendSuccess(() -> Component.translatable("commands.csgobox.tutorial.refresh.success"), false);
        return Command.SINGLE_SUCCESS;
    }

    // --- TACZ registry browser (/csbox tacz list) ---

    private record TaczQuery(TaczList.Kind kind, String ns, String filterKey, String filterValue,
                             int page, boolean json) {
    }

    private static LiteralArgumentBuilder<CommandSourceStack> taczSubtree() {
        return Commands.literal("tacz")
                .requires(CsboxCommand::isGameMaster)
                .then(Commands.literal("list")
                        .executes(CsboxCommand::taczOverview)
                        .then(taczCategoryNode(TaczList.Kind.GUNS))
                        .then(taczCategoryNode(TaczList.Kind.AMMO))
                        .then(taczCategoryNode(TaczList.Kind.ATTACHMENTS)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> taczCategoryNode(TaczList.Kind kind) {
        String category = taczCategoryLiteral(kind);
        LiteralArgumentBuilder<CommandSourceStack> flag = switch (kind) {
            case GUNS -> Commands.literal("--ammo")
                    .then(buildTaczFilterArg(Commands.argument("ammo", ResourceLocationArgument.id())
                            .suggests(TACZ_AMMO_SUGGESTIONS), kind, "ammo", true));
            case AMMO -> Commands.literal("--gun")
                    .then(buildTaczFilterArg(Commands.argument("gun", ResourceLocationArgument.id())
                            .suggests(TACZ_GUN_SUGGESTIONS), kind, "gun", true));
            case ATTACHMENTS -> Commands.literal("--slot")
                    .then(buildTaczFilterArg(Commands.argument("slot", StringArgumentType.word())
                            .suggests(TACZ_SLOT_SUGGESTIONS), kind, "slot", false));
        };
        return Commands.literal(category)
                .executes(ctx -> taczRun(ctx.getSource(),
                        new TaczQuery(kind, null, null, null, 1, false)))
                .then(Commands.literal("json")
                        .executes(ctx -> taczRun(ctx.getSource(),
                                new TaczQuery(kind, null, null, null, 1, true)))
                        .then(Commands.argument("ns", StringArgumentType.word())
                                .executes(ctx -> taczRun(ctx.getSource(),
                                        new TaczQuery(kind, getWord(ctx, "ns"), null, null, 1, true)))))
                .then(Commands.argument("ns", StringArgumentType.word())
                        .executes(ctx -> taczRun(ctx.getSource(),
                                new TaczQuery(kind, getWord(ctx, "ns"), null, null, 1, false))))
                .then(Commands.literal("page")
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> taczRun(ctx.getSource(),
                                        new TaczQuery(kind, null, null, null,
                                                IntegerArgumentType.getInteger(ctx, "page"), false)))))
                .then(flag);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildTaczFilterArg(
            ArgumentBuilder<CommandSourceStack, ?> arg, TaczList.Kind kind, String key, boolean resource) {
        return arg.executes(ctx -> taczRun(ctx.getSource(),
                        new TaczQuery(kind, null, key, filterValue(ctx, key, resource), 1, false)))
                .then(Commands.literal("json")
                        .executes(ctx -> taczRun(ctx.getSource(),
                                new TaczQuery(kind, null, key, filterValue(ctx, key, resource), 1, true)))
                        .then(Commands.argument("ns", StringArgumentType.word())
                                .executes(ctx -> taczRun(ctx.getSource(),
                                        new TaczQuery(kind, getWord(ctx, "ns"), key,
                                                filterValue(ctx, key, resource), 1, true)))))
                .then(Commands.argument("ns", StringArgumentType.word())
                        .executes(ctx -> taczRun(ctx.getSource(),
                                new TaczQuery(kind, getWord(ctx, "ns"), key,
                                        filterValue(ctx, key, resource), 1, false))))
                .then(Commands.literal("page")
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> taczRun(ctx.getSource(),
                                        new TaczQuery(kind, null, key, filterValue(ctx, key, resource),
                                                IntegerArgumentType.getInteger(ctx, "page"), false)))));
    }

    private static String filterValue(CommandContext<CommandSourceStack> ctx, String key, boolean resource) {
        return resource
                ? ResourceLocationArgument.getId(ctx, key).toString()
                : StringArgumentType.getString(ctx, key);
    }

    private static String getWord(CommandContext<CommandSourceStack> ctx, String name) {
        return StringArgumentType.getString(ctx, name);
    }

    private static String taczCategoryLiteral(TaczList.Kind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    private static int taczOverview(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!TaczList.isTaczLoaded()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.tacz.list.not_loaded"), false);
            return 0;
        }
        TaczList.TaczData data = TaczList.collect();
        source.sendSuccess(() -> Component.translatable("commands.csgobox.tacz.list.header"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.tacz.list.stats",
                String.valueOf(data.gunCount()), String.valueOf(data.ammoCount()),
                String.valueOf(data.attachmentCount()), String.valueOf(data.packCount())), false);
        taczPreview(source, TaczList.Kind.GUNS, data.guns(), 3);
        taczPreview(source, TaczList.Kind.AMMO, data.ammo(), 3);
        taczPreview(source, TaczList.Kind.ATTACHMENTS, data.attachments(), 3);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.tacz.list.usage"), false);
        return 1;
    }

    private static void taczPreview(CommandSourceStack source, TaczList.Kind kind,
                                    List<TaczList.Row> rows, int max) {
        if (rows.isEmpty()) {
            return;
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.tacz.list.preview_header",
                taczKindLabel(kind), String.valueOf(rows.size())), false);
        int shown = Math.min(rows.size(), max);
        for (int i = 0; i < shown; i++) {
            final TaczList.Row row = rows.get(i);
            source.sendSuccess(() -> taczEntryLine(kind, row), false);
        }
    }

    private static int taczRun(CommandSourceStack source, TaczQuery q) {
        if (!TaczList.isTaczLoaded()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.tacz.list.not_loaded"), false);
            return 0;
        }
        TaczList.TaczData data = TaczList.collect();
        List<TaczList.Row> rows = taczRows(data, q.kind());
        rows = TaczList.filterNs(rows, q.ns());
        if (q.filterKey() != null) {
            String value = q.filterValue();
            if (q.kind() == TaczList.Kind.ATTACHMENTS) {
                String upper = value.trim().toUpperCase(Locale.ROOT);
                if (!TaczList.SLOT_NAMES.contains(upper)) {
                    source.sendSuccess(() -> Component.translatable(
                            "commands.csgobox.tacz.list.filter.invalid_slot", upper), false);
                    return 0;
                }
                rows = TaczList.filterField(rows, "slot", upper);
            } else if (q.kind() == TaczList.Kind.GUNS) {
                rows = TaczList.filterField(rows, "ammo", value);
            } else {
                rows = TaczList.filterContains(rows, "guns", value);
            }
        }
        if (rows.isEmpty()) {
            final TaczList.Kind kindFinal = q.kind();
            source.sendSuccess(() -> Component.translatable(
                    "commands.csgobox.tacz.list.empty", taczKindLabel(kindFinal)), false);
            return 0;
        }
        if (q.json()) {
            String json = TaczList.toJson(rows);
            if (json.length() > TACZ_JSON_CHARS) {
                String truncated = json.substring(0, TACZ_JSON_CHARS);
                source.sendSuccess(() -> copyable(Component.literal(truncated), json), false);
                source.sendSuccess(() -> copyable(Component.translatable(
                        "commands.csgobox.tacz.list.json.truncated", String.valueOf(json.length())), json), false);
            } else {
                source.sendSuccess(() -> copyable(Component.literal(json), json), false);
            }
            source.sendSuccess(() -> copyable(Component.translatable(
                                    "commands.csgobox.tacz.list.json.copy")
                            .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.UNDERLINE),
                    json), false);
            return rows.size();
        }
        int totalPages = TaczList.pageCount(rows.size(), TACZ_PAGE_SIZE);
        int page = TaczList.clampPage(rows.size(), q.page(), TACZ_PAGE_SIZE);
        List<TaczList.Row> pageRows = TaczList.pageRows(rows, page, TACZ_PAGE_SIZE);
        final TaczList.Kind kindFinal = q.kind();
        final int matchCount = rows.size();
        source.sendSuccess(() -> Component.translatable("commands.csgobox.tacz.list.category.header",
                taczKindLabel(kindFinal), String.valueOf(matchCount),
                String.valueOf(page), String.valueOf(totalPages)), false);
        for (TaczList.Row row : pageRows) {
            final TaczList.Row rowFinal = row;
            source.sendSuccess(() -> taczEntryLine(kindFinal, rowFinal), false);
        }
        if (totalPages > 1) {
            final int next = page >= totalPages ? 1 : page + 1;
            source.sendSuccess(() -> Component.translatable("commands.csgobox.tacz.list.next",
                    taczCategoryLiteral(kindFinal), String.valueOf(next)), false);
        }
        return rows.size();
    }

    private static List<TaczList.Row> taczRows(TaczList.TaczData data, TaczList.Kind kind) {
        return switch (kind) {
            case GUNS -> data.guns();
            case AMMO -> data.ammo();
            case ATTACHMENTS -> data.attachments();
        };
    }

    private static Component taczKindLabel(TaczList.Kind kind) {
        return Component.translatable("commands.csgobox.tacz.list.category."
                + taczCategoryLiteral(kind));
    }

    private static Component taczEntryLine(TaczList.Kind kind, TaczList.Row row) {
        return switch (kind) {
            case GUNS -> Component.translatable("commands.csgobox.tacz.list.entry.gun",
                    row.id(), row.fields().getOrDefault("type", ""),
                    row.fields().getOrDefault("ammo", ""),
                    row.fields().getOrDefault("magazine", "0"));
            case AMMO -> Component.translatable("commands.csgobox.tacz.list.entry.ammo",
                    row.id(), row.fields().getOrDefault("stack", "0"),
                    row.fields().getOrDefault("guns", ""));
            case ATTACHMENTS -> Component.translatable("commands.csgobox.tacz.list.entry.attachment",
                    row.id(), row.fields().getOrDefault("slot", ""));
        };
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
                            .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.UNDERLINE),
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
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, clipboard))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("commands.csgobox.nbt.hand.click_copy"))));
    }

    private static BoxDefinition getBoxOrThrow(ResourceLocation boxId) throws CommandSyntaxException {
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            throw BOX_NOT_FOUND.create(boxId.toString());
        }
        return def;
    }
}
