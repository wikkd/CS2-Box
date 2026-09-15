package com.reclizer.csgobox.v26_2.jade;

import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.v26_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_2.box.GradeGroup;
import com.reclizer.csgobox.v26_2.item.ItemCsgoBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade provider for dropped box items: shows the drop rate and the per-grade
 * weight percentages of the box the item carries. Everything is client-side —
 * the box registry is already synced to the client by
 * {@code PacketSyncBoxDefinitions}, so no server data round-trip is needed.
 */
public final class BoxItemEntityProvider implements IEntityComponentProvider {

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "box_item_entity");

    private static final int MAX_GRADE_LINES = 5;

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
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

        tooltip.add(Component.translatable("jade.csgobox.box.drop_rate",
                formatPercent(definition.dropRate())));
        int[] weights = definition.getWeightArray();
        int shown = 0;
        for (GradeGroup grade : definition.grades()) {
            if (shown >= MAX_GRADE_LINES) {
                break;
            }
            double chance = BoxOdds.gradeChance(weights, com.reclizer.csgobox.box.BoxGrades.gradeLevel(grade.id()));
            tooltip.add(Component.translatable("jade.csgobox.box.grade_line",
                            grade.displayName(), formatPercent(chance))
                    .withStyle(style -> style.withColor(grade.color())));
            shown++;
        }
        if (definition.grades().size() > MAX_GRADE_LINES) {
            tooltip.add(Component.translatable("jade.csgobox.box.more_grades",
                    definition.grades().size()));
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    private static String formatPercent(double chance) {
        return String.format("%.1f", chance * 100.0);
    }
}