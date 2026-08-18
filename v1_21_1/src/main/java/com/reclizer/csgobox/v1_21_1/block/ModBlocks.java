package com.reclizer.csgobox.v1_21_1.block;

import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.block.entity.ArmoryRecyclerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Registers the mod's blocks, their {@link BlockItem}s, and their
 * {@link BlockEntityType}s. The single block so far is the
 * {@link ArmoryRecyclerBlock} (the arms-dealer villager's job site).
 */
public final class ModBlocks {

    private ModBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CsgoBox.MODID);
    public static final DeferredRegister.Items BLOCK_ITEMS =
            DeferredRegister.createItems(CsgoBox.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CsgoBox.MODID);

    public static final Supplier<ArmoryRecyclerBlock> ARMORY_RECYCLER =
            BLOCKS.register("armory_recycler", ArmoryRecyclerBlock::new);

    public static final Supplier<Item> ARMORY_RECYCLER_ITEM = BLOCK_ITEMS.register("armory_recycler",
            () -> new BlockItem(ARMORY_RECYCLER.get(), new Item.Properties()));

    public static final Supplier<BlockEntityType<ArmoryRecyclerBlockEntity>> ARMORY_RECYCLER_BE =
            BLOCK_ENTITY_TYPES.register("armory_recycler",
                    () -> BlockEntityType.Builder.of(ArmoryRecyclerBlockEntity::new, ARMORY_RECYCLER.get())
                            .build(null));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        BLOCK_ITEMS.register(eventBus);
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
