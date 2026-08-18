package com.reclizer.csgobox.forge_1_20_1.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

public class BoxOpeningEvent extends Event {

    public static final net.minecraftforge.eventbus.api.IEventBus BUS = net.minecraftforge.common.MinecraftForge.EVENT_BUS;

    private final Player player;
    private final ResourceLocation boxId;
    private final boolean bulk;
    private final int count;

    public BoxOpeningEvent(Player player, ResourceLocation boxId, boolean bulk, int count) {
        this.player = player;
        this.boxId = boxId;
        this.bulk = bulk;
        this.count = count;
    }

    public Player getEntity() {
        return player;
    }

    public ResourceLocation getBoxId() {
        return boxId;
    }

    public boolean isBulk() {
        return bulk;
    }

    public int getCount() {
        return count;
    }

    @Override
    public boolean isCancelable() {
        return true;
    }
}
