package com.reclizer.csgobox.forge_1_20_1.block.entity;

import com.reclizer.csgobox.forge_1_20_1.block.ModBlocks;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ModItems;
import com.reclizer.csgobox.forge_1_20_1.menu.ArmoryRecyclerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ArmoryRecyclerBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, ContainerData {

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    public static final int SMELT_TICKS = 40;

    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int progress;

    public ArmoryRecyclerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ARMORY_RECYCLER_BE.get(), pos, state);
    }

    public static int yieldForGrade(int grade) {
        switch (grade) {
            case 1: return 3;
            case 2: return 5;
            case 3: return 7;
            case 4: return 8;
            case 5: return 8;
            default: return 0;
        }
    }

    public void tick() {
        if (level == null || level.isClientSide()) return;
        ItemStack in = getItem(INPUT_SLOT);
        Integer grade = in.isEmpty() ? null : ItemCsgoBox.getGrade(in);
        int yield = grade != null && grade >= 1 && grade <= 5 ? yieldForGrade(grade) : 0;
        if (yield <= 0 || !canAcceptOutput(yield)) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            return;
        }
        progress++;
        if (progress >= SMELT_TICKS) {
            progress = 0;
            in.shrink(1);
            addOutput(yield);
            level.playSound(null, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                    worldPosition.getZ() + 0.5D, SoundEvents.VILLAGER_WORK_ARMORER,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        setChanged();
    }

    private boolean canAcceptOutput(int amount) {
        ItemStack out = getItem(OUTPUT_SLOT);
        if (out.isEmpty()) return true;
        return out.is(ModItems.ITEM_ARMORY_POINT.get())
                && out.getCount() + amount <= out.getMaxStackSize();
    }

    private void addOutput(int amount) {
        ItemStack out = getItem(OUTPUT_SLOT);
        if (out.isEmpty()) {
            setItem(OUTPUT_SLOT, new ItemStack(ModItems.ITEM_ARMORY_POINT.get(), amount));
        } else {
            out.grow(amount);
        }
    }

    // ---- ContainerData -------------------------------------------------------

    @Override
    public int get(int index) {
        return index == 0 ? progress : 0;
    }

    @Override
    public void set(int index, int value) {
        if (index == 0) {
            progress = value;
        }
    }

    @Override
    public int getCount() {
        return 1;
    }

    // ---- Container / BaseContainerBlockEntity ---------------------------------

    @Override
    public int getContainerSize() {
        return 2;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        items.set(index, stack);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack result = net.minecraft.world.ContainerHelper.removeItem(items, index, count);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return net.minecraft.world.ContainerHelper.takeItem(items, index);
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.csgobox.armory_recycler");
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.csgobox.armory_recycler");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv) {
        return new ArmoryRecyclerMenu(id, inv, this, this, this);
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction direction) {
        if (index == OUTPUT_SLOT) return false;
        Integer grade = ItemCsgoBox.getGrade(stack);
        return grade != null && grade >= 1 && grade <= 5;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return index == OUTPUT_SLOT;
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return new int[]{INPUT_SLOT, OUTPUT_SLOT};
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items);
        tag.putInt("Progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(2, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, items);
        progress = tag.getInt("Progress");
    }
}
