package com.reclizer.csgobox.utils;

/**
 * Shared easing curves for all GUI screens (tick-driven) and the terminal
 * (wall-clock driven). Pure functions, no MC imports — safe for common/.
 * Curves migrated from terminal/TerminalAnims so every platform and the
 * terminal use one implementation.
 */
public final class Easing {

    private Easing() {
    }

    /** Number of segments in the cubic-bezier lookup table. */
    private static final int BEZIER_LUT_SIZE = 256;

    /**
     * Precomputed samples of cubic-bezier(.25,.6,.3,1) on [0,1]. Built once
     * from the exact {@code cubicBezierX} solver; {@code cubicBezierCurve}
     * does a linear interpolation of neighbouring samples, so per-frame
     * calls skip the 20-iteration binary search without changing the curve's
     * shape (error well below 1e-3).
     */
    private static final float[] BEZIER_LUT = buildBezierLut();

    private static float[] buildBezierLut() {
        float[] lut = new float[BEZIER_LUT_SIZE + 1];
        for (int i = 0; i <= BEZIER_LUT_SIZE; i++) {
            float x = (float) i / BEZIER_LUT_SIZE;
            // Exact endpoints; interior samples use the solver.
            if (x <= 0F) {
                lut[i] = 0F;
            } else if (x >= 1F) {
                lut[i] = 1F;
            } else {
                lut[i] = solveBezierX(x, 0.25F, 0.60F, 0.30F, 1.00F);
            }
        }
        return lut;
    }

    public static float clamp01(float v) {
        return v < 0F ? 0F : (v > 1F ? 1F : v);
    }

    public static float easeOutCubic(float t) {
        float u = 1F - clamp01(t);
        return 1F - u * u * u;
    }

    public static float easeOutQuad(float t) {
        float p = clamp01(t);
        return 1F - (1F - p) * (1F - p);
    }

    /** easeOutBack — flip-in overshoot (c1/c3 standard constants). */
    public static float easeOutBack(float t) {
        float p = clamp01(t);
        final float c1 = 1.70158F;
        final float c3 = c1 + 1F;
        float q = p - 1F;
        float v = 1F + c3 * q * q * q + c1 * q * q;
        return v > 1F ? 1F : v;
    }

    /** GLSL smoothstep on [a,b]. */
    public static float smoothstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3F - 2F * t);
    }

    /**
     * Cubic-bezier(.25,.6,.3,1) evaluation. Uses a precomputed lookup table
     * with linear interpolation (see {@link #BEZIER_LUT}) instead of the
     * 20-iteration binary search on every call — faster on the per-frame
     * terminal render path while keeping the same curve within 1e-3.
     * Migrated verbatim from TerminalAnims.cubicBezierCurve.
     */
    public static float cubicBezierCurve(float t) {
        float x = clamp01(t);
        // Map [0,1] onto the LUT grid; guard against rounding at x==1.
        float pos = x * BEZIER_LUT_SIZE;
        int i = (int) pos;
        if (i >= BEZIER_LUT_SIZE) {
            return 1F;
        }
        float frac = pos - i;
        return BEZIER_LUT[i] + (BEZIER_LUT[i + 1] - BEZIER_LUT[i]) * frac;
    }

    /**
     * Numerical x(t)=t solve for a cubic-bezier (binary search, 20 iterations,
     * deviation < 1e-4). Kept as the exact reference used to build the LUT.
     */
    private static float solveBezierX(float x, float x1, float y1, float x2, float y2) {
        if (x <= 0F) {
            return 0F;
        }
        if (x >= 1F) {
            return 1F;
        }
        float lo = 0F, hi = 1F, t = 0.5F;
        for (int i = 0; i < 20; i++) {
            t = (lo + hi) / 2F;
            float u = 1F - t;
            float bx = 3F * u * u * t * x1 + 3F * u * t * t * x2 + t * t * t;
            if (bx < x) {
                lo = t;
            } else {
                hi = t;
            }
        }
        float u = 1F - t;
        return 3F * u * u * t * y1 + 3F * u * t * t * y2 + t * t * t;
    }
}
