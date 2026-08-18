package com.reclizer.csgobox.forge_1_20_1.villager;

import com.google.common.collect.ImmutableSet;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.block.ModBlocks;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Predicate;

public final class ModVillagers {

    private ModVillagers() {
    }

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(ForgeRegistries.POI_TYPES, CsgoBox.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, CsgoBox.MODID);

    public static final RegistryObject<PoiType> ARMORY_RECYCLER_POI = POI_TYPES.register("armory_recycler",
            () -> new PoiType(
                    ImmutableSet.copyOf(ModBlocks.ARMORY_RECYCLER.get().getStateDefinition().getPossibleStates()),
                    1, 1));

    public static final RegistryObject<VillagerProfession> ARMS_DEALER = PROFESSIONS.register("arms_dealer",
            () -> {
                Predicate<Holder<PoiType>> poiPredicate = holder ->
                        holder.value() == ARMORY_RECYCLER_POI.get();
                return new VillagerProfession(
                        "arms_dealer",
                        poiPredicate,
                        poiPredicate,
                        ImmutableSet.of(),
                        ImmutableSet.of(),
                        SoundEvents.VILLAGER_WORK_ARMORER);
            });

    public static void registerTrades() {
        VillagerTrades.TRADES.put(ARMS_DEALER.get(), generateTrades());
    }

    private static Int2ObjectMap<VillagerTrades.ItemListing[]> generateTrades() {
        Int2ObjectMap<VillagerTrades.ItemListing[]> map = new Int2ObjectOpenHashMap<>();

        // Level 1: basic resource trades
        map.put(1, new VillagerTrades.ItemListing[]{
                (trader, rng) -> new MerchantOffer(
                        new ItemStack(Items.IRON_INGOT, 5),
                        new ItemStack(com.reclizer.csgobox.forge_1_20_1.item.ModItems.ITEM_ARMORY_POINT.get(), 3),
                        16, 2, 0.05f),
                (trader, rng) -> new MerchantOffer(
                        new ItemStack(Items.COAL, 16),
                        new ItemStack(com.reclizer.csgobox.forge_1_20_1.item.ModItems.ITEM_ARMORY_POINT.get(), 2),
                        16, 2, 0.05f)
        });

        // Level 2: mid-tier trades
        map.put(2, new VillagerTrades.ItemListing[]{
                (trader, rng) -> new MerchantOffer(
                        new ItemStack(Items.GOLD_INGOT, 3),
                        new ItemStack(com.reclizer.csgobox.forge_1_20_1.item.ModItems.ITEM_ARMORY_POINT.get(), 4),
                        12, 5, 0.05f),
                (trader, rng) -> new MerchantOffer(
                        new ItemStack(Items.LAPIS_LAZULI, 8),
                        new ItemStack(com.reclizer.csgobox.forge_1_20_1.item.ModItems.ITEM_ARMORY_POINT.get(), 2),
                        12, 5, 0.05f)
        });

        // Level 3: premium box trade
        map.put(3, new VillagerTrades.ItemListing[]{
                (trader, rng) -> new MerchantOffer(
                        new ItemStack(com.reclizer.csgobox.forge_1_20_1.item.ModItems.ITEM_ARMORY_POINT.get(), 15),
                        new ItemStack(com.reclizer.csgobox.forge_1_20_1.item.ModItems.ITEM_PREMIUM_BOX.get()),
                        4, 10, 0.05f)
        });

        // Level 4: diamond trade
        map.put(4, new VillagerTrades.ItemListing[]{
                (trader, rng) -> new MerchantOffer(
                        new ItemStack(Items.DIAMOND, 1),
                        new ItemStack(com.reclizer.csgobox.forge_1_20_1.item.ModItems.ITEM_ARMORY_POINT.get(), 5),
                        8, 15, 0.05f)
        });

        // Level 5: netherite trade
        map.put(5, new VillagerTrades.ItemListing[]{
                (trader, rng) -> new MerchantOffer(
                        new ItemStack(Items.NETHERITE_SCRAP, 1),
                        new ItemStack(com.reclizer.csgobox.forge_1_20_1.item.ModItems.ITEM_ARMORY_POINT.get(), 8),
                        4, 20, 0.05f)
        });

        return map;
    }

    public static void register(IEventBus eventBus) {
        POI_TYPES.register(eventBus);
        PROFESSIONS.register(eventBus);
    }
}
