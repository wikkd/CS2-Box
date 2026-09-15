package com.reclizer.csgobox.v1_21_1.box;

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

    public static final Codec<GradeGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(GradeGroup::id),
            Codec.STRING.fieldOf("display_name").forGetter(GradeGroup::displayName),
            Codec.INT.fieldOf("color").forGetter(GradeGroup::color),
            Codec.INT.fieldOf("weight").forGetter(GradeGroup::weight),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(GradeGroup::items),
            PRICE_CODEC.listOf().optionalFieldOf("prices", List.of()).forGetter(GradeGroup::prices),
            Codec.INT.listOf().optionalFieldOf("item_weights", List.of()).forGetter(GradeGroup::itemWeights)
    ).apply(instance, GradeGroup::new));

    // 1.21.1's StreamCodec.composite caps at 6 components; this record has 7,
// so the stream codec is written by hand (same wire layout order as 26.x,
// with the price list encoded as count + [min, max] pairs).
public static final StreamCodec<RegistryFriendlyByteBuf, GradeGroup> STREAM_CODEC = StreamCodec.of(
        (buf, g) -> {
            ByteBufCodecs.STRING_UTF8.encode(buf, g.id());
            ByteBufCodecs.STRING_UTF8.encode(buf, g.displayName());
            ByteBufCodecs.INT.encode(buf, g.color());
            ByteBufCodecs.INT.encode(buf, g.weight());
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ITEMS)).encode(buf, g.items());
            encodePrices(buf, g.prices());
            ByteBufCodecs.INT.apply(ByteBufCodecs.list(MAX_ITEMS)).encode(buf, g.itemWeights());
        },
        buf -> new GradeGroup(
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.INT.decode(buf),
                ByteBufCodecs.INT.decode(buf),
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ITEMS)).decode(buf),
                decodePrices(buf),
                ByteBufCodecs.INT.apply(ByteBufCodecs.list(MAX_ITEMS)).decode(buf))
);

/** Encodes a price list as {@code count} + {@code [min, max]} pairs. */
private static void encodePrices(RegistryFriendlyByteBuf buf, List<PriceRange> prices) {
    if (prices.size() > MAX_ITEMS) {
        throw new IllegalStateException("too many prices: " + prices.size());
    }
    buf.writeVarInt(prices.size());
    for (PriceRange r : prices) {
        ByteBufCodecs.INT.encode(buf, r.min());
        ByteBufCodecs.INT.encode(buf, r.max());
    }
}

/** Decodes a price list (count + [min, max] pairs; negative pair = UNPRICED). */
private static List<PriceRange> decodePrices(RegistryFriendlyByteBuf buf) {
    int n = buf.readVarInt();
    if (n > MAX_ITEMS) {
        throw new IllegalStateException("too many prices on the wire: " + n);
    }
    List<PriceRange> list = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
        int min = ByteBufCodecs.INT.decode(buf);
        int max = ByteBufCodecs.INT.decode(buf);
        if (min < 0 && max < 0) {
            list.add(PriceRange.UNPRICED);
        } else if (min < 0 || max < 0 || min > max) {
            throw new IllegalStateException("invalid price range [" + min + ", " + max + "]");
        } else {
            list.add(new PriceRange(min, max));
        }
    }
    return list;
}

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