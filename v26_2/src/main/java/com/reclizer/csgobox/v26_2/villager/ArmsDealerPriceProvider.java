package com.reclizer.csgobox.v26_2.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.villager.VillagerPricing;
import com.reclizer.csgobox.villager.VillagerPricingConfig;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Loot {@link NumberProvider} behind the 26.x arms-dealer's dynamic villager
 * trades (NeoForge 26.2 flavour).
 *
 * <p>The datapack {@code villager_trade} JSON references this provider under
 * {@code "type": "csgobox:arms_dealer_price"}. Each time a villager refreshes
 * its offers (spawn / level-up / datapack reload) the provider re-evaluates
 * against {@link VillagerPricing}, which anchors every price to the live
 * {@code config/csbox/_prices.json} economy and re-samples within
 * {@code ±fluctuation} using the loot context's random source.</p>
 *
 * <p>When dynamic pricing is disabled (or the config file is missing /
 * unreadable) the provider returns the JSON {@code fallback}, which matches
 * the pre-dynamic static numbers — so all platforms stay aligned.</p>
 */
public record ArmsDealerPriceProvider(QuoteField field, int fallback) implements NumberProvider {

    /** Which field of {@link VillagerPricing.Quote} this provider reads. */
    public enum QuoteField {
        IRON, EMERALD, GOLD, DIAMOND,
        KEY0, KEY1, KEY2_POINTS, KEY2_DIAMONDS,
        BOX, TERMINAL;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<QuoteField> fromId(String id) {
            return Arrays.stream(values()).filter(f -> f.id().equals(id)).findFirst();
        }
    }

    private static final Codec<QuoteField> FIELD_CODEC = Codec.STRING.flatXmap(
            id -> QuoteField.fromId(id)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown quote_field '" + id + "'")),
            field -> DataResult.success(field.id()));

    public static final MapCodec<ArmsDealerPriceProvider> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    FIELD_CODEC.fieldOf("quote_field").forGetter(ArmsDealerPriceProvider::field),
                    Codec.INT.fieldOf("fallback").forGetter(ArmsDealerPriceProvider::fallback)
            ).apply(i, ArmsDealerPriceProvider::new));

    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("csbox");

    private static volatile VillagerPricingConfig pricingConfig;

    @Override
    public float getFloat(LootContext context) {
        return resolve(context);
    }

    @Override
    public MapCodec<? extends NumberProvider> codec() {
        return MAP_CODEC;
    }

    private int resolve(LootContext context) {
        VillagerPricing.Quote quote = VillagerPricing.quote(
                PriceTableRegistry.get(), config(), context.getRandom()::nextInt);
        if (quote == null) {
            return fallback;
        }
        int value = switch (field) {
            case IRON -> quote.iron();
            case EMERALD -> quote.emerald();
            case GOLD -> quote.gold();
            case DIAMOND -> quote.diamond();
            case KEY0 -> quote.key0();
            case KEY1 -> quote.key1();
            case KEY2_POINTS -> quote.key2Points();
            case KEY2_DIAMONDS -> quote.key2Diamonds();
            case BOX -> quote.box();
            case TERMINAL -> quote.terminal();
        };
        // Point inputs/outputs never exceed the 64-stack cap; the key2 diamond
        // add-on is clamped to ≥ 1 so the two-input offer never vanishes
        // (matching the 1.21.1 platform's Math.max(1, diamonds)).
        return field == QuoteField.KEY2_DIAMONDS
                ? Math.max(1, value)
                : Math.min(value, VillagerPricing.POINT_STACK_LIMIT);
    }

    /** Lazy, cached — matches the 1.20.1/1.21.1 platforms (restart to reload). */
    private static VillagerPricingConfig config() {
        VillagerPricingConfig c = pricingConfig;
        if (c == null) {
            synchronized (ArmsDealerPriceProvider.class) {
                c = pricingConfig;
                if (c == null) {
                    c = VillagerPricingConfig.load(CONFIG_DIR);
                    pricingConfig = c;
                }
            }
        }
        return c;
    }
}
