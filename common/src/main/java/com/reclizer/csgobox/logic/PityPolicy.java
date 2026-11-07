package com.reclizer.csgobox.logic;

import com.reclizer.csgobox.box.BoxGrades;

/**
 * v2.0.1 pity (保底) policy for box opening, parsed from the optional box
 * JSON field {@code "pity": { "grade": "classified", "every": 20 }}.
 *
 * <p>Semantics: after {@code every - 1} consecutive successful opens whose
 * result was BELOW {@code targetLevel} (the configured grade or higher), the
 * next open is forced to roll a grade within
 * {@code [targetLevel .. the box's tier count]}
 * (weighted by the existing per-grade weights). A result at or above the
 * target resets the counter; a forced pity roll also counts as a hit.</p>
 *
 * <p>Pure data + pure functions, no Minecraft types, so exactly one copy
 * lives in common and every platform reads the same behaviour. Pity state is
 * tracked per (player, box) in memory by {@link PityTracker}.</p>
 */
public final class PityPolicy {

    private final int targetLevel; // 1..the box's tier count (bounded by MAX_GRADES)
    private final int every;       // >= 2
    private final boolean firstOpen; // v2.x: the very first open is forced

    private PityPolicy(int targetLevel, int every, boolean firstOpen) {
        this.targetLevel = targetLevel;
        this.every = every;
        this.firstOpen = firstOpen;
    }

    /**
     * Creates a policy from a grade id and threshold, or {@code null} when the
     * id is unknown (level 0) or {@code every < 2} (invalid config → pity
     * disabled, never forces a roll).
     */
    public static PityPolicy of(String gradeId, int every) {
        return of(gradeId, every, false);
    }

    /** v2.x variant with the first-open guarantee (config {@code pity.first_open}). */
    public static PityPolicy of(String gradeId, int every, boolean firstOpen) {
        return ofLevel(BoxGrades.gradeLevel(gradeId), every, firstOpen);
    }

    /**
     * Creates a policy from a 1-based grade level, {@code null} when invalid.
     * The level is validated against {@link BoxGrades#MAX_GRADES} (the
     * network tier cap) rather than the legacy five-tier count — the box's
     * actual tier count is enforced where the policy is parsed and again at
     * roll time ({@code pickGradeWithPity} falls back to a normal roll when
     * the target exceeds the box's weights array).
     */
    public static PityPolicy ofLevel(int targetLevel, int every) {
        return ofLevel(targetLevel, every, false);
    }

    /** v2.x variant with the first-open guarantee (config {@code pity.first_open}). */
    public static PityPolicy ofLevel(int targetLevel, int every, boolean firstOpen) {
        if (targetLevel < 1 || targetLevel > BoxGrades.MAX_GRADES || every < 2) {
            return null;
        }
        return new PityPolicy(targetLevel, every, firstOpen);
    }

    /** v2.x: whether the very first open (no tracker record yet) is forced. */
    public boolean firstOpen() {
        return firstOpen;
    }

    /** The 1-based grade that a forced pity roll must reach (or exceed). */
    public int targetLevel() {
        return targetLevel;
    }

    /** Opens required between forced rolls when the target keeps being missed. */
    public int every() {
        return every;
    }

    /**
     * Whether the next open must be forced, given the current miss streak
     * (0 = the last open hit the target or no open yet).
     */
    public boolean shouldForce(int missStreak) {
        return missStreak >= every - 1;
    }

    /**
     * Advances the streak after one open with the given result grade:
     * resets to 0 on a hit (grade at or above the target), increments
     * otherwise. Pure, so bulk batches can recompute the final streak from
     * their result list without holding shared state.
     */
    public int nextStreak(int streak, int resultGrade) {
        if (resultGrade >= targetLevel) {
            return 0;
        }
        return streak + 1;
    }
}