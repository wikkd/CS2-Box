package com.reclizer.csgobox.fabric_1_21_1.event;

import com.reclizer.csgobox.fabric_1_21_1.block.entity.ArmoryRecyclerBlockEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Fired on the mod event bus ({@link CsgoBus}) just before the Armory
 * Recycler consumes an input item and produces Armory Points. Canceling this
 * event keeps the input item in the machine's input slot and produces nothing
 * — the machine skips the item (progress resets, the player can retrieve it)
 * and does not fire again for that same stack until it leaves the input slot.
 *
 * <p>Listeners can blacklist items from being recycled (farm / exploit
 * shields), adjust the effective yield via {@link #setYield(int)}, or observe
 * recycling activity for statistics.</p>
 */
public class ArmoryRecycleEvent implements Cancellable {

    private final ArmoryRecyclerBlockEntity blockEntity;
    private final ItemStack inputItem;
    private final int grade;
    private int yield;
    private boolean canceled;

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

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}
