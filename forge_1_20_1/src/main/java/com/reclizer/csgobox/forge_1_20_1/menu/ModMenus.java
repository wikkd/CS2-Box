package com.reclizer.csgobox.forge_1_20_1.menu;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CsgoBox.MODID);

    public static final RegistryObject<MenuType<ArmoryRecyclerMenu>> ARMORY_RECYCLER =
            MENUS.register("armory_recycler", () ->
                    new MenuType<>(ArmoryRecyclerMenu::new, FeatureFlags.VANILLA_SET));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
