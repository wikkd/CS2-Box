package com.reclizer.csgobox.v26_1_2.wthit;

import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.v26_1_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_1_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_1_2.box.GradeGroup;
import com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * WTHIT provider for dropped box items: appends the drop rate and per-grade
 * weight percentages of the box the item carries. Reads the already-synced
 * client {@code BoxRegistry} — no server data round-trip.
 */
public final class BoxItemEntityProvider implements IEntityComponentProvider {

    private static final int MAX_GRADE_LINES = 5;

    @Override
    public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemCsgoBox)) {
            return;
        }
        Identifier boxId = ItemCsgoBox.getBoxId(stack);
        if (boxId == null) {
            return;
        }
        BoxDefinition definition = BoxRegistry.get(boxId);
        if (definition == null) {
            return;
        }

        tooltip.addLine(Component.translatable("wthit.csgobox.box.drop_rate",
                formatPercent(definition.dropRate())));
        int[] weights = definition.getWeightArray();
        int shown = 0;
        for (GradeGroup grade : definition.grades()) {
            if (shown >= MAX_GRADE_LINES) {
                break;
            }
            double chance = BoxOdds.gradeChance(weights, com.reclizer.csgobox.box.BoxGrades.gradeLevel(grade.id()));
            tooltip.addLine(Component.translatable("wthit.csgobox.box.grade_line",
                            grade.displayName(), formatPercent(chance))
                    .withStyle(style -> style.withColor(grade.color())));
            shown++;
        }
        if (definition.grades().size() > MAX_GRADE_LINES) {
            tooltip.addLine(Component.translatable("wthit.csgobox.box.more_grades",
                    definition.grades().size()));
        }
    }

    private static String formatPercent(double chance) {
        return String.format("%.1f", chance * 100.0);
    }
}