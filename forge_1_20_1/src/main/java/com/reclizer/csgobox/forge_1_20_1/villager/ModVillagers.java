package com.reclizer.csgobox.forge_1_20_1.villager;

import com.google.common.collect.ImmutableSet;
import com.reclizer.csgobox.box.BoxDefaults;
import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.block.ModBlocks;
import com.reclizer.csgobox.forge_1_20_1.item.ModItems;
import com.reclizer.csgobox.villager.VillagerPricing;
import com.reclizer.csgobox.villager.VillagerPricingConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
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
 * values shared with the 26.x datapack tables (iron 1→2, emerald 1→2,
 * gold 1→4, diamond 1→12; box 8, key0 9, key1 24, key2 45 + 1 diamond,
 * terminal 18), so all platforms stay numerically aligned.</p>
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

    private static Int2ObjectMap<VillagerTrades.ItemListing[]> generateTrades() {
        Int2ObjectMap<VillagerTrades.ItemListing[]> map = new Int2ObjectOpenHashMap<>();

        Item point = ModItems.ITEM_ARMORY_POINT.get();
        Item box = ModItems.ITEM_CSGOBOX.get();
        Item key0 = ModItems.ITEM_CSGO_KEY0.get();
        Item key1 = ModItems.ITEM_CSGO_KEY1.get();
        Item key2 = ModItems.ITEM_CSGO_KEY2.get();
        Item terminal = ModItems.ITEM_TERMINAL.get();

        // Level 1: minerals → points (iron / emerald)
        map.put(1, new VillagerTrades.ItemListing[]{
                (trader, rng) -> {
                    VillagerPricing.Quote q = quoteFor(rng);
                    int count = q != null ? q.iron() : 2;
                    return buy(new ItemStack(Items.IRON_INGOT), new ItemStack(point, count), 16, 2);
                },
                (trader, rng) -> {
                    VillagerPricing.Quote q = quoteFor(rng);
                    int count = q != null ? q.emerald() : 2;
                    return buy(new ItemStack(Items.EMERALD), new ItemStack(point, count), 12, 2);
                }
        });

        // Level 2: gold → points; points → csgo_box
        map.put(2, new VillagerTrades.ItemListing[]{
                (trader, rng) -> {
                    VillagerPricing.Quote q = quoteFor(rng);
                    int count = q != null ? q.gold() : 4;
                    return buy(new ItemStack(Items.GOLD_INGOT), new ItemStack(point, count), 16, 5);
                },
                (trader, rng) -> {
                    VillagerPricing.Quote q = quoteFor(rng);
                    int count = q != null ? q.box() : 8;
                    return sell(count, new ItemStack(box), 12, 5);
                }
        });

        // Level 3: diamond → points; points → csgo_key0 (9-point currency anchor)
        map.put(3, new VillagerTrades.ItemListing[]{
                (trader, rng) -> {
                    VillagerPricing.Quote q = quoteFor(rng);
                    int count = q != null ? q.diamond() : 12;
                    return buy(new ItemStack(Items.DIAMOND), new ItemStack(point, count), 12, 10);
                },
                (trader, rng) -> {
                    VillagerPricing.Quote q = quoteFor(rng);
                    int count = q != null ? q.key0() : 9;
                    return sell(count, new ItemStack(key0), 16, 10);
                }
        });

        // Level 4: points → csgo_key1; points → terminal
        map.put(4, new VillagerTrades.ItemListing[]{
                (trader, rng) -> {
                    VillagerPricing.Quote q = quoteFor(rng);
                    int count = q != null ? q.key1() : 24;
                    return sell(count, new ItemStack(key1), 8, 15);
                },
                (trader, rng) -> {
                    VillagerPricing.Quote q = quoteFor(rng);
                    int count = q != null ? q.terminal() : 18;
                    return sell(count, new ItemStack(terminal), 4, 15);
                }
        });

        // Level 5: points + diamond → csgo_key2 (two-input offer: a single
        // armory_point stack caps at 64, so the rest is paid in diamonds)
        map.put(5, new VillagerTrades.ItemListing[]{
                (trader, rng) -> {
                    VillagerPricing.Quote q = quoteFor(rng);
                    int points = q != null ? q.key2Points() : 45;
                    int diamonds = q != null ? q.key2Diamonds() : 1;
                    return sellKey2(points, diamonds, new ItemStack(key2), 3, 30);
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

    /** Cost: {@code points} Armory Points + {@code diamonds} diamonds → result. */
    private static MerchantOffer sellKey2(int points, int diamonds, ItemStack result,
                                          int maxUses, int xp) {
        return new MerchantOffer(pointsStack(points),
                diamonds > 0 ? new ItemStack(Items.DIAMOND, diamonds) : ItemStack.EMPTY,
                result, maxUses, xp, 0.05f);
    }

    private static ItemStack pointsStack(int points) {
        return new ItemStack(ModItems.ITEM_ARMORY_POINT.get(), Math.max(1, points));
    }

    public static void register(IEventBus eventBus) {
        POI_TYPES.register(eventBus);
        PROFESSIONS.register(eventBus);
    }
}
