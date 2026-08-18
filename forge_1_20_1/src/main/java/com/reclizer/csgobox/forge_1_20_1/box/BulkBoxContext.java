package com.reclizer.csgobox.forge_1_20_1.box;

import com.reclizer.csgobox.logic.GradeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record BulkBoxContext(
        ResourceLocation boxId,
        int[] weights,
        GradeMap<ItemStack> gradeMap
) {
    public BulkBoxContext {
        weights = weights == null ? new int[0] : weights.clone();
        if (gradeMap == null) {
            gradeMap = new GradeMap<>(null, stack -> !stack.isEmpty(), ItemStack::copy);
        }
    }
}
