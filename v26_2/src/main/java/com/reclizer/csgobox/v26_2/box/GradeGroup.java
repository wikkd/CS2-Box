package com.reclizer.csgobox.v26_2.box;

import com.reclizer.csgobox.box.NetworkLimits;
import com.reclizer.csgobox.box.PriceRange;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A grade tier within a box definition. {@code prices} is a parallel list to
 * {@code items}: {@code prices.get(i)} is the terminal purchase price (in
 * Armory Points) for {@code items.get(i)} — either a fixed value or a random
 * {@code [min, max]} range sampled per terminal offer. A
 * {@link PriceRange#UNPRICED} entry means "no table price": the terminal
 * never offers it and the recycler pays 0 (no grade-default fallback).
 * An empty list means no item has a custom price.
 *
 * <p>{@code itemWeights} is another parallel list to {@code items}: the
 * intra-grade weight of each item (1 = uniform, the classic behaviour). A
 * weight {@code <= 0} removes the item from the weighted pool (authors can
 * temporarily disable single entries without deleting them). When absent the
 * list is filled with {@code 1}s so old configs and network payloads stay
 * uniform by default.</p>
 */
public record GradeGroup(String id, String displayName, int color, int weight,
                         List<ItemStack> items, List<PriceRange> prices,
                         List<Integer> itemWeights) {

    private static final int MAX_ITEMS = NetworkLimits.MAX_ITEMS;

    /** Codec for one price entry: encoded/decoded as {@code [min, max]}
     *  (fixed prices are {@code [v, v]}). */
    private static final Codec<PriceRange> PRICE_CODEC = Codec.INT.listOf().comapFlatMap(
            (List<Integer> list) -> {
                if (list.size() != 2) {
                    return DataResult.error(() -> "price entry must encode as [min, max]");
                }
                int min = list.get(0);
                int max = list.get(1);
                if (min < 0 && max < 0) {
                    return DataResult.success(PriceRange.UNPRICED);
                }
                if (min < 0 || max < 0 || min > max) {
                    return DataResult.error(() -> "invalid price entry [" + min + ", " + max + "]");
                }
                return DataResult.success(new PriceRange(min, max));
            },
            r -> List.of(r.min(), r.max()));

    /** Stream codec for one price entry: min then max (two ints). */
    private static final StreamCodec<RegistryFriendlyByteBuf, PriceRange> PRICE_STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, PriceRange::min,
                    ByteBufCodecs.INT, PriceRange::max,
                    PriceRange::new);

    public static final Codec<GradeGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(GradeGroup::id),
            Codec.STRING.fieldOf("display_name").forGetter(GradeGroup::displayName),
            Codec.INT.fieldOf("color").forGetter(GradeGroup::color),
            Codec.INT.fieldOf("weight").forGetter(GradeGroup::weight),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(GradeGroup::items),
            PRICE_CODEC.listOf().optionalFieldOf("prices", List.of()).forGetter(GradeGroup::prices),
            Codec.INT.listOf().optionalFieldOf("item_weights", List.of()).forGetter(GradeGroup::itemWeights)
    ).apply(instance, GradeGroup::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GradeGroup> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GradeGroup::id,
            ByteBufCodecs.STRING_UTF8, GradeGroup::displayName,
            ByteBufCodecs.INT, GradeGroup::color,
            ByteBufCodecs.INT, GradeGroup::weight,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ITEMS)), GradeGroup::items,
            PRICE_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ITEMS)), GradeGroup::prices,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list(MAX_ITEMS)), GradeGroup::itemWeights,
            GradeGroup::new
    );

    public GradeGroup {
        id = Objects.requireNonNull(id, "grade id");
        displayName = displayName == null ? id : displayName;
        if (items == null || items.isEmpty()) {
            items = List.of();
        } else {
            List<ItemStack> copies = new ArrayList<>(items.size());
            for (ItemStack stack : items) {
                if (stack != null && !stack.isEmpty()) {
                    copies.add(stack.copy());
                }
            }
            items = List.copyOf(copies);
        }
        if (prices == null) {
            prices = List.of();
        } else {
            prices = List.copyOf(prices);
        }
        // Normalize the weight list to be parallel with items (fill missing
        // entries with 1, keep explicit 0s so authors can disable entries).
        if (itemWeights == null || itemWeights.isEmpty()) {
            itemWeights = items.stream().map(i -> 1).toList();
        } else {
            List<Integer> weights = new ArrayList<>(items.size());
            for (int i = 0; i < items.size(); i++) {
                Integer w = i < itemWeights.size() ? itemWeights.get(i) : 1;
                weights.add(w == null ? 1 : w);
            }
            itemWeights = List.copyOf(weights);
        }
    }

    /**
     * Price range for the item at the given index, or
     * {@link PriceRange#UNPRICED} if the item has no custom price (fall back
     * to the default grade-level price). The terminal samples the range once
     * per offer.
     */
    public PriceRange priceForIndex(int index) {
        if (index >= 0 && index < prices.size()) {
            return prices.get(index);
        }
        return PriceRange.UNPRICED;
    }

    /** Intra-grade weight for the item at the given index (default 1). */
    public int itemWeightAt(int index) {
        if (index >= 0 && index < itemWeights.size()) {
            return itemWeights.get(index);
        }
        return 1;
    }

    /** Sum of positive intra-grade weights; equals the item count when every
     *  weight is 1 (uniform selection). */
    public long positiveItemWeightSum() {
        long sum = 0;
        for (int w : itemWeights) {
            if (w > 0) {
                sum += w;
            }
        }
        return sum;
    }
}