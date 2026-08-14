package com.reclizer.csgobox.forge_26_2.event;

import com.reclizer.csgobox.forge_26_2.block.entity.ArmoryRecyclerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.event.MutableEvent;

/**
 * Fired on the Forge event bus ({@link #BUS}, rooted at {@code BusGroup.DEFAULT})
 * just before the Armory Recycler consumes an input item and produces Armory
 * Points. Canceling this event keeps the input item in the machine's input
 * slot and produces nothing — the machine skips the item (progress resets,
 * the player can retrieve it).
 *
 * <p>Listeners can use this event to:</p>
 * <ul>
 *   <li>Blacklist items from being recycled (farm / exploit shields)</li>
 *   <li>Adjust the effective yield by tracking or intercepting specific items</li>
 *   <li>Observe recycling activity for statistics</li>
 * </ul>
 *
 * <p><b>KubeJS compatibility:</b> when KubeJS is installed, this event is
 * accessible from server scripts via
 * {@code ForgeEvents.onEvent('com.reclizer.csgobox.forge_26_2.event.ArmoryRecycleEvent', ...)}.
 * Call {@link #setCanceled(boolean)} to refuse the recycle — the input item
 * stays in the machine.</p>
 */
public class ArmoryRecycleEvent extends MutableEvent {

    public static final EventBus<ArmoryRecycleEvent> BUS = EventBus.create(ArmoryRecycleEvent.class);

    private final ArmoryRecyclerBlockEntity blockEntity;
    private final ItemStack inputItem;
    private final int grade;
    private final int yield;
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

    /** Marks this recycle as refused; the input stays in the machine and nothing is produced. */
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    /** Whether a listener refused this recycle. */
    public boolean isCanceled() {
        return canceled;
    }
}
