package com.reclizer.csgobox.v26_2.item;

import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.v26_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_2.box.BoxRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

                // Terminal creative entry is bound to its own decoupled box
                // definition (csgobox:terminal) so the terminal UI shows its
                // own loot list; if that config is missing it falls back to
                // the first registered box, and on a pure client with an
                // empty local BoxRegistry it degrades to an unbound terminal.
                ItemStack terminalStack = new ItemStack(ModItems.ITEM_TERMINAL.get());
                BoxDefinition terminalDef = BoxRegistry.get(Identifier.parse("csgobox:terminal"));
                if (terminalDef == null) {
                    terminalDef = BoxRegistry.getAll().stream().findFirst().orElse(null);
                }
                if (terminalDef != null) {
                    terminalStack.set(ItemCsgoBox.BOX_ID.get(), terminalDef.id());
                }
                entries.accept(terminalStack);

                // Village-exclusive premium case: bound to its own decoupled
                // box definition like the terminal; a missing config falls
                // back to the item's registry id at open time.
                ItemStack premiumStack = new ItemStack(ModItems.ITEM_PREMIUM_BOX.get());
                BoxDefinition premiumDef = BoxRegistry.get(Identifier.parse("csgobox:premium_supply_box"));
                if (premiumDef == null) {
                    premiumDef = BoxRegistry.getAll().stream().findFirst().orElse(null);
                }
                if (premiumDef != null) {
                    premiumStack.set(ItemCsgoBox.BOX_ID.get(), premiumDef.id());
                }
                entries.accept(premiumStack);

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
    public static final Supplier<Item> ITEM_TERMINAL = ITEMS.registerItem("terminal", ItemTerminal::new, p -> p);
    public static final Supplier<Item> ITEM_PREMIUM_BOX = ITEMS.registerItem("premium_supply_box", ItemPremiumBox::new, p -> p);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
