package com.reclizer.csgobox.v26_2.event;

import com.reclizer.csgobox.v26_2.block.entity.ArmoryRecyclerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on the NeoForge event bus just before the Armory Recycler consumes an
 * input item and produces Armory Points. Canceling this event keeps the input
 * item in the machine's input slot and produces nothing — the machine skips
 * the stack (progress resets, the player can retrieve it) and does not fire
 * again for that same stack until it leaves the input slot.
 *
 * <p>Listeners can use this event to:</p>
 * <ul>
 *   <li>Blacklist items from being recycled (farm / exploit shields)</li>
 *   <li>Adjust the effective yield for specific items via {@link #setYield(int)}</li>
 *   <li>Observe recycling activity for statistics</li>
 * </ul>
 *
 * <p><b>KubeJS compatibility:</b> when KubeJS is installed, this event is
 * accessible from server scripts via
 * {@code NeoForgeEvents.onEvent('com.reclizer.csgobox.<version>.event.ArmoryRecycleEvent', ...)}.
 * Call {@code event.cancel()} (KubeJS) or {@link #setCanceled(boolean)} to
 * refuse the recycle — the input item stays in the machine.</p>
 */
public class ArmoryRecycleEvent extends Event implements ICancellableEvent {

    private final ArmoryRecyclerBlockEntity blockEntity;
    private final ItemStack inputItem;
    private final int grade;
    private int yield;

    public ArmoryRecycleEvent(ArmoryRecyclerBlockEntity blockEntity, ItemStack inputItem, int grade, int yield) {
        this.blockEntity = blockEntity;
        this.inputItem = inputItem;
        this.grade = grade;
        this.yield = yield;
    }

    /** The recycler machine about to consume the input. */
    public ArmoryRecyclerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /** Copy of the item about to be consumed — mutating it has no effect. */
    public ItemStack getInputItem() {
        return inputItem;
    }

    /** Rarity grade of the input item (1–5). */
    public int getGrade() {
        return grade;
    }

    /** Armory Points the recycle would produce. */
    public int getYield() {
        return yield;
    }

    /**
     * Re-prices this recycle (Armory Points). {@code 0} (or negative) means
     * "consume nothing": the machine keeps the input and produces no output,
     * exactly like a cancellation. Raising the yield above what the output
     * slot can still hold also leaves the input untouched.
     */
    public void setYield(int yield) {
        this.yield = Math.max(0, yield);
    }
}