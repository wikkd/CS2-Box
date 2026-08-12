package com.reclizer.csgobox.utils;

/**
 * Minimal immutable unit-quaternion (x, y, z, w) with the rotation helpers the
 * 3D drag preview needs. Pure math — no Minecraft classes, so it can live in
 * {@code common/} (CONSTRAINT-001).
 */
public record Quat(float x, float y, float z, float w) {

    public static final Quat IDENTITY = new Quat(0, 0, 0, 1);

    /** Axis-angle quaternion; the axis need not be normalized. */
    public static Quat fromAxisAngle(float ax, float ay, float az, float rad) {
        float s = (float) Math.sin(rad / 2.0F);
        return new Quat(ax * s, ay * s, az * s, (float) Math.cos(rad / 2.0F));
    }

    /** Hamilton product — {@code a} composed before {@code b} ({@code a * b}). */
    public static Quat mul(Quat a, Quat b) {
        return new Quat(
                a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
                a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
                a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
                a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z);
    }

    public static Quat conj(Quat q) {
        return new Quat(-q.x, -q.y, -q.z, q.w);
    }

    public static Quat norm(Quat q) {
        float len = (float) Math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w);
        if (len < 1e-6F) {
            return IDENTITY;
        }
        return new Quat(q.x / len, q.y / len, q.z / len, q.w / len);
    }

    public static float dot(Quat a, Quat b) {
        return a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
    }

    /** Spherical interpolation with the standard negative-copy + nlerp guard. */
    public static Quat slerp(Quat a, Quat b, float t) {
        float d = dot(a, b);
        Quat b2 = b;
        if (d < 0) {
            d = -d;
            b2 = new Quat(-b.x, -b.y, -b.z, -b.w);
        }
        if (d > 0.9995F) {
            return norm(new Quat(
                    a.x + (b2.x - a.x) * t,
                    a.y + (b2.y - a.y) * t,
                    a.z + (b2.z - a.z) * t,
                    a.w + (b2.w - a.w) * t));
        }
        float th = (float) Math.acos(clamp(d, -1, 1));
        float s = (float) Math.sin(th);
        float k0 = (float) Math.sin((1 - t) * th) / s;
        float k1 = (float) Math.sin(t * th) / s;
        return new Quat(
                a.x * k0 + b2.x * k1,
                a.y * k0 + b2.y * k1,
                a.z * k0 + b2.z * k1,
                a.w * k0 + b2.w * k1);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
