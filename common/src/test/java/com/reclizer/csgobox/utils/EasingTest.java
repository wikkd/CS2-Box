package com.reclizer.csgobox.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasingTest {

    @Test
    void clamp01Clamps() {
        assertEquals(0F, Easing.clamp01(-1F));
        assertEquals(1F, Easing.clamp01(2F));
        assertEquals(0.5F, Easing.clamp01(0.5F));
    }

    @Test
    void easeOutCubicEndpoints() {
        assertEquals(0F, Easing.easeOutCubic(0F));
        assertEquals(1F, Easing.easeOutCubic(1F));
    }

    @Test
    void easeOutCubicMid() {
        assertEquals(0.875F, Easing.easeOutCubic(0.5F), 1e-4F);
    }

    @Test
    void easeOutBackOvershoots() {
        assertTrue(Easing.easeOutBack(0.5F) > 0.5F);
        assertEquals(1F, Easing.easeOutBack(1F), 1e-4F);
    }

    @Test
    void cubicBezierEndpoints() {
        assertEquals(0F, Easing.cubicBezierCurve(0F), 1e-4F);
        assertEquals(1F, Easing.cubicBezierCurve(1F), 1e-4F);
    }

    @Test
    void cubicBezierIsMonotonic() {
        float prev = -1F;
        for (float t = 0F; t <= 1F; t += 0.05F) {
            float v = Easing.cubicBezierCurve(t);
            assertTrue(v >= prev);
            prev = v;
        }
    }

    @Test
    void smoothstepMid() {
        assertEquals(0.5F, Easing.smoothstep(0F, 1F, 0.5F), 1e-4F);
    }
}
