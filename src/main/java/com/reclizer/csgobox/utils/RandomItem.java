package com.reclizer.csgobox.utils;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class RandomItem {

    private RandomItem() {
    }

    public static ItemStack randomItems(Random rng, int grade, Map<ItemStack, Integer> itemMap) {
        if (itemMap == null || itemMap.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> candidates = new ArrayList<>();
        for (Map.Entry<ItemStack, Integer> entry : itemMap.entrySet()) {
            ItemStack stack = entry.getKey();
            if (stack != null && !stack.isEmpty() && entry.getValue() != null && entry.getValue() == grade) {
                candidates.add(stack);
            }
        }
        if (candidates.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return candidates.get(rng.nextInt(candidates.size())).copy();
    }

    public static int randomItemsGrade(Random rng, int[] weights) {
        if (weights == null || weights.length == 0) return 1;
        long totalWeight = 0L;
        for (int num : weights) {
            if (num > 0) {
                totalWeight += num;
            }
        }
        if (totalWeight <= 0) return 1;

        long rn = nextLong(rng, totalWeight);
        long cumulative = 0L;
        for (int i = 0; i < weights.length; i++) {
            int weight = weights[i];
            if (weight <= 0) continue;
            if (rn < cumulative + weight) {
                return i + 1;
            }
            cumulative += weight;
        }
        return weights.length;
    }

    private static long nextLong(Random rng, long bound) {
        if (bound <= Integer.MAX_VALUE) {
            return rng.nextInt((int) bound);
        }
        long bits;
        long value;
        do {
            bits = rng.nextLong() >>> 1;
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0L);
        return value;
    }

    /**
     * Finds the nearest non-empty item around the requested index.
     *
     * @return a valid index, or -1 when the list is empty or all entries are empty
     */
    public static int clampToValidItem(List<ItemStack> items, int startingIndex) {
        if (items == null || items.isEmpty()) {
            return -1;
        }
        startingIndex = Math.clamp(startingIndex, 0, items.size() - 1);
        if (!items.get(startingIndex).isEmpty()) {
            return startingIndex;
        }
        for (int offset = 1; offset < items.size(); offset++) {
            int right = startingIndex + offset;
            if (right < items.size() && !items.get(right).isEmpty()) return right;
            int left = startingIndex - offset;
            if (left >= 0 && !items.get(left).isEmpty()) return left;
        }
        return -1;
    }

    public static ItemStack findFallback(int targetGrade, Map<ItemStack, Integer> group) {
        if (group == null || group.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (Map.Entry<ItemStack, Integer> entry : group.entrySet()) {
            if (isCandidate(entry, targetGrade)) {
                return entry.getKey().copy();
            }
        }
        for (int g = targetGrade - 1; g >= 1; g--) {
            for (Map.Entry<ItemStack, Integer> entry : group.entrySet()) {
                if (isCandidate(entry, g)) {
                    return entry.getKey().copy();
                }
            }
        }
        for (Map.Entry<ItemStack, Integer> entry : group.entrySet()) {
            ItemStack stack = entry.getKey();
            if (stack != null && !stack.isEmpty()) return stack.copy();
        }
        return ItemStack.EMPTY;
    }

    public static Map<Integer, List<ItemStack>> precomputeGradeMap(Map<ItemStack, Integer> itemMap) {
        Map<Integer, List<ItemStack>> gradeMap = new LinkedHashMap<>();
        if (itemMap == null || itemMap.isEmpty()) {
            return gradeMap;
        }
        for (Map.Entry<ItemStack, Integer> entry : itemMap.entrySet()) {
            ItemStack stack = entry.getKey();
            Integer grade = entry.getValue();
            if (stack == null || stack.isEmpty() || grade == null) continue;
            gradeMap.computeIfAbsent(grade, k -> new ArrayList<>()).add(stack);
        }
        return gradeMap;
    }

    public static ItemStack randomItemsFromGradeMap(Random rng, int grade, Map<Integer, List<ItemStack>> gradeMap) {
        if (gradeMap == null || gradeMap.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> candidates = gradeMap.get(grade);
        if (candidates == null || candidates.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return candidates.get(rng.nextInt(candidates.size())).copy();
    }

    public static ItemStack findFallbackFromGradeMap(int targetGrade, Map<Integer, List<ItemStack>> gradeMap) {
        if (gradeMap == null || gradeMap.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> sameGrade = gradeMap.get(targetGrade);
        if (sameGrade != null) {
            for (ItemStack stack : sameGrade) {
                if (!stack.isEmpty()) return stack.copy();
            }
        }
        for (int g = targetGrade - 1; g >= 1; g--) {
            List<ItemStack> lower = gradeMap.get(g);
            if (lower != null) {
                for (ItemStack stack : lower) {
                    if (!stack.isEmpty()) return stack.copy();
                }
            }
        }
        for (List<ItemStack> list : gradeMap.values()) {
            for (ItemStack stack : list) {
                if (!stack.isEmpty()) return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isCandidate(Map.Entry<ItemStack, Integer> entry, int grade) {
        ItemStack stack = entry.getKey();
        Integer entryGrade = entry.getValue();
        return stack != null && !stack.isEmpty() && entryGrade != null && entryGrade == grade;
    }
}
