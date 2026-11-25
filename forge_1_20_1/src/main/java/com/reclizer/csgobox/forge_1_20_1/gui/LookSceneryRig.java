package com.reclizer.csgobox.forge_1_20_1.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The scenery camera rig behind the look screen's live world backdrop: a
 * client-side-only marker entity the render camera rides via
 * {@link Minecraft#setCameraEntity} (the vanilla spectator camera
 * mechanism, no mixins). Markers never render and have no AI, and the
 * player's own view is never touched.
 *
 * <p>Lifecycle: the screen mounts on first init — the rig parks just in
 * front of the player's face and spins its yaw slowly and continuously
 * (main-menu panorama pace). The picture toolbar button flies the camera
 * to a random nearby spot with a short cinematic glide. On
 * {@link #dismount} the camera returns to the player and the rig is
 * discarded.</p>
 */
public final class LookSceneryRig {
    /** Random scenery (picture toolbar button): pitch band around the horizon
     *  in degrees; yaw is a full random circle. */
    private static final float SCENERY_PITCH_RANGE_DEG = 30.0F;
    /** Flight duration in client ticks: a slow cinematic glide (instant-replay
     *  style camera move). */
    private static final int GLIDE_TICKS = 60;
    /** Background panorama spin: continuous slow yaw drift while the screen
     *  is open, in degrees per client tick — the vanilla main-menu pace
     *  (PanoramaRenderer adds deltaTicks * panoramaSpeed * 0.1, i.e. 2 deg/s
     *  at the default 100% panorama speed). */
    private static final float SPIN_DEG_PER_TICK = 0.1F;
    /** Mount parking spot: this far in front of the player's face (along the
     *  view direction), so the camera starts outside the head model. */
    private static final double MOUNT_FORWARD = 0.5;
    /** Scenery flight: distance range from the player (blocks) and the
     *  clearance above the surface heightmap the camera parks at. */
    private static final double DIST_MIN = 8.0;
    private static final double DIST_RANGE = 12.0;
    private static final double SURFACE_CLEARANCE = 2.0;
    /** Client-side rig entity id seed (negative: never collides with server
     *  entity ids, which count up from zero). */
    private static int rigIdCounter;

    private Entity rig;
    private Entity originalCamera;
    /** Flight animation state: pose to fly from, target pose, and tick
     *  progress within GLIDE_TICKS (-1 = not flying). */
    private double fromX;
    private double fromY;
    private double fromZ;
    private float fromYaw;
    private float fromPitch;
    private double toX;
    private double toY;
    private double toZ;
    private float toYaw;
    private float toPitch;
    private int animTick = -1;
    /** Guards the screen-open camera mount so window resizes don't re-mount. */
    private boolean autoStarted;
    /** True while we hid the local player model for the panorama mount
     *  (skipped when already invisible, e.g. an invisibility potion). */
    private boolean playerHidden;

    /** True while the rig owns the render camera (panorama mount); read by
     *  the render-cancel handler in the event package. */
    public boolean mounted() {
        return this.rig != null;
    }

    /** Screen-open camera mount: the background rides the scenery rig like
     *  the main-menu panorama (continuous slow spin; the position never
     *  changes until a "change scenery" click). Later init() calls (window
     *  resize) are no-ops. */
    public void beginPanorama(Minecraft mc, Player player) {
        if (this.autoStarted) {
            return;
        }
        this.autoStarted = true;
        if (mc == null || mc.level == null || player == null) {
            return;
        }
        if (this.rig != null) {
            return;
        }
        if (!this.mount(mc, player)) {
            return;
        }
        // The panorama shows the world, not the player: hide the local model
        // while mounted (mid-spin the head would otherwise sweep through the
        // close-up frame). Restored in dismount().
        if (!player.isInvisible()) {
            player.setInvisible(true);
            this.playerHidden = true;
        }
    }

    /** Scenery toolbar button: fly the render camera to a random nearby spot
     *  so the live world backdrop behind the item changes, while the
     *  player's own view is never touched. The camera rides a client-side
     *  marker entity (vanilla spectator camera mechanism), mounted when the
     *  screen opens and later flying from wherever it currently is.
     *  {@link #dismount} hands the camera back and discards the rig. */
    public void randomize(Minecraft mc, Player player) {
        if (mc == null || mc.level == null || player == null) {
            return;
        }
        if (this.rig == null && !this.mount(mc, player)) {
            return;
        }
        this.snapshotPose();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        double dist = DIST_MIN + rnd.nextDouble() * DIST_RANGE;
        this.toX = player.getX() + Math.cos(angle) * dist;
        // Glide the short way around the circle: wrap the delta, not the target.
        this.toY = this.surfaceY(mc, player, this.toX, player.getZ() + Math.sin(angle) * dist);
        this.toZ = player.getZ() + Math.sin(angle) * dist;
        this.toYaw = this.fromYaw + wrapDegrees(rnd.nextFloat() * 360.0F - this.fromYaw);
        this.toPitch = (rnd.nextFloat() * 2.0F - 1.0F) * SCENERY_PITCH_RANGE_DEG;
        this.animTick = 0;
    }

    /** Advances the scenery camera: between flights it spins the rig yaw
     *  slowly and continuously (main-menu panorama pace); during a flight it
     *  eases the rig (and thus the render camera) from the pose the click
     *  started at toward the random target over GLIDE_TICKS client ticks.
     *  Old-position bookkeeping feeds the vanilla per-frame interpolation,
     *  so both stay smooth at frame rate. */
    public void advance() {
        if (this.rig == null) {
            return;
        }
        if (this.animTick < 0) {
            // Panorama spin: slow continuous yaw drift, position untouched.
            this.rig.yRotO = this.rig.getYRot();
            this.rig.setYRot(wrapDegrees(this.rig.getYRot() + SPIN_DEG_PER_TICK));
            return;
        }
        this.animTick++;
        float t = Math.min(1.0F, (float) this.animTick / (float) GLIDE_TICKS);
        float e = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t); // easeOutCubic
        this.rig.xo = this.rig.getX();
        this.rig.yo = this.rig.getY();
        this.rig.zo = this.rig.getZ();
        this.rig.yRotO = this.rig.getYRot();
        this.rig.xRotO = this.rig.getXRot();
        this.rig.setPos(this.fromX + (this.toX - this.fromX) * e,
                this.fromY + (this.toY - this.fromY) * e,
                this.fromZ + (this.toZ - this.fromZ) * e);
        this.rig.setYRot(this.fromYaw + (this.toYaw - this.fromYaw) * e);
        this.rig.setXRot(this.fromPitch + (this.toPitch - this.fromPitch) * e);
        if (t >= 1.0F) {
            this.animTick = -1;
        }
    }

    /** Screen teardown: hand the render camera back to the player, drop the
     *  rig, and unhide the local player model. Safe when never mounted. */
    public void dismount(Minecraft mc, Player player) {
        if (this.rig != null) {
            if (mc != null && mc.getCameraEntity() == this.rig) {
                mc.setCameraEntity(this.originalCamera != null ? this.originalCamera : player);
            }
            this.rig.discard();
            this.rig = null;
            this.animTick = -1;
            this.autoStarted = false;
        }
        if (this.playerHidden) {
            if (player != null) {
                player.setInvisible(false);
            }
            this.playerHidden = false;
        }
    }

    /** Creates the rig and mounts the camera; false when the level is gone
     *  or the marker entity could not be created. */
    private boolean mount(Minecraft mc, Player player) {
        ClientLevel level = mc.level;
        Entity created = EntityType.MARKER.create(level);
        if (created == null) {
            return false;
        }
        created.setId(-1_900_000_000 - rigIdCounter++);
        created.moveTo(player.getX(), player.getEyeY(), player.getZ(),
                player.getYRot(), player.getXRot());
        // Park in front of the face (along the view direction) so the camera
        // starts outside the head model instead of sitting inside it.
        double lookYaw = Math.toRadians(player.getYRot());
        double lookPitch = Math.toRadians(player.getXRot());
        created.setPos(created.getX() - Math.sin(lookYaw) * Math.cos(lookPitch) * MOUNT_FORWARD,
                created.getY() - Math.sin(lookPitch) * MOUNT_FORWARD,
                created.getZ() + Math.cos(lookYaw) * Math.cos(lookPitch) * MOUNT_FORWARD);
        created.xo = created.getX();
        created.yo = created.getY();
        created.zo = created.getZ();
        created.yRotO = created.getYRot();
        created.xRotO = created.getXRot();
        level.addFreshEntity(created);
        this.rig = created;
        this.originalCamera = mc.getCameraEntity();
        mc.setCameraEntity(this.rig);
        return true;
    }

    private void snapshotPose() {
        this.fromX = this.rig.getX();
        this.fromY = this.rig.getY();
        this.fromZ = this.rig.getZ();
        this.fromYaw = this.rig.getYRot();
        this.fromPitch = this.rig.getXRot();
    }

    /** Surface height at the target block, with an unloaded-chunk fallback
     *  (heightmaps answer the world floor for missing chunks). */
    private double surfaceY(Minecraft mc, Player player, double x, double z) {
        ClientLevel level = mc.level;
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE,
                (int) Math.floor(x), (int) Math.floor(z));
        if (surface <= level.getMinBuildHeight()) {
            return player.getY() + 4.0;
        }
        return surface + SURFACE_CLEARANCE;
    }

    /** Wraps an angle delta to [-180, 180). */
    private static float wrapDegrees(float deg) {
        deg %= 360.0F;
        if (deg >= 180.0F) {
            deg -= 360.0F;
        }
        if (deg < -180.0F) {
            deg += 360.0F;
        }
        return deg;
    }
}
