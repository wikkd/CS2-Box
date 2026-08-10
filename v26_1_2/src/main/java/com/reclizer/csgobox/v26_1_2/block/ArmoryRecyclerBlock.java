package com.reclizer.csgobox.v26_1_2.block;

import com.mojang.serialization.MapCodec;
import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.block.entity.ArmoryRecyclerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Armory Recycler: a job-site / workstation block that converts opened
 * box items (stamped with the {@code csgobox:grade} data component) into
 * Armory Points. Two paths: right-click with a graded item in hand (manual),
 * or push graded items into its single slot with a hopper (automated).
 * Also serves as the POI for the arms-dealer villager.
 *
 * <p>Deliberately has no GUI — a {@code MenuType} would need a per-platform
 * screen and the menu API drifts across 1.21.1 / 26.1 / 26.2.</p>
 */
public class ArmoryRecyclerBlock extends BaseEntityBlock {

    public static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "armory_recycler"));

    /** 26.x removed {@code DirectionProperty}; horizontal facing is a plain {@code EnumProperty<Direction>}. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final MapCodec<ArmoryRecyclerBlock> CODEC = MapCodec.unit(ArmoryRecyclerBlock::new);

    public ArmoryRecyclerBlock() {
        super(Properties.of()
                .setId(KEY)
                .strength(3.5f, 6.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .instrument(NoteBlockInstrument.IRON_XYLOPHONE));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArmoryRecyclerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lv, pos, st, be) -> {
            if (be instanceof ArmoryRecyclerBlockEntity rbe) {
                rbe.tick();
            }
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Right-click with an item: recycle it if it carries a {@code csgobox:grade}.
     * Returning {@link InteractionResult#TRY_WITH_EMPTY_HAND} when the held item is
     * not recyclable lets vanilla fall through to {@link #useWithoutItem}.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ArmoryRecyclerBlockEntity be) {
            InteractionResult result = be.recycleHeld(player, hand, level, pos);
            if (result == InteractionResult.PASS) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            return result;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /**
     * Right-click with an empty hand (or a non-recyclable item): eject whatever
     * is sitting in the hopper slot, so the block never swallows items.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ArmoryRecyclerBlockEntity be) {
            return be.ejectToPlayer(player);
        }
        return InteractionResult.PASS;
    }
}
