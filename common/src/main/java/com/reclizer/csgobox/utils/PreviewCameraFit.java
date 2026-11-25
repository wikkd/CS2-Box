package com.reclizer.csgobox.utils;

/**
 * Pure-Java camera fit for a perspective preview of an animated figure (the
 * Epic Fight battle inspect). Given every point the figure and its held
 * item reach over a whole animation cycle, it finds the smallest camera
 * distance that keeps all of them inside a target fraction of the frame —
 * one distance per camera yaw, so a drag-rotated view frames as tightly as
 * the opening one. Pure functions, no MC imports — safe for common/.
 *
 * <p>Projection model (the previewer's own, bytecode-checked): the camera
 * translates to {@code (0, yMove - 1, zoom)}, pitches by {@code xRot}, then
 * yaws by {@code yRot}; the vertical fov is {@code 2 * atan(halfTangent)}
 * over {@code aspect}. A depth servo holds the figure's ROOT at a fixed
 * view depth, which cancels root travel along the view axis only — the
 * root's lateral and vertical travel stays visible. A sample {@code p} from
 * a frame whose root sat at {@code r} therefore lands at view depth
 * {@code dist - w} with {@code w = (Ry(yaw) * (p - r)).z}, while its
 * lateral offset is the yawed <em>absolute</em> x and its vertical offset is
 * {@code y - centerY}. Containment inside {@code fill} of the frame needs,
 * for every sample,</p>
 *
 * <pre>
 *   dist &gt;= |y - centerY| / (fill * halfTangent)          + w
 *   dist &gt;= |x'| / (fill * halfTangent * aspect)          + w      (x' = yawed x)
 * </pre>
 *
 * <p>so each distance is a maximum over the samples. The vertical centre is
 * solved for the opening view — it equalises the top and bottom
 * requirements, which minimises that view's distance — and kept for every
 * other yaw.</p>
 */
public final class PreviewCameraFit {

    private PreviewCameraFit() {
    }

    /** Yaw resolution of the distance table: one entry every 15 degrees, a
     *  full turn starting at the opening view. */
    public static final int YAW_STEPS = 24;

    /** Values per sample in a {@link Cloud}: x, y, z, rootX, rootZ. */
    public static final int STRIDE = 5;

    private static final int TOP = 0;
    private static final int BOTTOM = 1;
    private static final int SIDE = 2;

    /** Sampled points of a whole animation cycle in model space, each
     *  tagged with its frame's root position (x and z — the servo cancels
     *  root depth, so the fit has to know how much of a sample's offset
     *  from the root is view-axial). */
    public static final class Cloud {
        private float[] data = new float[STRIDE * 256];
        private int count;

        public void add(float x, float y, float z, float rootX, float rootZ) {
            if (count * STRIDE == data.length) {
                float[] grown = new float[data.length * 2];
                System.arraycopy(data, 0, grown, 0, data.length);
                data = grown;
            }
            int o = count * STRIDE;
            data[o] = x;
            data[o + 1] = y;
            data[o + 2] = z;
            data[o + 3] = rootX;
            data[o + 4] = rootZ;
            count++;
        }

        /** Number of samples collected so far. */
        public int count() {
            return count;
        }

        /** Backing array, {@link #STRIDE} floats per sample, valid up to
         *  {@code count() * STRIDE}. */
        public float[] data() {
            return data;
        }
    }

    /** Result of {@link #fit}: one camera-to-root distance per yaw step plus
     *  the vertical centre they all share. */
    public static final class Fit {
        private final double openingYaw;
        private final double centerY;
        private final float[] distance;

        private Fit(double openingYaw, double centerY, float[] distance) {
            this.openingYaw = openingYaw;
            this.centerY = centerY;
            this.distance = distance;
        }

        /** Model-space height the camera centres on. */
        public double centerY() {
            return centerY;
        }

