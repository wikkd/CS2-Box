package com.reclizer.csgobox.v26_1_2.villager;

import com.google.common.collect.ImmutableSet;
import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.block.ModBlocks;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import java.util.function.Supplier;

/**
 * The arms-dealer villager: a {@link PoiType} (the Armory Recycler block) plus
 * the {@link VillagerProfession} itself.
 *
 * <p><b>26.x note:</b> villager trades are no longer code-registered. The old
 * {@code VillagerTrades.TRADES} map and {@code VillagerTrades.ItemListing} are
 * gone; {@code TradeSet} / {@code VillagerTrade} are datapack registries now.
 * A profession just carries {@code Int2ObjectMap<ResourceKey<TradeSet>>}, and
 * the actual trades live in JSON under
 * {@code data/csgobox/trade_set/arms_dealer/level_N.json} and
 * {@code data/csgobox/villager_trade/arms_dealer/N/*.json}.</p>
 *
 * <p>All point values are anchored to the in-repo economy: 9 Armory Points =
 * 1 {@code csgo_key0} (see {@code data/csgobox/recipe/armory_point_exchange.json}).
 * Mineral-to-point trades deliberately pay LESS than crafting a key0 from the
 * same minerals, so there is no arbitrage loop.</p>
 */
public final class ModVillagers {

    private ModVillagers() {
    }

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, CsgoBox.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, CsgoBox.MODID);

    private static final ResourceKey<PoiType> POI_KEY = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE,
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "armory_recycler"));

    public static final Supplier<PoiType> ARMORY_RECYCLER_POI = POI_TYPES.register("armory_recycler",
            () -> new PoiType(
                    Set.copyOf(ModBlocks.ARMORY_RECYCLER.get().getStateDefinition().getPossibleStates()),
                    1, 1));

    public static final Supplier<VillagerProfession> ARMS_DEALER = PROFESSIONS.register("arms_dealer",
            () -> new VillagerProfession(
                    Component.translatable("entity.csgobox.arms_dealer"),
                    holder -> holder.is(POI_KEY),
                    holder -> holder.is(POI_KEY),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_ARMORER,
                    tradeSets()));

    /** Maps villager level (1..5) to the datapack {@code TradeSet} that level offers. */
    private static Int2ObjectMap<ResourceKey<TradeSet>> tradeSets() {
        Int2ObjectMap<ResourceKey<TradeSet>> map = new Int2ObjectOpenHashMap<>();
        for (int level = 1; level <= 5; level++) {
            map.put(level, tradeSetKey("arms_dealer/level_" + level));
        }
        return map;
    }

    private static ResourceKey<TradeSet> tradeSetKey(String path) {
        return ResourceKey.create(Registries.TRADE_SET,
                Identifier.fromNamespaceAndPath(CsgoBox.MODID, path));
    }

    public static void register(IEventBus eventBus) {
        POI_TYPES.register(eventBus);
        PROFESSIONS.register(eventBus);
    }
}
