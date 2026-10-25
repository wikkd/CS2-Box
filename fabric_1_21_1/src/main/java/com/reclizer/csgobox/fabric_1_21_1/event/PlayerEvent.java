package com.reclizer.csgobox.fabric_1_21_1.event;

import net.minecraft.world.entity.player.Player;

/**
 * Base class for the mod's player-bound events — the Fabric equivalent of
 * NeoForge's {@code PlayerEvent} base (which is loader-bound).
 */
public abstract class PlayerEvent {
    private final Player player;

    protected PlayerEvent(Player player) {
        this.player = player;
    }

    /** The player this event concerns. */
    public Player getEntity() {
        return player;
    }
}
