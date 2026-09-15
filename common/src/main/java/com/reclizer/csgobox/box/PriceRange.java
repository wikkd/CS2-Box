package com.reclizer.csgobox.box;

import java.util.function.IntUnaryOperator;

/**
 * One entry of the central terminal price table
 * ({@code config/csbox/_prices.json}): either a fixed price
 * ({@code min == max}) or a random range ({@code [min, max]}, inclusive,
 * uniformly sampled per terminal offer). A fixed price is written as a bare
 * integer in the JSON file and a range as a two-element array, mirroring the
 * {@code count} interval syntax.
 *
 * <p>{@link #UNPRICED} ({@code (-1, -1)}) is the in-memory sentinel used by
 * the loaders' {@code GradeGroup} price lists to mean "no price-table entry":
 * the terminal never offers such items and the recycler pays 0 (the old
 * grade-default fallback was removed). It is never produced by
 * {@link PriceTable#parse}; entries absent from the table resolve to
 * {@code null} there.</p>
 *
 * <p>Pure value type — no Minecraft or platform imports — so it compiles in
 * the common module and serializes identically on every platform.</p>
 */
public record PriceRange(int min, int max) {

    /** Sentinel: no table price — the item is never offered by terminals and
     *  pays 0 at the recycler (no grade-default fallback). */
    public static final PriceRange UNPRICED = new PriceRange(-1, -1);

    /** Constructs a range; {@code min <= max} is the only invariant enforced
     *  (negative values are reserved for the {@link #UNPRICED} sentinel). */
    public PriceRange {
        if (min > max) {
            throw new IllegalArgumentException("price range min=" + min + " > max=" + max);
        }
    }

    /** Fixed price range ({@code [v, v]}); rejects negative values. */
    public static PriceRange fixed(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative fixed price " + value);
        }
        return new PriceRange(value, value);
    }

    /** True for the {@link #UNPRICED} sentinel only. */
    public boolean isUnpriced() {
        return min < 0 || max < 0;
    }

    /** True when this is a fixed price, not a random range. */
    public boolean isFixed() {
        return !isUnpriced() && min == max;
    }

    /**
     * Uniform random integer in {@code [min, max]} (inclusive). The bound is
     * drawn through {@code nextBounded} (e.g. a {@code java.util.Random} or a
     * Minecraft {@code RandomSource}), keeping this type Minecraft-free.
     * {@code nextBounded} receives the exclusive upper bound
     * ({@code max - min + 1}).
     */
    public int sample(IntUnaryOperator nextBounded) {
        if (isUnpriced()) {
            throw new IllegalStateException("cannot sample UNPRICED price range");
        }
        if (isFixed()) {
            return min;
        }
        return min + nextBounded.applyAsInt(max - min + 1);
    }

    /** Human-readable form used in diagnostics: {@code 1500} or
     *  {@code [1500, 3000]}. */
    public String toDisplayString() {
        return isUnpriced() ? "-1"
                : isFixed() ? String.valueOf(min)
                : "[" + min + ", " + max + "]";
    }
}