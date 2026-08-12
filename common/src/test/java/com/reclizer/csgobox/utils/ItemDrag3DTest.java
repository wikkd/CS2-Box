package com.reclizer.csgobox.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural tests for the "full combination, no inertia, damped" 3D drag
 * scheme: while dragging the shown orientation follows the target, and after
 * release the damped spring settles onto the target with the angular speed
 * monotonically decaying to zero — it must NOT keep spinning like the
 * arcball-with-inertia variant.
 */
class ItemDrag3DTest {

    private static final float FRAME = 1.0F / 60.0F;

    /** Double-precision angle between two unit quaternions (radians). */
    private static double angleBetween(Quat a, Quat b) {
        double d = (double) a.x() * b.x() + (double) a.y() * b.y()
                + (double) a.z() * b.z() + (double) a.w() * b.w();
        return 2 * Math.acos(Math.min(1, Math.abs(d)));
    }

    private static double movement(ItemDrag3D drag, Quat from) {
        return angleBetween(from, drag.rotation());
    }

    @Test
    void releaseSettlesToTargetWithNoInertia() {
        ItemDrag3D drag = new ItemDrag3D(0, 0);
        // Strong 240px horizontal flick over 0.5s: the follower lags the
        // target at release, so the damped spring has real work to do and the
        // decay is measurable across the windows below.
        for (int i = 0; i < 30; i++) {
            drag.accumulate(8, 0);
            drag.tickAt(FRAME);
        }
        Quat atRelease = drag.rotation();
        drag.release();

        // Measure the travelled distance over three consecutive windows. A
        // damped spring must travel strictly less in each later window; an
        // inertia (spin) variant would keep travelling at roughly constant
        // speed.
        Quat w0Start = drag.rotation();
        for (int i = 0; i < 20; i++) {
            drag.tickAt(FRAME);
        }
        double w0 = movement(drag, w0Start);
        Quat w1Start = drag.rotation();
        for (int i = 0; i < 40; i++) {
            drag.tickAt(FRAME);
        }
        double w1 = movement(drag, w1Start);
        Quat w2Start = drag.rotation();
        for (int i = 0; i < 140; i++) {
            drag.tickAt(FRAME);
        }
        double w2 = movement(drag, w2Start);

        assertTrue(w0 > w1, "first window must travel more than the second (got " + w0 + " vs " + w1 + ")");
        assertTrue(w1 > w2, "second window must travel more than the third (got " + w1 + " vs " + w2 + ")");
        assertTrue(w2 < 1e-3, "orientation must be settled after ~3s, travelled " + w2);
        assertTrue(drag.omega() < 0.05F, "omega must settle near zero, got " + drag.omega());
        // The flick must have moved the orientation away from identity.
        assertTrue(angleBetween(atRelease, Quat.IDENTITY) > 0.1,
                "a 240px flick should rotate the item noticeably");
    }

    @Test
    void releaseDoesNotSpinAtLowFrameRates() {
        // A fast flick released at 20 fps / 10 fps used to blow up the
        // explicit-Euler spring (c*dt > 2): the model spun out of control
        // (thousands of degrees) or hit NaN. Sub-stepped integration must
        // settle cleanly at any frame rate.
        for (float dt : new float[]{0.05F, 0.1F}) {
            ItemDrag3D drag = new ItemDrag3D(0, 0);
            // Fast flick: 12 frames of 40px horizontal drag.
            for (int i = 0; i < 12; i++) {
                drag.accumulate(40, 0);
                drag.tickAt(dt);
            }
            drag.accumulate(0, 0);
            drag.tickAt(dt);
            drag.release();

            double totalTravel = 0;
            Quat prev = drag.rotation();
            for (int i = 0; i < 120; i++) { // up to 12 s
                drag.tickAt(dt);
                Quat q = drag.rotation();
                assertTrue(Float.isFinite(q.x()) && Float.isFinite(q.y())
                                && Float.isFinite(q.z()) && Float.isFinite(q.w()),
                        "quaternion must stay finite at dt=" + dt + " frame " + i);
                totalTravel += angleBetween(prev, q);
                prev = q;
            }
            assertTrue(totalTravel < 4 * Math.PI,
                    "release must not spin at dt=" + dt + ", travelled "
                            + Math.toDegrees(totalTravel) + " deg");

            // Settled: the following 1s of movement is negligible.
            Quat sStart = drag.rotation();
            for (int i = 0; i < 60; i++) {
                drag.tickAt(dt);
            }
            double settledMove = angleBetween(sStart, drag.rotation());
            assertTrue(settledMove < 1e-3,
                    "must settle at dt=" + dt + ", moved " + settledMove + " rad");
            assertTrue(drag.omega() < 0.05F,
                    "omega must settle near zero at dt=" + dt + ", got " + drag.omega());
        }
    }

    @Test
    void followerCatchesTargetWhileDragging() {
        ItemDrag3D drag = new ItemDrag3D(0, 0);
        for (int i = 0; i < 30; i++) { // 60px over 0.5s
            drag.accumulate(2, 0);
            drag.tickAt(FRAME);
        }
        // Keep dragging without new input: the slerp follower converges.
        for (int i = 0; i < 60; i++) {
            drag.tickAt(FRAME);
        }
        Quat before = drag.rotation();
        drag.tickAt(FRAME);
        assertTrue(angleBetween(before, drag.rotation()) < 1e-3,
                "orientation should be stable once the follower catches the target");
    }

    @Test
    void resetRestoresInitialTilt() {
        float rx = (float) Math.toRadians(-38);
        float ry = (float) Math.toRadians(24);
        ItemDrag3D drag = new ItemDrag3D(rx, ry);
        drag.accumulate(40, -25);
        drag.tickAt(FRAME);
        drag.release();
        for (int i = 0; i < 10; i++) {
            drag.tickAt(FRAME);
        }
        drag.reset();
        Quat expected = Quat.mul(
                Quat.fromAxisAngle(1, 0, 0, ry),
                Quat.fromAxisAngle(0, 1, 0, rx));
        Quat got = drag.rotation();
        // reset() restores the exact constructor quaternion (component-equal).
        assertEquals(expected.x(), got.x(), 1e-7F);
        assertEquals(expected.y(), got.y(), 1e-7F);
        assertEquals(expected.z(), got.z(), 1e-7F);
        assertEquals(expected.w(), got.w(), 1e-7F);
        assertEquals(0, drag.omega(), 1e-5F);
    }

    @Test
    void horizontalDragSpinsVerticalAxis() {
        // With init (0,0), a positive horizontal drag must rotate the
        // orientation about the +Y axis (vertical-axis spin).
        ItemDrag3D drag = new ItemDrag3D(0, 0);
        drag.accumulate(200, 0);
        for (int i = 0; i < 30; i++) {
            drag.tickAt(FRAME);
        }
        Quat q = drag.rotation();
        // A pure +Y rotation has x == 0 and z == 0.
        assertTrue(Math.abs(q.x()) < 0.02F && Math.abs(q.z()) < 0.02F,
                "horizontal drag should stay a pure Y-axis rotation, got " + q);
        assertTrue(q.w() < 1 - 1e-3F, "rotation must have moved off identity");
    }
}
