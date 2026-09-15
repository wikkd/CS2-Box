package com.reclizer.csgobox.forge_1_20_1.event;

import com.reclizer.csgobox.forge_1_20_1.block.entity.ArmoryRecyclerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired on the Forge event bus ({@link #BUS}) just before the Armory
 * Recycler consumes an input item and produces Armory Points. Canceling
 * this event keeps the input item in the machine's input slot and produces
 * nothing — the machine skips the stack (progress resets, the player can
 * retrieve it) and does not fire again for that same stack until it leaves
 * the input slot.
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
 * {@code ForgeEvents.onEvent('com.reclizer.csgobox.forge_1_20_1.event.ArmoryRecycleEvent', ...)}.
 * Call {@link #setCanceled(boolean)} to refuse the recycle — the input item
 * stays in the machine.</p>
 */
public class ArmoryRecycleEvent extends Event {

    public static final net.minecraftforge.eventbus.api.IEventBus BUS = net.minecraftforge.common.MinecraftForge.EVENT_BUS;

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

    public ArmoryRecyclerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public ItemStack getInputItem() {
        return inputItem;
    }

    public int getGrade() {
        return grade;
    }

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
    public boolean isCancelable() {
        return true;
    }
}
