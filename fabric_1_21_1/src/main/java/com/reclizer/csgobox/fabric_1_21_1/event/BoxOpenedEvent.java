package com.reclizer.csgobox.fabric_1_21_1.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Fired on the mod event bus ({@link CsgoBus}) whenever a player successfully
 * opens a box and receives a result item. This is a <em>post-open
 * notification</em> — the key and box have already been consumed and the item
 * has already been granted.
 *
 * <p>Listeners can use this event to broadcast rare drops, track custom
 * statistics or quest progress, grant bonus items based on grade, or log
 * openings for analytics.</p>
 *
 * <p>This event is not cancelable — the open has already completed. To void a
 * result after the fact, call {@link #setResult(ItemStack)} with
 * {@link ItemStack#EMPTY}; note this only affects the reference exposed by the
 * event, not the item already placed in the player's inventory.</p>
 */
public class BoxOpenedEvent extends PlayerEvent {

    private final ResourceLocation boxId;
    private ItemStack resultItem;
    private final int grade;
    private final boolean bulk;

    public BoxOpenedEvent(Player player, ResourceLocation boxId, ItemStack resultItem, int grade, boolean bulk) {
        super(player);
        this.boxId = boxId;
        this.resultItem = resultItem;
        this.grade = grade;
        this.bulk = bulk;
    }

    /** The registry id of the box definition that was opened (e.g. {@code csgobox:weapon_case}). */
    public ResourceLocation getBoxId() {
        return boxId;
    }

    /** The item the player received from this opening. */
    public ItemStack getResultItem() {
        return resultItem;
    }

    /**
     * Rarity grade of the result (1–5):
     * 1 = consumer (gray), 2 = industrial (light blue), 3 = mil-spec (blue),
     * 4 = restricted (purple), 5 = classified (pink/red).
     */
    public int getGrade() {
        return grade;
    }

    /** Whether this opening was part of a bulk (batch) open operation. */
    public boolean isBulk() {
        return bulk;
    }

    /**
     * Replaces the result reference exposed by this event. This does NOT
     * retroactively remove the item from the player's inventory; it is
     * provided for listeners that wish to record or relay a different value.
     */
    public void setResult(ItemStack resultItem) {
        this.resultItem = resultItem;
    }
}
