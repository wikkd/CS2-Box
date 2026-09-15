package com.reclizer.csgobox.v1_21_1.emi;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.utils.EntityChineseMap;
import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.box.GradeGroup;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ModItems;
import com.reclizer.csgobox.v1_21_1.utils.RenderFontTool;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.DrawableWidget;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EMI recipe "开箱概率": one recipe per box definition. The key item is the
 * input, the box item and its whole item pool are outputs (so any drop can be
 * reverse-looked-up through EMI), item chance is carried on the output
 * {@link EmiStack}s, and the drawn text column shows the drop rate, per-grade
 * weight percentages and entity drop rates — the exact server semantics of
 * {@code OddsCalculator.pickGrade} + {@code GradeMap.pickRandom} via
 * {@link BoxOdds}.
 */
public final class BoxEmiRecipe implements EmiRecipe {

    public static final int WIDTH = 156;
    public static final int HEIGHT = 110;
    private static final int MAX_GRADE_LINES = 4;
    private static final int MAX_ITEM_SLOTS = 20;
    private static final int GRID_COLS = 4;
    private static final int GRID_ROWS = 5;
    private static final int SLOT_PITCH = 19;
    private static final float TEXT_SCALE = 0.65F;
    private static final int LINE_PITCH = 8;
    private static final int TEXT_X = 2;
    private static final int TEXT_START_Y = 46;
    private static final int GRID_X = 76;
    private static final int GRID_Y = 2;
    private static final int COLOR_BODY = 0xFFE0E0E0;
    private static final int COLOR_MUTED = 0xFF9A9A9A;

    private final EmiRecipeCategory category;
    private final ResourceLocation id;
    private final BoxDefinition definition;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;

    private BoxEmiRecipe(EmiRecipeCategory category, BoxDefinition definition,
                         ItemStack boxStack, ItemStack keyStack) {
        this.category = category;
        this.id = ResourceLocation.fromNamespaceAndPath(
                "csgobox", "box_" + definition.id().getNamespace() + "_" + definition.id().getPath());
        this.definition = definition;

        List<EmiIngredient> inputs = new ArrayList<>();
        if (!keyStack.isEmpty()) {
            inputs.add(EmiStack.of(keyStack));
        }
        this.inputs = List.copyOf(inputs);

        List<EmiStack> outputs = new ArrayList<>();
        outputs.add(EmiStack.of(boxStack).setChance(definition.dropRate()));
        int[] weights = definition.getWeightArray();
        for (GradeGroup grade : definition.grades()) {
            double gradeChance = BoxOdds.gradeChance(weights, BoxGrades.gradeLevel(grade.id()));
            int itemCount = grade.items().size();
            long itemWeightSum = grade.positiveItemWeightSum();
            for (int i = 0; i < grade.items().size(); i++) {
                outputs.add(EmiStack.of(grade.items().get(i))
                        .setChance((float) BoxOdds.itemChance(
                                gradeChance, grade.itemWeightAt(i), itemCount, itemWeightSum)));
            }
        }
        this.outputs = List.copyOf(outputs);
    }

    /** Snapshots every definition currently in the client box registry. */
    public static List<BoxEmiRecipe> fromRegistry(EmiRecipeCategory category) {
        List<BoxEmiRecipe> recipes = new ArrayList<>();
        for (BoxDefinition definition : BoxRegistry.getAll()) {
            // Box items are a fixed set (a box is data, not an item id).
            Item boxItem = ModItems.itemForBox(definition.id(), definition.isTerminal());
            ItemStack boxStack = ItemCsgoBox.setBoxId(definition.id(), new ItemStack(boxItem));
            ItemStack keyStack = keyStack(definition);
            recipes.add(new BoxEmiRecipe(category, definition, boxStack, keyStack));
        }
        return recipes;
    }

