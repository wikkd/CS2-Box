package com.reclizer.csgobox.utils;

import net.minecraft.world.item.ItemStack;
import java.util.*;

public class RandomItem {

    public static ItemStack randomItems(Random rng, int grade, Map<ItemStack, Integer> itemMap) {
        List<ItemStack> candidateItem = new ArrayList<>(itemMap.keySet().stream()
                .filter(key -> !key.isEmpty() && itemMap.get(key) == grade).toList());
        if (candidateItem.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return candidateItem.get(rng.nextInt(candidateItem.size())).copy();
    }

    public static int randomItemsGrade(Random rng, int[] weights) {
        int totalWeight = 0;
        for (int num : weights) {
            totalWeight += num;
        }
        int rn = rng.nextInt(totalWeight);
        float countRate = 0;
        int grade = weights.length;
        for (int num : weights) {
            if (rn < countRate + num) {
                break;
            }
            grade--;
            countRate += num;
        }
        return grade;
    }
}