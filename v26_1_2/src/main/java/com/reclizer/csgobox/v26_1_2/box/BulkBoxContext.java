package com.reclizer.csgobox.v26_1_2.box;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Server-side snapshot of the data needed to compute bulk box results off the
 * main thread. Built on the main thread from {@link BoxDefinition} + {@link ItemCsgoBox}
 * and consumed (read-only) by the {@code computeKResults} background task.
 */
public record BulkBoxContext(
        Identifier boxId,
        int[] weights,
        Map<Integer, List<ItemStack>> gradeMap
) {
    public BulkBoxContext {
        weights = weights == null ? new int[0] : weights.clone();
        gradeMap = gradeMap == null ? Map.of() : Map.copyOf(gradeMap);
    }
}
