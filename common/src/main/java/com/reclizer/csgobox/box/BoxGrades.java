package com.reclizer.csgobox.box;

/**
 * Grade-tier constants and pure helpers shared by every platform loader.
 *
 * <p>Extracted from the per-platform {@code BoxDefinition} statics: grade
 * count, default grade weights, the grade-id → level mapping, and the
 * network/deserialization size caps. Pure data + pure functions — no
 * Minecraft types — so the values live here exactly once.</p>
 */
public final class BoxGrades {

    private BoxGrades() {
    }

    /** Number of grade tiers a box definition supports. */
    public static final int GRADE_COUNT = 5;

    /**
     * Default per-grade drop weights, ordered grade1 → grade5 (grade1 is the
     * most common tier). Sum ≈ 785; weights are relative, not percentages.
     */
    public static final int[] DEFAULT_WEIGHTS = new int[]{625, 125, 25, 6, 4};

    /** Cap on the number of drop-entity entries per box (network + sanity). */
    public static final int MAX_DROP_ENTITIES = 1024;

    /** Cap on the number of grade groups per box (network + sanity). */
    public static final int MAX_GRADES = 64;

    /** Cap on the number of per-entity drop-rate overrides per box. */
    public static final int MAX_ENTITY_DROP_RATES = 1024;

    /**
     * Maps a grade id to its level (1..5). Unknown ids map to 0 so callers
     * can skip unrecognized tiers instead of crashing.
     */
    public static int gradeLevel(String id) {
        return switch (id) {
            case "consumer" -> 1;
            case "industrial" -> 2;
            case "mil_spec" -> 3;
            case "restricted" -> 4;
            case "classified" -> 5;
            default -> 0;
        };
    }

    /** Clamps a drop rate into the valid [0, 1] range. */
    public static float clampDropRate(float rate) {
        return Math.clamp(rate, 0.0F, 1.0F);
    }
}
