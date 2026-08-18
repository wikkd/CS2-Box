package com.reclizer.csgobox.logic;

import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Animation strip constants and winning-position utilities for the box opening
 * animation.
 *
 * <p>Extracted from per-platform {@code PacketBoxOpenResult} constants and
 * {@code RandomItem.clampToValidItem} — algorithm unchanged.</p>
 */
public final class AnimationStrip {

    private AnimationStrip() {
    }

    /** Total number of items in the opening animation strip. */
    public static final int ITEM_COUNT = 50;

    /** Minimum (inclusive) winning index in the animation strip. */
    public static final int MIN_WINNING_INDEX = 35;

    /** Maximum (inclusive) winning index in the animation strip. */
    public static final int MAX_WINNING_INDEX = 44;

    /**
     * Computes a random winning index within [{@link #MIN_WINNING_INDEX},
     * {@link #MAX_WINNING_INDEX}], clamped to the actual item count.
     *
     * @param rng      random source
     * @param itemCount number of items in the animation strip
     * @return a valid winning index in [0, itemCount-1]
     */
    public static int randomWinningIndex(Random rng, int itemCount) {
        int maxIndex = itemCount - 1;
        int min = Math.min(MIN_WINNING_INDEX, maxIndex);
        int max = Math.min(MAX_WINNING_INDEX, maxIndex);
        if (max <= min) {
            return min;
        }
        return min + rng.nextInt(max - min + 1);
    }

    /**
     * Finds the nearest valid item around the requested index, searching
     * outward in both directions.
     *
     * @param items         the item list
     * @param startingIndex preferred index
     * @param valid         predicate returning true for valid (non-empty) items
     * @param <T>           item type
     * @return a valid index, or -1 when the list is empty or all entries are invalid
     */
    public static <T> int findNearestValid(List<T> items, int startingIndex, Predicate<T> valid) {
        if (items == null || items.isEmpty()) {
            return -1;
        }
        startingIndex = Math.min(Math.max(startingIndex, 0), items.size() - 1);
        if (valid.test(items.get(startingIndex))) {
            return startingIndex;
        }
        for (int offset = 1; offset < items.size(); offset++) {
            int right = startingIndex + offset;
            if (right < items.size() && valid.test(items.get(right))) return right;
            int left = startingIndex - offset;
            if (left >= 0 && valid.test(items.get(left))) return left;
        }
        return -1;
    }
}
