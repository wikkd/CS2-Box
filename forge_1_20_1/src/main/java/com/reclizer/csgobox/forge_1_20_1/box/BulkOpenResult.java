package com.reclizer.csgobox.forge_1_20_1.box;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public record BulkOpenResult(
        ItemStack resultItem,
        int resultGrade,
        long serverSeed,
        int winningIndex,
        List<ItemStack> animationItems,
        List<Integer> animationGrades,
        float wear,
        boolean fallback
) {
    public BulkOpenResult {
        resultItem = resultItem == null ? ItemStack.EMPTY : resultItem.copy();
    }

    public boolean hasAnimation() {
        return serverSeed != 0L || (animationItems != null && !animationItems.isEmpty());
    }
}
