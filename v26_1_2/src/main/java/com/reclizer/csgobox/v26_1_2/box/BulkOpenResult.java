package com.reclizer.csgobox.v26_1_2.box;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Single box result, produced off the main thread. Box 1 of a bulk request
 * carries the full 50-icon animation strip so the client can replay it; all
 * other boxes carry only the winning item + grade.
 *
 * <p>v2.0.1: {@code pityGrade} is the grade the roll actually landed on
 * (before item-pool fallback / resolveGrade corrections) — the bulk path
 * recomputes the pity streak from it, so a forced roll that fell back to a
 * lower-tier item still counts as a hit.</p>
 */
public record BulkOpenResult(
        ItemStack resultItem,
        int resultGrade,
        long serverSeed,
        int winningIndex,
        List<ItemStack> animationItems,
        List<Integer> animationGrades,
        float wear,
        boolean fallback,
        int pityGrade
) {
    public BulkOpenResult {
        resultItem = resultItem == null ? ItemStack.EMPTY : resultItem.copy();
    }

    public boolean hasAnimation() {
        return serverSeed != 0L || (animationItems != null && !animationItems.isEmpty());
    }
}
