package com.reclizer.csgobox.v1_21_1.menu;

import com.reclizer.csgobox.v1_21_1.CsgoBox;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Registers the mod's {@code MenuType}s. The single menu so far backs the
 * armory recycler GUI (see {@code ArmoryRecyclerMenu}).
 */
public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CsgoBox.MODID);

    public static final Supplier<MenuType<ArmoryRecyclerMenu>> ARMORY_RECYCLER =
            MENUS.register("armory_recycler", () ->
                    new MenuType<>(ArmoryRecyclerMenu::new, FeatureFlags.VANILLA_SET));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
