package com.reclizer.csgobox.v26_1_2.menu;

import com.reclizer.csgobox.v26_1_2.block.entity.ArmoryRecyclerBlockEntity;
import com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Furnace-style container menu for the armory recycler: an input slot, an
 * output slot (Armory Points) and the player's 27+9 inventory slots. The
 * block entity converts one input item per {@code SMELT_TICKS} ticks and
 * places the yield into the output slot; smelting progress is exposed through
 * a {@link ContainerData} slot so the client screen can animate the arrow.
 *
 * <p>Server menus hold the block entity (Container + ContainerData + executor);
 * the client copy uses throwaway {@link SimpleContainer}/{@link SimpleContainerData}
 * whose state is synced from the server (same pattern as vanilla
 * {@code FurnaceMenu}).</p>
 */
public class ArmoryRecyclerMenu extends AbstractContainerMenu {

    private static final int INPUT_SLOT_INDEX = 0;
    private static final int OUTPUT_SLOT_INDEX = 1;
    private static final int PLAYER_SLOT_START = 2;
    private static final int PLAYER_SLOT_END = 38;

    private final Container recycler;
    private final ContainerData data;
    @Nullable
    private final ArmoryRecyclerBlockEntity blockEntity;

    /** Client / dummy path (MenuType factory): slot state arrives via sync. */
    public ArmoryRecyclerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(2), new SimpleContainerData(1), null);
    }

    /** Server path: the block entity is the input/output container and the smelt executor. */
    public ArmoryRecyclerMenu(int containerId, Inventory playerInventory,
                              Container recycler, ContainerData data,
                              @Nullable ArmoryRecyclerBlockEntity blockEntity) {
        super(ModMenus.ARMORY_RECYCLER.get(), containerId);
        this.recycler = recycler;
        this.data = data;
        this.blockEntity = blockEntity;

        addSlot(new InputSlot(recycler, 0, 51, 35));
        addSlot(new ResultSlot(recycler, 1, 107, 35));
        addDataSlots(data);
        addPlayerInventorySlots(playerInventory);
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    /** Furnace-style smelting progress in ticks (0..SMELT_TICKS), synced from the server. */
    public int getProgress() {
        return data.get(0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = getSlot(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == INPUT_SLOT_INDEX || index == OUTPUT_SLOT_INDEX) {
                if (!moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                boolean graded = stack.get(ItemCsgoBox.GRADE.get()) != null;
                boolean movedToInput = graded
                        && moveItemStackTo(stack, INPUT_SLOT_INDEX, OUTPUT_SLOT_INDEX, false);
                if (!movedToInput && !moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null) {
            return true; // client-side dummy menu
        }
        return blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) == blockEntity
                && player.distanceToSqr(blockEntity.getBlockPos().getX() + 0.5D,
                        blockEntity.getBlockPos().getY() + 0.5D,
                        blockEntity.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    /** Input slot: only box items stamped with a valid grade can be placed. */
    private static class InputSlot extends Slot {
        InputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            Integer grade = stack.get(ItemCsgoBox.GRADE.get());
            return grade != null && grade >= 1 && grade <= 5;
        }
    }

    /** Result slot: players may only take Armory Points out, never place items in. */
    private static class ResultSlot extends Slot {
        ResultSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
