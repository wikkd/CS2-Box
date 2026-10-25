package com.reclizer.csgobox.fabric_1_21_1.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Fired on the mod event bus ({@link CsgoBus}) BEFORE a box open is rolled and
 * BEFORE any key or box is consumed. Canceling this event aborts the open
 * cleanly — nothing has been rolled or consumed yet, so there is no rollback
 * needed.
 *
 * <p>Listeners can use this event to veto opens (permission gates, quest
 * requirements, event boxes, daily caps) or observe open attempts for
 * statistics.</p>
 *
 * <p><b>Boundary contract:</b> this event fires AFTER the built-in guards
 * (held item check, alive check, 10-tick cooldown, non-terminal definition)
 * and BEFORE the server-authoritative RNG roll and inventory consumption. It
 * is never fired for requests already rejected by those guards, and it cannot
 * change the result of an open — only allow or refuse it. To redirect an
 * open to a different box definition, use a wrapper box instead.</p>
 */
public class BoxOpeningEvent extends PlayerEvent implements Cancellable {

    private final ResourceLocation boxId;
    private final boolean bulk;
    private final int count;
    private boolean canceled;

    public BoxOpeningEvent(Player player, ResourceLocation boxId, boolean bulk, int count) {
        super(player);
        this.boxId = boxId;
        this.bulk = bulk;
        this.count = count;
    }

    /** The registry id of the box definition being opened (e.g. {@code csgobox:weapon_case}). */
    public ResourceLocation getBoxId() {
        return boxId;
    }

    /** Whether this is a bulk (batch) open request; {@link #getCount()} carries the batch size. */
    public boolean isBulk() {
        return bulk;
    }

    /** Number of boxes about to open: 1 for a single open, the server-authorized batch size for a bulk open. */
    public int getCount() {
        return count;
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
