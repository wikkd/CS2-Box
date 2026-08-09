package com.reclizer.csgobox.v26_1_2.box;

import com.reclizer.csgobox.logic.AnimationStrip;
import com.reclizer.csgobox.logic.GradeMap;
import com.reclizer.csgobox.logic.OddsCalculator;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the animation strip (item + grade per slot, plus the nearest valid
 * winning index) shared by the single-open and bulk-open paths. The grade pool
 * is taken from the caller-provided {@link GradeMap}, keep-alive for exact
 * parity with the pool the caller later uses for fallbacks and grants.
 *
 * <p>The caller decides what an absent winner ({@code winningIndex < 0}, every
 * strip slot empty) means: the single-open path rejects the whole open while
 * the bulk path coerces the index to 0.</p>
 */
public final class BoxStripGenerator {

    private BoxStripGenerator() {
    }

    /**
     * Mutable strip carrier; the callers may still patch the winning slot
     * (fallback resolution) before the strip is transmitted.
     */
    public record Strip(List<ItemStack> items, List<Integer> grades, int winningIndex) {
    }

    public static Strip generate(GradeMap<ItemStack> gradeMap, int[] weights, Random rng) {
        List<ItemStack> items = new ArrayList<>(AnimationStrip.ITEM_COUNT);
        List<Integer> grades = new ArrayList<>(AnimationStrip.ITEM_COUNT);
        for (int j = 0; j < AnimationStrip.ITEM_COUNT; j++) {
            int g = OddsCalculator.pickGrade(rng, weights);
            ItemStack s = gradeMap.pickRandom(rng, g);
            if (s == null) {
                s = gradeMap.findFallback(g);
            }
            if (s == null) {
                s = ItemStack.EMPTY;
            }
            items.add(s);
            grades.add(Mth.clamp(g, 1, 5));
        }
        int winningIndex = AnimationStrip.randomWinningIndex(rng, items.size());
        winningIndex = AnimationStrip.findNearestValid(items, winningIndex, stack -> !stack.isEmpty());
        return new Strip(items, grades, winningIndex);
    }
}