package com.reclizer.csgobox.box;

/**
 * Grade-tier constants and pure helpers shared by every platform loader.
 *
 * <p>Extracted from the per-platform {@code BoxDefinition} statics: grade
 * count, default grade weights, the grade-id → level mapping, and the
 * network/deserialization size caps. Pure data + pure functions — no
 * Minecraft types — so the values live here exactly once.</p>
 *
 * <p>Tier levels are <b>positional</b> (see {@link #levelAt}): a box's tier
 * count is whatever its config declares (1..{@link #MAX_GRADES}, default 5
 * via the legacy {@code random}/{@code grade1..5} format). {@link #GRADE_COUNT}
 * and {@link #gradeLevel} only describe the legacy five canonical tiers.</p>
 */
public final class BoxGrades {

    private BoxGrades() {
    }

    /** Number of grade tiers the legacy {@code random}/{@code grade1..5}
     *  format supports, and the tier count a box gets when it configures
     *  none (the default). Custom {@code grades} arrays may declare fewer
     *  or more tiers (up to {@link #MAX_GRADES}). */
    public static final int GRADE_COUNT = 5;

    /**
     * Default per-grade drop weights, ordered grade1 → grade5 (grade1 is the
     * most common tier). Sum ≈ 785; weights are relative, not percentages.
     */
    public static final int[] DEFAULT_WEIGHTS = new int[]{625, 125, 25, 6, 4};

    /** Default ARGB color for custom tiers configured without {@code "color"}. */
    public static final int DEFAULT_TIER_COLOR = 0xFFFFFFFF;

    /** Cap on the number of drop-entity entries per box (network + sanity). */
    public static final int MAX_DROP_ENTITIES = 1024;

    /** Cap on the number of grade groups per box (network + sanity). */
    public static final int MAX_GRADES = 64;

    /** Cap on the number of per-entity drop-rate overrides per box. */
    public static final int MAX_ENTITY_DROP_RATES = 1024;

    /**
     * Maps a legacy grade id to its level (1..5). Only meaningful for the
     * five canonical tiers of the legacy format; custom {@code grades} tiers
     * resolve their level positionally via {@link #levelAt} instead.
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

    /**
     * Positional tier level: the tier at {@code zeroBasedIndex} of a box's
     * tier list ordered highest → lowest has level {@code tierCount -
     * index} (level 1 = the most common tier, {@code tierCount} = the
     * rarest). The single level definition shared by the roll, pity and
     * display layers; for a legacy box whose tiers are all canonical and
     * prefix-complete this equals {@link #gradeLevel}.
     */
    public static int levelAt(int tierCount, int zeroBasedIndex) {
        return tierCount - zeroBasedIndex;
    }

    /**
     * Parses a {@code "#RRGGBB"} tier color into 0xFFRRGGBB (ARGB), or
     * {@code null} when the string is malformed (callers report and fall
     * back to {@link #DEFAULT_TIER_COLOR}).
     */
    public static Integer parseTierColor(String raw) {
        if (raw == null || raw.length() != 7 || raw.charAt(0) != '#') {
            return null;
        }
        if (!raw.substring(1).matches("[0-9A-Fa-f]{6}")) {
            return null;
        }
        return 0xFF000000 | Integer.parseInt(raw.substring(1), 16);
    }

    /** Clamps a drop rate into the valid [0, 1] range. */
    public static float clampDropRate(float rate) {
        return Math.min(Math.max(rate, 0.0F), 1.0F);
    }
}
