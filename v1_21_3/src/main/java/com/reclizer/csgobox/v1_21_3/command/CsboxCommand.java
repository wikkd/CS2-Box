package com.reclizer.csgobox.v1_21_3.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import com.reclizer.csgobox.v1_21_3.CsgoBox;
import com.reclizer.csgobox.v1_21_3.box.BoxDefaults;
import com.reclizer.csgobox.v1_21_3.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_3.box.BoxJsonLoader;
import com.reclizer.csgobox.v1_21_3.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_3.box.GradeGroup;
import com.reclizer.csgobox.v1_21_3.box.LoadError;
import com.reclizer.csgobox.v1_21_3.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_3.item.ModItems;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = CsgoBox.MODID)
public final class CsboxCommand {
    private CsboxCommand() {
    }

    private static final DynamicCommandExceptionType BOX_NOT_FOUND = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.csgobox.info.not_found", id)
    );
    private static final DynamicCommandExceptionType GRADE_NOT_FOUND = new DynamicCommandExceptionType(
            args -> Component.translatable("commands.csgobox.error.grade_not_found", args)
    );
    private static final DynamicCommandExceptionType ITEM_NOT_FOUND = new DynamicCommandExceptionType(
            args -> Component.translatable("commands.csgobox.set.item_not_found", args)
    );

    private static final SuggestionProvider<CommandSourceStack> BOX_SUGGESTIONS = (context, builder) -> {
        SharedSuggestionProvider.suggestResource(BoxRegistry.getIds(), builder);
        return builder.buildFuture();
    };

    private static final String SCOREBOARD_OBJECTIVE_NAME = "csbox_opened";

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("csbox")
                .requires(source -> source.hasPermission(2))
                .executes(CsboxCommand::showHelp)
                .then(Commands.literal("help").executes(CsboxCommand::showHelp))
                .then(Commands.literal("list")
                        .executes(CsboxCommand::listAllBoxes)
                        .then(Commands.argument("box", ResourceLocationArgument.id())
                                .suggests(BOX_SUGGESTIONS)
                                .executes(ctx -> listBoxDetail(ctx, ResourceLocationArgument.getId(ctx, "box")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("box", ResourceLocationArgument.id())
                                .suggests(BOX_SUGGESTIONS)
                                .executes(ctx -> showBoxInfo(ResourceLocationArgument.getId(ctx, "box"), ctx.getSource()))))
                .then(Commands.literal("set")
                        .then(Commands.argument("box", ResourceLocationArgument.id())
                                .suggests(BOX_SUGGESTIONS)
                                .then(Commands.argument("grade", StringArgumentType.word())
                                        .suggests(CsboxCommand::gradeSuggestions)
                                        .then(Commands.literal("count")
                                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                                                .executes(ctx -> setItemCount(
                                                                        ResourceLocationArgument.getId(ctx, "box"),
                                                                        StringArgumentType.getString(ctx, "grade"),
                                                                        IntegerArgumentType.getInteger(ctx, "index"),
                                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                                        ctx.getSource()
                                                                ))
                                                        )
                                                )
                                        )
                                        .then(Commands.literal("weight")
                                                .then(Commands.argument("weight", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> setGradeWeight(
                                                                ResourceLocationArgument.getId(ctx, "box"),
                                                                StringArgumentType.getString(ctx, "grade"),
                                                                IntegerArgumentType.getInteger(ctx, "weight"),
                                                                ctx.getSource()
                                                        ))
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("reload")
                        .executes(CsboxCommand::reloadBoxes)
                )
                .then(Commands.literal("tutorial")
                        .then(Commands.literal("refresh")
                                .executes(CsboxCommand::refreshTutorials))
                )
                .then(Commands.literal("errors")
                        .executes(CsboxCommand::showLoadErrors)
                )
                .then(Commands.literal("scoreboard")
                        .executes(CsboxCommand::scoreboardStatus)
                        .then(Commands.literal("on")
                                .executes(CsboxCommand::scoreboardOn))
                        .then(Commands.literal("off")
                                .executes(CsboxCommand::scoreboardOff))
                        .then(Commands.literal("list")
                                .executes(ctx -> scoreboardSetDisplay(ctx, DisplaySlot.LIST)))
                        .then(Commands.literal("sidebar")
                                .executes(ctx -> scoreboardSetDisplay(ctx, DisplaySlot.SIDEBAR)))
                        .then(Commands.literal("belowName")
                                .executes(ctx -> scoreboardSetDisplay(ctx, DisplaySlot.BELOW_NAME)))
                )
        );
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.title"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.list"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.list_detail"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.info"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.set_count"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.set_weight"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.give_vanilla"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.reload"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.scoreboard"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.footer"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int listAllBoxes(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Set<ResourceLocation> ids = BoxRegistry.getIds();
        if (ids.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.list.empty"), false);
            return 0;
        }
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
        return ids.size();
    }

    private static int listBoxDetail(CommandContext<CommandSourceStack> ctx, ResourceLocation boxId) throws CommandSyntaxException {
        return showBoxInfo(boxId, ctx.getSource());
    }

    private static int showBoxInfo(ResourceLocation boxId, CommandSourceStack source) throws CommandSyntaxException {
        BoxDefinition def = getBoxOrThrow(boxId);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.info.header",
                def.id().toString(), def.name().getString()), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.info.key",
                def.keyItem().toString()), false);
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

    private static int setItemCount(ResourceLocation boxId, String gradeId, int index, int count, CommandSourceStack source) throws CommandSyntaxException {
        BoxDefinition def = getBoxOrThrow(boxId);
        GradeGroup targetGrade = getGradeOrThrow(def, gradeId);
        int zeroBasedIndex = index - 1;
        if (zeroBasedIndex < 0 || zeroBasedIndex >= targetGrade.items().size()) {
            throw ITEM_NOT_FOUND.create(index + " (valid: 1-" + targetGrade.items().size() + ")");
        }
        List<ItemStack> updatedItems = new ArrayList<>(targetGrade.items());
        ItemStack removedItem = updatedItems.get(zeroBasedIndex);
        if (count == 0) {
            updatedItems.remove(zeroBasedIndex);
            source.sendSuccess(() -> Component.translatable("commands.csgobox.set.item_count.removed",
                    removedItem.getItem().getName(removedItem).getString(), boxId.toString(), gradeId), false);
        } else {
            ItemStack existingItem = updatedItems.get(zeroBasedIndex);
            ItemStack updatedItem = existingItem.copyWithCount(count);
            updatedItems.set(zeroBasedIndex, updatedItem);
            source.sendSuccess(() -> Component.translatable("commands.csgobox.set.item_count.success",
                    removedItem.getItem().getName(removedItem).getString(), count, boxId.toString(), gradeId), false);
        }
        GradeGroup updatedGrade = new GradeGroup(
                targetGrade.id(),
                targetGrade.displayName(),
                targetGrade.color(),
                targetGrade.weight(),
                updatedItems
        );
        BoxDefinition updatedBox = def.withUpdatedGrade(gradeId, updatedGrade);
        BoxRegistry.register(updatedBox);
        BoxJsonLoader.saveToFile(updatedBox);
        return Command.SINGLE_SUCCESS;
    }

    private static int setGradeWeight(ResourceLocation boxId, String gradeId, int weight, CommandSourceStack source) throws CommandSyntaxException {
        BoxDefinition def = getBoxOrThrow(boxId);
        GradeGroup targetGrade = getGradeOrThrow(def, gradeId);
        GradeGroup updatedGrade = new GradeGroup(
                targetGrade.id(),
                targetGrade.displayName(),
                targetGrade.color(),
                weight,
                targetGrade.items()
        );
        BoxDefinition updatedBox = def.withUpdatedGrade(gradeId, updatedGrade);
        BoxRegistry.register(updatedBox);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.set.grade_weight.success",
                gradeId, weight, boxId.toString()), false);
        BoxJsonLoader.saveToFile(updatedBox);
        return Command.SINGLE_SUCCESS;
    }

    private static int reloadBoxes(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        BoxJsonLoader.reloadPreserving();
        source.sendSuccess(() -> Component.translatable("commands.csgobox.reload.success", BoxRegistry.size()), false);
        return BoxRegistry.size();
    }

    /** Force re-download of the tutorial markdown files. */
    private static int refreshTutorials(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        BoxDefaults.refreshTutorials(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("csbox"));
        source.sendSuccess(() -> Component.translatable("commands.csgobox.tutorial.refresh.success"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showLoadErrors(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        java.util.List<LoadError> errors = BoxJsonLoader.getLastLoadErrors();

        if (errors.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[CS2-Box] 当前无箱子加载错误")
                    .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN)), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "[CS2-Box] 当前 " + errors.size() + " 个加载错误:")
                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW)), false);
        for (LoadError err : errors) {
            source.sendSuccess(err::toChatMessage, false);
        }
        return errors.size();
    }

    private static int scoreboardStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.literal("[CS2-Box] Server context unavailable"));
            return 0;
        }
        Scoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(SCOREBOARD_OBJECTIVE_NAME);
        if (obj == null) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.scoreboard.status_off"), false);
            return 0;
        }
        String slot = currentDisplaySlotName(sb, obj);
        source.sendSuccess(() -> Component.translatable(
                "commands.csgobox.scoreboard.status_on", slot), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int scoreboardOn(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.literal("[CS2-Box] Server context unavailable"));
            return 0;
        }
        Scoreboard sb = server.getScoreboard();
        if (sb.getObjective(SCOREBOARD_OBJECTIVE_NAME) != null) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.scoreboard.already_on"), false);
            return 0;
        }
        Objective obj = sb.addObjective(
                SCOREBOARD_OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                Component.translatable("commands.csgobox.scoreboard.objective_display_name"),
                ObjectiveCriteria.RenderType.INTEGER,
                true,
                null
        );
        if (sb instanceof ServerScoreboard ssb) {
            ssb.setDisplayObjective(DisplaySlot.LIST, obj);
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            syncOpenedBoxesToScoreboard(p);
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.scoreboard.on_success"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int scoreboardOff(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.literal("[CS2-Box] Server context unavailable"));
            return 0;
        }
        Scoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(SCOREBOARD_OBJECTIVE_NAME);
        if (obj == null) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.scoreboard.off_not_found"), false);
            return 0;
        }
        sb.removeObjective(obj);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.scoreboard.off_success"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int scoreboardSetDisplay(CommandContext<CommandSourceStack> ctx, DisplaySlot slot) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.literal("[CS2-Box] Server context unavailable"));
            return 0;
        }
        Scoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(SCOREBOARD_OBJECTIVE_NAME);
        if (obj == null) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.scoreboard.display_not_set"), false);
            return 0;
        }
        if (sb instanceof ServerScoreboard ssb) {
            ssb.setDisplayObjective(slot, obj);
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.csgobox.scoreboard.display_changed", slot.getSerializedName()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static String currentDisplaySlotName(Scoreboard sb, Objective obj) {
        if (sb.getDisplayObjective(DisplaySlot.LIST) == obj) return "list";
        if (sb.getDisplayObjective(DisplaySlot.SIDEBAR) == obj) return "sidebar";
        if (sb.getDisplayObjective(DisplaySlot.BELOW_NAME) == obj) return "belowName";
        return "none";
    }

    public static void syncOpenedBoxesToScoreboard(ServerPlayer player) {
        Stat<ResourceLocation> stat = CsgoBox.OPENED_BOXES_STAT;
        if (stat == null) return;
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        Scoreboard sb = server.getScoreboard();
        Objective objective = sb.getObjective(SCOREBOARD_OBJECTIVE_NAME);
        if (objective == null) return;
        int value = player.getStats().getValue(stat);
        ScoreHolder holder = ScoreHolder.forNameOnly(player.getScoreboardName());
        ScoreAccess score = sb.getOrCreatePlayerScore(holder, objective);
        score.set(value);
    }

    private static ResourceLocation resolveBoxId(String boxArg) {
        if (boxArg.contains(":")) {
            return ResourceLocation.parse(boxArg);
        }
        return ResourceLocation.parse(CsgoBox.MODID + ":" + boxArg);
    }

    private static BoxDefinition getBoxOrThrow(ResourceLocation boxId) throws CommandSyntaxException {
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            throw BOX_NOT_FOUND.create(boxId.toString());
        }
        return def;
    }

    private static GradeGroup getGradeOrThrow(BoxDefinition def, String gradeId) throws CommandSyntaxException {
        return def.findGrade(gradeId).orElseThrow(() -> GRADE_NOT_FOUND.create(gradeId));
    }

    private static CompletableFuture<Suggestions> gradeSuggestions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            String boxStr = ctx.getArgument("box", String.class);
            ResourceLocation boxId = resolveBoxId(boxStr);
            BoxDefinition def = BoxRegistry.get(boxId);
            if (def != null) {
                for (GradeGroup grade : def.grades()) {
                    builder.suggest(grade.id());
                }
            }
        } catch (Exception e) {
            CsgoBox.LOGGER.warn("Error in grade suggestions: {}", e.getMessage());
        }
        return builder.buildFuture();
    }
}
