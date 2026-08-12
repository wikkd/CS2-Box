package com.reclizer.csgobox.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TerminalAnims} easing / timeline functions.
 */
final class TerminalAnimsTest {

    @Test
    @DisplayName("easeOutCubic boundaries and monotonicity")
    void easeOutCubic() {
        assertEquals(0F, TerminalAnims.easeOutCubic(0F), 1e-6F);
        assertEquals(1F, TerminalAnims.easeOutCubic(1F), 1e-6F);
        assertEquals(1F, TerminalAnims.easeOutCubic(1.5F), 1e-6F);
        float prev = -1;
        for (int i = 0; i <= 20; i++) {
            float v = TerminalAnims.easeOutCubic(i / 20F);
            assertTrue(v >= prev, "must be non-decreasing");
            prev = v;
        }
    }

    @Test
    @DisplayName("cubic-bezier(.25,.6,.3,1) endpoints, monotonicity, value at t=.5")
    void bezier() {
        assertEquals(0F, TerminalAnims.cubicBezierCurve(0F), 1e-4F);
        assertEquals(1F, TerminalAnims.cubicBezierCurve(1F), 1e-4F);
        float prev = -1;
        for (int i = 0; i <= 40; i++) {
            float v = TerminalAnims.cubicBezierCurve(i / 40F);
            assertTrue(v >= prev - 1e-4F, "must be non-decreasing");
            prev = v;
        }
        // CSS cubic-bezier(.25,.6,.3,1) at x=0.5 evaluates to ~0.884
        assertEquals(0.884F, TerminalAnims.cubicBezierCurve(0.5F), 0.01F);
    }

    @Test
    @DisplayName("arrow glides 0..1 over ARROW_MS")
    void arrow() {
        assertEquals(0F, TerminalAnims.arrowLeft(1000, 1000));
        float mid = TerminalAnims.arrowLeft(1400, 1000); // 400ms of 950ms
        assertTrue(mid > 0F && mid < 1F, "mid glide in (0,1): " + mid);
        assertEquals(1F, TerminalAnims.arrowLeft(1000 + TerminalAnims.ARROW_MS, 1000));
        assertEquals(1F, TerminalAnims.arrowLeft(5000, 1000));
    }

    @Test
    @DisplayName("flipIn: rows staggered, row 0 leads row 1")
    void flip() {
        long t0 = 5000;
        // at 100ms row 0 is animating, row 1 has not started
        float a0 = TerminalAnims.flipAlpha(t0 + 100, t0, 0);
        float a1 = TerminalAnims.flipAlpha(t0 + 100, t0, 1);
        assertTrue(a0 > a1);
        assertTrue(a0 > 0F && a0 < 1F);
        assertEquals(1F, TerminalAnims.flipAlpha(t0 + TerminalAnims.FLIP_IN_MS * 2, t0, 3));
        assertEquals(0F, TerminalAnims.flipAlpha(t0, t0, 0));
        // slide starts negative and ends at 0
        assertTrue(TerminalAnims.flipSlideY(t0, t0, 0) < 0F);
        assertEquals(0F, TerminalAnims.flipSlideY(t0 + 5000, t0, 0), 1e-4F);
    }

    @Test
    @DisplayName("hold fill: 0 at start, 1 at HOLD_MS, eased shape")
    void hold() {
        assertEquals(0F, TerminalAnims.holdFill(0, 0));
        assertEquals(1F, TerminalAnims.holdFill(TerminalAnims.HOLD_MS, 0));
        float mid = TerminalAnims.holdFill(TerminalAnims.HOLD_MS / 2, 0);
        assertTrue(mid > 0.3F && mid < 0.95F, "cubic-bezier(.25,.6,.3,1) mid ≈ 0.884, got " + mid);
        assertEquals(0.884F, mid, 0.02F);
    }

    @Test
    @DisplayName("scan: 0..1 ease-out over SCAN_MS")
    void scan() {
        assertEquals(0F, TerminalAnims.scanX(10, 10));
        assertEquals(1F, TerminalAnims.scanX(10 + TerminalAnims.SCAN_MS, 10));
        float half = TerminalAnims.scanX(10 + TerminalAnims.SCAN_MS / 2, 10);
        assertTrue(half > 0.5F && half < 1F, "ease-out: half-way progress > 0.5");
    }

    @Test
    @DisplayName("typing dots: staggered 3-dot pulse, alpha in [0.25,1]")
    void typingDots() {
        long t = 1234567L;
        for (int i = 0; i < 3; i++) {
            float a = TerminalAnims.typingDotAlpha(t, i);
            assertTrue(a >= 0.249F && a <= 1.001F, "alpha in range, got " + a);
            assertTrue(TerminalAnims.typingDotY(t, i) <= 0F, "dot lifts up");
            assertTrue(TerminalAnims.typingDotY(t, i) >= -3.001F);
        }
        // stagger: same t, different i => different phases (period 1050 / 180)
        assertTrue(Math.abs(TerminalAnims.typingDotAlpha(t, 0) - TerminalAnims.typingDotAlpha(t, 1)) > 1e-6F);
        // peak value occurs somewhere over one period
        float peak = 0;
        for (long tt = t; tt < t + TerminalAnims.DOT_PERIOD_MS; tt += 1) {
            peak = Math.max(peak, TerminalAnims.typingDotAlpha(tt, 0));
        }
        assertEquals(1F, peak, 1e-3F, "peak must reach 1.0");
    }

    @Test
    @DisplayName("slot index cycles and swap pop is ease-out")
    void slot() {
        assertEquals(0, TerminalAnims.slotIndex(0, 4));
        assertEquals(1, TerminalAnims.slotIndex(TerminalAnims.SLOT_SWAP_MS, 4));
        assertEquals(3, TerminalAnims.slotIndex(TerminalAnims.SLOT_SWAP_MS * 3, 4));
        assertEquals(0, TerminalAnims.slotIndex(TerminalAnims.SLOT_SWAP_MS * 4, 4));
        assertEquals(0F, TerminalAnims.swapPop(0, 0));
        assertEquals(1F, TerminalAnims.swapPop(180, 0));
    }

    @Test
    @DisplayName("countdown renders DD:HH:MM:SS")
    void countdown() {
        assertEquals("00:03:00:00", TerminalAnims.countdownText(NegotiationModel.COUNT_INITIAL_MS));
        assertEquals("00:00:00:00", TerminalAnims.countdownText(0));
        assertEquals("00:00:00:00", TerminalAnims.countdownText(-500));
        assertEquals("00:00:01:01", TerminalAnims.countdownText(61_000));
        assertEquals("01:00:00:00", TerminalAnims.countdownText(86_400_000));
    }
}
