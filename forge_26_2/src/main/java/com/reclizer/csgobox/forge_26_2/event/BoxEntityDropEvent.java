package com.reclizer.csgobox.forge_26_2.event;

import com.reclizer.csgobox.forge_26_2.box.BoxDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.event.MutableEvent;

/**
 * Fired on the Forge event bus ({@link #BUS}, rooted at {@code BusGroup.DEFAULT})
 * when a configured entity dies and CS2-Box is about to roll its box drop —
 * one event per matching box definition, BEFORE the RNG roll and BEFORE the
 * box item spawns. Listeners can cancel the drop
 * ({@link #setCanceled(boolean)}) or adjust the effective drop rate via
 * {@link #setDropRate(float)} (e.g. event weekends, loot luck, per-box gates).
 *
 * <p><b>KubeJS compatibility:</b> when KubeJS is installed, this event is
 * accessible from server scripts via
 * {@code ForgeEvents.onEvent('com.reclizer.csgobox.forge_26_2.event.BoxEntityDropEvent', ...)}.
 * Call {@link #setCanceled(boolean)} to suppress the drop, or
 * {@link #setDropRate(float)} to change its chance.</p>
 *
 * <p>The drop rate is capped at 1.0; a negative value suppresses the roll.</p>
 */
public class BoxEntityDropEvent extends MutableEvent {

    public static final EventBus<BoxEntityDropEvent> BUS = EventBus.create(BoxEntityDropEvent.class);

    private final Identifier entityType;
    private final LivingEntity mob;
    private final BoxDefinition definition;
    private float dropRate;
    private boolean canceled;

    public BoxEntityDropEvent(Identifier entityType, LivingEntity mob,
                              BoxDefinition definition, float dropRate) {
        this.entityType = entityType;
        this.mob = mob;
        this.definition = definition;
        this.dropRate = Math.min(Math.max(dropRate, 0.0F), 1.0F);
    }

    /** The entity type that died (config key of the drop table, e.g. {@code minecraft:zombie}). */
    public Identifier getEntityType() {
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

    /** Marks this drop as suppressed; no roll happens and nothing spawns. */
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    /** Whether a listener suppressed this drop. */
    public boolean isCanceled() {
        return canceled;
    }
}