package com.reclizer.csgobox.forge_26_1_2.item;

import com.reclizer.csgobox.forge_26_1_2.CsgoBox;
import com.reclizer.csgobox.forge_26_1_2.block.ModBlocks;
import com.reclizer.csgobox.forge_26_1_2.box.BoxDefinition;
import com.reclizer.csgobox.forge_26_1_2.box.BoxRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModItems {
    private ModItems() {
    }

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CsgoBox.MODID);

    public static void registerTab(BusGroup eventBus) {
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
                entries.accept(ModBlocks.ARMORY_RECYCLER_ITEM.get());

                for (BoxDefinition def : BoxRegistry.getAll()) {
                    // Resolve the item registered for this box id: dynamic
                    // items are registered under config/csbox file names, so
                    // terminal-type boxes surface as ItemTerminal. Fall back to
                    // the plain box item when no matching item exists (e.g. a
                    // definition added by /csbox reload before a restart).
                    Item item = BuiltInRegistries.ITEM.get(def.id())
                            .map(Holder.Reference::value)
                            .orElse(ModItems.ITEM_CSGOBOX.get());
                    ItemStack stack = new ItemStack(item);
                    ItemCsgoBox.setBoxId(def.id(), stack);
                    entries.accept(stack);
                }
            })
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .build());

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CsgoBox.MODID);
    public static final Supplier<Item> ITEM_CSGOBOX = ITEMS.register("csgo_box", () -> new ItemCsgoBox(itemProperties("csgo_box")));
    public static final Supplier<Item> ITEM_CSGO_KEY0 = ITEMS.register("csgo_key0", () -> new ItemCsgoKey(itemProperties("csgo_key0")));
    public static final Supplier<Item> ITEM_CSGO_KEY1 = ITEMS.register("csgo_key1", () -> new ItemCsgoKey(itemProperties("csgo_key1")));
    public static final Supplier<Item> ITEM_CSGO_KEY2 = ITEMS.register("csgo_key2", () -> new ItemCsgoKey(itemProperties("csgo_key2")));
    public static final Supplier<Item> ITEM_CSGO_KEY3 = ITEMS.register("csgo_key3", () -> new ItemCsgoKey(itemProperties("csgo_key3")));
    public static final Supplier<Item> ITEM_ARMORY_POINT = ITEMS.register("armory_point", () -> new Item(itemProperties("armory_point").rarity(Rarity.COMMON)));

    // NOTE: the village-exclusive "premium_supply_box" item was permanently
    // removed on 2026-08-19. Do NOT reintroduce it.

    /** Forge 26.1 requires the item id to be set on Properties before construction. */
    private static Item.Properties itemProperties(String name) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(CsgoBox.MODID, name)));
    }


    public static void register(BusGroup eventBus) {
        ITEMS.register(eventBus);
    }
}
