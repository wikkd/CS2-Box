package com.reclizer.csgobox.forge_1_20_1.box;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A grade tier within a box definition. {@code prices} is a parallel list to
 * {@code items}: {@code prices.get(i)} is the terminal purchase price (in
 * Armory Points) for {@code items.get(i)}. A value of -1 means "use the
 * default grade-level price" ({@code NegotiationModel.GRADE_PRICE}). An empty
 * list means all items use the default price.
 */
public record GradeGroup(String id, String displayName, int color, int weight,
                         List<ItemStack> items, List<Integer> prices) {

    private static final int MAX_ITEMS = 256;

    public static final Codec<GradeGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(GradeGroup::id),
            Codec.STRING.fieldOf("display_name").forGetter(GradeGroup::displayName),
            Codec.INT.fieldOf("color").forGetter(GradeGroup::color),
            Codec.INT.fieldOf("weight").forGetter(GradeGroup::weight),
            ItemStack.CODEC.listOf().fieldOf("items").forGetter(GradeGroup::items),
            Codec.INT.listOf().optionalFieldOf("prices", List.of()).forGetter(GradeGroup::prices)
    ).apply(instance, GradeGroup::new));

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
    }

    /**
     * Price for the item at the given index, or -1 if the item has no custom
     * price (fall back to the default grade-level price).
     */
    public int priceForIndex(int index) {
        if (index >= 0 && index < prices.size()) {
            return prices.get(index);
        }
        return -1;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(displayName);
        buf.writeInt(color);
        buf.writeInt(weight);
        buf.writeVarInt(items.size());
        for (ItemStack stack : items) {
            buf.writeNbt(stack.save(new CompoundTag()));
        }
        buf.writeVarInt(prices.size());
        for (int p : prices) {
            buf.writeVarInt(p);
        }
    }

    public static GradeGroup decode(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String displayName = buf.readUtf();
        int color = buf.readInt();
        int weight = buf.readInt();
        int count = buf.readVarInt();
        List<ItemStack> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CompoundTag tag = buf.readNbt();
            items.add(ItemStack.of(tag));
        }
        int priceCount = buf.readVarInt();
        List<Integer> prices = new ArrayList<>(priceCount);
        for (int i = 0; i < priceCount; i++) {
            prices.add(buf.readVarInt());
        }
        return new GradeGroup(id, displayName, color, weight, items, prices);
    }
}
