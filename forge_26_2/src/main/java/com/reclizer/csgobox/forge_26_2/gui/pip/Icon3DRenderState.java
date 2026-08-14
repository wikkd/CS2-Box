package com.reclizer.csgobox.forge_26_2.gui.pip;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Custom PIP render state that carries a pre-extracted item render state plus
 * the rotation/scale settings the GUI wants applied at draw time.
 *
 * <p>The 3D rendering work happens inside {@link Icon3DRenderer#renderToTexture}
 * — the {@code rotXDeg} / {@code rotYDeg} fields are inputs to a fresh
 * {@code PoseStack} built there, not pre-baked matrices. Carrying primitives
 * (degrees, not matrices) keeps the state immutable and safe to reuse across
 * frames for PIP texture caching.</p>
 *
 * <p>Note: this class references {@link PictureInPictureRenderState}, which
 * lives in {@code net.minecraft.client.*}. In a dedicated-server context
 * the class still loads (the JVM only resolves client classes when methods
 * are called), and the registration listener in {@code CsgoBox.registerIcon3DRenderer}
 * never fires without a client. We deliberately omit the
 * {@code @OnlyIn(Dist.CLIENT)} annotation because in NeoForge 26.1.2 it's a
 * runtime no-op that now logs an {@code ERROR}-level deprecation warning at
 * every mod load.</p>
 */
public record Icon3DRenderState(
        TrackingItemStackRenderState itemStackRenderState,
        float rotXDeg,
        float rotYDeg,
        float rotZDeg,
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
}
