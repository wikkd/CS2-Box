package com.reclizer.csgobox.api.preview;

/**
 * Rest orientation for a declared preview, in degrees (2026-11-22 预览适配
 * 接口 v1). Applied in model space — the pipeline conjugates it under the
 * user's drag rotation, so the rest pose is what the item looks like before
 * the player drags it (e.g. {@code yaw=180} turns a back-facing model to face
 * the viewer, the Sophisticated Backpacks convention).
 *
 * <p>Immutable value: return the same instance (or {@link #NONE}) per call.</p>
 */
public record RestPose(float yawDeg, float pitchDeg, float rollDeg) {

    /** Identity pose: no extra orientation. */
    public static final RestPose NONE = new RestPose(0F, 0F, 0F);

    /** True when this pose carries no rotation. */
    public boolean isNone() {
        return yawDeg == 0F && pitchDeg == 0F && rollDeg == 0F;
    }
}
