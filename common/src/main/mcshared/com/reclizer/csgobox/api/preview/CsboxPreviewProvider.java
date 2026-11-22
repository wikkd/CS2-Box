package com.reclizer.csgobox.api.preview;

import net.minecraft.world.item.ItemStack;

/**
 * Third-party extension point for the CS2-Box 3D item preview (2026-11-22
 * 预览适配接口 v1). Implement this, register it via
 * {@link CsboxPreviewAPI#register} during client setup, and matching items
 * render through your {@link PreviewSource} everywhere CS2-Box shows 3D
 * previews: the box preview grid, terminal offer hover, the inspect screen,
 * drag-follow previews and the bulk overview.
 *
 * <p>Minimal example:</p>
 *
 * <pre>{@code
 * public final class MyPreviewProvider extends CsboxPreviewProvider {
 *     @Override
 *     public boolean matches(ItemStack stack) {
 *         return stack.getItem() == ModItems.MY_BOXED_ITEM.get();
 *     }
 *
 *     @Override
 *     public PreviewSource preview(ItemStack stack) {
 *         return PreviewSource.entity(ModEntities.MY_MONSTER.get(), null, RestPose.NONE, 1.0F);
 *     }
 * }
 * }</pre>
 *
 * <p>Contract:</p>
 * <ul>
 *   <li>Return {@code null} from {@link #preview} to pass the item through to
 *       CS2-Box's built-in chain and default pipeline.</li>
 *   <li>{@link #matches} and {@link #preview} run every frame for every
 *       visible item — keep them cheap; cache inside your provider when the
 *       answer needs heavy computation.</li>
 *   <li>Providers must be stateless across frames (or thread-confined to the
 *       render thread); they are called from the render thread only.</li>
 *   <li>Register during client setup (FMLClientSetupEvent / Fabric
 *       onInitializeClient). Registration order does not matter; use
 *       {@link #priority} when two providers must fight over the same item —
 *       higher priority wins, ties keep registration order.</li>
 * </ul>
 */
public abstract class CsboxPreviewProvider {

    /** Sort key between providers; higher wins. Default 0. */
    public int priority() {
        return 0;
    }

    /** Whether this provider owns the preview of {@code stack}. */
    public abstract boolean matches(ItemStack stack);

    /**
     * The preview to render, or {@code null} to let the built-in chain and the
     * default pipeline handle the item.
     */
    public abstract PreviewSource preview(ItemStack stack);
}
