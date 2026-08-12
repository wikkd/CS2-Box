package com.reclizer.csgobox.v26_1_2.block.entity;

import com.reclizer.csgobox.v26_1_2.block.ModBlocks;
import com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_1_2.item.ModItems;
import com.reclizer.csgobox.v26_1_2.menu.ArmoryRecyclerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Armory Recycler's block entity: a furnace-style converter that turns
 * graded box items (stamped with {@link ItemCsgoBox#GRADE}) into Armory
 * Points. One input item is consumed per {@link #SMELT_TICKS} ticks and the
 * yield appears in the output slot, which the player picks up (or a hopper
 * extracts). There is no automatic payout, no dismantle button and no fuel:
 * smelting only advances while the input holds a graded item and the output
 * can still accept the yield.
 */
public class ArmoryRecyclerBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, ContainerData {

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    /** Ticks to convert one input item (furnace-style progress bar). */
    public static final int SMELT_TICKS = 40;

    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int progress;

    public ArmoryRecyclerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ARMORY_RECYCLER_BE.get(), pos, state);
    }

    /** Recycle value (Armory Points) per rarity grade (1=consumer .. 5=classified). */
    public static int yieldForGrade(int grade) {
        return switch (grade) {
            case 1 -> 3;   // consumer
            case 2 -> 5;   // industrial
            case 3 -> 7;   // mil-spec
            case 4 -> 8;   // restricted (clamped below the 9-point key cost)
            case 5 -> 8;   // classified (clamped below the 9-point key cost, so a single
                           // jackpot item can never fund a key outright — GDD §一)
            default -> 0;
        };
    }

    public void tick() {
        if (level == null || level.isClientSide()) return;
        ItemStack in = getItem(INPUT_SLOT);
        Integer grade = in.isEmpty() ? null : in.get(ItemCsgoBox.GRADE.get());
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

    // ---- ContainerData (progress bar sync to the GUI) ------------------------

    @Override
    public int getCount() {
        return 1;
    }

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

    // ---- BaseContainerBlockEntity plumbing -----------------------------------

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return 2;
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
        Integer grade = stack.get(ItemCsgoBox.GRADE.get());
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
}
