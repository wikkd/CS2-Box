package com.reclizer.csgobox.forge_1_20_1.rei;

import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One REI display = one box definition: the box item and its whole item pool
 * are outputs (so any drop can be reverse-looked-up), the key item is the
 * input (absent for terminals). The category renders the drop-rate / per-grade
 * probability text straight from the definition.
 *
 * <p>Displays are never snapshotted: they are rebuilt on demand from the live
 * client {@link BoxRegistry} by {@link CsgoBoxReiPlugin}'s dynamic display
 * generator, so {@code /csbox reload} and file hot reloads are reflected with
 * no manual refresh.</p>
 */
public record BoxReiDisplay(BoxDefinition definition, ItemStack boxStack, ItemStack keyStack) implements Display {

    /** Builds displays for every definition currently in the client registry. */
    public static List<BoxReiDisplay> fromRegistry() {
        List<BoxReiDisplay> displays = new ArrayList<>();
        for (BoxDefinition definition : BoxRegistry.getAll()) {
            Item boxItem = BuiltInRegistries.ITEM.getOptional(definition.id()).orElse(null);
            if (boxItem == null || boxItem == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            ItemStack boxStack = ItemCsgoBox.setBoxId(definition.id(), new ItemStack(boxItem));
            ItemStack keyStack = keyStack(definition);
            displays.add(new BoxReiDisplay(definition, boxStack, keyStack));
        }
        return displays;
    }

    private static ItemStack keyStack(BoxDefinition definition) {
        if (definition.isTerminal() || definition.keyItem() == null
                || "minecraft:air".equals(definition.keyItem().toString())) {
            return ItemStack.EMPTY;
        }
        Item keyItem = BuiltInRegistries.ITEM.getOptional(definition.keyItem()).orElse(null);
        return keyItem == null || keyItem == net.minecraft.world.item.Items.AIR
                ? ItemStack.EMPTY
                : new ItemStack(keyItem);
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        if (keyStack.isEmpty()) {
            return List.of();
        }
        return List.of(EntryIngredient.of(EntryStacks.of(keyStack)));
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        List<EntryIngredient> outputs = new ArrayList<>();
        outputs.add(EntryIngredient.of(EntryStacks.of(boxStack)));
        for (var grade : definition.grades()) {
            for (ItemStack item : grade.items()) {
                if (!item.isEmpty()) {
                    outputs.add(EntryIngredient.of(EntryStacks.of(item)));
                }
            }
        }
        return outputs;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return BoxOpenCategory.TYPE;
    }

    @Override
    public Optional<ResourceLocation> getDisplayLocation() {
        return Optional.of(definition.id());
    }
}
