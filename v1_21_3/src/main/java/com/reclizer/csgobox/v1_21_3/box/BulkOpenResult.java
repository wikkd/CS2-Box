package com.reclizer.csgobox.v1_21_3.box;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Single box result, produced off the main thread. Box 1 of a bulk request
 * carries the full 50-icon animation strip so the client can replay it; all
 * other boxes carry only the winning item + grade.
 */
public record BulkOpenResult(
        ItemStack resultItem,
        int resultGrade,
        long serverSeed,
        int winningIndex,
        List<ItemStack> animationItems,
        List<Integer> animationGrades
) {
    public BulkOpenResult {
        resultItem = resultItem == null ? ItemStack.EMPTY : resultItem.copy();
    }

    public boolean hasAnimation() {
        return serverSeed != 0L || (animationItems != null && !animationItems.isEmpty());
    }
}
