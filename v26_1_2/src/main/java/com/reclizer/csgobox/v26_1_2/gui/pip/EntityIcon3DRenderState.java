package com.reclizer.csgobox.v26_1_2.gui.pip;

import com.reclizer.csgobox.utils.Quat;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Custom PIP render state carrying a pre-extracted {@link EntityRenderState}
 * plus the drag rotation/scale settings — the entity mirror of
 * {@link Icon3DRenderState} (spawn eggs, armor stands, summon containers,
 * same-id entities).
 *
 * <p>A dedicated renderer is used instead of vanilla's GuiEntityRenderer
 * because the vanilla one applies no {@code (1,-1,-1)} flip, so its rotation
 * frame would spin entities opposite to item previews. Keeping the item
 * renderer's exact pose pipeline makes the drag feel identical across both.</p>
 */
public record EntityIcon3DRenderState(
        EntityRenderState entityRenderState,
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
        // Clip 3D model rendering to the preview bounds so the dark
        // render-target background does not overflow and cover UI elements.
        return new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
    }
}
