package com.reclizer.csgobox.forge_1_20_1.item;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.block.ModBlocks;
import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModItems {
    private ModItems() {
    }

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CsgoBox.MODID);

    public static void registerTab(IEventBus eventBus) {
        TABS.register(eventBus);
    }

    public static final RegistryObject<CreativeModeTab> EQUIPMENT_TAB = TABS.register(CsgoBox.MODID, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + CsgoBox.MODID + ".cs_tab"))
            .icon(() -> new ItemStack(ModItems.ITEM_CSGO_KEY0.get()))
            .displayItems((enabledFeatures, entries) -> {
                entries.accept(ModItems.ITEM_CSGOBOX.get());
                entries.accept(ModItems.ITEM_CSGO_KEY0.get());
                entries.accept(ModItems.ITEM_CSGO_KEY1.get());
                entries.accept(ModItems.ITEM_CSGO_KEY2.get());
                entries.accept(ModItems.ITEM_CSGO_KEY3.get());
                entries.accept(ModItems.ITEM_ARMORY_POINT.get());
                entries.accept(ModBlocks.ARMORY_RECYCLER_ITEM.get());

                for (BoxDefinition def : BoxRegistry.getAll()) {
                    ResourceLocation id = def.id();
                    Item item = ForgeRegistries.ITEMS.getValue(id);
                    if (item == null) {
                        item = ModItems.ITEM_CSGOBOX.get();
                    }
                    ItemStack stack = new ItemStack(item);
                    ItemCsgoBox.setBoxId(def.id(), stack);
                    entries.accept(stack);
                }
            })
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .build());

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CsgoBox.MODID);
    public static final RegistryObject<Item> ITEM_CSGOBOX = ITEMS.register("csgo_box", () -> new ItemCsgoBox(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_CSGO_KEY0 = ITEMS.register("csgo_key0", () -> new ItemCsgoKey(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_CSGO_KEY1 = ITEMS.register("csgo_key1", () -> new ItemCsgoKey(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_CSGO_KEY2 = ITEMS.register("csgo_key2", () -> new ItemCsgoKey(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_CSGO_KEY3 = ITEMS.register("csgo_key3", () -> new ItemCsgoKey(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_ARMORY_POINT = ITEMS.register("armory_point", () -> new Item(new Item.Properties().rarity(Rarity.COMMON)));
    public static final RegistryObject<Item> ITEM_PREMIUM_BOX = ITEMS.register("premium_supply_box", () -> new ItemPremiumBox(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
