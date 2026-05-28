package com.reclizer.csgobox.gui;

import com.reclizer.csgobox.CsgoBox;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class RecModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, CsgoBox.MODID);

    public static final Supplier<MenuType<CsgoBoxCraftMenu>> CSGO_BOX_CRAFT = MENUS.register("csgo_box_craft", () -> new MenuType<>((id, inventory) -> new CsgoBoxCraftMenu(id, inventory, null), FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}