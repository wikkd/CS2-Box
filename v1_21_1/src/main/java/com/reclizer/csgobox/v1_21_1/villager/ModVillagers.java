package com.reclizer.csgobox.v1_21_1.villager;

import com.google.common.collect.ImmutableSet;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.block.ModBlocks;
import com.reclizer.csgobox.v1_21_1.item.ModItems;
import com.reclizer.csgobox.villager.VillagerPricing;
import com.reclizer.csgobox.villager.VillagerPricingConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
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
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The arms-dealer villager on the 1.21.1 (legacy) API:
 * a {@link PoiType} for the Armory Recycler, a {@link VillagerProfession},
 * and trades injected directly into {@link VillagerTrades#TRADES} during
 * {@link FMLCommonSetupEvent} (after registries are frozen).
 *
 * <p>Trades are <b>dynamically priced</b>: each refresh samples a fresh quote
 * from {@link VillagerPricing}, anchored to the live
 * {@code config/csbox/_prices.json} economy (knobs in
 * {@code config/csbox/_villager_prices.json}). When dynamic pricing is
 * disabled the trades fall back to the static values shared with the 26.x
 * datapack tables, so all platforms stay numerically aligned.</p>
 *
 * <p>Economy anchor: 9 Armory Points = 1 {@code csgo_key0}
 * (see {@code data/csgobox/recipe/armory_point_exchange.json}).
 * Mineral-to-point trades pay LESS than crafting a key0 from the same minerals
 * to prevent arbitrage; key2 uses a two-input offer (points + diamonds)
 * because a single armory_point stack maxes at 64.</p>
 */
public final class ModVillagers {

    private ModVillagers() {
    }

    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("csbox");

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

    // ---- dynamic pricing config (lazy, cached) --------------------------------

    private static volatile VillagerPricingConfig pricingConfig;

    private static VillagerPricingConfig config() {
        VillagerPricingConfig c = pricingConfig;
        if (c == null) {
            synchronized (ModVillagers.class) {
                c = pricingConfig;
                if (c == null) {
                    c = VillagerPricingConfig.load(CONFIG_DIR);
                    pricingConfig = c;
                }
            }
        }
        return c;
    }

    /** One fresh dynamic quote, or null when dynamic pricing is disabled. */
    private static VillagerPricing.Quote quoteFor(RandomSource rng) {
        return VillagerPricing.quote(PriceTableRegistry.get(), config(), rng::nextInt);
    }

    // ---- trade registration ----------------------------------------------------

    public static void register(IEventBus eventBus) {
        POI_TYPES.register(eventBus);
        PROFESSIONS.register(eventBus);
        eventBus.addListener(FMLCommonSetupEvent.class, ModVillagers::injectTrades);
    }

    private static void injectTrades(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Ship a tunable default config on first run; loaders pick it up lazily.
            BoxDefaults.writeVillagerPricesIfMissing(CONFIG_DIR);

            Item point = ModItems.ITEM_ARMORY_POINT.get();
            Item key0 = ModItems.ITEM_CSGO_KEY0.get();
            Item key1 = ModItems.ITEM_CSGO_KEY1.get();
            Item key2 = ModItems.ITEM_CSGO_KEY2.get();
            Item box = ModItems.ITEM_CSGOBOX.get();
            Item terminal = ModItems.ITEM_TERMINAL.get();

            Int2ObjectMap<VillagerTrades.ItemListing[]> byLevel = new Int2ObjectOpenHashMap<>();

            // Level 1: minerals → points (iron / emerald)
            byLevel.put(1, new VillagerTrades.ItemListing[]{
                    dynamic(Items.IRON_INGOT, 1, point, q -> q.iron(), 2, 16, 2),
                    dynamic(Items.EMERALD, 1, point, q -> q.emerald(), 2, 12, 2),
            });
            // Level 2: gold → points; points → csgo_box
            byLevel.put(2, new VillagerTrades.ItemListing[]{
                    dynamic(Items.GOLD_INGOT, 1, point, q -> q.gold(), 4, 16, 5),
                    dynamicSell(point, q -> q.box(), box, 8, 12, 5),
            });
            // Level 3: diamond → points; points → csgo_key0 (9-point anchor)
            byLevel.put(3, new VillagerTrades.ItemListing[]{
                    dynamic(Items.DIAMOND, 1, point, q -> q.diamond(), 12, 12, 10),
                    dynamicSell(point, q -> q.key0(), key0, 9, 16, 10),
            });
            // Level 4: points → csgo_key1; points → terminal
            byLevel.put(4, new VillagerTrades.ItemListing[]{
                    dynamicSell(point, q -> q.key1(), key1, 24, 8, 15),
                    dynamicSell(point, q -> q.terminal(), terminal, 18, 4, 15),
            });
            // Level 5: points + diamond → csgo_key2 (two-input offer)
            byLevel.put(5, new VillagerTrades.ItemListing[]{
                    (entity, random) -> {
                        VillagerPricing.Quote q = quoteFor(random);
                        int points = q != null ? q.key2Points() : 45;
                        int diamonds = q != null ? q.key2Diamonds() : 1;
                        return new MerchantOffer(
                                new ItemCost(point, Math.max(1, points)),
                                Optional.of(new ItemCost(Items.DIAMOND, Math.max(1, diamonds))),
                                new ItemStack(key2, 1),
                                3, 30, 0.05F);
                    },
            });

            VillagerTrades.TRADES.put(ARMS_DEALER.get(), byLevel);
        });
    }

    /**
     * Mineral → points listing: {@code in} → N points, where N is the
     * dynamic quote value (or the static fallback when dynamic pricing is
     * disabled).
     */
    private static VillagerTrades.ItemListing dynamic(Item in, int inCount, Item out,
                                                      java.util.function.ToIntFunction<VillagerPricing.Quote> price,
                                                      int fallback, int maxUses, int xp) {
        return (entity, random) -> {
            VillagerPricing.Quote q = quoteFor(random);
            int count = q != null ? Math.max(1, price.applyAsInt(q)) : fallback;
            return new MerchantOffer(new ItemCost(in, inCount), new ItemStack(out, count),
                    maxUses, xp, 0.05F);
        };
    }

    /** Points → item listing: {@code price} points → 1 item. */
    private static VillagerTrades.ItemListing dynamicSell(Item point,
                                                          java.util.function.ToIntFunction<VillagerPricing.Quote> price,
                                                          Item out, int fallback, int maxUses, int xp) {
        return (entity, random) -> {
            VillagerPricing.Quote q = quoteFor(random);
            int points = q != null ? Math.max(1, price.applyAsInt(q)) : fallback;
            return new MerchantOffer(new ItemCost(point, points), new ItemStack(out, 1),
                    maxUses, xp, 0.05F);
        };
    }
}
