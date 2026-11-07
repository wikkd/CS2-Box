package com.reclizer.csgobox.fabric_1_21_1.item;

import com.google.common.base.Suppliers;
import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.fabric_1_21_1.CsgoBox;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.fabric_1_21_1.box.GradeGroup;
import com.reclizer.csgobox.fabric_1_21_1.gui.BoxScreenOpener;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.Registry;

public class ItemCsgoBox extends Item {

    private static final ChatFormatting[] TOOLTIP_GRADE_COLORS = {
            ChatFormatting.BLUE,
            ChatFormatting.DARK_BLUE,
            ChatFormatting.DARK_PURPLE,
            ChatFormatting.RED,
            ChatFormatting.GOLD
    };

    private static ResourceLocation componentId(String path) {
        return ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, path);
    }

    public static final Supplier<DataComponentType<ResourceLocation>> BOX_ID = Suppliers.memoize(() ->
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, componentId("box_id"),
                    DataComponentType.<ResourceLocation>builder()
                            .persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC)
                            .build()));

    /**
     * Rarity grade (1=consumer .. 5=classified) stamped onto the opened item
     * when it is granted to the player. The Armory Recycler reads this to
     * decide how many Armory Points an item is worth, so recycling stays
     * rarity-accurate without re-deriving grade from the (ambiguous) item id.
     */
    public static final Supplier<DataComponentType<Integer>> GRADE = Suppliers.memoize(() ->
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, componentId("grade"),
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build()));

    /**
     * Unique per-terminal id, stamped by the server the first time the player
     * opens that terminal. The locked session binds to it so a timeout can
     * destroy THE terminal that was opened (and only that one), even after a
     * rename or a trip through a chest.
     */
    public static final Supplier<DataComponentType<String>> TERMINAL_UID = Suppliers.memoize(() ->
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, componentId("terminal_uid"),
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build()));

    /**
     * Display name of the player whose live negotiation owns this terminal,
     * stamped by the server when a fresh session is created for the terminal.
     * The dealer reads it when another player opens a locked terminal ("去问问
     * xxx吧"), so the owner's name survives hand-offs, server restarts and the
     * owner being offline without needing a runtime profile lookup.
     */
    public static final Supplier<DataComponentType<String>> TERMINAL_OWNER = Suppliers.memoize(() ->
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, componentId("terminal_owner"),
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build()));

    /**
     * v2.0.1: optional JSON spec for box items needing open-time resolution —
     * count range {@code {"c":[min,max]}}, random enchant {@code {"e":...}},
     * loot-table reference {@code {"l":"ns:path"}}. Absent on plain items so
     * the default network payload stays unchanged.
     */
    public static final Supplier<DataComponentType<String>> ITEM_SPEC = Suppliers.memoize(() ->
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, componentId("item_spec"),
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build()));

    /** Eager registration during mod init (mirrors the DeferredRegister flush). */
    public static void registerDataComponents() {
        BOX_ID.get();
        GRADE.get();
        TERMINAL_UID.get();
        TERMINAL_OWNER.get();
        ITEM_SPEC.get();
    }

    public ItemCsgoBox() {
        this(16);
    }

    /** Terminal machines are unstackable — every terminal owns its own uid/lock. */
    protected ItemCsgoBox(int maxStack) {
        super(new Properties().stacksTo(maxStack).rarity(Rarity.EPIC));
    }

    public static Optional<BoxDefinition> getDefinition(ItemStack stack) {
        ResourceLocation id = getBoxId(stack);
        return id == null ? Optional.empty() : Optional.ofNullable(BoxRegistry.get(id));
    }

    public static ResourceLocation getBoxId(ItemStack stack) {
        ResourceLocation id = stack.get(BOX_ID.get());
        if (id != null) {
            return id;
        }
        // Fallback for vanilla /give: use the item's own registry id as the
        // default box_id so `/give @p csgobox:csgo_box` Just Works without any
        // components syntax. The player can still override via vanilla
        // components: `/give @p csgobox:csgo_box[csgobox:box_id='"csgobox:..."']`
        if (stack.getItem() instanceof ItemCsgoBox) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem());
        }
        return null;
    }

    /** The terminal's unique id, or null when never opened (e.g. a fresh /give). */
    public static String getTerminalUid(ItemStack stack) {
        return stack.get(TERMINAL_UID.get());
    }

    /** Returns the terminal's unique id, stamping a fresh one on first use. */
    public static String ensureTerminalUid(ItemStack stack) {
        String uid = stack.get(TERMINAL_UID.get());
        if (uid == null) {
            uid = UUID.randomUUID().toString();
            stack.set(TERMINAL_UID.get(), uid);
        }
        return uid;
    }

    /** The stamped owner name of the terminal, or null when never opened. */
    public static String getTerminalOwner(ItemStack stack) {
        return stack.get(TERMINAL_OWNER.get());
    }

    /** Stamp the terminal's owner name (kept current on every fresh session). */
    public static void stampTerminalOwner(ItemStack stack, String name) {
        if (name != null && !name.isEmpty()) {
            stack.set(TERMINAL_OWNER.get(), name);
        }
    }

    public static ItemStack setBoxId(ResourceLocation boxId, ItemStack stack) {
        if (stack.getItem() instanceof ItemCsgoBox) {
            stack.set(BOX_ID.get(), boxId);
            BoxDefinition def = BoxRegistry.get(boxId);
            if (def != null) {
                stack.set(DataComponents.CUSTOM_NAME, def.name());
                applyIcon(def, stack);
            }
        }
        return stack;
    }

    /**
     * v2.0.1: applies the configured {@code icon} to a box stack. An integer
     * value sets CustomModelData (pair with a resource pack). 1.21.1 has no
     * {@code ITEM_MODEL} component, so a non-integer (model-id) icon is a
     * warning-only no-op here.
     */
    static void applyIcon(BoxDefinition def, ItemStack stack) {
        def.icon().ifPresent(icon -> {
            try {
                int cmd = Integer.parseInt(icon);
                stack.set(DataComponents.CUSTOM_MODEL_DATA,
                        new net.minecraft.world.item.component.CustomModelData(cmd));
            } catch (NumberFormatException e) {
                CsgoBox.LOGGER.warn("icon model id '{}' for box {} is not supported on MC 1.21.1 "
                        + "(use an integer CustomModelData value) — ignored", icon, def.id());
            }
        });
    }

    /**
     * Client-side open entry: plays the open sound and opens the classic crate
     * screen (Shift → bulk overview). The terminal machine overrides this in
     * {@link ItemTerminal}. Only called from {@code ClickEvent} on the client;
     * never invoke on a dedicated server. The actual screen code lives in
     * {@link BoxScreenOpener} so server-side class loading stays client-free.
     */
    public void openScreen(ItemStack stack) {
        // v2.0.1: dispatch on the box definition rather than the item class.
        // Box items are generic now (identity in the csgobox:box_id data
        // component), so a terminal-type box handed out as csgo_box must still
        // open the terminal UI.
        if (getDefinition(stack).map(BoxDefinition::isTerminal).orElse(false)) {
            BoxScreenOpener.openTerminal(stack);
        } else {
            BoxScreenOpener.openClassic(stack);
        }
    }

    public static int[] getRandom(ItemStack stack) {
        return getDefinition(stack)
                .map(BoxDefinition::getWeightArray)
                .orElseGet(() -> BoxGrades.DEFAULT_WEIGHTS.clone());
    }

    public static Map<ItemStack, Integer> getItemGroup(ItemStack stack) {
        Map<ItemStack, Integer> itemsMap = new LinkedHashMap<>();
        getDefinition(stack).ifPresent(def -> {
            List<GradeGroup> tiers = def.grades();
            for (int i = 0; i < tiers.size(); i++) {
                int gradeLevel = BoxGrades.levelAt(tiers.size(), i);
                for (ItemStack item : tiers.get(i).items()) {
                    if (!item.isEmpty()) {
                        itemsMap.put(item.copy(), gradeLevel);
                    }
                }
            }
        });
        return itemsMap;
    }

    /**
     * v2.0.1: builds the weighted grade pool for opening. Items with a
     * non-positive {@code weight} are excluded; the rest are weighted inside
     * their grade.
     */
    public static com.reclizer.csgobox.logic.GradeMap<ItemStack> buildGradeMap(ItemStack box) {
        Map<Integer, List<com.reclizer.csgobox.logic.GradeMap.Weighted<ItemStack>>> raw = new LinkedHashMap<>();
        getDefinition(box).ifPresent(def -> {
            List<GradeGroup> tiers = def.grades();
            for (int t = 0; t < tiers.size(); t++) {
                int gradeLevel = BoxGrades.levelAt(tiers.size(), t);
                GradeGroup grade = tiers.get(t);
                for (int i = 0; i < grade.items().size(); i++) {
                    ItemStack item = grade.items().get(i);
                    if (item == null || item.isEmpty()) continue;
                    int w = grade.itemWeightAt(i);
                    if (w <= 0) continue;
                    raw.computeIfAbsent(gradeLevel, k -> new java.util.ArrayList<>())
                            .add(new com.reclizer.csgobox.logic.GradeMap.Weighted<>(item, w));
                }
            }
        });
        return com.reclizer.csgobox.logic.GradeMap.fromWeighted(raw,
                stack -> !stack.isEmpty(), ItemStack::copy);
    }

    public static ResourceLocation getKey(ItemStack stack) {
        return getDefinition(stack)
                .map(BoxDefinition::keyItem)
                .orElse(null);
    }

    /** v2.x: every accepted key id (primary + extras); empty = keyless. */
    public static java.util.List<String> getAcceptedKeys(ItemStack stack) {
        return getDefinition(stack).map(BoxDefinition::acceptedKeys)
                .orElseGet(java.util.List::of);
    }

    @Override
    public Component getName(ItemStack stack) {
        return getDefinition(stack)
                .map(BoxDefinition::name)
                .orElseGet(() -> super.getName(stack));
    }

    /** Per-grade tooltip line cap. The full list stays reachable via
     *  {@code /csbox info} and the JEI/REI/EMI probability tables; an
     *  oversized crate otherwise pushes its tooltip past the screen edge. */
    private static final int MAX_TOOLTIP_ITEMS_PER_GRADE = 8;

    /**
     * v2.0.2 perf: vanilla re-invokes {@link #appendHoverText} every frame the
     * tooltip is visible, so the grade list is built once per (definition,
     * advanced) pair and cached. Keyed on the definition instance — a reload
     * builds new BoxDefinition objects, which invalidates the cache naturally.
     * Render-thread only, single slot.
     */
    private record TooltipKey(BoxDefinition def, boolean advanced) {
    }

    private static TooltipKey sTooltipKey;
    private static List<Component> sTooltipLines = List.of();

    /** Adds the configured box contents to the item tooltip. */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltips.csgobox.item.cs_box").withStyle(ChatFormatting.GRAY));
        getDefinition(stack).ifPresent(def -> {
            TooltipKey key = new TooltipKey(def, tooltipFlag.isAdvanced());
            if (key != sTooltipKey) {
                sTooltipKey = key;
                sTooltipLines = buildTooltipLines(def, key.advanced());
            }
            for (Component line : sTooltipLines) {
                tooltipComponents.add(line);
            }
        });
    }

    /** Builds the full grade/probability line list for one tooltip cache slot. */
    private static List<Component> buildTooltipLines(BoxDefinition def, boolean advanced) {
        List<Component> lines = new ArrayList<>();
        if (def.openCost() > 0) {
            lines.add(Component.translatable("tooltips.csgobox.item.open_cost",
                    String.valueOf(def.openCost())).withStyle(ChatFormatting.GRAY));
        }
        if (def.availableFromMs() > 0L || def.availableUntilMs() > 0L) {
            lines.add(Component.translatable("tooltips.csgobox.item.window",
                    def.windowText(), def.windowTextUntil()).withStyle(ChatFormatting.GRAY));
        }
        int[] weights = def.getWeightArray();
        boolean showChances = advanced && !def.hideOdds();
        int tierCount = def.grades().size();
        for (int i = 0; i < tierCount; i++) {
            GradeGroup grade = def.grades().get(i);
            // Positional tiers: the list runs highest → lowest, so the level
            // of index i is tierCount - i and its weight slot is level - 1.
            // (The old code indexed the low→high weights array with the
            // high→low list index, which scrambled advanced tooltips.)
            int level = BoxGrades.levelAt(tierCount, i);
            ChatFormatting color = level - 1 < TOOLTIP_GRADE_COLORS.length
                    ? TOOLTIP_GRADE_COLORS[level - 1] : ChatFormatting.WHITE;
            if (level - 1 < weights.length && weights[level - 1] > 0 && showChances) {
                double gradeChance = BoxOdds.gradeChance(weights, level);
                lines.add(Component.translatable("tooltips.csgobox.item.grade_chance",
                        String.valueOf(level), BoxOdds.percent(gradeChance)).withStyle(color));
                appendGradeItems(grade, color, lines, true, gradeChance);
            } else {
                appendGradeItems(grade, color, lines, false, 0.0D);
            }
        }
        // "Gold" hint is legacy-tier vocabulary; custom tier sets skip it.
        if (tierCount >= BoxGrades.GRADE_COUNT && def.grades().stream().allMatch(g -> BoxGrades.gradeLevel(g.id()) > 0)) {
            lines.add(Component.translatable("gui.csgobox.csgo_box.label_gold").withStyle(ChatFormatting.YELLOW));
        }
        return List.copyOf(lines);
    }

    /** One line per item (weighted mode shows the per-item chance), capped at
     *  {@link #MAX_TOOLTIP_ITEMS_PER_GRADE} lines with a "+N more" summary. */
    private static void appendGradeItems(GradeGroup grade, ChatFormatting color, List<Component> lines,
                                         boolean weighted, double gradeChance) {
        int size = grade.items().size();
        long itemWeightSum = grade.positiveItemWeightSum();
        boolean useWeights = weighted && itemWeightSum != size;
        for (int j = 0; j < size; j++) {
            if (j == MAX_TOOLTIP_ITEMS_PER_GRADE) {
                lines.add(Component.translatable("tooltips.csgobox.item.more",
                        String.valueOf(size - j)).withStyle(ChatFormatting.GRAY));
                break;
            }
            ItemStack itemStack = grade.items().get(j);
            Component name = itemStack.getItem().getName(itemStack).copy().withStyle(color);
            if (useWeights) {
                lines.add(Component.translatable("tooltips.csgobox.item.item_chance",
                        name, BoxOdds.percent(BoxOdds.weightedItemChance(
                                gradeChance, grade.itemWeightAt(j), itemWeightSum)))
                        .withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(name);
            }
        }
    }
}
