package com.reclizer.csgobox.forge_1_20_1.event;

import com.reclizer.csgobox.forge_1_20_1.block.entity.ArmoryRecyclerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

public class ArmoryRecycleEvent extends Event {

    public static final net.minecraftforge.eventbus.api.IEventBus BUS = net.minecraftforge.common.MinecraftForge.EVENT_BUS;

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

    public ArmoryRecyclerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public ItemStack getInputItem() {
        return inputItem;
    }

    public int getGrade() {
        return grade;
    }

    public int getYield() {
        return yield;
    }

    @Override
    public boolean isCancelable() {
        return true;
    }
}
