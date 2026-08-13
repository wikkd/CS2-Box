package com.reclizer.csgobox.v26_2.event;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Fired on the NeoForge event bus BEFORE a box open is rolled and BEFORE any
 * key or box is consumed. Canceling this event aborts the open cleanly —
 * nothing has been rolled or consumed yet, so there is no rollback needed.
 *
 * <p>Listeners can use this event to:</p>
 * <ul>
 *   <li>Veto opens (permission gates, quest requirements, event boxes, daily caps)</li>
 *   <li>Observe open attempts for statistics or analytics</li>
 * </ul>
 *
 * <p><b>KubeJS compatibility:</b> when KubeJS is installed, this event is
 * accessible from server scripts via
 * {@code NeoForgeEvents.onEvent('com.reclizer.csgobox.<version>.event.BoxOpeningEvent', ...)}.
 * Call {@code event.cancel()} (KubeJS) or {@link #setCanceled(boolean)} to
 * refuse the open — the client screen closes and nothing is consumed.</p>
 *
 * <p><b>Boundary contract:</b> this event fires AFTER the built-in guards
 * (held item check, alive check, 10-tick cooldown, non-terminal definition)
 * and BEFORE the server-authoritative RNG roll and inventory consumption. It
 * is never fired for requests already rejected by those guards, and it cannot
 * change the result of an open — only allow or refuse it. To redirect an
 * open to a different box definition, use a wrapper box instead.</p>
 */
public class BoxOpeningEvent extends PlayerEvent implements ICancellableEvent {

    private final Identifier boxId;
    private final boolean bulk;
    private final int count;

    public BoxOpeningEvent(Player player, Identifier boxId, boolean bulk, int count) {
        super(player);
        this.boxId = boxId;
        this.bulk = bulk;
        this.count = count;
    }

    /** The registry id of the box definition being opened (e.g. {@code csgobox:weapon_case}). */
    public Identifier getBoxId() {
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
}
