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
     * Cubic-bezier(.25,.6,.3,1) evaluation (numerical x(t)=t solve, binary
     * search, 20 iterations, deviation < 1e-4). Migrated verbatim from
     * TerminalAnims.cubicBezierCurve — used by the terminal wear-bar arrow
     * and long-press fill.
     */
    public static float cubicBezierCurve(float t) {
        return cubicBezierX(clamp01(t), 0.25F, 0.60F, 0.30F, 1.00F);
    }

    private static float cubicBezierX(float x, float x1, float y1, float x2, float y2) {
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
