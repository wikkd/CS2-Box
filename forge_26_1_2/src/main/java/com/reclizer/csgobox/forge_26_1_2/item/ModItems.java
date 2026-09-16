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
                entries.accept(ModItems.ITEM_CSGO_KEY_COPPER.get());
                entries.accept(ModItems.ITEM_ARMORY_POINT.get());
                entries.accept(ModItems.ITEM_TERMINAL.get());
                entries.accept(ModBlocks.ARMORY_RECYCLER_ITEM.get());

                for (BoxDefinition def : BoxRegistry.getAll()) {
                    // Fixed item when the box id ships with the mod, generic
                    // csgo_box / terminal otherwise — box identity travels in
                    // the csgobox:box_id component, never in the registry.
                    ItemStack stack = new ItemStack(itemForBox(def.id(), def.isTerminal()));
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
    public static final Supplier<Item> ITEM_CSGO_KEY_COPPER = ITEMS.register("csgo_key_copper", () -> new ItemCsgoKey(itemProperties("csgo_key_copper")));
    public static final Supplier<Item> ITEM_ARMORY_POINT = ITEMS.register("armory_point", () -> new Item(itemProperties("armory_point").rarity(Rarity.COMMON)));
    /** Terminal machine — statically registered so it always exists even
     *  without a {@code terminal.json}; the loot pool (BoxDefinition) still
     *  comes from {@code config/csbox/terminal.json} at server start, and the
     *  dynamic item registration skips this id ({@code containsKey}). */
    public static final Supplier<Item> ITEM_TERMINAL = ITEMS.register("terminal", () -> new ItemTerminal(itemProperties("terminal")));

    // ===== Fixed box items (v2.0.1, registry-safety hotfix) =====
    // The item registry is synced over the network and frozen before login, so
    // its contents MUST NOT depend on the local config/csbox/ folder: doing so
    // made a client and server registry diverge and killed the join with
    // Forge's "Failed to synchronize registry data from server".
    // Boxes are data now — the box identity lives in the csgobox:box_id
    // component plus the server-synced BoxRegistry. Only the ids that ship
    // with the mod keep a dedicated item (so /give csgobox:gun_crate keeps
    // working and old stacks stay valid); custom boxes use the generic
    // csgo_box / terminal item and are handed out with /csbox give.
    public static final Supplier<Item> ITEM_AMMO_CRATE = fixedBoxItem("ammo_crate", false);
    public static final Supplier<Item> ITEM_ATTACHMENT_CRATE = fixedBoxItem("attachment_crate", false);
    public static final Supplier<Item> ITEM_GUN_CRATE = fixedBoxItem("gun_crate", false);
    public static final Supplier<Item> ITEM_NORMAL_CRATE = fixedBoxItem("normal_crate", false);
    public static final Supplier<Item> ITEM_TACZ_TERMINAL = fixedBoxItem("tacz_terminal", true);

    /**
     * Registers a compile-time box item whose default instance carries its own
     * box id in the {@code csgobox:box_id} component. 26.x resolves an item's
     * model from {@code assets/csgobox/items/<id>.json} (stamped from the
     * registry id), so these ids ship one each — the 1.20.1 port remaps the
     * baked model instead, since it has no per-stack model component.
     */
    private static Supplier<Item> fixedBoxItem(String id, boolean terminal) {
        Identifier boxId = Identifier.fromNamespaceAndPath(CsgoBox.MODID, id);
        return ITEMS.register(id, () -> terminal
                ? new ItemTerminal(itemProperties(id)) {
                    @Override
                    public ItemStack getDefaultInstance() {
                        return ItemCsgoBox.setBoxId(boxId, super.getDefaultInstance());
                    }
                }
                : new ItemCsgoBox(itemProperties(id)) {
                    @Override
                    public ItemStack getDefaultInstance() {
                        return ItemCsgoBox.setBoxId(boxId, super.getDefaultInstance());
                    }
                });
    }

    /**
     * Item to hand out for a box definition: the fixed shipped item when the
     * box id is one that ships with the mod, otherwise the generic
     * {@code csgo_box} / {@code terminal} item (identity travels in the
     * {@code csgobox:box_id} component). Used by {@code /csbox give} and the
     * creative tab.
     */
    public static Item itemForBox(Identifier boxId, boolean terminal) {
        Item fixed = BuiltInRegistries.ITEM.get(boxId)
                .map(Holder.Reference::value)
                .orElse(null);
        if (fixed instanceof ItemCsgoBox) {
            return fixed;
        }
        return terminal ? ITEM_TERMINAL.get() : ITEM_CSGOBOX.get();
    }

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
