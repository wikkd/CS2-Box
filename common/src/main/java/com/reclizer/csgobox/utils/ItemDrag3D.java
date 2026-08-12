package com.reclizer.csgobox.utils;

/**
 * 3D drag-rotation state for the GUI item preview — the "full combination"
 * scheme from the drag-feel lab, with inertia removed:
 *
 * <ul>
 *   <li>dead-zone + One-Euro low-pass filter on raw pointer deltas (kills
 *       sub-pixel jitter without adding visible lag),</li>
 *   <li>adaptive sensitivity (fast flicks loosen the grip),</li>
 *   <li>arcball: each filtered delta rotates a target quaternion,</li>
 *   <li>while dragging the shown orientation slerps toward the target,</li>
 *   <li>on release a damped spring settles the orientation — no inertia/spin.</li>
 * </ul>
 *
 * <p>Pure math (CONSTRAINT-001): no Minecraft classes. Drive pattern:</p>
 * <pre>
 *   mouseDragged  -&gt; {@link #accumulate(double, double)}
 *   render frame  -&gt; {@link #tick()}   (advances with real dt, applies pending input)
 *   mouseReleased -&gt; {@link #release()}
 * </pre>
 *
 * <p>The orientation is a unit quaternion in the same frame the renderer
 * applies rotations (horizontal drag spins the vertical axis, vertical drag
 * the horizontal axis). {@link #rotation()} feeds the 3D item renderer.</p>
 */
public final class ItemDrag3D {

    // Lab defaults for "全组合去掉惯性使用阻尼".
    private static final float SENS = 0.025F;          // rad/px base sensitivity
    private static final float DEAD_ZONE = 0.8F;       // px
    private static final float MIN_CUTOFF = 2.0F;      // Hz, One-Euro floor
    private static final float BETA = 0.4F;            // One-Euro velocity gain
    private static final float SPRING_FREQ = 3.5F;     // Hz
    private static final float SPRING_ZETA = 1.0F;     // critical damping
    private static final float SLERP_RATE = 12.0F;     // /s follow rate
    // Internal integration step for the release spring. Explicit Euler for
    // the damping term goes unstable once c*dt > 2 (c = 2*w*zeta ~= 44 with
    // the lab constants), i.e. at dt > ~45 ms — a low-FPS frame or the hitch
    // guard (DT_MAX = 0.1) would make the release "spin out of control".
    // Sub-stepping keeps the same physics stable at any frame rate.
    private static final float SPRING_SUBSTEP = 1.0F / 240.0F;
    private static final boolean ADAPTIVE = true;      // fast flick boost
    private static final float MAX_DELTA = 80.0F;      // px per frame clamp
    private static final float DT_MIN = 0.001F;        // s
    private static final float DT_MAX = 0.1F;          // s (frame hitch guard)
    private static final long IDLE_RELEASE_NANOS = 2_000_000_000L; // stuck-drag guard

    private final Quat initial;                        // showcase tilt, restored by reset()

    private Quat cur;                                  // shown orientation
    private Quat tgt;                                  // drag target orientation
    private Quat prevQ;                                // previous cur, for |omega|
    private boolean dragging;
    private double pendingX;
    private double pendingY;
    private float fx;                                  // filtered delta X
    private float fy;                                  // filtered delta Y
    private float rawPrevX;
    private float rawPrevY;
    private float velXf;
    private float velYf;
    private float velMag;                              // spring velocity toward tgt
    private float approachVel;                         // |omega| carried across release
    private float omega;                               // |omega| rad/s (readout)
    private long lastTickNanos;
    private long lastInputNanos;

    /**
     * @param initRotX initial Y-axis rotation (radians), matching the legacy
     *                 {@code itemRotX} showcase tilt
     * @param initRotY initial X-axis rotation (radians), matching the legacy
     *                 {@code itemRotY} showcase tilt
     */
    public ItemDrag3D(float initRotX, float initRotY) {
        Quat q0 = Quat.mul(
                Quat.fromAxisAngle(1, 0, 0, initRotY),
                Quat.fromAxisAngle(0, 1, 0, initRotX));
        this.initial = q0;
        this.cur = q0;
        this.tgt = q0;
        this.prevQ = q0;
        long now = System.nanoTime();
        this.lastTickNanos = now;
        this.lastInputNanos = now;
    }

    /** Current orientation to render (unit quaternion). */
    public Quat rotation() {
        return this.cur;
    }

    /** Angular speed readout, rad/s (same metric as the lab's |omega| chart). */
    public float omega() {
        return this.omega;
    }

    /** Adds a raw pointer delta (px) and marks the drag active. */
    public void accumulate(double dx, double dy) {
        this.pendingX += dx;
        this.pendingY += dy;
        this.dragging = true;
        this.lastInputNanos = System.nanoTime();
    }

    /** Marks the drag ended; the damped spring takes over from the next tick.
     *  Seeds the spring velocity once from the speed at release — re-seeding
     *  on every zero-crossing (the naive lab transcription) would kick the
     *  model again after it has already settled. */
    public void release() {
        if (this.dragging) {
            this.velMag = this.approachVel;
        }
        this.dragging = false;
    }

    /** Restores the initial showcase tilt and clears all filter/spring state. */
    public void reset() {
        this.cur = this.initial;
        this.tgt = this.initial;
        this.prevQ = this.initial;
        this.pendingX = 0;
        this.pendingY = 0;
        this.fx = 0;
        this.fy = 0;
        this.rawPrevX = 0;
        this.rawPrevY = 0;
        this.velXf = 0;
        this.velYf = 0;
        this.velMag = 0;
        this.approachVel = 0;
        this.omega = 0;
        this.dragging = false;
    }

