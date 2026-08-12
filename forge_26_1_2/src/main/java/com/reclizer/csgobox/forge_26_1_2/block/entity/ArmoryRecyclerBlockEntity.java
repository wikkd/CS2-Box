package com.reclizer.csgobox.forge_26_1_2.block.entity;

import com.reclizer.csgobox.forge_26_1_2.block.ModBlocks;
import com.reclizer.csgobox.forge_26_1_2.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_26_1_2.item.ModItems;
import com.reclizer.csgobox.forge_26_1_2.sounds.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Armory Recycler's block entity.
 *
 * <p>Two recycle paths:
 * <ul>
 *   <li><b>Manual</b> — a player right-clicks the block while holding an item
 *       tagged with {@code csgobox:grade}; the whole held stack is converted
 *       into Armory Points (paid directly to that player).</li>
 *   <li><b>Automated</b> — hoppers may push graded items into the single slot;
 *       the entity ticks and disposes of them, dropping Armory Point items at
 *       the block (no player reference needed).</li>
 * </ul>
 *
 * <p>Only items stamped by the box-opening code (see {@link ItemCsgoBox#GRADE})
 * are accepted, so raw/non-box loot cannot be recycled — this is what keeps
 * the point economy anchored below the key cost.
 */
public class ArmoryRecyclerBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {

    private static final int SLOT = 0;
    private static final int TICK_COOLDOWN = 10;

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int cooldown = 0;

    public ArmoryRecyclerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ARMORY_RECYCLER_BE.get(), pos, state);
    }

    /** Recycle value (Armory Points) per rarity grade (1=consumer .. 5=classified). */
    public static int yieldForGrade(int grade) {
        return switch (grade) {
            case 1 -> 3;   // consumer
            case 2 -> 5;   // industrial
            case 3 -> 8;   // mil-spec
            case 4 -> 11;  // restricted
            case 5 -> 15;  // classified (best tier; single item can't fund a box+key loop = 17 pts)
            default -> 0;
        };
    }

    // ---- Manual path (called from ArmoryRecyclerBlock.use) --------------------

    public InteractionResult recycleHeld(Player player, InteractionHand hand, Level level, BlockPos pos) {
        ItemStack held = player.getItemInHand(hand);
        Integer grade = held.isEmpty() ? null : held.get(ItemCsgoBox.GRADE.get());
        if (grade == null || grade < 1 || grade > 5) {
            return InteractionResult.PASS;
        }
        int count = held.getCount();
        int points = yieldForGrade(grade) * count;
        held.shrink(count);
        payPoints(player, points, level, pos);
        return InteractionResult.CONSUME;
    }

    /** Empty-hand right-click: hand the buffered slot back so nothing gets stuck. */
    public InteractionResult ejectToPlayer(Player player) {
        ItemStack in = getItem(SLOT);
        if (in.isEmpty()) {
            return InteractionResult.PASS;
        }
        ItemStack out = in.copy();
        setItem(SLOT, ItemStack.EMPTY);
        setChanged();
        if (!player.getInventory().add(out)) {
            player.drop(out, false);
        }
        return InteractionResult.CONSUME;
    }

    // ---- Automated path (hopper-fed slot) ------------------------------------

    public void tick() {
        if (level == null || level.isClientSide()) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        ItemStack in = getItem(SLOT);
        Integer grade = in.isEmpty() ? null : in.get(ItemCsgoBox.GRADE.get());
        if (grade == null || grade < 1 || grade > 5) return;
        int points = yieldForGrade(grade);
        in.shrink(1);
        setChanged();
        dropPoints(points);
        cooldown = TICK_COOLDOWN;
    }

    private void dropPoints(int points) {
        if (points <= 0 || level == null) return;
        Item item = ModItems.ITEM_ARMORY_POINT.get();
        int max = item.getDefaultMaxStackSize();
        int left = points;
        while (left > 0) {
            int s = Math.min(left, max);
            Block.popResource(level, worldPosition.above(), new ItemStack(item, s));
            left -= s;
        }
    }

    private void payPoints(Player player, int points, Level level, BlockPos pos) {
        if (points <= 0) return;
        Item item = ModItems.ITEM_ARMORY_POINT.get();
        int max = item.getDefaultMaxStackSize();
        int left = points;
        while (left > 0) {
            int s = Math.min(left, max);
            ItemStack stack = new ItemStack(item, s);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            left -= s;
        }
        player.sendSystemMessage(
                Component.literal("+" + points + " ").append(
                        Component.translatable("item.csgobox.armory_point")).withStyle(net.minecraft.ChatFormatting.GREEN));
        level.playSound(player, pos, ModSounds.CS_FINSH.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        for (int i = 0; i < 6; i++) {
            double x = pos.getX() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.5;
            double y = pos.getY() + 1.0 + level.getRandom().nextDouble() * 0.4;
            double z = pos.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.5;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, x, y, z, 0, 0.05, 0);
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
        return 1;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.csgobox.armory_recycler");
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.csgobox.armory_recycler");
    }

    @Nullable
    @Override
    protected net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv) {
        return null; // no GUI — recycling is manual / hopper-driven
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, net.minecraft.core.Direction direction) {
        return stack.get(ItemCsgoBox.GRADE.get()) != null;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, net.minecraft.core.Direction direction) {
        return false;
    }

    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction direction) {
        return new int[]{SLOT};
    }
}
