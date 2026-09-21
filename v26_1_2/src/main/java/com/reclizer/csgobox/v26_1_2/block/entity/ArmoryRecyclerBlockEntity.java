package com.reclizer.csgobox.v26_1_2.block.entity;

import com.reclizer.csgobox.box.PriceRange;
import com.reclizer.csgobox.box.PriceTable;
import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.v26_1_2.block.ModBlocks;
import com.reclizer.csgobox.v26_1_2.event.ArmoryRecycleEvent;
import com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_1_2.item.ModItems;
import com.reclizer.csgobox.v26_1_2.menu.ArmoryRecyclerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Armory Recycler's block entity: a furnace-style converter that turns
 * graded box items (stamped with {@link ItemCsgoBox#GRADE}) into Armory
 * Points. One input item is consumed per {@link #SMELT_TICKS} ticks and the
 * yield appears in the output slot, which the player picks up (or a hopper
 * extracts). Payouts larger than the output stack size stream into the slot
 * across ticks (no input is stranded by an oversized price). There is no
 * automatic payout, no dismantle button and no fuel.
 */
public class ArmoryRecyclerBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, ContainerData {

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    /** Ticks to convert one input item (furnace-style progress bar). */
    public static final int SMELT_TICKS = 40;

    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int progress;
    /** Exact input stack a policy listener refused; while it stays in the input
     *  slot the recycle event is not re-fired (a veto must not become a loop). */
    private ItemStack refusedInput = ItemStack.EMPTY;
    /** Points already earned but not yet fully moved into the output slot —
     *  payouts larger than the stack size stream across ticks instead of
     *  stranding the input (a 4500-point item yields 4050, far above 64). */
    private int pendingPayout;

    public ArmoryRecyclerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ARMORY_RECYCLER_BE.get(), pos, state);
    }

    /**
     * v2.0.1 economy: recycle value of a graded stack — 90% of its central
     * price-table price (rounded up). A range entry is sampled once per
     * recycle through {@code nextBounded} (the level's random source on the
     * server). Items without a price-table entry (including loot_table
     * placeholders) are NOT recyclable — no grade-ladder fallback.
     * 0 = cannot be recycled.
     */
    public static int yieldForStack(ItemStack stack, java.util.function.IntUnaryOperator nextBounded) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        PriceRange range = PriceTableRegistry.get().lookupRange(itemIdOf(stack), variantIdOf(stack));
        if (range != null) {
            return PriceTable.recycleYield(range, nextBounded);
        }
        return 0;
    }

    /** Registry id string of the stack ({@code ns:path}). */
    private static String itemIdOf(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Price-table variant id of the stack (TACZ GunId/AmmoId). This platform
     *  has no TACZ compat inside the item codec, so stacks price by plain id. */
    private static String variantIdOf(ItemStack stack) {
        return null;
    }

    public void tick() {
        if (level == null || level.isClientSide()) return;
        // Stream owed payout into the output slot first; a payout larger than
        // the stack size drains over several ticks once space frees up.
        drainPendingPayout();
        ItemStack in = getItem(INPUT_SLOT);
        if (in.isEmpty()) {
            refusedInput = ItemStack.EMPTY;
            resetProgress();
            return;
        }
        // A refused stack stays refused until the player swaps it out, so a
        // policy veto cannot turn into a recycle event every SMELT_TICKS.
        if (!refusedInput.isEmpty() && ItemStack.isSameItemSameComponents(refusedInput, in)) {
            resetProgress();
            return;
        }
        Integer grade = in.get(ItemCsgoBox.GRADE.get());
        int yield = grade != null && grade >= 1 && grade <= 5
                ? yieldForStack(in, level.getRandom()::nextInt) : 0;
        if (yield <= 0) {
            resetProgress();
            return;
        }
        progress++;
        if (progress >= SMELT_TICKS) {
            progress = 0;
            // Policy hook: scripts may veto recycling this item (e.g. blacklist
            // farmed items) or re-price it through setYield. Canceled / zero
            // yield → the input stays in the machine, nothing is consumed.
            ArmoryRecycleEvent recycle = new ArmoryRecycleEvent(this, in.copy(), grade, yield);
            NeoForge.EVENT_BUS.post(recycle);
            if (recycle.isCanceled()) {
                refusedInput = in.copy();
                setChanged();
                return;
            }
            int payout = recycle.getYield();
            if (payout <= 0) {
                // Zero yield after the event hook: keep the item, retry next
                // round once a listener stops zeroing it.
                setChanged();
                return;
            }
            refusedInput = ItemStack.EMPTY;
            in.shrink(1);
            pendingPayout += payout;
            drainPendingPayout();
            level.playSound(null, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                    worldPosition.getZ() + 0.5D, SoundEvents.VILLAGER_WORK_ARMORER,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        setChanged();
    }

    /** Clears the progress bar (marking the chunk dirty only when it moves). */
    private void resetProgress() {
        if (progress != 0) {
            progress = 0;
            setChanged();
        }
    }

    /** Moves owed payout into the output slot while capacity remains. */
    private void drainPendingPayout() {
        if (pendingPayout <= 0) {
            return;
        }
        ItemStack out = getItem(OUTPUT_SLOT);
        if (!out.isEmpty() && !out.is(ModItems.ITEM_ARMORY_POINT.get())) {
            // Foreign item in the output: wait until the player removes it.
            return;
        }
        int capacity = out.isEmpty()
                ? ModItems.ITEM_ARMORY_POINT.get().getDefaultMaxStackSize()
                : out.getMaxStackSize() - out.getCount();
        int moved = Math.min(pendingPayout, capacity);
        if (moved <= 0) {
            return;
        }
        if (out.isEmpty()) {
            setItem(OUTPUT_SLOT, new ItemStack(ModItems.ITEM_ARMORY_POINT.get(), moved));
        } else {
            out.grow(moved);
        }
        pendingPayout -= moved;
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

    // ---- Persistence
    // ---- Persistence ---------------------------------------------------------

    /**
     * 26.x: {@link BaseContainerBlockEntity} no longer persists the container
     * itself — every concrete container BE must do it (cf.
     * {@code AbstractFurnaceBlockEntity}). Without this the input, the output
     * and the progress bar were silently dropped on every chunk save/reload.
     */
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Progress", progress);
        output.putInt("PendingPayout", pendingPayout);
        if (!refusedInput.isEmpty()) {
            output.store("RefusedInput", ItemStack.CODEC, refusedInput);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("Progress", 0);
        pendingPayout = input.getIntOr("PendingPayout", 0);
        refusedInput = input.read("RefusedInput", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }
}
