package com.reclizer.csgobox.v1_21_1.item;

import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
                BoxDefinition terminalDef = BoxRegistry.get(ResourceLocation.parse("csgobox:terminal"));
                if (terminalDef == null) {
                    terminalDef = BoxRegistry.getAll().stream().findFirst().orElse(null);
                }
                if (terminalDef != null) {
                    terminalStack.set(ItemCsgoBox.BOX_ID.get(), terminalDef.id());
                }
                entries.accept(terminalStack);

                for (BoxDefinition def : BoxRegistry.getAll()) {
                    ItemStack stack = new ItemStack(ModItems.ITEM_CSGOBOX.get());
                    ItemCsgoBox.setBoxId(def.id(), stack);
                    entries.accept(stack);
                }
            })
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .build());

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.createItems(CsgoBox.MODID);
    public static final Supplier<Item> ITEM_CSGOBOX = ITEMS.register("csgo_box", ItemCsgoBox::new);
    public static final Supplier<Item> ITEM_CSGO_KEY0 = ITEMS.register("csgo_key0", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_CSGO_KEY1 = ITEMS.register("csgo_key1", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_CSGO_KEY2 = ITEMS.register("csgo_key2", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_CSGO_KEY3 = ITEMS.register("csgo_key3", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_ARMORY_POINT = ITEMS.register("armory_point", () -> new Item(new Item.Properties().rarity(Rarity.COMMON)));
    public static final Supplier<Item> ITEM_TERMINAL = ITEMS.register("terminal", ItemTerminal::new);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
