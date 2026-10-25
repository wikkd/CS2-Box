package com.reclizer.csgobox.forge_1_18_2.jei;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.forge_1_18_2.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_18_2.box.GradeGroup;
import com.reclizer.csgobox.forge_1_18_2.utils.RenderFontTool;
import com.reclizer.csgobox.utils.EntityChineseMap;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * JEI category "开箱概率": one recipe per box definition. The key item is the
 * input, the box item and its whole item pool are outputs (so any drop can be
 * reverse-looked-up), and the drawn text column shows the drop rate, per-grade
 * weight percentages and entity drop rates. Percentages use the exact server
 * semantics of {@code OddsCalculator.pickGrade} + {@code GradeMap.pickRandom}
 * via {@link BoxOdds}.
 */
public final class BoxOpenCategory implements IRecipeCategory<BoxJeiRecipe> {

    public static final RecipeType<BoxJeiRecipe> TYPE =
            RecipeType.create("csgobox", "box_open", BoxJeiRecipe.class);

    private static final int WIDTH = 156;
    private static final int HEIGHT = 110;
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

    private final IDrawable background;
    private final IDrawable icon;

    public BoxOpenCategory(IJeiHelpers helpers, ItemStack iconStack) {
        this.background = helpers.getGuiHelper().createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helpers.getIngredientManager().createTypedIngredient(iconStack)
                .map(helpers.getGuiHelper()::createDrawableIngredient)
                .orElseThrow(() -> new IllegalStateException("JEI cannot render ItemStack ingredient"));
    }

    @Override
    public RecipeType<BoxJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return new TranslatableComponent("jei.csgobox.category.box_open");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BoxJeiRecipe recipe, IFocusGroup focuses) {
        if (!recipe.keyStack().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 2, 2).addItemStack(recipe.keyStack());
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 2, 26)
                .addItemStack(recipe.boxStack())
                .addRichTooltipCallback((view, tooltip) -> addBoxTooltip(tooltip, recipe.definition()));

        int slotIndex = 0;
        for (GradeGroup grade : recipe.definition().grades()) {
            java.util.List<ItemStack> items = grade.items();
            for (int i = 0; i < items.size(); i++) {
                if (slotIndex >= MAX_ITEM_SLOTS) {
                    break;
                }
                int col = slotIndex % GRID_COLS;
                int row = slotIndex / GRID_COLS;
                final int itemIndex = i;
                builder.addSlot(RecipeIngredientRole.OUTPUT, GRID_X + col * SLOT_PITCH, GRID_Y + row * SLOT_PITCH)
                        .addItemStack(items.get(i))
                        .addRichTooltipCallback((view, tooltip) -> addItemTooltip(tooltip, recipe.definition(), grade, itemIndex));
                slotIndex++;
            }
            if (slotIndex >= MAX_ITEM_SLOTS) {
                break;
            }
        }
    }

    @Override
    public void draw(BoxJeiRecipe recipe, IRecipeSlotsView slotsView, PoseStack pose, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        if (font == null) {
            return;
        }
        BoxDefinition definition = recipe.definition();
        int[] weights = definition.getWeightArray();
        int y = TEXT_START_Y;

        drawText(pose, font, TEXT_X, y, COLOR_BODY,
                new TranslatableComponent("jei.csgobox.recipe.drop_rate", formatPercent(definition.dropRate())));
        y += LINE_PITCH;

        int shown = 0;
        for (GradeGroup grade : definition.grades()) {
            if (shown >= MAX_GRADE_LINES) {
                break;
            }
            double chance = BoxOdds.gradeChance(weights, BoxGrades.gradeLevel(grade.id()));
            drawText(pose, font, TEXT_X, y, grade.color(),
                    new TranslatableComponent("jei.csgobox.recipe.grade_line", grade.displayName(), formatPercent(chance)));
            shown++;
            y += LINE_PITCH;
        }
        if (definition.grades().size() > MAX_GRADE_LINES) {
            drawText(pose, font, TEXT_X, y, COLOR_MUTED,
                    new TranslatableComponent("jei.csgobox.recipe.more_grades", definition.grades().size()));
            y += LINE_PITCH;
        }
        for (Map.Entry<ResourceLocation, Float> entry : definition.entityDropRates().entrySet()) {
            if (y + LINE_PITCH > HEIGHT) {
                break;
            }
            drawText(pose, font, TEXT_X, y, COLOR_MUTED,
                    new TranslatableComponent("jei.csgobox.recipe.entity_rate",
                            EntityChineseMap.getDisplayName(entry.getKey().toString()),
                            formatPercent(entry.getValue())));
            y += LINE_PITCH;
        }

        int totalItems = definition.grades().stream().mapToInt(g -> g.items().size()).sum();
        if (totalItems > MAX_ITEM_SLOTS) {
            drawText(pose, font, GRID_X, GRID_Y + GRID_ROWS * SLOT_PITCH, COLOR_MUTED,
                    new TranslatableComponent("jei.csgobox.recipe.more_items", totalItems));
        }
    }

    private void addBoxTooltip(ITooltipBuilder tooltip, BoxDefinition definition) {
        tooltip.add(new TranslatableComponent("jei.csgobox.recipe.drop_rate", formatPercent(definition.dropRate())));
        int lines = 0;
        for (Map.Entry<ResourceLocation, Float> entry : definition.entityDropRates().entrySet()) {
            if (lines >= 4) {
                break;
            }
            tooltip.add(new TranslatableComponent("jei.csgobox.recipe.entity_rate",
                    EntityChineseMap.getDisplayName(entry.getKey().toString()),
                    formatPercent(entry.getValue())));
            lines++;
        }
    }

    private void addItemTooltip(ITooltipBuilder tooltip, BoxDefinition definition, GradeGroup grade, int itemIndex) {
        int[] weights = definition.getWeightArray();
        long totalWeight = BoxOdds.totalWeight(weights);
        double gradeChance = BoxOdds.gradeChance(weights, BoxGrades.gradeLevel(grade.id()));
        int itemCount = grade.items().size();
        tooltip.add(new TranslatableComponent("jei.csgobox.tooltip.grade_weight",
                grade.displayName(), grade.weight(), totalWeight));
        tooltip.add(new TranslatableComponent("jei.csgobox.tooltip.item_chance",
                formatPercent(BoxOdds.itemChance(gradeChance,
                        grade.itemWeightAt(itemIndex), itemCount, grade.positiveItemWeightSum()))));
    }

    private static void drawText(PoseStack pose, Font font, int x, int y, int color, Component text) {
        FormattedCharSequence sequence = text.getVisualOrderText();
        RenderFontTool.drawString(pose, font, sequence, x, y, 0, 0, TEXT_SCALE, color);
    }

    private static String formatPercent(double chance) {
        return String.format("%.1f", chance * 100.0);
    }
}
