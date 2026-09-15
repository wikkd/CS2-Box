package com.reclizer.csgobox.v1_21_1.emi;

import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ModItems;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiRenderable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EMI plugin for the box-opening probability category.
 *
 * <p>Recipes are snapshotted from the client {@code BoxRegistry} at every EMI
 * reload; after the box-definition sync packet refreshes the registry,
 * {@link BoxEmiReload} schedules one {@code Minecraft.reloadResourcePacks()} so
 * EMI re-runs this plugin against the fresh data (EMI has no JEI-style
 * runtime recipe manager).</p>
 *
 * <p>When JEI is also installed, EMI's built-in {@code JemiPlugin} bridges the
 * existing JEI category automatically; registering a native EMI category in
 * that case would duplicate it, so the native plugin is skipped under JEI and
 * the bridge takes over.</p>
 *
 * <p>Discovery is {@code @EmiEntrypoint} annotation scanning, performed only
 * by EMI itself — the class is inert when EMI is absent.</p>
 */
@EmiEntrypoint
public final class BoxEmiPlugin implements EmiPlugin {

    private static final ResourceLocation CATEGORY_ID =
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "box_open");

    private static final AtomicBoolean RELOADING = new AtomicBoolean();

    @Override
    public void register(EmiRegistry registry) {
        // Native EMI category only when JEI is not installed; with JEI present
        // JemiPlugin bridges the existing JEI plugin (no duplication).
        if (ModList.get().isLoaded("jei")) {
            BoxEmiReload.setRefresher(null);
            return;
        }

        EmiRecipeCategory category = new EmiRecipeCategory(CATEGORY_ID, icon());
        List<BoxEmiRecipe> recipes = BoxEmiRecipe.fromRegistry(category);
        registry.addCategory(category);
        for (BoxEmiRecipe recipe : recipes) {
            registry.addRecipe(recipe);
            if (!recipe.getOutputs().isEmpty()) {
                registry.addWorkstation(category, recipe.getOutputs().get(0));
            }
        }

        BoxEmiReload.setRefresher(BoxEmiPlugin::requestReload);
    }

    private static EmiRenderable icon() {
        ItemStack stack = firstBoxStack();
        return (gui, x, y, delta) -> gui.renderItem(stack, x, y);
    }

    private static ItemStack firstBoxStack() {
        List<BoxEmiRecipe> recipes = BoxEmiRecipe.fromRegistry(
                new EmiRecipeCategory(CATEGORY_ID, (gui, x, y, delta) -> { }));
        if (!recipes.isEmpty() && !recipes.get(0).getOutputs().isEmpty()) {
            return recipes.get(0).getOutputs().get(0).getItemStack();
        }
        return new ItemStack(ModItems.ITEM_CSGOBOX.get());
    }

    /** One EMI-triggered resource reload per box-registry change, re-entrancy guarded. */
    private static void requestReload() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        if (!RELOADING.compareAndSet(false, true)) {
            return;
        }
        minecraft.reloadResourcePacks().whenComplete((result, error) -> RELOADING.set(false));
    }
}