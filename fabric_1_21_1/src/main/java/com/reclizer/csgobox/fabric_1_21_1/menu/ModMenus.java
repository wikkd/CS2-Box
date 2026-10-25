package com.reclizer.csgobox.fabric_1_21_1.menu;

import com.google.common.base.Suppliers;
import com.reclizer.csgobox.fabric_1_21_1.CsgoBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;
import net.minecraft.core.Registry;

/**
 * Registers the mod's {@code MenuType}s. The single menu so far backs the
 * armory recycler GUI (see {@link ArmoryRecyclerMenu}).
 */
public final class ModMenus {

    private ModMenus() {
    }

    public static final Supplier<MenuType<ArmoryRecyclerMenu>> ARMORY_RECYCLER = Suppliers.memoize(() ->
            Registry.register(BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "armory_recycler"),
                    new MenuType<>(ArmoryRecyclerMenu::new, FeatureFlags.VANILLA_SET)));

    public static void register() {
        ARMORY_RECYCLER.get();
    }
}
