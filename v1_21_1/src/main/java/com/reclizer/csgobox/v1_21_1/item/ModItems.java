package com.reclizer.csgobox.v1_21_1.item;

import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.block.ModBlocks;
import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
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
                entries.accept(ModBlocks.ARMORY_RECYCLER_ITEM.get());

                // Registered boxes (config/csbox/*.json) appear here the same
                // way for every kind; the terminal machine and the premium
                // case join through this loop too, so they only show up once
                // their definition is registered (no default terminal config
                // is generated on first run; premium_supply_box.json is).
                for (BoxDefinition def : BoxRegistry.getAll()) {
                    ItemStack stack = new ItemStack(boxItemFor(def).get());
                    ItemCsgoBox.setBoxId(def.id(), stack);
                    entries.accept(stack);
                }
            })
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .build());

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.createItems(CsgoBox.MODID);
    public static final Supplier<Item> ITEM_CSGOBOX = ITEMS.register("csgo_box", () -> new ItemCsgoBox());
    public static final Supplier<Item> ITEM_CSGO_KEY0 = ITEMS.register("csgo_key0", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_CSGO_KEY1 = ITEMS.register("csgo_key1", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_CSGO_KEY2 = ITEMS.register("csgo_key2", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_CSGO_KEY3 = ITEMS.register("csgo_key3", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_ARMORY_POINT = ITEMS.register("armory_point", () -> new Item(new Item.Properties().rarity(Rarity.COMMON)));
    public static final Supplier<Item> ITEM_TERMINAL = ITEMS.register("terminal", () -> new ItemTerminal());
    public static final Supplier<Item> ITEM_PREMIUM_BOX = ITEMS.register("premium_supply_box", ItemPremiumBox::new);

    /**
     * Item class a box definition maps to: the terminal machine uses
     * {@link ItemTerminal}, the village-exclusive premium case uses
     * {@link ItemPremiumBox}, every other box uses the generic
     * {@link ItemCsgoBox}. Shared by the creative tab and mob drops so a
     * definition always yields the same item kind.
     */
    public static Supplier<Item> boxItemFor(BoxDefinition def) {
        if (def.isTerminal()) {
            return ModItems.ITEM_TERMINAL;
        }
        if ("premium_supply_box".equals(def.id().getPath())) {
            return ModItems.ITEM_PREMIUM_BOX;
        }
        return ModItems.ITEM_CSGOBOX;
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
