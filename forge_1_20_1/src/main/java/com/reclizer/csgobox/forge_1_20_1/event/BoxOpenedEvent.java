package com.reclizer.csgobox.forge_1_20_1.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

public class BoxOpenedEvent extends Event {

    public static final net.minecraftforge.eventbus.api.IEventBus BUS = net.minecraftforge.common.MinecraftForge.EVENT_BUS;

    private final Player player;
    private final ResourceLocation boxId;
    private ItemStack resultItem;
    private final int grade;
    private final boolean bulk;

    public BoxOpenedEvent(Player player, ResourceLocation boxId, ItemStack resultItem, int grade, boolean bulk) {
        this.player = player;
        this.boxId = boxId;
        this.resultItem = resultItem;
        this.grade = grade;
        this.bulk = bulk;
    }

    public Player getEntity() {
        return player;
    }

    public ResourceLocation getBoxId() {
        return boxId;
    }

    public ItemStack getResultItem() {
        return resultItem;
    }

    public int getGrade() {
        return grade;
    }

    public boolean isBulk() {
        return bulk;
    }

    public void setResult(ItemStack resultItem) {
        this.resultItem = resultItem;
    }
}
