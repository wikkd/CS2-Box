package com.reclizer.csgobox.v1_21_1.villager;

import com.google.common.collect.ImmutableSet;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.block.ModBlocks;
import com.reclizer.csgobox.v1_21_1.item.ModItems;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import java.util.function.Supplier;

/**
 * The arms-dealer villager on the 1.21.1 (legacy) API:
 * a {@link PoiType} for the Armory Recycler, a {@link VillagerProfession},
 * and trades injected directly into {@link VillagerTrades#TRADES} during
 * {@link FMLCommonSetupEvent} (after registries are frozen).
 *
 * <p>This is the "old-style" villager trade path; on 26.x the same data lives
 * in datapack JSON under {@code data/<ns>/{villager_trade,trade_set}/...}.
 * See the 26.x mirror for the modern approach.</p>
 *
 * <p>Economy anchor: 9 Armory Points = 1 {@code csgo_key0}
 * (see {@code data/csgobox/recipe/armory_point_exchange.json}).
 * Mineral-to-point trades pay LESS than crafting a key0 from the same minerals
 * to prevent arbitrage; key2 (45 pts + 1 diamond) uses a two-input MerchantOffer
 * because a single armory_point stack maxes at 64.</p>
 */
public final class ModVillagers {

    private ModVillagers() {
    }

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, CsgoBox.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, CsgoBox.MODID);

    public static final Supplier<PoiType> ARMORY_RECYCLER_POI = POI_TYPES.register("armory_recycler",
            () -> new PoiType(
                    Set.copyOf(ModBlocks.ARMORY_RECYCLER.get().getStateDefinition().getPossibleStates()),
                    1, 1));

    private static final ResourceLocation POI_KEY = ResourceLocation.fromNamespaceAndPath(
            CsgoBox.MODID, "armory_recycler");

    public static final Supplier<VillagerProfession> ARMS_DEALER = PROFESSIONS.register("arms_dealer",
            () -> new VillagerProfession(
                    "arms_dealer",
                    holder -> holder.is(POI_KEY),
                    holder -> holder.is(POI_KEY),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_ARMORER));

    public static void register(IEventBus eventBus) {
        POI_TYPES.register(eventBus);
        PROFESSIONS.register(eventBus);
        eventBus.addListener(FMLCommonSetupEvent.class, ModVillagers::injectTrades);
    }

    private static void injectTrades(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Item point = ModItems.ITEM_ARMORY_POINT.get();
            Item key0 = ModItems.ITEM_CSGO_KEY0.get();
            Item key1 = ModItems.ITEM_CSGO_KEY1.get();
            Item key2 = ModItems.ITEM_CSGO_KEY2.get();

            Int2ObjectMap<VillagerTrades.ItemListing[]> byLevel = new Int2ObjectOpenHashMap<>();

            byLevel.put(1, new VillagerTrades.ItemListing[]{
                    buyPoints(Items.IRON_INGOT, 1, point, 2, 16, 2),
                    buyPoints(Items.EMERALD, 1, point, 2, 12, 2),
            });
            byLevel.put(2, new VillagerTrades.ItemListing[]{
                    buyPoints(Items.GOLD_INGOT, 1, point, 4, 16, 5),
                    sellDynamicItem(point, 8, "csgo_box", 12, 5),
            });
            byLevel.put(3, new VillagerTrades.ItemListing[]{
                    buyPoints(Items.DIAMOND, 1, point, 12, 12, 10),
                    sellKey(point, 9, key0, 16, 10),
            });
            byLevel.put(4, new VillagerTrades.ItemListing[]{
                    sellKey(point, 24, key1, 8, 15),
                    sellDynamicItem(point, 12, "terminal", 4, 15),
            });
            byLevel.put(5, new VillagerTrades.ItemListing[]{
                    (entity, random) -> new MerchantOffer(
                            new ItemCost(point, 45),
                            java.util.Optional.of(new ItemCost(Items.DIAMOND, 1)),
                            new ItemStack(key2, 1),
                            3, 30, 0.05F),
            });

            VillagerTrades.TRADES.put(ARMS_DEALER.get(), byLevel);
        });
    }

    /** Mineral -> points: {@code inCount} item -> {@code outCount} armory points. */
    private static VillagerTrades.ItemListing buyPoints(Item in, int inCount, Item out,
                                                        int outCount, int maxUses, int xp) {
        return (entity, random) -> new MerchantOffer(
                new ItemCost(in, inCount), new ItemStack(out, outCount),
                maxUses, xp, 0.05F);
    }

    /** Points -> key/terminal: {@code points} armory points -> 1 item. */
    private static VillagerTrades.ItemListing sellKey(Item point, int points, Item out, int maxUses, int xp) {
        return (entity, random) -> new MerchantOffer(
                new ItemCost(point, points), new ItemStack(out, 1),
                maxUses, xp, 0.05F);
    }

    /** Points -> 1 dynamically registered item (config/csbox file name). The
     *  item only exists after runtime registration, so it is looked up by name
     *  when constructing the offer; terminal.json registers as ItemTerminal. */
    private static VillagerTrades.ItemListing sellDynamicItem(Item point, int points,
                                                             String itemId, int maxUses, int xp) {
        return (entity, random) -> {
            Item box = BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("csgobox", itemId));
            if (box == null || box == Items.AIR) {
                // Fallback: hand back the points themselves, so the trade never lies.
                return new MerchantOffer(
                        new ItemCost(point, points), new ItemStack(point, points),
                        maxUses, xp, 0.05F);
            }
            return new MerchantOffer(
                    new ItemCost(point, points), new ItemStack(box, 1),
                    maxUses, xp, 0.05F);
        };
    }
}
