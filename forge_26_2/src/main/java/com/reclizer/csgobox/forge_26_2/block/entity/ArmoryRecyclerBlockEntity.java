package com.reclizer.csgobox.forge_26_2.block.entity;

import com.reclizer.csgobox.box.PriceTable;
import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.forge_26_2.block.ModBlocks;
import com.reclizer.csgobox.forge_26_2.event.ArmoryRecycleEvent;
import com.reclizer.csgobox.forge_26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_26_2.item.ModItems;
import com.reclizer.csgobox.forge_26_2.menu.ArmoryRecyclerMenu;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
    /** Exact input stack a policy listener refused; while it stays in the input
     *  slot the recycle event is not re-fired (a veto must not become a loop). */
    private ItemStack refusedInput = ItemStack.EMPTY;

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

    /**
     * v2.1.0 economy: recycle value of a graded stack — 90% of its central
     * price-table price (rounded up), falling back to the grade ladder for
     * items absent from the table. 0 = cannot be recycled.
     */
    public static int yieldForStack(ItemStack stack, int grade) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int price = PriceTableRegistry.get().lookup(itemIdOf(stack), variantIdOf(stack));
        if (price != PriceTable.UNPRICED) {
            return PriceTable.recycleYield(price);
        }
        return yieldForGrade(grade);
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
        int yield = grade != null && grade >= 1 && grade <= 5 ? yieldForStack(in, grade) : 0;
        if (yield <= 0 || !canAcceptOutput(yield)) {
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
            ArmoryRecycleEvent.BUS.fire(recycle);
            if (recycle.isCanceled()) {
                refusedInput = in.copy();
                setChanged();
                return;
            }
            int payout = recycle.getYield();
            if (payout <= 0 || !canAcceptOutput(payout)) {
                // Zero yield, or a price the output slot cannot hold: keep the
                // item and retry once the output drains.
                setChanged();
                return;
            }
            refusedInput = ItemStack.EMPTY;
            in.shrink(1);
            addOutput(payout);
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

    private boolean canAcceptOutput(int amount) {
        if (amount <= 0) {
            return false;
        }
        ItemStack out = getItem(OUTPUT_SLOT);
        if (out.isEmpty()) {
            // A single payout must still be a valid stack — a listener may lift
            // the yield through ArmoryRecycleEvent#setYield.
            return amount <= ModItems.ITEM_ARMORY_POINT.get().getDefaultMaxStackSize();
        }
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

    // ---- Automation compat (Create arms/pipes/hoppers) ----------------------

    /**
     * v2.1.0 compatibility: expose the standard {@link IItemHandler}
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
            } else if (ItemStack.isSameItemSameComponents(cur, stack)) {
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
            Integer grade = stack.get(ItemCsgoBox.GRADE.get());
            return grade != null && grade >= 1 && grade <= 5;
        }
    }

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
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("Progress", 0);
    }
}
