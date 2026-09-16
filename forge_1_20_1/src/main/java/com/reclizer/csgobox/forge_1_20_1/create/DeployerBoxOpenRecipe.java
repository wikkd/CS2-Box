package com.reclizer.csgobox.forge_1_20_1.create;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Create deployer recipe for opening a csgobox crate (forge_1_20_1, Create
 * 6.0.8): the crate rides the belt/depot, the deployer holds the matching
 * key. Matching is fully custom ({@link #matches}) and the real prize is
 * injected lazily by {@link DeployerBoxOpenRecipeSearch} through
 * {@code enforceNextResult} when Create commits the recipe.
 *
 * <p>The declared output is a {@link ProcessingOutput#EMPTY} placeholder:
 * Create's assembly rolls by iterating the declared output list and only
 * index 0 consumes the enforced result — with an empty list the deployer
 * would eat the crate and drop nothing.</p>
 */
final class DeployerBoxOpenRecipe extends ManualApplicationRecipe {

    private DeployerBoxOpenRecipe(ProcessingRecipeBuilder.ProcessingRecipeParams params) {
        super(params);
    }

    /** Creates a fresh recipe for one activation (the params carry no data). */
    static DeployerBoxOpenRecipe newInstance() {
        return new ProcessingRecipeBuilder<>(DeployerBoxOpenRecipe::new,
                new ResourceLocation(CsgoBox.MODID, "deployer_box_open"))
                .withItemOutputs(ProcessingOutput.EMPTY)
                .build();
    }

    @Override
    public boolean matches(RecipeWrapper inv, Level level) {
        if (inv == null) {
            return false;
        }
        // Deployer recipe inventory is always exactly two slots
        // (slot 0 = platform crate, slot 1 = held item).
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
        return !held.isEmpty() && key.equals(ForgeRegistries.ITEMS.getKey(held.getItem()));
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