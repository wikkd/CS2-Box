package com.reclizer.csgobox.forge_1_20_1.villager;

import com.google.common.collect.ImmutableSet;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.block.ModBlocks;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ModItems;
import com.reclizer.csgobox.villager.VillagerBoxPool;
import com.reclizer.csgobox.villager.VillagerPricing;
import com.reclizer.csgobox.villager.VillagerPricingConfig;
import com.reclizer.csgobox.villager.VillagerTradePool;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * The arms-dealer villager on the 1.20.1 (legacy) API: a {@link PoiType}
 * (the Armory Recycler block) plus the {@link VillagerProfession} itself.
 *
 * <p>Trades are injected into {@link VillagerTrades#TRADES} during common
 * setup and are <b>dynamically priced</b>: each refresh samples a fresh
 * quote from {@link VillagerPricing}, which anchors the numbers to the live
 * {@code config/csbox/_prices.json} economy (see
 * {@code config/csbox/_villager_prices.json} for knobs). When dynamic
 * pricing is disabled in the config, the trades fall back to the static
 * values shared with the 26.x datapack tables (iron 1→3, emerald 1→3,
 * gold 1→3, diamond 1→12; box 8, key0 9, key1 24 + 1 gold,
 * key2 45 + 1 diamond, terminal 18), so all platforms stay numerically
 * aligned. key1 and key2 sell as two-input offers (points + gold /
 * points + diamonds) because their crafting-cost anchors exceed a single
 * armory_point stack of 64.</p>
 *
 * <p>Mineral buys additionally come from a <b>random pool</b>
 * ({@link VillagerTradePool}): each villager offers a random subset of its
 * level's minerals (keyed by UUID+level, re-rolled on level-up) while every
 * sell offer stays present; {@code pool_enabled=false} restores the
 * pre-pool fixed lists.</p>
 *
 * <p>The box (level 2) and terminal (level 4) sales draw from the live
 * {@code BoxRegistry} ({@link VillagerBoxSell}): each villager deterministically
 * sells ONE configured box / terminal (UUID-seeded — stable across level-ups),
 * priced at the contents' table-price expectation × {@code box_sell_ratio};
 * nothing sellable → the trade does not appear.</p>
 */
public final class ModVillagers {

    private ModVillagers() {
    }

    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("csbox");

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

    public static void registerTrades() {
        // Ship a tunable default config on first run; loaders pick it up lazily.
        BoxDefaults.writeVillagerPricesIfMissing(CONFIG_DIR);
        VillagerTrades.TRADES.put(ARMS_DEALER.get(), generateTrades());
    }

    /** Pool gate: {@code false} unless the mineral is active for this villager
     *  (VillagerTradePool, keyed by UUID+level). Inactive buy offers return
     *  null and vanilla's draw skips them without consuming a slot. */
    private static boolean poolActive(Entity trader, int level, String mineral) {
        return VillagerTradePool.isActive(trader.getUUID().getLeastSignificantBits(), level, mineral,
                config().poolEnabled());
    }

    /**
     * Pooled mineral → points listing: {@code in} → N points, where N is the
     * dynamic quote value (or the static fallback when dynamic pricing is
     * disabled). The offer is {@code null} unless the mineral is active for
     * this villager per {@link VillagerTradePool}, so gating composes with
     * vanilla's random draw and with the always-present sell offers.
     */
    private static VillagerTrades.ItemListing pooledBuy(int level, String mineral, Item in,
                                                        java.util.function.ToIntFunction<VillagerPricing.Quote> price,
                                                        int fallback, int maxUses, int xp) {
        return (trader, rng) -> {
            if (!poolActive(trader, level, mineral)) {
                return null;
            }
            VillagerPricing.Quote q = quoteFor(rng);
            int count = q != null ? clampPoints(price.applyAsInt(q)) : fallback;
            return buy(new ItemStack(in), pointsStack(count), maxUses, xp);
        };
    }

    /**
     * Villager-specific box sale: picks one crate deterministically per
     * villager ({@link VillagerBoxSell}, seeded by UUID — stable across
     * level-ups) from the live {@code BoxRegistry} and prices it at the
     * contents' table-price expectation × {@code box_sell_ratio}. Prices
     * beyond one points stack are paid with gold-ingot change (key1/key2
     * convention). Nothing sellable → {@code null} (vanilla skips the slot —
     * no more selling unbound empty boxes).
     */
    private static VillagerTrades.ItemListing boxSell(int maxUses, int xp) {
        return (trader, rng) -> {
            VillagerBoxSell.Offer offer = VillagerBoxSell.resolve(
                    trader.getUUID().getLeastSignificantBits(), false,
                    config(), PriceTableRegistry.get());
            if (offer == null) {
                return null;
            }
            ItemStack boxStack = ItemCsgoBox.setBoxId(offer.boxId(),
                    new ItemStack(ModItems.itemForBox(offer.boxId(), false), 1));
            int points = VillagerBoxPool.pointsPart(offer.price());
            int gold = VillagerBoxPool.goldChange(offer.price(), offer.goldBase());
            return gold > 0
                    ? sellTwo(points, Items.GOLD_INGOT, gold, boxStack, maxUses, xp)
                    : sell(points, boxStack, maxUses, xp);
        };
    }

    /**
     * Villager-specific terminal sale: same pool mechanics as {@link #boxSell}
     * for {@code type: "terminal"} definitions; the price stays the key0 ×
     * {@code terminal_key_ratio} quote (a terminal is a shopfront, not a
     * value store — its contents already sell at table price).
     */
    private static VillagerTrades.ItemListing terminalSell(int maxUses, int xp) {
        return (trader, rng) -> {
            VillagerBoxSell.Offer offer = VillagerBoxSell.resolve(
                    trader.getUUID().getLeastSignificantBits(), true,
                    config(), PriceTableRegistry.get());
            if (offer == null) {
                return null;
            }
            VillagerPricing.Quote q = quoteFor(rng);
            int points = q != null ? clampPoints(q.terminal()) : 18;
            ItemStack terminalStack = ItemCsgoBox.setBoxId(offer.boxId(),
                    new ItemStack(ModItems.itemForBox(offer.boxId(), true), 1));
            return sell(points, terminalStack, maxUses, xp);
        };
    }

    private static Int2ObjectMap<VillagerTrades.ItemListing[]> generateTrades() {
        Int2ObjectMap<VillagerTrades.ItemListing[]> map = new Int2ObjectOpenHashMap<>();

        Item key0 = ModItems.ITEM_CSGO_KEY0.get();
        Item key1 = ModItems.ITEM_CSGO_KEY1.get();
        Item key2 = ModItems.ITEM_CSGO_KEY2.get();

        // Mineral buys are drawn from per-level pools (VillagerTradePool):
        // vanilla shuffles the array and fills 2 offer slots per level-up,
        // skipping null offers without consuming a slot — so inactive pool
        // members gate themselves off and the sell offers below are always
        // present. pool_enabled=false restores the pre-pool fixed lists.

        // Level 1: 2 of {coal, copper, iron, emerald, gold} → points;
        // statics match the dynamic defaults (fallback × buyRate 0.06)
        map.put(1, new VillagerTrades.ItemListing[]{
                pooledBuy(1, VillagerTradePool.COAL, Items.COAL, q -> q.coal(), 1, 16, 2),
                pooledBuy(1, VillagerTradePool.COPPER, Items.COPPER_INGOT, q -> q.copper(), 2, 16, 2),
                pooledBuy(1, VillagerTradePool.IRON, Items.IRON_INGOT, q -> q.iron(), 3, 16, 2),
                pooledBuy(1, VillagerTradePool.EMERALD, Items.EMERALD, q -> q.emerald(), 3, 12, 2),
                pooledBuy(1, VillagerTradePool.GOLD, Items.GOLD_INGOT, q -> q.gold(), 3, 16, 2),
        });

        // Level 2: 1 of {iron, emerald, gold} → points;
        // villager-specific box sale (EV-priced pool from BoxRegistry)
        map.put(2, new VillagerTrades.ItemListing[]{
                pooledBuy(2, VillagerTradePool.IRON, Items.IRON_INGOT, q -> q.iron(), 3, 16, 5),
                pooledBuy(2, VillagerTradePool.EMERALD, Items.EMERALD, q -> q.emerald(), 3, 12, 5),
                pooledBuy(2, VillagerTradePool.GOLD, Items.GOLD_INGOT, q -> q.gold(), 3, 16, 5),
                boxSell(12, 5)
        });

        // Level 3: 1 of {gold, diamond} → points; points → csgo_key0 (9-point currency anchor)
        map.put(3, new VillagerTrades.ItemListing[]{
                pooledBuy(3, VillagerTradePool.GOLD, Items.GOLD_INGOT, q -> q.gold(), 3, 12, 10),
                pooledBuy(3, VillagerTradePool.DIAMOND, Items.DIAMOND, q -> q.diamond(), 12, 12, 10),
                (trader, rng) -> {
                    VillagerPricing.Quote q = quoteFor(rng);
                    int count = q != null ? q.key0() : 9;
                    return sell(count, new ItemStack(key0), 16, 10);
                }
        });

        // Level 4: points + gold → csgo_key1 (key1's 3-gold crafting anchor
        // exceeds one 64-point stack, so the remainder is paid in whole gold
        // ingots — same convention as the key2 offer);
        // villager-specific terminal sale (pool from BoxRegistry)
        map.put(4, new VillagerTrades.ItemListing[]{
                (trader, rng) -> {
                    VillagerPricing.Quote q = quoteFor(rng);
                    int points = q != null ? q.key1Points() : 24;
                    int gold = q != null ? q.key1Gold() : 1;
                    return sellTwo(points, Items.GOLD_INGOT, Math.max(1, gold),
                            new ItemStack(key1), 8, 15);
                },
                terminalSell(4, 15)
        });

        // Level 5: 1 of {iron, emerald, gold, diamond} → points;
        // points + diamond → csgo_key2 (two-input offer: a single
        // armory_point stack caps at 64, so the rest is paid in diamonds)
        map.put(5, new VillagerTrades.ItemListing[]{
                pooledBuy(5, VillagerTradePool.IRON, Items.IRON_INGOT, q -> q.iron(), 3, 12, 30),
                pooledBuy(5, VillagerTradePool.EMERALD, Items.EMERALD, q -> q.emerald(), 3, 12, 30),
                pooledBuy(5, VillagerTradePool.GOLD, Items.GOLD_INGOT, q -> q.gold(), 3, 12, 30),
                pooledBuy(5, VillagerTradePool.DIAMOND, Items.DIAMOND, q -> q.diamond(), 12, 12, 30),
                (trader, rng) -> {
                    VillagerPricing.Quote q = quoteFor(rng);
                    int points = q != null ? q.key2Points() : 45;
                    int diamonds = q != null ? q.key2Diamonds() : 1;
                    return sellTwo(points, Items.DIAMOND, Math.max(1, diamonds),
                            new ItemStack(key2), 3, 30);
                }
        });

        return map;
    }

    // ---- MerchantOffer helpers -------------------------------------------------

    /** Cost: {@code cost} → result {@code points} Armory Points. */
    private static MerchantOffer buy(ItemStack cost, ItemStack points, int maxUses, int xp) {
        return new MerchantOffer(cost, points, maxUses, xp, 0.05f);
    }

    /** Cost: {@code points} Armory Points → result. */
    private static MerchantOffer sell(int points, ItemStack result, int maxUses, int xp) {
        return new MerchantOffer(pointsStack(points), result, maxUses, xp, 0.05f);
    }

    /** Cost: {@code points} Armory Points + {@code extraCount} × {@code extraItem} → result. */
    private static MerchantOffer sellTwo(int points, Item extraItem, int extraCount,
                                         ItemStack result, int maxUses, int xp) {
        return new MerchantOffer(pointsStack(points),
                extraCount > 0 ? new ItemStack(extraItem, extraCount) : ItemStack.EMPTY,
                result, maxUses, xp, 0.05f);
    }

    private static ItemStack pointsStack(int points) {
        return new ItemStack(ModItems.ITEM_ARMORY_POINT.get(), clampPoints(points));
    }

    /** Clamps a dynamic point count to a payable single stack [1, 64]
     *  (matches the 26.x number-provider side). */
    private static int clampPoints(int v) {
        return Math.max(1, Math.min(v, VillagerPricing.POINT_STACK_LIMIT));
    }

    public static void register(IEventBus eventBus) {
        POI_TYPES.register(eventBus);
        PROFESSIONS.register(eventBus);
    }
}
