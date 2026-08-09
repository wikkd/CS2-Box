package com.reclizer.csgobox.v1_21_11.item;

import com.reclizer.csgobox.v1_21_11.CsgoBox;
import com.reclizer.csgobox.v1_21_11.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_11.box.BoxRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModItems {
    private ModItems() {
    }

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CsgoBox.MODID);

    public static void registerTab(IEventBus eventBus) {
        TABS.register(eventBus);
    }

    public static final Supplier<CreativeModeTab> EQUIPMENT_TAB = TABS.register(CsgoBox.MODID, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + CsgoBox.MODID + ".cs_tab"))
            .icon(() -> new ItemStack(ModItems.ITEM_CSGO_KEY0.get()))
            .displayItems((enabledFeatures, entries) -> {
                entries.accept(ModItems.ITEM_CSGOBOX.get());
                entries.accept(ModItems.ITEM_CSGO_KEY0.get());
                entries.accept(ModItems.ITEM_CSGO_KEY1.get());
                entries.accept(ModItems.ITEM_CSGO_KEY2.get());
                entries.accept(ModItems.ITEM_CSGO_KEY3.get());
                entries.accept(ModItems.ITEM_ARMORY_POINT.get());

                for (BoxDefinition def : BoxRegistry.getAll()) {
                    ItemStack stack = new ItemStack(ModItems.ITEM_CSGOBOX.get());
                    ItemCsgoBox.setBoxId(def.id(), stack);
                    entries.accept(stack);
                }
            })
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .build());

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CsgoBox.MODID);
    public static final Supplier<Item> ITEM_CSGOBOX = ITEMS.registerItem("csgo_box", ItemCsgoBox::new, p -> p);
    public static final Supplier<Item> ITEM_CSGO_KEY0 = ITEMS.registerItem("csgo_key0", ItemCsgoKey::new, p -> p);
    public static final Supplier<Item> ITEM_CSGO_KEY1 = ITEMS.registerItem("csgo_key1", ItemCsgoKey::new, p -> p);
    public static final Supplier<Item> ITEM_CSGO_KEY2 = ITEMS.registerItem("csgo_key2", ItemCsgoKey::new, p -> p);
    public static final Supplier<Item> ITEM_CSGO_KEY3 = ITEMS.registerItem("csgo_key3", ItemCsgoKey::new, p -> p);
    public static final Supplier<Item> ITEM_ARMORY_POINT = ITEMS.registerItem("armory_point", p -> new Item(p.rarity(Rarity.COMMON)), p -> p);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