        /** Distance tabulated for one yaw step (0 = the view the preview
         *  opens on). */
        public double distanceAtStep(int step) {
            return distance[Math.floorMod(step, YAW_STEPS)];
        }

        /** Largest distance over the full turn. */
        public double maxDistance() {
            double max = distance[0];
            for (int i = 1; i < distance.length; i++) {
                max = Math.max(max, distance[i]);
            }
            return max;
        }

        /** Required distance at an arbitrary camera yaw, in the caller's own
         *  degree convention: the larger of the two table steps bracketing
         *  it. The true requirement between two steps can exceed both
         *  endpoints by well under one percent; the caller's frame margin
         *  absorbs that. */
        public double distanceAt(double yawDegrees) {
            double step = (yawDegrees - openingYaw) / (360.0 / YAW_STEPS);
            int current = Math.floorMod((int) Math.floor(step), YAW_STEPS);
            int next = (current + 1) % YAW_STEPS;
            return Math.max(distance[current], distance[next]);
        }
    }

    /** Smallest camera-to-root distance that keeps every sample inside
     *  {@code fill} of the frame, tabulated per yaw step starting at the
     *  opening view ({@code openingYawDegrees}, same convention as
     *  {@link Fit#distanceAt}). */
    public static Fit fit(Cloud cloud, double halfTangent, double aspect, double fill,
                          double openingYawDegrees) {
        int count = cloud.count;
        if (count == 0) {
            throw new IllegalArgumentException("no samples to fit");
        }
        float[] pts = cloud.data;
        double ky = 1.0 / (fill * halfTangent);
        double kx = ky / aspect;

        // Opening view first: with the centre still unknown the per-sample
        // requirements are top = y * ky + w and bottom = -y * ky + w; the
        // camera then needs dist >= top - centerY * ky and
        // dist >= bottom + centerY * ky, equalised by the centre below.
        double radians = Math.toRadians(openingYawDegrees);
        double[] open = requirements(pts, count, Math.sin(radians), Math.cos(radians), ky, kx, 0.0);
        double centerY = (open[TOP] - open[BOTTOM]) / (2.0 * ky);
        float[] distance = new float[YAW_STEPS];
        distance[0] = (float) Math.max((open[TOP] + open[BOTTOM]) / 2.0, open[SIDE]);

        for (int step = 1; step < YAW_STEPS; step++) {
            radians = Math.toRadians(openingYawDegrees + step * (360.0 / YAW_STEPS));
            double[] need = requirements(pts, count, Math.sin(radians), Math.cos(radians),
                    ky, kx, centerY);
            distance[step] = (float) Math.max(need[TOP], Math.max(need[BOTTOM], need[SIDE]));
        }
        return new Fit(openingYawDegrees, centerY, distance);
    }

    /** {@code {top, bottom, side}} requirements over all samples for one
     *  yaw. With {@code centerY == 0} the first two are the raw sums the
     *  opening pass solves the centre from; otherwise they are the final
     *  per-view requirements. */
    private static double[] requirements(float[] pts, int count, double sin, double cos,
                                         double ky, double kx, double centerY) {
        double top = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        double side = Double.NEGATIVE_INFINITY;
        for (int o = 0; o < count * STRIDE; o += STRIDE) {
            // The sample's view-axial offset from the root plane, which the
            // servo cancels: w = (Ry(yaw) * (p - r)).z. Larger model z is
            // farther from the camera, so the sample sits at depth dist - w
            // and a near sample (w < 0) adds to the requirement.
            double w = -(pts[o] - pts[o + 3]) * sin + (pts[o + 2] - pts[o + 4]) * cos;
            double y = pts[o + 1] - centerY;
            top = Math.max(top, y * ky + w);
            bottom = Math.max(bottom, -y * ky + w);
            side = Math.max(side, Math.abs(pts[o] * cos + pts[o + 2] * sin) * kx + w);
        }
        return new double[]{top, bottom, side};
    }
}
