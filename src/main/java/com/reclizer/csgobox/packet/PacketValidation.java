package com.reclizer.csgobox.packet;

import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

final class PacketValidation {
    private PacketValidation() {
    }

    static void requireSameSize(String leftName, List<?> left, String rightName, List<?> right) {
        if (left.size() != right.size()) {
            throw new IllegalArgumentException(leftName + " size " + left.size()
                    + " does not match " + rightName + " size " + right.size());
        }
    }

    static void requireMaxSize(String name, List<?> list, int maxSize) {
        if (list.size() > maxSize) {
            throw new IllegalArgumentException(name + " size " + list.size() + " exceeds max " + maxSize);
        }
    }

    static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }
        List<ItemStack> copies = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copies.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return List.copyOf(copies);
    }

    static List<Integer> copyClampedInts(List<Integer> values, int min, int max, int defaultValue) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Integer> copies = new ArrayList<>(values.size());
        for (Integer value : values) {
            copies.add(Mth.clamp(value == null ? defaultValue : value, min, max));
        }
        return List.copyOf(copies);
    }

    static List<Integer> copyNonNegativeInts(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Integer> copies = new ArrayList<>(values.size());
        for (Integer value : values) {
            copies.add(Math.max(0, value == null ? 0 : value));
        }
        return List.copyOf(copies);
    }

    static <T> void trimQueue(Queue<T> queue, int maxSize) {
        while (queue.size() >= maxSize) {
            queue.poll();
        }
    }
}
