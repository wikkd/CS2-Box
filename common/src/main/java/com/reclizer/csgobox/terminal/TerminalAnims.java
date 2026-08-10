package com.reclizer.csgobox.terminal;

import com.reclizer.csgobox.utils.Easing;

/**
 * Timeline / easing helpers for the terminal screens — pure functions over a
 * millisecond clock (client {@code Util.getMillis()}), mirroring the HTML
 * prototype timings so the three platform renderers share one implementation.
 *
 * <p>Constants align with the HTML prototype (design/terminal-chat.html):
 * typing pulse 1.05s stagger .18s; flipIn .45s stagger .09s/row (approximated
 * as slide+alpha since decoupled poses have no X rotation); wear-bar arrow and
 * scan .95s; long-press hold 700ms; random-item slot 2.5s.
 */
public final class TerminalAnims {

    /** Dealer "typing" delay before an offer card appears. */
    public static final long TYPING_MS = 1100L;
    /** Offer-card flip-in duration (slide+alpha approximation). */
    public static final long FLIP_IN_MS = 450L;
    /** Per-row stagger of the four offer-card info lines. */
    public static final long FLIP_STAGGER_MS = 90L;
    /** Wear-bar arrow glide. */
    public static final long ARROW_MS = 950L;
    /** Wear-bar scan sweep. */
    public static final long SCAN_MS = 950L;
    /** Long-press capsule fill. */
    public static final long HOLD_MS = 700L;
    /** Random-item slot cycle. */
    public static final long SLOT_SWAP_MS = 2500L;
    /** Typing dot pulse period. */
    public static final long DOT_PERIOD_MS = 1050L;
    /** Typing dot stagger. */
    public static final long DOT_STAGGER_MS = 180L;

    /** Control points of the CSS curve used by hold-fill and the arrow. */
    public static final float CURVE_X1 = 0.25F;
    public static final float CURVE_Y1 = 0.60F;
    public static final float CURVE_X2 = 0.30F;
    public static final float CURVE_Y2 = 1.00F;

    private TerminalAnims() {
    }

    public static float clamp01(float v) {
        return Easing.clamp01(v);
    }

    public static float easeOutCubic(float t) {
        return Easing.easeOutCubic(t);
    }

    /** easeOutBack — used by the flip-in slide overshoot. */
    public static float easeOutBack(float t) {
        return Easing.easeOutBack(t);
    }

    /**
     * Cubic-bezier(.25,.6,.3,1) evaluation. Delegates to {@link Easing}.
     * CURVE_X1..Y2 constants are retained for source compatibility; the
     * canonical control points now live in Easing.cubicBezierCurve().
     */
    public static float cubicBezierCurve(float t) {
        return Easing.cubicBezierCurve(t);
    }

    // ---- wear-bar arrow ----

    /** 0..1 glide of the arrow toward the wear value, over ARROW_MS. */
    public static float arrowLeft(long tMs, long startMs) {
        return cubicBezierCurve((float) (tMs - startMs) / ARROW_MS);
    }

    // ---- flip-in (slide + alpha approximation) ----

    /** 0..1 alpha of offer-card row {@code row} (staggered 90ms/row). */
    public static float flipAlpha(long tMs, long startMs, int row) {
        long elapsed = tMs - startMs - row * FLIP_STAGGER_MS;
        return easeOutCubic((float) elapsed / FLIP_IN_MS);
    }

    /** Slide offset in px of offer-card row {@code row} (-8 -> 0). */
    public static float flipSlideY(long tMs, long startMs, int row) {
        long elapsed = tMs - startMs - row * FLIP_STAGGER_MS;
        float p = easeOutCubic((float) elapsed / FLIP_IN_MS);
        return -8F * (1F - p);
    }

    // ---- long-press hold ----

    /** 0..1 fill progress of the accept/reject capsule (700ms). */
    public static float holdFill(long tMs, long startMs) {
        return cubicBezierCurve((float) (tMs - startMs) / HOLD_MS);
    }

    // ---- wear-bar scan ----

    /** 0..1 scan sweep position (ease-out over SCAN_MS). */
    public static float scanX(long tMs, long startMs) {
        return easeOutCubic((float) (tMs - startMs) / SCAN_MS);
    }

    // ---- typing dots ----

    private static float typingPhase(long tMs, int i) {
        long phase = (tMs + i * DOT_STAGGER_MS) % DOT_PERIOD_MS;
        if (phase < 0) {
            phase += DOT_PERIOD_MS;
        }
        return (float) phase / DOT_PERIOD_MS;
    }

    /** Dot opacity 0.25..1, peak at 40% of the period. */
    public static float typingDotAlpha(long tMs, int i) {
        float p = typingPhase(tMs, i);
        float shape = p < 0.4F ? p / 0.4F : 1F - (p - 0.4F) / 0.6F;
        return 0.25F + 0.75F * shape;
    }

    /** Dot lift in px (negative = up), -3 at the peak. */
    public static float typingDotY(long tMs, int i) {
        float p = typingPhase(tMs, i);
        float shape = p < 0.4F ? p / 0.4F : 1F - (p - 0.4F) / 0.6F;
        return -3F * shape;
    }

    /** Eased counter/seconds flip: 0..1 over 300ms. */
    public static float counterFlip(long tMs, long startMs) {
        return easeOutCubic((float) (tMs - startMs) / 300F);
    }

    /** Inspect auto-spin angle in degrees at time tMs (0.4°/frame @60fps). */
    public static float spinDeg(long tMs) {
        return tMs / 1000F * 24F;
    }

    // ---- random-item slot ----

    /** Index of the currently shown item (0..n-1), cycling every 2.5s. */
    public static int slotIndex(long tMs, int n) {
        long idx = (tMs / SLOT_SWAP_MS) % n;
        return (int) (idx < 0 ? idx + n : idx);
    }

    /** Progress 0..1 of the current slot's swap pop-in (0.18s). */
    public static float swapPop(long tMs, long slotStartMs) {
        return easeOutCubic((float) (tMs - slotStartMs) / 180F);
    }

    // ---- countdown ----

    /** DD:HH:MM:SS rendering (region 9). */
    public static String countdownText(long remainMs) {
        long total = Math.max(0L, remainMs / 1000L);
        long d = total / 86400L;
        long h = (total % 86400L) / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        return String.format("%02d:%02d:%02d:%02d", d, h, m, s);
    }
}
