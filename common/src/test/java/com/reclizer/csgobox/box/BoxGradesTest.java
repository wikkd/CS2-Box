package com.reclizer.csgobox.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BoxGrades}: the grade-id → level mapping and the
 * drop-rate clamp, pinned to the historical per-platform BoxDefinition
 * behavior.
 */
final class BoxGradesTest {

    @Test
    @DisplayName("gradeLevel maps the five canonical grade ids to 1..5")
    void gradeLevelMapsFiveTiers() {
        assertEquals(1, BoxGrades.gradeLevel("consumer"));
        assertEquals(2, BoxGrades.gradeLevel("industrial"));
        assertEquals(3, BoxGrades.gradeLevel("mil_spec"));
        assertEquals(4, BoxGrades.gradeLevel("restricted"));
        assertEquals(5, BoxGrades.gradeLevel("classified"));
    }

    @Test
    @DisplayName("gradeLevel returns 0 for unknown ids")
    void gradeLevelUnknownIdIsZero() {
        assertEquals(0, BoxGrades.gradeLevel("legendary"));
        assertEquals(0, BoxGrades.gradeLevel(""));
    }

    @Test
    @DisplayName("default weights are the five historical values in grade1..grade5 order")
    void defaultWeights() {
        assertArrayEquals(new int[]{625, 125, 25, 6, 4}, BoxGrades.DEFAULT_WEIGHTS);
        assertEquals(BoxGrades.GRADE_COUNT, BoxGrades.DEFAULT_WEIGHTS.length);
    }

    @Test
    @DisplayName("clampDropRate pins values into [0, 1]")
    void clampDropRate() {
        assertEquals(0.0F, BoxGrades.clampDropRate(-3.0F));
        assertEquals(0.0F, BoxGrades.clampDropRate(0.0F));
        assertEquals(0.12F, BoxGrades.clampDropRate(0.12F));
        assertEquals(1.0F, BoxGrades.clampDropRate(1.0F));
        assertEquals(1.0F, BoxGrades.clampDropRate(42.0F));
        // Math.clamp propagates NaN (verified on Java 21); pin that behaviour so
        // a bad config value doesn't silently change.
        assertTrue(Float.isNaN(BoxGrades.clampDropRate(Float.NaN)),
                "clampDropRate(NaN) must stay NaN");
    }
}
