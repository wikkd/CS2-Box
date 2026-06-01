package com.reclizer.csgobox.command;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.api.box.BoxDefinition;
import com.reclizer.csgobox.api.box.BoxJsonLoader;
import com.reclizer.csgobox.api.box.BoxRegistry;
import com.reclizer.csgobox.api.box.GradeGroup;
import com.reclizer.csgobox.item.ItemCsgoBox;
import com.reclizer.csgobox.item.ModItems;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = CsgoBox.MODID)
public class CsboxCommand {

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
                .then(Commands.literal("add")
                        .then(Commands.argument("box", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    SharedSuggestionProvider.suggestResource(BoxRegistry.getIds(), builder);
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("grade", StringArgumentType.word())
                                        .suggests(CsboxCommand::gradeSuggestions)
                                        .then(Commands.literal("hand")
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> addHandItem(
                                                                StringArgumentType.getString(ctx, "box"),
                                                                StringArgumentType.getString(ctx, "grade"),
                                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                                ctx.getSource().getPlayerOrException()
                                                        )))
                                        )
                                        .then(Commands.literal("inventory")
                                                .executes(ctx -> addInventoryItems(
                                                        StringArgumentType.getString(ctx, "box"),
                                                        StringArgumentType.getString(ctx, "grade"),
                                                        ctx.getSource().getPlayerOrException()
                                                )))
                                )
                                .executes(ctx -> addBoxByName(StringArgumentType.getString(ctx, "box"), ctx.getSource()))
                        )
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.csgobox.add.usage"), false);
                            return 1;
                        }))
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
                .then(Commands.literal("give")
                        .then(Commands.argument("box", ResourceLocationArgument.id())
                                .suggests(BOX_SUGGESTIONS)
                                .executes(ctx -> giveBox(
                                        ResourceLocationArgument.getId(ctx, "box"),
                                        1,
                                        List.of(ctx.getSource().getPlayerOrException()),
                                        ctx.getSource()
                                ))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> giveBox(
                                                ResourceLocationArgument.getId(ctx, "box"),
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                List.of(ctx.getSource().getPlayerOrException()),
                                                ctx.getSource()
                                        ))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> giveBox(
                                                        ResourceLocationArgument.getId(ctx, "box"),
                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                        EntityArgument.getPlayers(ctx, "player"),
                                                        ctx.getSource()
                                                ))
                                        )
                                )
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> giveBox(
                                                ResourceLocationArgument.getId(ctx, "box"),
                                                1,
                                                EntityArgument.getPlayers(ctx, "player"),
                                                ctx.getSource()
                                        ))
                                )
                        )
                )
                .then(Commands.literal("reload")
                        .executes(CsboxCommand::reloadBoxes)
                )
        );
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.title"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.list"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.list_detail"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.info"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.add_create"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.add_item"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.add_inventory"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.set_count"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.set_weight"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.give"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.reload"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.footer"), false);
        return 1;
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
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            throw BOX_NOT_FOUND.create(boxId.toString());
        }
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
            List<ItemStack> displayItems = grade.items().stream().limit(5).collect(Collectors.toList());
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
        return 1;
    }

    private static int addBoxByName(String name, CommandSourceStack source) {
        ResourceLocation boxId = resolveBoxId(name);
        if (BoxRegistry.contains(boxId)) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.add.create.already_exists",
                    boxId.toString()), false);
            return 1;
        }
        BoxDefinition newBox = BoxDefinition.builder(boxId, name).build();
        BoxRegistry.register(newBox);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.add.create.success", boxId.toString()), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.add.create.next_steps"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.add.create.next_add", boxId.toString()), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.add.create.next_info", boxId.toString()), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.add.create.next_give", boxId.toString()), false);
        return 1;
    }

    private static int addHandItem(String boxArg, String gradeId, int count, ServerPlayer player) throws CommandSyntaxException {
        ResourceLocation boxId = resolveBoxId(boxArg);
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            throw BOX_NOT_FOUND.create(boxId.toString());
        }
        ItemStack handItem = player.getMainHandItem();
        if (handItem.isEmpty()) {
            player.sendSystemMessage(Component.translatable("commands.csgobox.add.item.empty_hand"));
            return 0;
        }
        GradeGroup targetGrade = findGrade(def, gradeId);
        if (targetGrade == null) {
            throw GRADE_NOT_FOUND.create(gradeId);
        }
        List<ItemStack> newItems = new ArrayList<>(targetGrade.items());
        newItems.add(handItem.copyWithCount(count));
        GradeGroup updatedGrade = new GradeGroup(
                targetGrade.id(), targetGrade.displayName(),
                targetGrade.color(), targetGrade.weight(), newItems
        );
        BoxDefinition updatedBox = updateGradeInBox(def, gradeId, updatedGrade);
        BoxRegistry.register(updatedBox);
        player.sendSystemMessage(Component.translatable("commands.csgobox.add.item.success",
                handItem.getItem().getName(handItem).getString(), count, boxId.toString(), gradeId));
        return 1;
    }

    private static int addInventoryItems(String boxArg, String gradeId, ServerPlayer player) throws CommandSyntaxException {
        ResourceLocation boxId = resolveBoxId(boxArg);
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            throw BOX_NOT_FOUND.create(boxId.toString());
        }
        if (player.isCreative()) {
            player.sendSystemMessage(Component.translatable("commands.csgobox.add.inventory.creative_mode"));
            return 0;
        }
        GradeGroup targetGrade = findGrade(def, gradeId);
        if (targetGrade == null) {
            throw GRADE_NOT_FOUND.create(gradeId);
        }
        List<ItemStack> existingItems = new ArrayList<>(targetGrade.items());
        List<ItemStack> newItemsToAdd = new ArrayList<>();
        int addedCount = 0;
        int skippedCount = 0;
        for (ItemStack item : player.getInventory().items) {
            if (item.isEmpty()) continue;
            boolean alreadyExists = existingItems.stream().anyMatch(existing ->
                    ItemStack.isSameItemSameComponents(existing, item));
            if (!alreadyExists) {
                newItemsToAdd.add(item.copy());
                existingItems.add(item.copy());
                addedCount++;
            } else {
                skippedCount++;
            }
        }
        if (addedCount == 0) {
            player.sendSystemMessage(Component.translatable("commands.csgobox.add.inventory.no_items"));
            return 0;
        }
        GradeGroup updatedGrade = new GradeGroup(
                targetGrade.id(),
                targetGrade.displayName(),
                targetGrade.color(),
                targetGrade.weight(),
                new ArrayList<>(existingItems)
        );
        BoxDefinition updatedBox = updateGradeInBox(def, gradeId, updatedGrade);
        BoxRegistry.register(updatedBox);
        player.sendSystemMessage(Component.translatable("commands.csgobox.add.inventory.success", addedCount, boxId.toString(), gradeId));
        if (skippedCount > 0) {
            player.sendSystemMessage(Component.translatable("commands.csgobox.add.inventory.skipped", skippedCount));
        }
        return addedCount;
    }

    private static int setItemCount(ResourceLocation boxId, String gradeId, int index, int count, CommandSourceStack source) throws CommandSyntaxException {
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            throw BOX_NOT_FOUND.create(boxId.toString());
        }
        GradeGroup targetGrade = findGrade(def, gradeId);
        if (targetGrade == null) {
            throw GRADE_NOT_FOUND.create(gradeId);
        }
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
        BoxDefinition updatedBox = updateGradeInBox(def, gradeId, updatedGrade);
        BoxRegistry.register(updatedBox);
        return 1;
    }

    private static int setGradeWeight(ResourceLocation boxId, String gradeId, int weight, CommandSourceStack source) throws CommandSyntaxException {
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            throw BOX_NOT_FOUND.create(boxId.toString());
        }
        GradeGroup targetGrade = findGrade(def, gradeId);
        if (targetGrade == null) {
            throw GRADE_NOT_FOUND.create(gradeId);
        }
        GradeGroup updatedGrade = new GradeGroup(
                targetGrade.id(),
                targetGrade.displayName(),
                targetGrade.color(),
                weight,
                targetGrade.items()
        );
        BoxDefinition updatedBox = updateGradeInBox(def, gradeId, updatedGrade);
        BoxRegistry.register(updatedBox);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.set.grade_weight.success",
                gradeId, weight, boxId.toString()), false);
        return 1;
    }

    private static int giveBox(ResourceLocation boxId, int count, Collection<ServerPlayer> targets, CommandSourceStack source) throws CommandSyntaxException {
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            throw BOX_NOT_FOUND.create(boxId.toString());
        }
        ItemStack boxStack = new ItemStack(ModItems.ITEM_CSGOBOX.get());
        ItemCsgoBox.setBoxId(boxId, boxStack);
        boxStack.setCount(Math.min(count, boxStack.getMaxStackSize()));
        for (ServerPlayer player : targets) {
            boolean added = player.getInventory().add(boxStack.copy());
            if (!added) {
                ItemEntity entity = player.drop(boxStack, false);
                if (entity != null) {
                    entity.setNoPickUpDelay();
                    entity.setTarget(player.getUUID());
                }
            }
            player.containerMenu.broadcastChanges();
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.give.success",
                def.name().getString(), String.valueOf(count), targets.iterator().next().getName().getString()), true);
        return targets.size();
    }

    private static int reloadBoxes(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        BoxRegistry.clear();
        BoxJsonLoader.loadAll();
        source.sendSuccess(() -> Component.translatable("commands.csgobox.reload.success", BoxRegistry.size()), false);
        return BoxRegistry.size();
    }

    private static ResourceLocation resolveBoxId(String boxArg) {
        if (boxArg.contains(":")) {
            return ResourceLocation.parse(boxArg);
        }
        return ResourceLocation.parse(CsgoBox.MODID + ":" + boxArg);
    }

    private static GradeGroup findGrade(BoxDefinition def, String gradeId) {
        for (GradeGroup grade : def.grades()) {
            if (grade.id().equals(gradeId)) {
                return grade;
            }
        }
        return null;
    }

    private static BoxDefinition updateGradeInBox(BoxDefinition def, String gradeId, GradeGroup updatedGrade) {
        List<GradeGroup> newGrades = new ArrayList<>();
        for (GradeGroup grade : def.grades()) {
            if (grade.id().equals(gradeId)) {
                newGrades.add(updatedGrade);
            } else {
                newGrades.add(grade);
            }
        }
        return new BoxDefinition(
                def.id(),
                def.name(),
                def.keyItem(),
                def.dropRate(),
                def.dropEntities(),
                newGrades,
                def.texture(),
                def.sound(),
                def.entityDropRates()
        );
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
