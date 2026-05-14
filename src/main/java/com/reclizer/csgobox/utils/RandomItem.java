package com.reclizer.csgobox.utils;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

import static com.reclizer.csgobox.utils.ColorTools.*;

public class RandomItem {

    public static ItemStack randomItems(Random rng, int grade, Map<ItemStack, Integer> itemMap) {
        List<ItemStack> candidateItem = new ArrayList<>(itemMap.keySet().stream().filter(key -> grade == 5 || itemMap.get(key) == grade).toList());
        if (candidateItem.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return candidateItem.get(rng.nextInt(candidateItem.size()));
    }

    public static int randomItemsGrade(Random rng, int[] weights, Player player) {
        int totalWeight = 0;
        for (int num : weights) {
            totalWeight += num;
        }
        int rn = rng.nextInt(totalWeight);
        float countRate = 0;
        int grade = 0;
        for (int num : weights) {
            if (rn < countRate + num) {
                break;
            }
            grade++;
            countRate += num;
        }
        return grade + 1;
    }
}