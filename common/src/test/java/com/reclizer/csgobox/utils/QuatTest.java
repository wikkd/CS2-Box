package com.reclizer.csgobox.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure-math quaternion helpers in {@link Quat}.
 */
final class QuatTest {

    private static final float EPS = 1e-4F;

    private static void assertQuat(Quat expected, Quat actual) {
        assertEquals(expected.x(), actual.x(), EPS, "x");
        assertEquals(expected.y(), actual.y(), EPS, "y");
        assertEquals(expected.z(), actual.z(), EPS, "z");
        assertEquals(expected.w(), actual.w(), EPS, "w");
    }

    @Test
    void identityIsUnit() {
        assertEquals(1F, Quat.dot(Quat.IDENTITY, Quat.IDENTITY), EPS);
    }

    @Test
    void fromAxisAngleProducesUnitLength() {
        Quat q = Quat.fromAxisAngle(1, 0, 0, (float) Math.PI / 2);
        // (sin(pi/4), 0, 0, cos(pi/4))
        float s = (float) Math.sin(Math.PI / 4);
        assertEquals(s, q.x(), EPS);
        assertEquals(0F, q.y(), EPS);
        assertEquals(0F, q.z(), EPS);
        assertEquals(s, q.w(), EPS);
        // unit length
        assertEquals(1F, q.x() * q.x() + q.y() * q.y() + q.z() * q.z() + q.w() * q.w(), EPS);
    }

    @Test
    void fromAxisAngleZeroAngleIsIdentity() {
        Quat q = Quat.fromAxisAngle(1, 1, 1, 0);
        assertQuat(Quat.IDENTITY, q);
    }

    @Test
    void mulIsHamiltonProduct() {
        // (0,0,0,1) * q == q (identity)
        assertQuat(Quat.mul(Quat.IDENTITY, new Quat(1, 2, 3, 4)), new Quat(1, 2, 3, 4));
        // Hand-checked: a=(1,0,0,0) (180deg around x), b=(0,1,0,0) (180deg around y)
        // Hamilton a*b = (x*y' etc): for pure quats, a*b = -dot + cross
        Quat a = new Quat(1, 0, 0, 0);
        Quat b = new Quat(0, 1, 0, 0);
        Quat r = Quat.mul(a, b);
        // a*b = (1,0,0,0)*(0,1,0,0) = cross(1i,1j) = 1k => (0,0,1,0)
        assertQuat(new Quat(0, 0, 1, 0), r);
    }

    @Test
    void conjNegatesVectorPart() {
        Quat q = new Quat(1, 2, 3, 4);
        Quat c = Quat.conj(q);
        assertEquals(-1F, c.x(), EPS);
        assertEquals(-2F, c.y(), EPS);
        assertEquals(-3F, c.z(), EPS);
        assertEquals(4F, c.w(), EPS);
    }

    @Test
    void normNormalizes() {
        Quat q = new Quat(3, 0, 0, 4); // length 5
        Quat n = Quat.norm(q);
        assertEquals(0.6F, n.x(), EPS);
        assertEquals(0.8F, n.w(), EPS);
        assertEquals(1F, n.x() * n.x() + n.w() * n.w(), EPS);
    }

    @Test
    void normDegenerateReturnsIdentity() {
        assertQuat(Quat.IDENTITY, Quat.norm(new Quat(0, 0, 0, 0)));
    }

    @Test
    void slerpEndpoints() {
        Quat a = Quat.fromAxisAngle(0, 1, 0, 0.3F);
        Quat b = Quat.fromAxisAngle(0, 1, 0, 1.2F);
        assertQuat(a, Quat.slerp(a, b, 0F));
        assertQuat(b, Quat.slerp(a, b, 1F));
        // midpoint is halfway on the great circle
        float mid = Quat.slerp(a, b, 0.5F).w();
        float expectedMid = (float) Math.cos((0.3F + 1.2F) / 2 / 2);
        assertEquals(expectedMid, mid, 1e-2F);
    }

    @Test
    void slerpWithSameInputReturnsOriginal() {
        // Two identical quaternions have dot(a,a)=1, which takes the
        // nlerp branch (d > 0.9995F); interpolation must yield the original
        // at any t (same-axis, same angle, so nlerp is exact).
        Quat a = Quat.fromAxisAngle(0, 1, 0, 0.7F);
        assertQuat(a, Quat.slerp(a, a, 0F));
        assertQuat(a, Quat.slerp(a, a, 0.5F));
        assertQuat(a, Quat.slerp(a, a, 1F));
    }

    @Test
    void slerpInterpolatesSmoothly() {
        Quat a = Quat.fromAxisAngle(0, 1, 0, 0F);
        Quat b = Quat.fromAxisAngle(0, 1, 0, 1F);
        float w0 = Quat.slerp(a, b, 0F).w();
        float wHalf = Quat.slerp(a, b, 0.5F).w();
        float w1 = Quat.slerp(a, b, 1F).w();
        // w goes cos(0)=1 -> cos(0.5)=0.8776 -> cos(1)=0.5403; monotonic decreasing
        assertTrue(w0 > wHalf && wHalf > w1, "slerp must be monotonic");
    }

    @Test
    void dotSymmetry() {
        Quat a = new Quat(1, 0, 0, 0);
        Quat b = new Quat(0, 1, 0, 0);
        assertEquals(0F, Quat.dot(a, b), EPS); // orthogonal pure quats
    }
}
