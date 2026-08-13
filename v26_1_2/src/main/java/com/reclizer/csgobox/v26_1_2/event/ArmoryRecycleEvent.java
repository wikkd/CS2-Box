package com.reclizer.csgobox.v26_1_2.event;

import com.reclizer.csgobox.v26_1_2.block.entity.ArmoryRecyclerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on the NeoForge event bus just before the Armory Recycler consumes an
 * input item and produces Armory Points. Canceling this event keeps the input
 * item in the machine's input slot and produces nothing — the machine skips
 * the item (progress resets, the player can retrieve it).
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
 * {@code NeoForgeEvents.onEvent('com.reclizer.csgobox.<version>.event.ArmoryRecycleEvent', ...)}.
 * Call {@code event.cancel()} (KubeJS) or {@link #setCanceled(boolean)} to
 * refuse the recycle — the input item stays in the machine.</p>
 */
public class ArmoryRecycleEvent extends Event implements ICancellableEvent {

    private final ArmoryRecyclerBlockEntity blockEntity;
    private final ItemStack inputItem;
    private final int grade;
    private final int yield;

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
}
