package com.reclizer.csgobox.forge_1_20_1.item;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.box.GradeGroup;
import com.reclizer.csgobox.logic.GradeMap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ItemCsgoBox extends Item {

    private static final ChatFormatting[] TOOLTIP_GRADE_COLORS = {
            ChatFormatting.BLUE,
            ChatFormatting.DARK_BLUE,
            ChatFormatting.DARK_PURPLE,
            ChatFormatting.RED,
            ChatFormatting.GOLD
    };

    public static final String TAG_BOX_ID = "csgobox:box_id";
    public static final String TAG_GRADE = "csgobox:grade";
    public static final String TAG_TERMINAL_UID = "csgobox:terminal_uid";
    public static final String TAG_TERMINAL_OWNER = "csgobox:terminal_owner";

    /**
     * v2.0.1: optional JSON spec for box items that need open-time resolution:
     * count range {@code {"c":[min,max]}}, random enchant
     * {@code {"e":{"id":"...","level":[3,5]}}}, loot-table reference
     * {@code {"l":"namespace:path"}} — combined fields allowed. Absent on
     * plain items. Stored as a String NBT tag (1.20.1 has no data
     * components).
     */
    public static final String TAG_ITEM_SPEC = "csgobox:item_spec";

    /** Dedicated NBT tag carrying the loot-table id for {@code loot_table}
     *  entries (kept alongside {@link #TAG_ITEM_SPEC}). */
    public static final String TAG_LOOT_TABLE = "csgobox:loot_table";

    public ItemCsgoBox(Properties properties) {
        this(properties, 16);
    }

    protected ItemCsgoBox(Properties properties, int maxStack) {
        super(properties.stacksTo(maxStack).rarity(Rarity.EPIC));
    }

    // ---- NBT helpers ----

    public static Optional<BoxDefinition> getDefinition(ItemStack stack) {
        ResourceLocation id = getBoxId(stack);
        return id == null ? Optional.empty() : Optional.ofNullable(BoxRegistry.get(id));
    }

    public static ResourceLocation getBoxId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_BOX_ID)) {
            String idStr = tag.getString(TAG_BOX_ID);
            try {
                return new ResourceLocation(idStr);
            } catch (Exception e) {
                return null;
            }
        }
        if (stack.getItem() instanceof ItemCsgoBox) {
            return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        }
        return null;
    }

    public static Integer getGrade(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_GRADE)) {
            return tag.getInt(TAG_GRADE);
        }
        return null;
    }

    public static void setGrade(ItemStack stack, int grade) {
        stack.getOrCreateTag().putInt(TAG_GRADE, grade);
    }

    public static String getTerminalUid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_TERMINAL_UID)) {
            return tag.getString(TAG_TERMINAL_UID);
        }
        return null;
    }

    public static String ensureTerminalUid(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        String uid = tag.getString(TAG_TERMINAL_UID);
        if (uid == null || uid.isEmpty()) {
            uid = UUID.randomUUID().toString();
            tag.putString(TAG_TERMINAL_UID, uid);
        }
        return uid;
    }

    public static String getTerminalOwner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_TERMINAL_OWNER)) {
            return tag.getString(TAG_TERMINAL_OWNER);
        }
        return null;
    }

    public static void stampTerminalOwner(ItemStack stack, String name) {
        if (name != null && !name.isEmpty()) {
            stack.getOrCreateTag().putString(TAG_TERMINAL_OWNER, name);
        }
    }

    public static ItemStack setBoxId(ResourceLocation boxId, ItemStack stack) {
        if (stack.getItem() instanceof ItemCsgoBox) {
            stack.getOrCreateTag().putString(TAG_BOX_ID, boxId.toString());
            BoxDefinition def = BoxRegistry.get(boxId);
            if (def != null) {
                stack.setHoverName(def.name());
                applyIcon(def, stack);
            }
        }
        return stack;
    }

    /**
     * v2.0.1: applies the configured {@code icon} to a box stack. 1.20.1 has
     * no item-model component, so only an integer icon is honored (sets NBT
     * {@code CustomModelData}, pair with a resource pack); any other value is
     * skipped with a warning.
     */
    static void applyIcon(BoxDefinition def, ItemStack stack) {
        def.icon().ifPresent(icon -> {
            try {
                int cmd = Integer.parseInt(icon);
                stack.getOrCreateTag().putInt("CustomModelData", cmd);
            } catch (NumberFormatException e) {
                CsgoBox.LOGGER.warn("Invalid icon '{}' for box {} — only numeric CustomModelData is supported "
                        + "on 1.20.1, ignored", icon, def.id());
            }
        });
    }

    // ---- Open screen (client-side entry, overridden by ItemTerminal) ----

    /**
     * Client-side open entry: plays the open sound and opens the classic crate
     * screen (Shift → bulk overview). The terminal machine overrides this in
     * {@link ItemTerminal}. Only called from {@code ClickEvent} on the client;
     * never invoke on a dedicated server. The actual screen code lives in
     * {@link com.reclizer.csgobox.forge_1_20_1.gui.BoxScreenOpener} so server-side class loading stays client-free.
     */
    public void openScreen(ItemStack stack) {
        // v2.0.1: dispatch on the box definition rather than the item class.
        // Box items are generic now (identity in NBT), so a terminal-type box
        // handed out as csgo_box must still open the terminal UI.
        if (getDefinition(stack).map(BoxDefinition::isTerminal).orElse(false)) {
            com.reclizer.csgobox.forge_1_20_1.gui.BoxScreenOpener.openTerminal(stack);
        } else {
            com.reclizer.csgobox.forge_1_20_1.gui.BoxScreenOpener.openClassic(stack);
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
            for (GradeGroup grade : def.grades()) {
                int gradeLevel = BoxGrades.gradeLevel(grade.id());
                if (gradeLevel == 0) continue;
                for (ItemStack item : grade.items()) {
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
     * non-positive {@code weight} are excluded (authors disable entries
     * without deleting them); the rest are weighted inside their grade.
     */
    public static GradeMap<ItemStack> buildGradeMap(ItemStack box) {
        Map<Integer, List<GradeMap.Weighted<ItemStack>>> raw = new LinkedHashMap<>();
        getDefinition(box).ifPresent(def -> {
            for (GradeGroup grade : def.grades()) {
                int gradeLevel = BoxGrades.gradeLevel(grade.id());
                if (gradeLevel == 0) continue;
                for (int i = 0; i < grade.items().size(); i++) {
                    ItemStack item = grade.items().get(i);
                    if (item == null || item.isEmpty()) continue;
                    int w = grade.itemWeightAt(i);
                    if (w <= 0) continue;
                    raw.computeIfAbsent(gradeLevel, k -> new java.util.ArrayList<>())
                            .add(new GradeMap.Weighted<>(item, w));
                }
            }
        });
        return GradeMap.fromWeighted(raw,
                stack -> !stack.isEmpty(), ItemStack::copy);
    }

    public static ResourceLocation getKey(ItemStack stack) {
        return getDefinition(stack)
                .map(BoxDefinition::keyItem)
                .orElse(null);
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

    /** Adds the configured box contents to the item tooltip (v2.0.1 weighted). */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.Level level,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
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
        int[] weights = def.getWeightArray();
        for (int i = 0; i < def.grades().size(); i++) {
            GradeGroup grade = def.grades().get(i);
            ChatFormatting color = i < TOOLTIP_GRADE_COLORS.length ? TOOLTIP_GRADE_COLORS[i] : ChatFormatting.WHITE;
            if (i < weights.length && weights[i] > 0 && advanced) {
                double gradeChance = BoxOdds.gradeChance(weights, i + 1);
                lines.add(Component.translatable("tooltips.csgobox.item.grade_chance",
                        String.valueOf(i + 1), BoxOdds.percent(gradeChance)).withStyle(color));
                appendGradeItems(grade, color, lines, true, gradeChance);
            } else {
                appendGradeItems(grade, color, lines, false, 0.0D);
            }
        }
        if (def.grades().size() >= BoxGrades.GRADE_COUNT) {
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
