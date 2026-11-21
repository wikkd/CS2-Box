package com.reclizer.csgobox.fabric_1_21_1.command;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.PriceTable;
import com.reclizer.csgobox.fabric_1_21_1.CsgoBox;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxItemCodec;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxJsonLoader;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.fabric_1_21_1.box.GradeGroup;
import com.reclizer.csgobox.fabric_1_21_1.box.LoadError;
import com.reclizer.csgobox.fabric_1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.fabric_1_21_1.item.ModItems;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
    private static final SimpleCommandExceptionType NBT_LOOK_NO_TARGET = new SimpleCommandExceptionType(
            Component.translatable("commands.csgobox.nbt.look.no_target")
    );
    private static final SimpleCommandExceptionType NBT_LOOK_NO_EGG = new SimpleCommandExceptionType(
            Component.translatable("commands.csgobox.nbt.look.no_egg")
    );
    private static final SimpleCommandExceptionType NBT_LOOK_NO_ITEM = new SimpleCommandExceptionType(
            Component.translatable("commands.csgobox.nbt.look.no_item")
    );

    // --- Constants & suggestions ---

    private static final int MAX_NBT_CHARS = 20000;

    /** /csbox nbt look ray range — shared by the block pick and the entity
     *  ray (lang: look.no_target promises "within 6 blocks"). */
    private static final double LOOK_RANGE = 6.0D;

    private static final SuggestionProvider<CommandSourceStack> BOX_SUGGESTIONS = (context, builder) -> {
        SharedSuggestionProvider.suggestResource(BoxRegistry.getIds(), builder);
        return builder.buildFuture();
    };

    // --- Command tree ---

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
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
                        .executes(CsboxCommand::reloadBoxes))
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
                .then(Commands.literal("upgrade")
                        .executes(CsboxCommand::upgradeBox))
                .then(Commands.literal("nbt")
                        .then(Commands.literal("hand")
                                .executes(CsboxCommand::showHandNbt))
                        .then(Commands.literal("look")
                                .executes(CsboxCommand::showLookNbt)));
        dispatcher.register(csbox);
    }


    /**
     * v2.x upgrade path: the HELD box must declare an "upgrade" rule
     * (target + consume); consuming {@code consume} copies of the held box
     * from the inventory grants one target box. All players (no OP).
     */
    private static int upgradeBox(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        java.util.Optional<BoxDefinition> defOpt = ItemCsgoBox.getDefinition(held);
        if (defOpt.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.upgrade.need_box"), false);
            return 0;
        }
        BoxDefinition def = defOpt.get();
        var rule = def.upgradeRule();
        if (rule.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.upgrade.no_rule"), false);
            return 0;
        }
        String targetId = rule.get().target();
        int consume = rule.get().consume();
        BoxDefinition targetDef = BoxRegistry.get(ResourceLocation.parse(targetId));
        if (targetDef == null) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.upgrade.target_missing", targetId), false);
            return 0;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative) {
            // Count consumable copies of the HELD box's own type; the held stack itself does not count.
            int available = 0;
            for (var stack : player.getInventory().items) {
                if (stack != null && !stack.isEmpty() && stack != held
                        && (stack.getItem() instanceof ItemCsgoBox && def.id().equals(ItemCsgoBox.getBoxId(stack)))) {
                    available += stack.getCount();
                }
            }
            if (available < consume) {
                int finalAvailable = available;
                source.sendSuccess(() -> Component.translatable("commands.csgobox.upgrade.need_copies", consume, finalAvailable), false);
                return 0;
            }
            int remaining = consume;
            for (var stack : player.getInventory().items) {
                if (remaining <= 0) break;
                if (stack != null && !stack.isEmpty() && stack != held
                        && (stack.getItem() instanceof ItemCsgoBox && def.id().equals(ItemCsgoBox.getBoxId(stack)))) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                }
            }
        }
        Item item = ModItems.itemForBox(targetDef.id(), targetDef.isTerminal());
        ItemStack grant = new ItemStack(item, 1);
        ItemCsgoBox.setBoxId(targetDef.id(), grant);
        if (!player.getInventory().add(grant)) {
            player.drop(grant, false);
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.upgrade.done",
                def.name(), consume, targetDef.name()), false);
        return 1;
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
            // Hand out legal stacks only: count can exceed maxStackSize (6400
            // cap in the command tree), so split into per-slot stacks like the
            // vanilla /give does instead of producing one oversized stack.
            int remaining = count;
            int maxStack = Math.max(1, stack.getMaxStackSize());
            while (remaining > 0) {
                int n = Math.min(remaining, maxStack);
                ItemStack give = stack.copyWithCount(n);
                if (!player.getInventory().add(give)) {
                    player.drop(give, false);
                }
                remaining -= n;
            }
        }
        source.sendSuccess(() -> Component.translatable("commands.csgobox.give.success",
                String.valueOf(count), boxId.toString(), String.valueOf(players.size())), true);
        return players.size();
    }

    /** Help is visible to everyone; carries clickable links to the web editor and the online tutorial mirror. */
    private static int showHelp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.title"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.info"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.reload"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.validate"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.give_vanilla"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.give"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.upgrade"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.nbt"), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.editor")
                .append(editorLink()), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.line.tutorial")
                .append(tutorialOnlineLink()), false);
        source.sendSuccess(() -> Component.translatable("commands.csgobox.help.footer"), false);
        return Command.SINGLE_SUCCESS;
    }
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
                    String.valueOf(def.stock())), false);
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
        java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("csbox");
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
        java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("csbox");
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
        com.reclizer.csgobox.fabric_1_21_1.CsgoBox.broadcastBoxDefinitions();
        source.sendSuccess(() -> Component.translatable("commands.csgobox.reload.success", BoxRegistry.size()), false);
        return BoxRegistry.size();
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
            JsonObject serialized = BoxItemCodec.serializeItemStack(item);
            String json = BoxItemCodec.gson().toJson(serialized);
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
            PriceTable.variantId(serialized).ifPresent(variant -> {
                String itemId = serialized.getAsJsonPrimitive("id").getAsString();
                JsonObject priceKey = new JsonObject();
                priceKey.addProperty(itemId + "#" + variant, 0);
                String priceJson = BoxItemCodec.gson().toJson(priceKey);
                source.sendSuccess(() -> copyable(
                        Component.translatable("commands.csgobox.nbt.hand.price_key", itemId + "#" + variant)
                                .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.UNDERLINE),
                        priceJson,
                        Component.translatable("commands.csgobox.nbt.hand.price_key.click_copy")), false);
            });
        } catch (Exception e) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.nbt.hand.error",
                    e.getMessage()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Any player; serializes the crosshair target into a paste-ready items
     *  entry. Three targets, nearest-wins when both rays land: an entity
     *  (its spawn-egg id plus a full entity snapshot in the era-native slot —
     *  minecraft:entity_data component on 1.21+, tag.EntityTag on NBT eras;
     *  movement + UUID keys are stripped so the egg spawns fresh at the use
     *  site), a block with block-entity data (minecraft:block_entity_data on
     *  1.21+, tag.BlockEntityTag on NBT eras), or a plain block (bare
     *  {id, count} — no NBT, which /csbox nbt hand cannot capture). Vanilla
     *  middle-click pick-block captures the same data — this mirrors it
     *  server-side without needing the item in hand. A plain _prices.json
     *  price-key line is always appended: unpriced ids reject the whole box
     *  on load. */
    private static int showLookNbt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(LOOK_RANGE));
        AABB searchBox = player.getBoundingBox()
                .expandTowards(player.getViewVector(1.0F).scale(LOOK_RANGE)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eye, end, searchBox,
                target -> target != player && !target.isSpectator() && target.isPickable());
        HitResult blockHit = player.pick(LOOK_RANGE, 0.0F, false);
        double blockDist = blockHit.getType() == HitResult.Type.BLOCK
                ? eye.distanceTo(blockHit.getLocation()) : Double.MAX_VALUE;
        double entityDist = entityHit != null ? eye.distanceTo(entityHit.getLocation()) : Double.MAX_VALUE;
        try {
            if (entityHit != null && entityDist < blockDist) {
                lookEntity(source, level, entityHit.getEntity());
            } else if (blockHit.getType() == HitResult.Type.BLOCK) {
                lookBlock(source, level, ((BlockHitResult) blockHit).getBlockPos());
            } else {
                throw NBT_LOOK_NO_TARGET.create();
            }
        } catch (CommandSyntaxException e) {
            throw e;
        } catch (Exception e) {
            source.sendSuccess(() -> Component.translatable("commands.csgobox.nbt.look.error",
                    e.getMessage()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Block branch: block-entity data when present; otherwise a bare
     *  {id, count} entry (previously rejected with no_block_entity — plain
     *  blocks need no NBT, and look is the only way to grab their id from
     *  the world without first holding the item). */
    private static void lookBlock(CommandSourceStack source, ServerLevel level, BlockPos pos)
            throws CommandSyntaxException {
        Item item = level.getBlockState(pos).getBlock().asItem();
        if (item == Items.AIR) {
            throw NBT_LOOK_NO_ITEM.create();
        }
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        JsonObject serialized = new JsonObject();
        serialized.addProperty("id", itemId);
        serialized.addProperty("count", 1);
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            CompoundTag beTag = be.saveWithoutMetadata(level.registryAccess());
            JsonElement beJson = CompoundTag.CODEC.encodeStart(JsonOps.INSTANCE, beTag)
                    .resultOrPartial(err -> CsgoBox.LOGGER.warn(
                            "Partial block-entity serialization for {}: {}", itemId, err))
                    .orElseThrow(() -> new IllegalStateException("block entity serialization failed"));
            JsonObject comps = new JsonObject();
            comps.add("minecraft:block_entity_data", beJson);
            serialized.add("components", comps);
        }
        sendLookEntry(source, serialized, itemId,
                level.getBlockState(pos).getBlock().getName().getString());
    }

    /** Entity branch: the spawn egg whose egg type matches the target, plus
     *  a full entity snapshot (id re-added; Pos/Motion/Rotation/UUID stripped
     *  so the egg spawns fresh at its use site instead of teleporting back
     *  to where the snapshot was taken). */
    private static void lookEntity(CommandSourceStack source, ServerLevel level, Entity entity)
            throws CommandSyntaxException {
        SpawnEggItem egg = SpawnEggItem.byId(entity.getType());
        if (egg == null) {
            throw NBT_LOOK_NO_EGG.create();
        }
        String eggId = BuiltInRegistries.ITEM.getKey(egg).toString();
        CompoundTag tag = entity.saveWithoutId(new CompoundTag());
        tag.remove("Pos");
        tag.remove("Motion");
        tag.remove("Rotation");
        tag.remove("UUID");
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        JsonElement tagJson = CompoundTag.CODEC.encodeStart(JsonOps.INSTANCE, tag)
                .resultOrPartial(err -> CsgoBox.LOGGER.warn(
                        "Partial entity serialization for {}: {}", eggId, err))
                .orElseThrow(() -> new IllegalStateException("entity serialization failed"));
        JsonObject serialized = new JsonObject();
        serialized.addProperty("id", eggId);
        serialized.addProperty("count", 1);
        JsonObject comps = new JsonObject();
        comps.add("minecraft:entity_data", tagJson);
        serialized.add("components", comps);
        sendLookEntry(source, serialized, eggId, entity.getName().getString());
    }

    /** Shared tail: header + full/truncated JSON + copy button + the
     *  mandatory _prices.json price-key line ({itemId: 0}, 0 = placeholder,
     *  edit to the real price). */
    private static void sendLookEntry(CommandSourceStack source, JsonObject serialized,
            String priceItemId, String targetName) {
        source.sendSuccess(() -> Component.translatable("commands.csgobox.nbt.look.header", targetName), false);
        String json = BoxItemCodec.gson().toJson(serialized);
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
        JsonObject priceKey = new JsonObject();
        priceKey.addProperty(priceItemId, 0);
        String priceJson = BoxItemCodec.gson().toJson(priceKey);
        source.sendSuccess(() -> copyable(
                Component.translatable("commands.csgobox.nbt.look.price_key", priceItemId)
                        .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.UNDERLINE),
                priceJson,
                Component.translatable("commands.csgobox.nbt.hand.price_key.click_copy")), false);
    }

    // --- Private helpers ---

    /** Wraps a chat component so clicking it copies {@code clipboard} to the player's clipboard. */
    private static Component copyable(Component component, String clipboard) {
        return copyable(component, clipboard,
                Component.translatable("commands.csgobox.nbt.hand.click_copy"));
    }

    /** Same copy-on-click wrapper with a custom hover label (the price-key
     *  fragment copies a different payload than the full item JSON). */
    private static Component copyable(Component component, String clipboard, Component hover) {
        return component.copy().withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, clipboard))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static BoxDefinition getBoxOrThrow(ResourceLocation boxId) throws CommandSyntaxException {
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def == null) {
            throw BOX_NOT_FOUND.create(boxId.toString());
        }
        return def;
    }
}
