package com.reclizer.csgobox.v26_1_2.rei;

import com.reclizer.csgobox.v26_1_2.box.BoxDefinition;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REI plugin for the box-opening probability category.
 *
 * <p>Displays are produced on demand by a {@link DynamicDisplayGenerator} that
 * reads the live client {@code BoxRegistry}, so {@code /csbox reload} and file
 * hot reloads are reflected on the next REI view build with no manual refresh
 * (unlike the JEI path, which swaps recipes through a sync bridge). The
 * generator also answers reverse lookups: recipes-for a drop item and usages
 * for a box item.</p>
 *
 * <p>Discovery is {@code @REIPluginClient} annotation scanning (works on both
 * Forge and NeoForge; MEI, being a REI fork, picks this plugin up too).</p>
 */
@REIPluginClient
public final class CsgoBoxReiPlugin implements REIClientPlugin {

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new BoxOpenCategory());
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        registry.registerDisplayGenerator(BoxOpenCategory.TYPE, new DynamicDisplayGenerator<>() {

            @Override
            public Optional<List<BoxReiDisplay>> generate(ViewSearchBuilder builder) {
                return Optional.of(BoxReiDisplay.fromRegistry());
            }

            @Override
            public Optional<List<BoxReiDisplay>> getRecipeFor(EntryStack<?> entry) {
                // Reverse lookup: a drop item -> every box that can drop it.
                ItemStack stack = entry.castValue();
                if (stack == null || stack.isEmpty()) {
                    return Optional.empty();
                }
                List<BoxReiDisplay> matches = new ArrayList<>();
                for (BoxReiDisplay display : BoxReiDisplay.fromRegistry()) {
                    if (isInPool(display.definition(), stack)) {
                        matches.add(display);
                    }
                }
                return matches.isEmpty() ? Optional.empty() : Optional.of(matches);
            }

            @Override
            public Optional<List<BoxReiDisplay>> getUsageFor(EntryStack<?> entry) {
                // A box item -> its own drop-probability display.
                ItemStack stack = entry.castValue();
                if (stack == null || stack.isEmpty()) {
                    return Optional.empty();
                }
                List<BoxReiDisplay> matches = new ArrayList<>();
                for (BoxReiDisplay display : BoxReiDisplay.fromRegistry()) {
                    if (ItemStack.isSameItemSameComponents(stack, display.boxStack())) {
                        matches.add(display);
                    }
                }
                return matches.isEmpty() ? Optional.empty() : Optional.of(matches);
            }
        });
    }

    private static boolean isInPool(BoxDefinition definition, ItemStack stack) {
        for (var grade : definition.grades()) {
            for (ItemStack item : grade.items()) {
                if (ItemStack.isSameItemSameComponents(stack, item)) {
                    return true;
                }
            }
        }
        return false;
    }
}
