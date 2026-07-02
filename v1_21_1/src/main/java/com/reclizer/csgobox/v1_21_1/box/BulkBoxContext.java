package com.reclizer.csgobox.v1_21_1.box;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Server-side snapshot of the data needed to compute bulk box results off the
 * main thread. Built on the main thread from {@link BoxDefinition} + {@link ItemCsgoBox}
 * and consumed (read-only) by the {@code computeKResults} background task.
 */
public record BulkBoxContext(
        ResourceLocation boxId,
        int[] weights,
        Map<Integer, List<ItemStack>> gradeMap
) {
    public BulkBoxContext {
        weights = weights == null ? new int[0] : weights.clone();
        gradeMap = gradeMap == null ? Map.of() : Map.copyOf(gradeMap);
    }
}
