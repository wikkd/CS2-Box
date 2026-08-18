package com.reclizer.csgobox.forge_1_20_1.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

public class TerminalBuyEvent extends Event {

    public static final net.minecraftforge.eventbus.api.IEventBus BUS = net.minecraftforge.common.MinecraftForge.EVENT_BUS;

    private final Player player;
    private final int grade;
    private final int price;
    private final float wearVal;
    private final ItemStack item;
    private final int round;

    public TerminalBuyEvent(Player player, int grade, int price, float wearVal, ItemStack item, int round) {
        this.player = player;
        this.grade = grade;
        this.price = price;
        this.wearVal = wearVal;
        this.item = item;
        this.round = round;
    }

    public Player getEntity() {
        return player;
    }

    public int getGrade() {
        return grade;
    }

    public int getPrice() {
        return price;
    }

    public float getWearVal() {
        return wearVal;
    }

    public ItemStack getItem() {
        return item;
    }

    public int getRound() {
        return round;
    }
}
