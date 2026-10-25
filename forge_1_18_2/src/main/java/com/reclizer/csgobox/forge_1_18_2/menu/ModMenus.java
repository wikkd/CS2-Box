package com.reclizer.csgobox.forge_1_18_2.menu;

import com.reclizer.csgobox.forge_1_18_2.CsgoBox;
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
            DeferredRegister.create(ForgeRegistries.CONTAINERS, CsgoBox.MODID);

    public static final RegistryObject<MenuType<ArmoryRecyclerMenu>> ARMORY_RECYCLER =
            MENUS.register("armory_recycler", () ->
                    new MenuType<>(ArmoryRecyclerMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
