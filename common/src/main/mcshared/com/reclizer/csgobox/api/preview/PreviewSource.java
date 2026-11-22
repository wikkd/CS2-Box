package com.reclizer.csgobox.api.preview;

import com.reclizer.csgobox.utils.Quat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * What the CS2-Box preview pipeline should render for an item (2026-11-22
 * 预览适配接口 v1). Returned by {@link CsboxPreviewProvider#preview} — exactly
 * one of the three kinds is present. The pipeline stays responsible for the
 * hard parts: bounds probing, auto-fit scaling, drag pivot selection, shading
 * and the degrade chain; providers only declare the preview source.
 *
 * <p>Advanced escape hatch: {@link #raw} hands the provider the same canvas a
 * built-in strategy gets. No auto-fit or centering is applied to raw previews
 * — the provider owns the full pose math. Prefer {@link #entity} /
 * {@link #block} whenever possible.</p>
 */
public record PreviewSource(
        EntityType<?> entityType,
        CompoundTag spawnData,
        BlockState blockState,
        RawRenderer rawRenderer,
        RestPose restPose,
        float fitBoost
) {
    public PreviewSource {
        restPose = restPose == null ? RestPose.NONE : restPose;
        if (!(fitBoost > 0F)) {
            fitBoost = 1.0F;
        }
    }

    /** Renders the given entity type through the entity preview pipeline. */
    public static PreviewSource entity(EntityType<?> type, CompoundTag spawnData, RestPose pose, float fitBoost) {
        return new PreviewSource(Objects.requireNonNull(type, "entityType"), spawnData, null, null, pose, fitBoost);
    }

    /** Renders the given block state through the block preview pipeline. */
    public static PreviewSource block(BlockState state, RestPose pose, float fitBoost) {
        return new PreviewSource(null, null, Objects.requireNonNull(state, "blockState"), null, pose, fitBoost);
    }

    /**
     * Advanced: draws directly on the caller's canvas with no pipeline assist.
     * The canvas is already positioned so the preview centre sits at
     * {@code (cx + 8*scale, cy + 8*scale)}; {@code rotation} is the current
     * drag orientation.
     */
    public static PreviewSource raw(RawRenderer renderer) {
        return new PreviewSource(null, null, null, Objects.requireNonNull(renderer, "rawRenderer"), RestPose.NONE, 1.0F);
    }

    public boolean isEntity() {
        return entityType != null;
    }

    public boolean isBlock() {
        return blockState != null;
    }

    public boolean isRaw() {
        return rawRenderer != null;
    }

    /**
     * Raw draw callback. Receives the same canvas a built-in strategy body
     * gets: {@code cx, cy} are the preview cell's centre coordinates, and
     * {@code rotation} carries the current drag orientation. Called every
     * frame while the item is on screen.
     */
    @FunctionalInterface
    public interface RawRenderer {
        void render(GuiGraphics graphics, ItemStack stack, LivingEntity player,
                    int cx, int cy, Quat rotation, float scale);
    }
}
