package com.reclizer.csgobox.fabric_1_21_1.block;

import com.google.common.base.Suppliers;
import com.reclizer.csgobox.fabric_1_21_1.CsgoBox;
import com.reclizer.csgobox.fabric_1_21_1.block.entity.ArmoryRecyclerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;
import net.minecraft.core.Registry;

/**
 * Registers the mod's blocks, their {@link BlockItem}s, and their
 * {@link BlockEntityType}s (Fabric edition — direct {@code Registry.register}
 * instead of NeoForge's DeferredRegister; the memoized suppliers keep the
 * {@code .get()} call-site shape of the other platforms).
 */
public final class ModBlocks {

    private ModBlocks() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, path);
    }

    public static final Supplier<ArmoryRecyclerBlock> ARMORY_RECYCLER = Suppliers.memoize(() ->
            Registry.register(BuiltInRegistries.BLOCK, id("armory_recycler"), new ArmoryRecyclerBlock()));

    public static final Supplier<Item> ARMORY_RECYCLER_ITEM = Suppliers.memoize(() ->
            Registry.register(BuiltInRegistries.ITEM, id("armory_recycler"),
                    new BlockItem(ARMORY_RECYCLER.get(), new Item.Properties())));

    public static final Supplier<BlockEntityType<ArmoryRecyclerBlockEntity>> ARMORY_RECYCLER_BE = Suppliers.memoize(() ->
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("armory_recycler"),
                    BlockEntityType.Builder.of(ArmoryRecyclerBlockEntity::new, ARMORY_RECYCLER.get()).build(null)));

    /** Eager registration during mod init (mirrors the DeferredRegister flush). */
    public static void register() {
        ARMORY_RECYCLER.get();
        ARMORY_RECYCLER_ITEM.get();
        ARMORY_RECYCLER_BE.get();
    }
}
