package com.reclizer.csgobox.v26_2.gui.pip;

import com.reclizer.csgobox.utils.Quat;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Custom PIP render state that carries a pre-extracted item render state plus
 * the rotation/scale settings the GUI wants applied at draw time.
 *
 * <p>The 3D rendering work happens inside {@link Icon3DRenderer#renderToTexture}
 * — the {@link Quat} field is the user-driven orientation in the renderer's
 * rotation frame (horizontal drag spins the vertical axis). Carrying the raw
 * quaternion (four floats) keeps the state immutable and safe to reuse across
 * frames for PIP texture caching.</p>
 *
 * <p>This class references {@link PictureInPictureRenderState}, which
 * lives in {@code net.minecraft.client.*}. In a dedicated-server context
 * the class still loads (the JVM only resolves client classes when methods
 * are called), and the registration listener in {@code CsgoBox.registerIcon3DRenderer}
 * never fires without a client. The {@code @OnlyIn(Dist.CLIENT)} annotation
 * is omitted because in NeoForge 26.1.2 it's a
 * runtime no-op that now logs an {@code ERROR}-level deprecation warning at
 * every mod load.</p>
 */
public record Icon3DRenderState(
        TrackingItemStackRenderState itemStackRenderState,
        Quat rotation,
        float targetPixelSize,
        float modelSpan,
        float modelCenterX,
        float modelCenterY,
        float modelCenterZ,
        int x0,
        int y0,
        int x1,
        int y1
) implements PictureInPictureRenderState {

    /** Fits the model's largest axis into the preview square with a small margin. */
    @Override
    public float scale() {
        return targetPixelSize * 0.82F / Math.max(0.01F, modelSpan);
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return null;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
    }

    /** GUI-side 2D pose is identity — the parent interface's default
     *  ({@link PictureInPictureRenderState#pose}) returns its built-in
     *  {@code IDENTITY_POSE}, and the parent screen already positions
     *  the slot via its own Matrix3x2fStack. We rely on the default
     *  here (no override) so we don't have to keep a mutable Matrix3x2f
     *  field in sync. */
}
