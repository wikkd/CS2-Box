package com.reclizer.csgobox.v1_21_4.box;

import com.reclizer.csgobox.logic.GradeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side snapshot of the data needed to compute bulk box results off the
 * main thread. Built on the main thread from {@link BoxDefinition} + {@link ItemCsgoBox}
 * and consumed (read-only) by the {@code computeKResults} background task.
 */
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
