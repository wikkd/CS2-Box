package com.reclizer.csgobox.kubejs;

import net.minecraft.nbt.NbtOps;
import com.reclizer.csgobox.api.box.GradeGroup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GradeBuilderJS {

    private final String id;
    private final String displayName;
    private final int color;
    private final int weight;
    private final List<ItemStack> items = new ArrayList<>();

    public GradeBuilderJS(String id, String displayName, int color, int weight) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.weight = weight;
    }

    public GradeBuilderJS addItem(String itemId) {
        return addItem(itemId, 1, null);
    }

    public GradeBuilderJS addItem(String itemId, int count) {
        return addItem(itemId, count, null);
    }

    public GradeBuilderJS addItem(String itemId, int count, String nbt) {
        ResourceLocation rl = ResourceLocation.parse(itemId);
        var item = BuiltInRegistries.ITEM.get(rl);
        if (item != null) {
            ItemStack stack = new ItemStack(item, count);
            if (nbt != null && !nbt.isEmpty()) {
                try {
                    var tag = net.minecraft.nbt.TagParser.parseTag(nbt);
                    DataComponentPatch patch = DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, tag)
                            .result().orElse(DataComponentPatch.EMPTY);
                    stack.applyComponents(patch);
                } catch (Exception ignored) {
                }
            }
            items.add(stack);
        }
        return this;
    }

    public GradeBuilderJS addItemStack(ItemStack stack) {
        items.add(stack.copy());
        return this;
    }

    public GradeGroup build() {
        return new GradeGroup(id, displayName, color, weight, List.copyOf(items));
    }
}
