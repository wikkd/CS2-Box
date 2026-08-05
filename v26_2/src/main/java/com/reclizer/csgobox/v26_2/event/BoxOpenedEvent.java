package com.reclizer.csgobox.v26_2.event;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Fired on the NeoForge event bus whenever a player successfully opens a box
 * and receives a result item. This is a <em>post-open notification</em> — the
 * key and box have already been consumed and the item has already been granted.
 *
 * <p>Listeners can use this event to:</p>
 * <ul>
 *   <li>Broadcast rare drops to the server</li>
 *   <li>Track custom statistics or quest progress</li>
 *   <li>Grant bonus items based on grade</li>
 *   <li>Log openings for analytics</li>
 * </ul>
 *
 * <p><b>KubeJS compatibility:</b> when KubeJS is installed, this event is
 * accessible from server scripts via
 * {@code NeoForgeEvents.onEvent('com.reclizer.csgobox.<version>.event.BoxOpenedEvent', ...)}.
 * No KubeJS dependency is required at compile or runtime.</p>
 *
 * <p>This event is not cancelable — the open has already completed. To void a
 * result after the fact, call {@link #setResult(ItemStack)} with
 * {@link ItemStack#EMPTY}; note this only affects the reference exposed by the
 * event, not the item already placed in the player's inventory.</p>
 */
public class BoxOpenedEvent extends PlayerEvent {

    private final Identifier boxId;
    private ItemStack resultItem;
    private final int grade;
    private final boolean bulk;

    public BoxOpenedEvent(Player player, Identifier boxId, ItemStack resultItem, int grade, boolean bulk) {
        super(player);
        this.boxId = boxId;
        this.resultItem = resultItem;
        this.grade = grade;
        this.bulk = bulk;
    }

    /** The registry id of the box definition that was opened (e.g. {@code csgobox:weapon_case}). */
    public Identifier getBoxId() {
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
