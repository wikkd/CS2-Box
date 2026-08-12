package com.reclizer.csgobox.v1_21_1.block;

import com.mojang.serialization.MapCodec;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.block.entity.ArmoryRecyclerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.MenuProvider;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Armory Recycler (1.21.1 legacy API): job-site / workstation that converts
 * opened-box items (stamped with the {@code csgobox:grade} data component)
 * into Armory Points. Right-clicking opens a vanilla furnace-style container
 * GUI: graded items go into the input slot, smelt over a short progress bar
 * and come out of the output slot as Armory Points; hoppers may also feed the
 * input and extract the output. On break the input/output contents are
 * dropped (no {@code preRemoveSideEffects} in this MC version, so the block
 * handles it in {@code onRemove}).
 *
 * <p>Mirror of {@code v26_1_2.block.ArmoryRecyclerBlock}; differs in:</p>
 * <ul>
 *   <li>{@code useItemOn(...)} returns {@link ItemInteractionResult}
 *       (1.21.1) instead of {@link InteractionResult} (26.x).</li>
 *   <li>{@code DirectionProperty} is still around in 1.21.1 (vanilla
 *       {@code BlockStateProperties.HORIZONTAL_FACING}).</li>
 *   <li>{@code InteractionResult.TRY_WITH_EMPTY_HAND} does not exist in
 *       1.21.1; use {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} for the same
 *       fall-through behavior.</li>
 * </ul>
 */
public class ArmoryRecyclerBlock extends BaseEntityBlock {

    public static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "armory_recycler"));

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final MapCodec<ArmoryRecyclerBlock> CODEC = MapCodec.unit(ArmoryRecyclerBlock::new);

    public ArmoryRecyclerBlock() {
        super(Properties.of()
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
     * Right-click with an item in hand: open the recycler GUI. Recycling is
     * furnace-style (input -&gt; progress -&gt; output), there is no button and
     * nothing is consumed until the smelt completes.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof MenuProvider menuProvider) {
            player.openMenu(menuProvider);
        }
        return ItemInteractionResult.CONSUME;
    }

    /**
     * Right-click with an empty hand: also opens the recycler GUI (the input
     * slot is fully managed inside it, including taking items back).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof MenuProvider menuProvider) {
            player.openMenu(menuProvider);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MenuProvider menuProvider ? menuProvider : null;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity be) {
                net.minecraft.world.Containers.dropContents(level, pos, be);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
