package com.reclizer.csgobox.v1_21_1.create;

import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipeParams;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/**
 * Create deployer recipe for opening a csgobox crate: the crate rides the
 * belt/depot, the deployer (or player) holds the matching key. Matching is
 * fully custom ({@link #matches}) — the crate id and the key come from
 * {@link BoxDefinition}, not from static ingredients — and the actual prize
 * is injected lazily by the search listener through
 * {@link #enforceNextResult} when Create commits the recipe (so the roll
 * happens exactly once, at the moment the platform item is replaced).
 *
 * <p>This path is the official Create API integration
 * ({@code DeployerRecipeSearchEvent} + {@code ManualApplicationRecipe});
 * Create owns held-item consumption and platform replacement.</p>
 */
final class DeployerBoxOpenRecipe extends ManualApplicationRecipe {

    private DeployerBoxOpenRecipe() {
        super(new ItemApplicationRecipeParams());
        // Create's assembly rolls outputs by iterating the DECLARED output
        // list (rollResults(getRollableResults(), rng)); with an empty list
        // the index-0 spot that consumes enforceNextResult is never visited,
        // so the deployer would eat the crate and drop nothing. A placeholder
        // output gives rollResults an index 0; the real prize still comes
        // from the enforced supplier (EMPTY rolls are dropped by isEmpty).
        this.results.add(ProcessingOutput.EMPTY);
    }

    /** Creates a fresh recipe for one activation (the params carry no data). */
    static DeployerBoxOpenRecipe newInstance() {
        return new DeployerBoxOpenRecipe();
    }

    @Override
    public boolean matches(RecipeWrapper inv, Level level) {
        if (inv == null) {
            return false;
        }
        // The deployer's recipe inventory is always exactly two slots
        // (slot 0 = platform crate, slot 1 = held item); an empty hand is a
        // valid "key" for keyless crates.
        ItemStack box = inv.getItem(0);
        ItemStack held = inv.getItem(1);
        if (!(box.getItem() instanceof ItemCsgoBox)) {
            return false;
        }
        ResourceLocation boxId = ItemCsgoBox.getBoxId(box);
        BoxDefinition def = boxId == null ? null : BoxRegistry.get(boxId);
        if (def == null || def.isTerminal()) {
            return false;
        }
        return keyMatches(held, def);
    }

    /** True when the deployer/player holds the key the crate requires
     *  (keyed crates need the exact item; keyless crates accept an empty hand). */
    static boolean keyMatches(ItemStack held, BoxDefinition def) {
        ResourceLocation key = def.keyItem();
        if (key == null || "minecraft:air".equals(key.toString())) {
            return held.isEmpty();
        }
        return !held.isEmpty() && key.equals(BuiltInRegistries.ITEM.getKey(held.getItem()));
    }

    @Override
    protected int getMaxInputCount() {
        return 2;
    }

    @Override
    protected int getMaxOutputCount() {
        return 1;
    }

    // The params carry no ingredients; matching is custom above, so expose
    // permissive values (Create's own assembly never consults these for the
    // recipe-search path, but keeping them non-null avoids NPEs).
    @Override
    public Ingredient getRequiredHeldItem() {
        return Ingredient.EMPTY;
    }

    @Override
    public Ingredient getProcessedItem() {
        return Ingredient.EMPTY;
    }
}