package com.reclizer.csgobox.forge_1_20_1.block;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.block.entity.ArmoryRecyclerBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;
import java.util.function.Supplier;

public final class ModBlocks {

    private ModBlocks() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CsgoBox.MODID);
    public static final DeferredRegister<Item> BLOCK_ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CsgoBox.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CsgoBox.MODID);

    public static final RegistryObject<ArmoryRecyclerBlock> ARMORY_RECYCLER =
            BLOCKS.register("armory_recycler", ArmoryRecyclerBlock::new);

    public static final RegistryObject<Item> ARMORY_RECYCLER_ITEM = BLOCK_ITEMS.register("armory_recycler",
            () -> new BlockItem(ARMORY_RECYCLER.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<ArmoryRecyclerBlockEntity>> ARMORY_RECYCLER_BE =
            BLOCK_ENTITY_TYPES.register("armory_recycler",
                    () -> BlockEntityType.Builder.of(ArmoryRecyclerBlockEntity::new, ARMORY_RECYCLER.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        BLOCK_ITEMS.register(eventBus);
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
