package com.reclizer.csgobox.v26_1_2.jei;

import com.reclizer.csgobox.v26_1_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_1_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One JEI recipe = one box definition: the box item (output), its key item
 * (input, absent for terminals) and the full definition the category renders
 * probability text from.
 */
public record BoxJeiRecipe(BoxDefinition definition, ItemStack boxStack, ItemStack keyStack) {

    /** Snapshots every definition currently in the client box registry. */
    public static List<BoxJeiRecipe> fromRegistry() {
        List<BoxJeiRecipe> recipes = new ArrayList<>();
        for (BoxDefinition definition : BoxRegistry.getAll()) {
            Item boxItem = BuiltInRegistries.ITEM.get(definition.id())
                    .map(Holder.Reference::value)
                    .orElse(null);
            if (boxItem == null) {
                continue;
            }
            ItemStack boxStack = ItemCsgoBox.setBoxId(definition.id(), new ItemStack(boxItem));
            ItemStack keyStack = keyStack(definition);
            recipes.add(new BoxJeiRecipe(definition, boxStack, keyStack));
        }
        return recipes;
    }

    private static ItemStack keyStack(BoxDefinition definition) {
        if (definition.isTerminal() || definition.keyItem() == null
                || "minecraft:air".equals(definition.keyItem().toString())) {
            return ItemStack.EMPTY;
        }
        Item keyItem = BuiltInRegistries.ITEM.get(definition.keyItem())
                .map(Holder.Reference::value)
                .orElse(null);
        return keyItem == null ? ItemStack.EMPTY : new ItemStack(keyItem);
    }
}
