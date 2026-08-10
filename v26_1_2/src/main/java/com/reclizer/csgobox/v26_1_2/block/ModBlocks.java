package com.reclizer.csgobox.v26_1_2.block;

import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.block.entity.ArmoryRecyclerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, CsgoBox.MODID);
    public static final DeferredRegister<Item> BLOCK_ITEMS =
            DeferredRegister.create(Registries.ITEM, CsgoBox.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CsgoBox.MODID);

    public static final Supplier<ArmoryRecyclerBlock> ARMORY_RECYCLER =
            BLOCKS.register("armory_recycler", ArmoryRecyclerBlock::new);

    public static final Supplier<Item> ARMORY_RECYCLER_ITEM = BLOCK_ITEMS.register("armory_recycler",
                    () -> new BlockItem(ARMORY_RECYCLER.get(), new Item.Properties().setId(ResourceKey.create(
                            Registries.ITEM, Identifier.fromNamespaceAndPath(CsgoBox.MODID, "armory_recycler")))));

    public static final Supplier<BlockEntityType<ArmoryRecyclerBlockEntity>> ARMORY_RECYCLER_BE =
            BLOCK_ENTITY_TYPES.register("armory_recycler",
                    () -> new BlockEntityType<>(ArmoryRecyclerBlockEntity::new, ARMORY_RECYCLER.get()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        BLOCK_ITEMS.register(eventBus);
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
