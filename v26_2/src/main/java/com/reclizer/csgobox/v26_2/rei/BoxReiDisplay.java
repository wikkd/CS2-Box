package com.reclizer.csgobox.v26_2.rei;

import com.reclizer.csgobox.v26_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_2.item.ModItems;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
public record BoxReiDisplay(BoxDefinition definition, ItemStack boxStack, ItemStack keyStack,
                            List<EntryIngredient> outputs) implements Display {

    /** Convenience constructor: derives the cached output list. */
    public BoxReiDisplay(BoxDefinition definition, ItemStack boxStack, ItemStack keyStack) {
        this(definition, boxStack, keyStack, null);
    }

    public BoxReiDisplay {
        List<EntryIngredient> computed = new ArrayList<>();
        computed.add(EntryIngredient.of(EntryStacks.of(boxStack)));
        for (var grade : definition.grades()) {
            for (ItemStack item : grade.items()) {
                if (!item.isEmpty()) {
                    computed.add(EntryIngredient.of(EntryStacks.of(item)));
                }
            }
        }
        outputs = List.copyOf(computed);
    }

    /** Builds displays for every definition currently in the client registry. */
    public static List<BoxReiDisplay> fromRegistry() {
        List<BoxReiDisplay> displays = new ArrayList<>();
        for (BoxDefinition definition : BoxRegistry.getAll()) {
            // Box items are a fixed set (a box is data, not an item id).
            Item boxItem = ModItems.itemForBox(definition.id(), definition.isTerminal());
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
        Item keyItem = BuiltInRegistries.ITEM.get(definition.keyItem())
                .map(Holder.Reference::value)
                .orElse(null);
        return keyItem == null ? ItemStack.EMPTY : new ItemStack(keyItem);
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        if (keyStack.isEmpty()) {
            return List.of();
        }
        return List.of(EntryIngredient.of(EntryStacks.of(keyStack)));
    }

    /**
     * Cached at construction (the {@code outputs} component): REI re-queries
     * the output list per display during search/filter/render passes, and it
     * only depends on the immutable components — it used to rebuild one
     * EntryStack allocation per pool item on every query.
     */
    @Override
    public List<EntryIngredient> getOutputEntries() {
        return outputs;
    }

    /** Keeps the pre-caching three-component identity semantics: the derived
     *  outputs list must not affect display equality. */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BoxReiDisplay other)) {
            return false;
        }
        return definition.equals(other.definition)
                && boxStack.equals(other.boxStack)
                && keyStack.equals(other.keyStack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(definition, boxStack, keyStack);
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return BoxOpenCategory.TYPE;
    }

    @Override
    public Optional<Identifier> getDisplayLocation() {
        return Optional.of(definition.id());
    }

    /** Client-only, always rebuilt from the live registry: never persisted. */
    @Override
    public DisplaySerializer<? extends Display> getSerializer() {
        return null;
    }
}
