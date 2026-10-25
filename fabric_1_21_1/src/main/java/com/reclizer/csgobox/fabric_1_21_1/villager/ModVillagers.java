package com.reclizer.csgobox.fabric_1_21_1.villager;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.fabric_1_21_1.CsgoBox;
import com.reclizer.csgobox.fabric_1_21_1.block.ModBlocks;
import com.reclizer.csgobox.fabric_1_21_1.item.ModItems;
import com.reclizer.csgobox.villager.VillagerPricing;
import com.reclizer.csgobox.villager.VillagerPricingConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The arms-dealer villager on Fabric: a {@link PoiType} for the Armory
 * Recycler, a {@link VillagerProfession}, and trades injected directly into
 * {@link VillagerTrades#TRADES} during mod init.
 *
 * <p>Trades are <b>dynamically priced</b>: each refresh samples a fresh quote
 * from {@link VillagerPricing}, anchored to the live
 * {@code config/csbox/_prices.json} economy (knobs in
 * {@code config/csbox/_villager_prices.json}). When dynamic pricing is
 * disabled the trades fall back to the static values shared with the 26.x
 * datapack tables, so all platforms stay numerically aligned.</p>
 *
 * <p>Economy anchor: 9 Armory Points = 1 {@code csgo_key0}
 * (see {@code data/csgobox/recipe/armory_point_exchange.json}). key1 and
 * key2 use two-input offers (points + gold / points + diamonds) because
 * their crafting-cost anchors exceed a single armory_point stack of 64.</p>
 */
public final class ModVillagers {

    private ModVillagers() {
    }

    private static final Path CONFIG_DIR = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().resolve("csbox");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, path);
    }

    public static final Supplier<PoiType> ARMORY_RECYCLER_POI = Suppliers.memoize(() -> {
        PoiType poiType = new PoiType(
                Set.copyOf(ModBlocks.ARMORY_RECYCLER.get().getStateDefinition().getPossibleStates()),
                1, 1);
        Registry.register(BuiltInRegistries.POINT_OF_INTEREST_TYPE, id("armory_recycler"), poiType);
        // NeoForge populates the state→POI map automatically on registration;
        // on Fabric the mod must do it itself or villagers can never claim
        // the workstation.
        PoiTypes.registerBlockStates(
                BuiltInRegistries.POINT_OF_INTEREST_TYPE.wrapAsHolder(poiType),
                poiType.matchingStates());
        return poiType;
    });

    private static final ResourceLocation POI_KEY = id("armory_recycler");

    public static final Supplier<VillagerProfession> ARMS_DEALER = Suppliers.memoize(() -> {
        VillagerProfession profession = new VillagerProfession(
                "arms_dealer",
                holder -> holder.is(POI_KEY),
                holder -> holder.is(POI_KEY),
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_ARMORER);
        Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, id("arms_dealer"), profession);
        return profession;
    });

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

    public static void register() {
        ARMORY_RECYCLER_POI.get();
        ARMS_DEALER.get();
        injectTrades();
    }

    private static void injectTrades() {
        // Ship a tunable default config on first run; loaders pick it up lazily.
        BoxDefaults.writeVillagerPricesIfMissing(CONFIG_DIR);

        Item point = ModItems.ITEM_ARMORY_POINT.get();
        Item key0 = ModItems.ITEM_CSGO_KEY0.get();
        Item key1 = ModItems.ITEM_CSGO_KEY1.get();
        Item key2 = ModItems.ITEM_CSGO_KEY2.get();
        Item box = ModItems.ITEM_CSGOBOX.get();
        Item terminal = ModItems.ITEM_TERMINAL.get();

        Int2ObjectMap<VillagerTrades.ItemListing[]> byLevel = new Int2ObjectOpenHashMap<>();

        // Level 1: minerals → points (iron / emerald); statics match the
        // dynamic defaults (table 50/55 × buyRate 0.06 → 3)
        byLevel.put(1, new VillagerTrades.ItemListing[]{
                dynamic(Items.IRON_INGOT, 1, point, q -> q.iron(), 3, 16, 2),
                dynamic(Items.EMERALD, 1, point, q -> q.emerald(), 3, 12, 2),
        });
        // Level 2: gold → points; points → csgo_box
        byLevel.put(2, new VillagerTrades.ItemListing[]{
                dynamic(Items.GOLD_INGOT, 1, point, q -> q.gold(), 3, 16, 5),
                dynamicSell(point, q -> q.box(), box, 8, 12, 5),
        });
        // Level 3: diamond → points; points → csgo_key0 (9-point anchor)
        byLevel.put(3, new VillagerTrades.ItemListing[]{
                dynamic(Items.DIAMOND, 1, point, q -> q.diamond(), 12, 12, 10),
                dynamicSell(point, q -> q.key0(), key0, 9, 16, 10),
        });
        // Level 4: points + gold → csgo_key1; points → terminal
        byLevel.put(4, new VillagerTrades.ItemListing[]{
                // key1's 3-gold crafting anchor exceeds one 64-point
                // stack, so the remainder is paid in whole gold ingots
                // (same convention as the key2 offer below).
                (entity, random) -> {
                    VillagerPricing.Quote q = quoteFor(random);
                    int points = q != null ? q.key1Points() : 24;
                    int gold = q != null ? q.key1Gold() : 1;
                    return new MerchantOffer(
                            new ItemCost(point, Math.max(1, points)),
                            Optional.of(new ItemCost(Items.GOLD_INGOT, Math.max(1, gold))),
                            new ItemStack(key1, 1),
                            8, 15, 0.05F);
                },
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
    }

    /**
     * Mineral → points listing: {@code in} → N points, where N is the
     * dynamic quote value (or the static fallback when dynamic pricing is
     * disabled). Count is clamped to the 64-point stack cap, matching the
     * 26.x number-provider side.
     */
    private static VillagerTrades.ItemListing dynamic(Item in, int inCount, Item out,
                                                      java.util.function.ToIntFunction<VillagerPricing.Quote> price,
                                                      int fallback, int maxUses, int xp) {
        return (entity, random) -> {
            VillagerPricing.Quote q = quoteFor(random);
            int count = q != null
                    ? Math.max(1, Math.min(price.applyAsInt(q), VillagerPricing.POINT_STACK_LIMIT))
                    : fallback;
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
            int points = q != null
                    ? Math.max(1, Math.min(price.applyAsInt(q), VillagerPricing.POINT_STACK_LIMIT))
                    : fallback;
            return new MerchantOffer(new ItemCost(point, points), new ItemStack(out, 1),
                    maxUses, xp, 0.05F);
        };
    }
}
