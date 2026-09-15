package com.reclizer.csgobox.v1_21_1.top;

import com.reclizer.csgobox.box.BoxOdds;
import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.box.GradeGroup;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import mcjty.theoneprobe.api.IProbeHitEntityData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoEntityProvider;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * TOP entity provider for dropped box items: the drop rate plus per-grade
 * weight percentages, colored by grade. Reads the client
 * {@code BoxRegistry} (already synced), so no server round-trip is needed.
 */
public final class BoxItemEntityProvider implements IProbeInfoEntityProvider {

    private static final int MAX_GRADE_LINES = 5;

    @Override
    public String getID() {
        return "csgobox:box_item";
    }

    @Override
    public void addProbeEntityInfo(ProbeMode mode, IProbeInfo probeInfo, Player player, Level level,
                                   Entity entity, IProbeHitEntityData data) {
        if (!(entity instanceof ItemEntity itemEntity)) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemCsgoBox)) {
            return;
        }
        ResourceLocation boxId = ItemCsgoBox.getBoxId(stack);
        if (boxId == null) {
            return;
        }
        BoxDefinition definition = BoxRegistry.get(boxId);
        if (definition == null) {
            return;
        }

        probeInfo.text(Component.translatable("top.csgobox.box.drop_rate",
                formatPercent(definition.dropRate())));
        int[] weights = definition.getWeightArray();
        int shown = 0;
        for (GradeGroup grade : definition.grades()) {
            if (shown >= MAX_GRADE_LINES) {
                break;
            }
            double chance = BoxOdds.gradeChance(weights, com.reclizer.csgobox.box.BoxGrades.gradeLevel(grade.id()));
            probeInfo.text(Component.translatable("top.csgobox.box.grade_line",
                            grade.displayName(), formatPercent(chance))
                    .withStyle(style -> style.withColor(grade.color())));
            shown++;
        }
        if (definition.grades().size() > MAX_GRADE_LINES) {
            probeInfo.text(Component.translatable("top.csgobox.box.more_grades",
                    definition.grades().size()));
        }
    }

    private static String formatPercent(double chance) {
        return String.format("%.1f", chance * 100.0);
    }
}