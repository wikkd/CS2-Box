package com.reclizer.csgobox.v1_21_1.jei;

import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
            Item boxItem = BuiltInRegistries.ITEM.get(definition.id());
            if (boxItem == null || boxItem == net.minecraft.world.item.Items.AIR) {
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
        Item keyItem = BuiltInRegistries.ITEM.get(definition.keyItem());
        return keyItem == null || keyItem == net.minecraft.world.item.Items.AIR
                ? ItemStack.EMPTY
                : new ItemStack(keyItem);
    }
}
