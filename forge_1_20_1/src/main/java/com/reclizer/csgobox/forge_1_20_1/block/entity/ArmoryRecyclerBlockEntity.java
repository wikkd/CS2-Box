package com.reclizer.csgobox.forge_1_20_1.block.entity;
import com.reclizer.csgobox.forge_1_20_1.box.BoxItemCodec;

import com.reclizer.csgobox.box.PriceRange;
import com.reclizer.csgobox.box.PriceTable;
import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.forge_1_20_1.block.ModBlocks;
import com.reclizer.csgobox.forge_1_20_1.event.ArmoryRecycleEvent;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ArmoryRecyclerBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, ContainerData {

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
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
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }

    /** Price-table variant id of the stack (TACZ GunId/AmmoId from the
     *  top-level tag), or null — see BoxItemCodec#priceVariantId. */
    private static String variantIdOf(ItemStack stack) {
        return BoxItemCodec.priceVariantId(stack);
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
        if (!refusedInput.isEmpty() && ItemStack.isSameItemSameTags(refusedInput, in)) {
            resetProgress();
            return;
        }
        Integer grade = ItemCsgoBox.getGrade(in);
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
            ArmoryRecycleEvent.BUS.post(recycle);
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
                ? ModItems.ITEM_ARMORY_POINT.get().getMaxStackSize()
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

    // ---- Automation compat (Create arms/pipes/hoppers) ----------------------

    /**
     * v2.0.1 compatibility: expose the standard {@link IItemHandler}
     * capability so Create mechanical arms/tunnels, modded pipes and any
     * automation that talks to item handlers can feed graded items into the
     * input slot and pull Armory Points out of the output slot. The rules
     * mirror the {@link WorldlyContainer} face semantics.
     */
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return LazyOptional.of(() -> new RecyclerHandler(this)).cast();
        }
        return super.getCapability(cap, side);
    }

    private static final class RecyclerHandler implements IItemHandler {
        private final ArmoryRecyclerBlockEntity be;

        private RecyclerHandler(ArmoryRecyclerBlockEntity be) {
            this.be = be;
        }

        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return be.getItem(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != INPUT_SLOT || stack.isEmpty() || !isItemValid(slot, stack)) {
                return stack;
            }
            ItemStack cur = be.getItem(INPUT_SLOT);
            int limit = Math.min(stack.getMaxStackSize(), 64);
            int move;
            if (cur.isEmpty()) {
                move = Math.min(stack.getCount(), limit);
            } else if (ItemStack.isSameItemSameTags(cur, stack)) {
                move = Math.min(stack.getCount(), limit - cur.getCount());
            } else {
                return stack;
            }
            if (move <= 0) {
                return stack;
            }
            if (!simulate) {
                if (cur.isEmpty()) {
                    be.setItem(INPUT_SLOT, stack.copy());
                } else {
                    cur.grow(move);
                }
                be.setChanged();
            }
            ItemStack rest = stack.copy();
            rest.shrink(move);
            return rest;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != OUTPUT_SLOT || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack out = be.getItem(OUTPUT_SLOT);
            if (out.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int take = Math.min(amount, out.getCount());
            ItemStack result = out.copy();
            result.setCount(take);
            if (!simulate) {
                be.removeItem(OUTPUT_SLOT, take);
                be.setChanged();
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot != INPUT_SLOT || stack.isEmpty()) {
                return false;
            }
            Integer grade = ItemCsgoBox.getGrade(stack);
            return grade != null && grade >= 1 && grade <= 5;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items);
        tag.putInt("Progress", progress);
        tag.putInt("PendingPayout", pendingPayout);
        if (!refusedInput.isEmpty()) {
            tag.put("RefusedInput", refusedInput.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(2, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, items);
        progress = tag.getInt("Progress");
        pendingPayout = tag.getInt("PendingPayout");
        refusedInput = tag.contains("RefusedInput") && tag.get("RefusedInput") instanceof CompoundTag refusedTag
                ? ItemStack.of(refusedTag)
                : ItemStack.EMPTY;
    }
}
