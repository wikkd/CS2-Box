package com.reclizer.csgobox.v26_1_2.rei;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.utils.EntityChineseMap;
import com.reclizer.csgobox.v26_1_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_1_2.box.GradeGroup;
import com.reclizer.csgobox.v26_1_2.item.ModItems;
import com.reclizer.csgobox.v26_1_2.utils.RenderFontTool;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.DisplayRenderer;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.SimpleDisplayRenderer;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REI category "开箱概率": one display per box definition. The key item is the
 * input, the box item and its whole item pool are outputs (so any drop can be
 * reverse-looked-up), and the drawn text column shows the drop rate, per-grade
 * weight percentages and entity drop rates. Percentages use the exact server
 * semantics of {@code OddsCalculator.pickGrade} + {@code GradeMap.pickRandom}
 * via {@link BoxOdds}.
 */
public final class BoxOpenCategory implements DisplayCategory<BoxReiDisplay> {

    public static final CategoryIdentifier<BoxReiDisplay> TYPE =
            CategoryIdentifier.of("csgobox", "box_open");

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

    @Override
    public CategoryIdentifier<BoxReiDisplay> getCategoryIdentifier() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("rei.csgobox.category.box_open");
    }

    @Override
    public Renderer getIcon() {
        List<BoxReiDisplay> displays = BoxReiDisplay.fromRegistry();
        if (!displays.isEmpty()) {
            return EntryStacks.of(displays.get(0).boxStack());
        }
        return EntryStacks.of(ModItems.ITEM_CSGOBOX.get());
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public int getDisplayWidth(BoxReiDisplay display) {
        return WIDTH;
    }

    @Override
    public DisplayRenderer getDisplayRenderer(BoxReiDisplay display) {
        return SimpleDisplayRenderer.from(display.getInputEntries(), display.getOutputEntries());
    }

    @Override
    public List<Widget> setupDisplay(BoxReiDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createCategoryBase(bounds));

        if (!display.keyStack().isEmpty()) {
            widgets.add(slot(bounds, 2, 2, display.keyStack(), false));
        }
        widgets.add(slot(bounds, 2, 26, display.boxStack(), true));

        int slotIndex = 0;
        for (GradeGroup grade : display.definition().grades()) {
            for (ItemStack item : grade.items()) {
                if (slotIndex >= MAX_ITEM_SLOTS) {
                    break;
                }
                int col = slotIndex % GRID_COLS;
                int row = slotIndex / GRID_COLS;
                widgets.add(slot(bounds, GRID_X + col * SLOT_PITCH, GRID_Y + row * SLOT_PITCH, item, true));
                slotIndex++;
            }
            if (slotIndex >= MAX_ITEM_SLOTS) {
                break;
            }
        }

        widgets.add(Widgets.createDrawableWidget((gui, mouseX, mouseY, delta) ->
                drawProbabilityText(gui, display.definition())));
        return widgets;
    }

    private static Slot slot(Rectangle bounds, int x, int y, ItemStack stack, boolean output) {
        Slot slot = Widgets.createSlot(new Point(bounds.x + x, bounds.y + y));
        slot.entry(EntryStacks.of(stack));
        if (output) {
            slot.markOutput();
        } else {
            slot.markInput();
        }
        return slot;
    }

    private void drawProbabilityText(GuiGraphicsExtractor gui, BoxDefinition definition) {
        Font font = Minecraft.getInstance().font;
        if (font == null) {
            return;
        }
        int[] weights = definition.getWeightArray();
        int y = TEXT_START_Y;

        drawText(gui, font, TEXT_X, y, COLOR_BODY,
                Component.translatable("rei.csgobox.recipe.drop_rate", formatPercent(definition.dropRate())));
        y += LINE_PITCH;

        int shown = 0;
        for (GradeGroup grade : definition.grades()) {
            if (shown >= MAX_GRADE_LINES) {
                break;
            }
            double chance = BoxOdds.gradeChance(weights, BoxGrades.gradeLevel(grade.id()));
            drawText(gui, font, TEXT_X, y, grade.color(),
                    Component.translatable("rei.csgobox.recipe.grade_line", grade.displayName(), formatPercent(chance)));
            shown++;
            y += LINE_PITCH;
        }
        if (definition.grades().size() > MAX_GRADE_LINES) {
            drawText(gui, font, TEXT_X, y, COLOR_MUTED,
                    Component.translatable("rei.csgobox.recipe.more_grades", definition.grades().size()));
            y += LINE_PITCH;
        }
        for (Map.Entry<Identifier, Float> entry : definition.entityDropRates().entrySet()) {
            if (y + LINE_PITCH > HEIGHT) {
                break;
            }
            drawText(gui, font, TEXT_X, y, COLOR_MUTED,
                    Component.translatable("rei.csgobox.recipe.entity_rate",
                            EntityChineseMap.getDisplayName(entry.getKey().toString()),
                            formatPercent(entry.getValue())));
            y += LINE_PITCH;
        }

        int totalItems = definition.grades().stream().mapToInt(g -> g.items().size()).sum();
        if (totalItems > MAX_ITEM_SLOTS) {
            drawText(gui, font, GRID_X, GRID_Y + GRID_ROWS * SLOT_PITCH, COLOR_MUTED,
                    Component.translatable("rei.csgobox.recipe.more_items", totalItems));
        }
    }

    private static void drawText(GuiGraphicsExtractor gui, Font font, int x, int y, int color, Component text) {
        FormattedCharSequence sequence = text.getVisualOrderText();
        RenderFontTool.drawString(gui, font, sequence, x, y, 0, 0, TEXT_SCALE, color);
    }

    private static String formatPercent(double chance) {
        return String.format("%.1f", chance * 100.0);
    }
}