    private static ItemStack keyStack(BoxDefinition definition) {
        if (definition.isTerminal() || definition.keyItem() == null
                || "minecraft:air".equals(definition.keyItem().toString())) {
            return ItemStack.EMPTY;
        }
        Item keyItem = BuiltInRegistries.ITEM.get(definition.keyItem());
        return keyItem == null || keyItem == net.minecraft.world.item.Items.AIR
                ? ItemStack.EMPTY
                : new ItemStack(keyItem);
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return category;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        if (!inputs.isEmpty()) {
            widgets.addSlot(inputs.get(0), 2, 2).recipeContext(this);
        }
        widgets.addSlot(outputs.get(0), 2, 26)
                .recipeContext(this)
                .appendTooltip(Component.translatable(
                        "emi.csgobox.tooltip.drop_rate", formatPercent(definition.dropRate())));

        int slotIndex = 0;
        int[] weights = definition.getWeightArray();
        long totalWeight = BoxOdds.totalWeight(weights);
        for (GradeGroup grade : definition.grades()) {
            double gradeChance = BoxOdds.gradeChance(weights, BoxGrades.gradeLevel(grade.id()));
            int itemCount = grade.items().size();
            long itemWeightSum = grade.positiveItemWeightSum();
            for (int i = 0; i < grade.items().size(); i++) {
                if (slotIndex >= MAX_ITEM_SLOTS) {
                    break;
                }
                int col = slotIndex % GRID_COLS;
                int row = slotIndex / GRID_COLS;
                SlotWidget slot = widgets.addSlot(
                        EmiStack.of(grade.items().get(i)),
                        GRID_X + col * SLOT_PITCH,
                        GRID_Y + row * SLOT_PITCH);
                slot.recipeContext(this);
                final GradeGroup gradeRef = grade;
                final int itemIndex = i;
                slot.appendTooltip(() -> ClientTooltipComponent.create(Component.translatable(
                                "emi.csgobox.tooltip.grade_weight",
                                gradeRef.displayName(), gradeRef.weight(), totalWeight)
                        .getVisualOrderText()))
                        .appendTooltip(() -> ClientTooltipComponent.create(Component.translatable(
                                        "emi.csgobox.tooltip.item_chance",
                                        formatPercent(BoxOdds.itemChance(gradeChance,
                                                gradeRef.itemWeightAt(itemIndex), itemCount, itemWeightSum)))
                                .getVisualOrderText()));
                slotIndex++;
            }
            if (slotIndex >= MAX_ITEM_SLOTS) {
                break;
            }
        }

        widgets.add(new DrawableWidget(0, TEXT_START_Y, WIDTH, HEIGHT - TEXT_START_Y,
                (gui, mouseX, mouseY, delta) -> drawProbabilityText(gui, definition)));
    }

    private void drawProbabilityText(GuiGraphics gui, BoxDefinition definition) {
        Font font = Minecraft.getInstance().font;
        if (font == null) {
            return;
        }
        int[] weights = definition.getWeightArray();
        int y = 0;

        drawText(gui, font, TEXT_X, y, COLOR_BODY,
                Component.translatable("emi.csgobox.recipe.drop_rate", formatPercent(definition.dropRate())));
        y += LINE_PITCH;

        int shown = 0;
        for (GradeGroup grade : definition.grades()) {
            if (shown >= MAX_GRADE_LINES) {
                break;
            }
            double chance = BoxOdds.gradeChance(weights, BoxGrades.gradeLevel(grade.id()));
            drawText(gui, font, TEXT_X, y, grade.color(),
                    Component.translatable("emi.csgobox.recipe.grade_line", grade.displayName(), formatPercent(chance)));
            shown++;
            y += LINE_PITCH;
        }
        if (definition.grades().size() > MAX_GRADE_LINES) {
            drawText(gui, font, TEXT_X, y, COLOR_MUTED,
                    Component.translatable("emi.csgobox.recipe.more_grades", definition.grades().size()));
            y += LINE_PITCH;
        }
        for (Map.Entry<ResourceLocation, Float> entry : definition.entityDropRates().entrySet()) {
            if (y + LINE_PITCH > HEIGHT - TEXT_START_Y) {
                break;
            }
            drawText(gui, font, TEXT_X, y, COLOR_MUTED,
                    Component.translatable("emi.csgobox.recipe.entity_rate",
                            EntityChineseMap.getDisplayName(entry.getKey().toString()),
                            formatPercent(entry.getValue())));
            y += LINE_PITCH;
        }

        int totalItems = definition.grades().stream().mapToInt(g -> g.items().size()).sum();
        if (totalItems > MAX_ITEM_SLOTS) {
            // The DrawableWidget origin sits at TEXT_START_Y, so the grid-relative
            // line below the item grid is shifted up to display coordinates.
            drawText(gui, font, GRID_X, GRID_Y + GRID_ROWS * SLOT_PITCH - TEXT_START_Y, COLOR_MUTED,
                    Component.translatable("emi.csgobox.recipe.more_items", totalItems));
        }
    }

    private static void drawText(GuiGraphics gui, Font font, int x, int y, int color, Component text) {
        FormattedCharSequence sequence = text.getVisualOrderText();
        RenderFontTool.drawString(gui, font, sequence, x, y, 0, 0, TEXT_SCALE, color);
    }

    private static String formatPercent(double chance) {
        return String.format("%.1f", chance * 100.0);
    }
}