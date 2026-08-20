package com.reclizer.csgobox.v26_2.jei;

import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.v26_2.item.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI plugin for the box-opening probability category.
 *
 * <p>Recipes are snapshotted from the client {@code BoxRegistry} at
 * registration time, then refreshed whenever {@link BoxJeiSync} fires (the
 * box-definition sync packet, i.e. world join, {@code /csbox reload} or file
 * hot reload). Refresh replaces the whole recipe list so removed/changed
 * definitions never linger.</p>
 */
@JeiPlugin
public final class CsgoBoxJeiPlugin implements IModPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(CsgoBox.MODID, "jei_plugin");

    private IJeiRuntime runtime;
    private List<BoxJeiRecipe> registeredRecipes = List.of();

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new BoxOpenCategory(registration.getJeiHelpers(), firstBoxStack()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<BoxJeiRecipe> recipes = BoxJeiRecipe.fromRegistry();
        if (!recipes.isEmpty()) {
            registration.addRecipes(BoxOpenCategory.TYPE, recipes);
            registeredRecipes = recipes;
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (BoxJeiRecipe recipe : BoxJeiRecipe.fromRegistry()) {
            registration.addCraftingStation(BoxOpenCategory.TYPE, recipe.boxStack());
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        this.runtime = runtime;
        BoxJeiSync.setRefresher(this::refresh);
        refresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        BoxJeiSync.setRefresher(null);
        this.runtime = null;
        this.registeredRecipes = List.of();
    }

    /** Replaces every registered recipe with a fresh snapshot of the registry. */
    private void refresh() {
        IRecipeManager manager = runtime == null ? null : runtime.getRecipeManager();
        if (manager == null) {
            return;
        }
        List<BoxJeiRecipe> fresh = BoxJeiRecipe.fromRegistry();
        if (!registeredRecipes.isEmpty()) {
            manager.hideRecipes(BoxOpenCategory.TYPE, registeredRecipes);
        }
        registeredRecipes = new ArrayList<>(fresh);
        if (!fresh.isEmpty()) {
            manager.addRecipes(BoxOpenCategory.TYPE, fresh);
        }
    }

    private static ItemStack firstBoxStack() {
        List<BoxJeiRecipe> recipes = BoxJeiRecipe.fromRegistry();
        if (!recipes.isEmpty()) {
            return recipes.get(0).boxStack();
        }
        return new ItemStack(ModItems.ITEM_CSGOBOX.get());
    }
}
