package com.reclizer.csgobox.utils;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewCameraFitTest {

    /** The previewer's fovY is 50 degrees over a square rect. */
    private static final double HALF_TANGENT = Math.tan(Math.toRadians(25.0));
    private static final double FILL = 0.82;
    /** The previewer's opening yaw. */
    private static final double OPENING_YAW = 180.0;
    private static final double STEP = 360.0 / PreviewCameraFit.YAW_STEPS;

    /** Replays the previewer's projection for one yaw: translate, pitch 0,
     *  yaw, then the depth servo (root pinned at {@code distance}). Returns
     *  the largest frame fraction any sample reaches — 1.0 means a sample
     *  sits exactly on the fill boundary. */
    private static double worstFraction(PreviewCameraFit.Cloud cloud, double centerY,
                                        double yawDegrees, double distance) {
        float[] p = cloud.data();
        double radians = Math.toRadians(yawDegrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double worst = 0.0;
        for (int o = 0; o < cloud.count() * PreviewCameraFit.STRIDE; o += PreviewCameraFit.STRIDE) {
            double yawedX = p[o] * cos + p[o + 2] * sin;
            double yawedZ = -p[o] * sin + p[o + 2] * cos;
            double yawedRootZ = -p[o + 3] * sin + p[o + 4] * cos;
            // view depth = -(yawedZ + zoom) with zoom = -yawedRootZ - distance
            double depth = distance - (yawedZ - yawedRootZ);
            double ndcX = yawedX / (depth * HALF_TANGENT);
            double ndcY = (p[o + 1] - centerY) / (depth * HALF_TANGENT);
            worst = Math.max(worst, Math.max(Math.abs(ndcX), Math.abs(ndcY)) / FILL);
        }
        return worst;
    }

    private static void addBox(PreviewCameraFit.Cloud cloud, float minX, float minY, float minZ,
                               float maxX, float maxY, float maxZ, float rootX, float rootZ) {
        for (int i = 0; i < 8; i++) {
            cloud.add((i & 1) == 0 ? minX : maxX, (i & 2) == 0 ? minY : maxY,
                    (i & 4) == 0 ? minZ : maxZ, rootX, rootZ);
        }
    }

    private static PreviewCameraFit.Fit fit(PreviewCameraFit.Cloud cloud) {
        return PreviewCameraFit.fit(cloud, HALF_TANGENT, 1.0, FILL, OPENING_YAW);
    }

    @Test
    void planarBoxMatchesTheAnalyticDistance() {
        PreviewCameraFit.Cloud cloud = new PreviewCameraFit.Cloud();
        addBox(cloud, -0.5F, -1.0F, 0.0F, 0.5F, 1.0F, 0.0F, 0.0F, 0.0F);
        PreviewCameraFit.Fit fit = fit(cloud);
        assertEquals(0.0, fit.centerY(), 1e-9);
        assertEquals(1.0 / (FILL * HALF_TANGENT), fit.distanceAtStep(0), 1e-6);
        // Head-on the box is flat; turned, its edge swings toward the
        // camera and needs a little more room.
        assertTrue(fit.maxDistance() >= fit.distanceAtStep(0));
        assertTrue(fit.maxDistance() <= fit.distanceAtStep(0) + 0.5);
    }

    @Test
    void randomSwingStaysInsideAndTightAtEveryYawStep() {
        Random random = new Random(20261124L);
        PreviewCameraFit.Cloud cloud = new PreviewCameraFit.Cloud();
        addBox(cloud, -0.5F, 0.0F, -0.25F, 0.5F, 2.0F, 0.25F, 0.0F, 0.0F);
        for (int frame = 0; frame < 40; frame++) {
            float rootX = (random.nextFloat() - 0.5F) * 0.8F;
            float rootZ = (random.nextFloat() - 0.5F) * 0.8F;
            addBox(cloud, -0.5F, 0.0F, -0.25F, 0.5F, 2.0F, 0.25F, rootX, rootZ);
            // a weapon tip sweeping a sphere around the shoulder
            for (int i = 0; i < 8; i++) {
                double theta = random.nextDouble() * Math.PI * 2;
                double phi = random.nextDouble() * Math.PI;
                float r = 0.8F + random.nextFloat() * 0.8F;
                cloud.add(rootX + (float) (r * Math.sin(phi) * Math.cos(theta)),
                        1.4F + (float) (r * Math.cos(phi)),
                        rootZ + (float) (r * Math.sin(phi) * Math.sin(theta)), rootX, rootZ);
            }
        }
        PreviewCameraFit.Fit fit = fit(cloud);
        for (int step = 0; step < PreviewCameraFit.YAW_STEPS; step++) {
            double yaw = OPENING_YAW + step * STEP;
            double reached = worstFraction(cloud, fit.centerY(), yaw, fit.distanceAtStep(step));
            assertTrue(reached <= 1.0 + 1e-4, "step " + step + " overflows: " + reached);
            assertTrue(reached >= 0.999, "step " + step + " is not tight: " + reached);
        }
        // Shaving two percent off the opening distance must let a sample cross the line.
        assertTrue(worstFraction(cloud, fit.centerY(), OPENING_YAW, fit.distanceAtStep(0) * 0.98) > 1.0);
    }

    @Test
    void distanceAtFollowsTheBracketingSteps() {
        PreviewCameraFit.Cloud cloud = new PreviewCameraFit.Cloud();
        addBox(cloud, -0.5F, 0.0F, -0.25F, 0.5F, 2.0F, 0.25F, 0.0F, 0.0F);
        cloud.add(0.0F, 1.0F, 1.5F, 0.0F, 0.0F);
        cloud.add(0.0F, 1.0F, -1.5F, 0.0F, 0.0F);
        PreviewCameraFit.Fit fit = fit(cloud);
        assertEquals(Math.max(fit.distanceAtStep(0), fit.distanceAtStep(1)),
                fit.distanceAt(OPENING_YAW), 0.0);
        assertEquals(Math.max(fit.distanceAtStep(3), fit.distanceAtStep(4)),
                fit.distanceAt(OPENING_YAW + 3.5 * STEP), 0.0);
        // a full turn maps back onto the same table
        assertEquals(fit.distanceAt(OPENING_YAW + 40.0), fit.distanceAt(OPENING_YAW + 400.0), 0.0);
        assertEquals(Math.max(fit.distanceAtStep(23), fit.distanceAtStep(0)),
                fit.distanceAt(OPENING_YAW - 7.5), 0.0);
    }

    @Test
    void forwardReachOnlyWidensTheSideViews() {
        // A spike along z points at the opening camera (foreshortened, and
        // level with the centre so it costs nothing there) but sticks out
        // sideways once the view is turned a quarter.
        PreviewCameraFit.Cloud cloud = new PreviewCameraFit.Cloud();
        addBox(cloud, -0.25F, 0.0F, -0.25F, 0.25F, 2.0F, 0.25F, 0.0F, 0.0F);
        cloud.add(0.0F, 1.0F, 1.5F, 0.0F, 0.0F);
        cloud.add(0.0F, 1.0F, -1.5F, 0.0F, 0.0F);
        PreviewCameraFit.Fit fit = fit(cloud);
        double opening = fit.distanceAt(OPENING_YAW);
        double side = fit.distanceAt(OPENING_YAW + 90.0);
        assertTrue(side > opening, "side " + side + " opening " + opening);
        assertTrue(worstFraction(cloud, fit.centerY(), OPENING_YAW + 90.0, opening) > 1.0);
        assertTrue(worstFraction(cloud, fit.centerY(), OPENING_YAW + 90.0, side) <= 1.0 + 1e-4);
    }

    @Test
    void rootLateralTravelCountsButRootDepthTravelDoesNot() {
        PreviewCameraFit.Cloud sideways = new PreviewCameraFit.Cloud();
        sideways.add(-0.25F, 1.0F, 0.0F, 0.0F, 0.0F);
        sideways.add(0.75F, 1.0F, 0.0F, 1.0F, 0.0F);
        PreviewCameraFit.Cloud deeper = new PreviewCameraFit.Cloud();
        deeper.add(-0.25F, 1.0F, 0.0F, 0.0F, 0.0F);
        deeper.add(-0.25F, 1.0F, 1.0F, 0.0F, 1.0F);
        double unit = 1.0 / (FILL * HALF_TANGENT);
        assertEquals(0.75 * unit, fit(sideways).distanceAtStep(0), 1e-6);
        assertEquals(0.25 * unit, fit(deeper).distanceAtStep(0), 1e-6);
    }

    @Test
    void nearSamplesCostMoreThanFarOnes() {
        // Opening view: smaller model z is nearer the camera. The same
        // figure moved half a unit toward it needs one unit more distance
        // (both its top and its bottom come nearer).
        PreviewCameraFit.Cloud far = new PreviewCameraFit.Cloud();
        far.add(0.0F, 2.0F, 0.5F, 0.0F, 0.0F);
        far.add(0.0F, 0.0F, 0.5F, 0.0F, 0.0F);
        PreviewCameraFit.Cloud near = new PreviewCameraFit.Cloud();
        near.add(0.0F, 2.0F, -0.5F, 0.0F, 0.0F);
        near.add(0.0F, 0.0F, -0.5F, 0.0F, 0.0F);
        assertEquals(fit(far).distanceAtStep(0) + 1.0, fit(near).distanceAtStep(0), 1e-6);
        assertEquals(1.0, fit(far).centerY(), 1e-6);
        assertEquals(1.0, fit(near).centerY(), 1e-6);
    }

    @Test
    void centreIsPerspectiveWeighted() {
        // The top of the figure leans toward the camera (smaller model z):
        // being nearer it needs more room than its height alone, so the
        // centre rises past the plain midpoint.
        PreviewCameraFit.Cloud cloud = new PreviewCameraFit.Cloud();
        cloud.add(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        cloud.add(0.0F, 2.0F, -0.5F, 0.0F, 0.0F);
        PreviewCameraFit.Fit fit = fit(cloud);
        double unit = 1.0 / (FILL * HALF_TANGENT);
        assertEquals((2.0 * unit + 0.5) / (2.0 * unit), fit.centerY(), 1e-6);
        assertTrue(fit.centerY() > 1.0);
    }

    @Test
    void cloudGrowsPastItsFirstPage() {
        PreviewCameraFit.Cloud cloud = new PreviewCameraFit.Cloud();
        for (int i = 0; i < 3000; i++) {
            cloud.add(i, i * 2.0F, i * 3.0F, i * 4.0F, i * 5.0F);
        }
        assertEquals(3000, cloud.count());
        int last = 2999 * PreviewCameraFit.STRIDE;
        assertEquals(2999.0F, cloud.data()[last], 0.0F);
        assertEquals(2999.0F * 5.0F, cloud.data()[last + 4], 0.0F);
    }

    @Test
    void emptyCloudIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PreviewCameraFit.fit(new PreviewCameraFit.Cloud(), HALF_TANGENT, 1.0, FILL, OPENING_YAW));
    }
}
