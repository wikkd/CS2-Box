package com.reclizer.csgobox.v1_21_1.event;

import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on the NeoForge event bus when a configured entity dies and CS2-Box
 * is about to roll its box drop — one event per matching box definition,
 * BEFORE the RNG roll and BEFORE the box item spawns. Listeners can cancel
 * the drop ({@code setCanceled(true)}) or adjust the effective drop rate via
 * {@link #setDropRate(float)} (e.g. event weekends, loot luck, per-box gates).
 *
 * <p><b>KubeJS compatibility:</b> when KubeJS is installed, this event is
 * accessible from server scripts via
 * {@code NeoForgeEvents.onEvent('com.reclizer.csgobox.v1_21_1.event.BoxEntityDropEvent', ...)}.
 * Call {@code event.cancel()} to suppress the drop, or
 * {@code event.setDropRate(0.5)} to change its chance.</p>
 *
 * <p>The drop rate is capped at 1.0; a negative value suppresses the roll.</p>
 */
public class BoxEntityDropEvent extends Event implements ICancellableEvent {

    private final ResourceLocation entityType;
    private final LivingEntity mob;
    private final BoxDefinition definition;
    private float dropRate;

    public BoxEntityDropEvent(ResourceLocation entityType, LivingEntity mob,
                              BoxDefinition definition, float dropRate) {
        this.entityType = entityType;
        this.mob = mob;
        this.definition = definition;
        this.dropRate = Math.min(Math.max(dropRate, 0.0F), 1.0F);
    }

    /** The entity type that died (config key of the drop table, e.g. {@code minecraft:zombie}). */
    public ResourceLocation getEntityType() {
        return entityType;
    }

    /** The dying mob itself. */
    public LivingEntity getMob() {
        return mob;
    }

    /** The box definition about to drop. */
    public BoxDefinition getDefinition() {
        return definition;
    }

    /** Effective drop rate after looting + global config, clamped to [0, 1]. */
    public float getDropRate() {
        return dropRate;
    }

    /** Overrides the effective drop rate (clamped to [0, 1]; 0 suppresses the roll). */
    public void setDropRate(float dropRate) {
        this.dropRate = Math.min(Math.max(dropRate, 0.0F), 1.0F);
    }
}