    /** Per-frame advance: applies pending input, follows the target while
     *  dragging, and runs the damped spring after release. Self-measures dt. */
    public void tick() {
        long now = System.nanoTime();
        float dt = (float) ((now - this.lastTickNanos) / 1e9);
        this.lastTickNanos = now;
        advance(dt);
    }

    /** Test hook: advances by an explicit dt (seconds) instead of wall-clock. */
    void tickAt(float dt) {
        advance(dt);
    }

    private void advance(float dt) {
        dt = clamp(dt, DT_MIN, DT_MAX);

        // Stuck-drag guard: if the release event was lost (focus change etc.),
        // treat a long input silence as a release so the spring still settles.
        if (this.dragging && System.nanoTime() - this.lastInputNanos > IDLE_RELEASE_NANOS) {
            this.dragging = false;
        }

        if (this.dragging) {
            float dx = (float) clamp(this.pendingX, -MAX_DELTA, MAX_DELTA);
            float dy = (float) clamp(this.pendingY, -MAX_DELTA, MAX_DELTA);
            this.pendingX = 0;
            this.pendingY = 0;
            float ex = euroX(dead(dx), dt);
            float ey = euroY(dead(dy), dt);
            float sx = SENS;
            float sy = SENS;
            if (ADAPTIVE) {
                float v = (float) Math.hypot(ex, ey) / Math.max(dt, 1e-4F);
                float k = smoothstep(40, 420, v);
                sx = sy = SENS * (1 + 1.3F * k);
            }
            // Horizontal drag spins the vertical axis, vertical drag the
            // horizontal one (same mapping the 26.x renderer was tuned to).
            Quat dq = Quat.norm(Quat.mul(
                    Quat.fromAxisAngle(1, 0, 0, ey * sy),
                    Quat.fromAxisAngle(0, 1, 0, ex * sx)));
            this.tgt = Quat.norm(Quat.mul(dq, this.tgt));
            this.velMag = 0;
            this.cur = Quat.slerp(this.cur, this.tgt, 1 - (float) Math.exp(-SLERP_RATE * dt));
            this.approachVel = lerp(this.approachVel, this.omega, 0.3F);
        } else {
            this.pendingX = 0;
            this.pendingY = 0;
            dampedSpring(dt);
        }

        this.omega = angularSpeed(this.prevQ, this.cur, dt);
        this.prevQ = this.cur;
    }

    // ---- One-Euro filter (per axis, same constants as the lab) ----

    private float euroX(float raw, float dt) {
        return euroAxis(true, raw, dt);
    }

    private float euroY(float raw, float dt) {
        return euroAxis(false, raw, dt);
    }

    private float euroAxis(boolean isX, float raw, float dt) {
        float rawV = dt > 0 ? (raw - (isX ? this.rawPrevX : this.rawPrevY)) / dt : 0;
        if (isX) {
            this.rawPrevX = raw;
        } else {
            this.rawPrevY = raw;
        }
        float aV = alphaFromCutoff(MIN_CUTOFF * 0.5F, dt);
        if (isX) {
            this.velXf = this.velXf + aV * (rawV - this.velXf);
        } else {
            this.velYf = this.velYf + aV * (rawV - this.velYf);
        }
        float cutoff = MIN_CUTOFF + BETA * Math.abs(isX ? this.velXf : this.velYf);
        float aD = alphaFromCutoff(cutoff, dt);
        float f = (isX ? this.fx : this.fy) + aD * (raw - (isX ? this.fx : this.fy));
        if (isX) {
            this.fx = f;
        } else {
            this.fy = f;
        }
        return f;
    }

    private static float alphaFromCutoff(float cutoff, float dt) {
        float tau = 1 / (float) (2 * Math.PI * Math.max(cutoff, 0.05F));
        return 1 / (1 + tau / Math.max(dt, 1e-4F));
    }

    // ---- damped spring toward the drag target (release behaviour) ----

    private void dampedSpring(float dt) {
        float remaining = dt;
        while (remaining > 1e-6F) {
            float h = Math.min(remaining, SPRING_SUBSTEP);
            springStep(h);
            remaining -= h;
        }
    }

    private void springStep(float dt) {
        Quat err = Quat.norm(Quat.mul(this.tgt, Quat.conj(this.cur)));
        float ang = 2 * (float) Math.acos(clamp(err.w(), -1, 1));
        if (ang < 1e-4F) {
            this.velMag = 0;
            this.cur = this.tgt;
            return;
        }
        float s = (float) Math.sin(ang / 2);
        float ax = err.x() / s;
        float ay = err.y() / s;
        float az = err.z() / s;
        float w = 2 * (float) Math.PI * SPRING_FREQ;
        float c = 2 * w * SPRING_ZETA;
        // velMag is the angular velocity TOWARD tgt (ang' = -velMag), so the
        // restoring term is +w^2 * ang — mirrors w^2 * (tgt - pos).
        float a = w * w * ang - c * this.velMag;
        this.velMag += a * dt;
        this.cur = Quat.norm(Quat.mul(Quat.fromAxisAngle(ax, ay, az, this.velMag * dt), this.cur));
    }

    // ---- small helpers ----

    private static float angularSpeed(Quat prev, Quat q, float dt) {
        float d = Quat.dot(prev, q);
        float ang = 2 * (float) Math.acos(clamp(Math.abs(d), -1, 1));
        return dt > 0 ? ang / dt : 0;
    }

    private static float dead(float v) {
        return Math.abs(v) < DEAD_ZONE ? 0 : v - Math.signum(v) * DEAD_ZONE;
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float clamp(float v, float lo, float hi) {
        return (float) clamp((double) v, (double) lo, (double) hi);
    }
}
