package com.reclizer.csgobox.v1_21_1.item;

import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.block.ModBlocks;
import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
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
                entries.accept(ModItems.ITEM_CSGO_KEY_COPPER.get());
                entries.accept(ModItems.ITEM_ARMORY_POINT.get());
                entries.accept(ModItems.ITEM_TERMINAL.get());
                entries.accept(ModBlocks.ARMORY_RECYCLER_ITEM.get());

                for (BoxDefinition def : BoxRegistry.getAll()) {
                    // Fixed item when the box id ships with the mod, generic
                    // csgo_box / terminal otherwise — box identity travels in
                    // the csgobox:box_id data component, never in the registry.
                    ItemStack stack = new ItemStack(itemForBox(def.id(), def.isTerminal()));
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
    public static final Supplier<Item> ITEM_CSGO_KEY_COPPER = ITEMS.register("csgo_key_copper", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_ARMORY_POINT = ITEMS.register("armory_point", () -> new Item(new Item.Properties().rarity(Rarity.COMMON)));
    /** Terminal machine — statically registered so it always exists even
     *  without a {@code terminal.json}; the loot pool (BoxDefinition) still
     *  comes from {@code config/csbox/terminal.json} at server start, and the
     *  dynamic item registration skips this id ({@code containsKey}). */
    public static final Supplier<Item> ITEM_TERMINAL = ITEMS.register("terminal", ItemTerminal::new);

    // ===== Fixed box items (v2.1.0, registry-safety hotfix) =====
    // The item registry is synced over the network and frozen at startup, so
    // its contents MUST NOT depend on the local config/csbox/ folder: doing so
    // made a client and server registry diverge and killed the connection with
    // NeoForge's "Failed to synchronize registry data from server".
    // Boxes are data now — the box identity lives in the csgobox:box_id data
    // component plus the server-synced BoxRegistry. Only the ids that ship
    // with the mod keep a dedicated item (so /give csgobox:gun_crate keeps
    // working and old stacks stay valid); custom boxes use the generic
    // csgo_box / terminal item and are handed out with /csbox give.
    public static final Supplier<Item> ITEM_AMMO_CRATE = fixedBoxItem("ammo_crate", false);
    public static final Supplier<Item> ITEM_ATTACHMENT_CRATE = fixedBoxItem("attachment_crate", false);
    public static final Supplier<Item> ITEM_GUN_CRATE = fixedBoxItem("gun_crate", false);
    public static final Supplier<Item> ITEM_NORMAL_CRATE = fixedBoxItem("normal_crate", false);
    public static final Supplier<Item> ITEM_TACZ_TERMINAL = fixedBoxItem("tacz_terminal", true);

    /** Registers a compile-time box item whose default instance carries its
     *  own box id in the {@code csgobox:box_id} data component. */
    private static Supplier<Item> fixedBoxItem(String id, boolean terminal) {
        ResourceLocation boxId = ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, id);
        return ITEMS.register(id, () -> terminal
                ? new ItemTerminal() {
                    @Override
                    public ItemStack getDefaultInstance() {
                        return ItemCsgoBox.setBoxId(boxId, super.getDefaultInstance());
                    }
                }
                : new ItemCsgoBox() {
                    @Override
                    public ItemStack getDefaultInstance() {
                        return ItemCsgoBox.setBoxId(boxId, super.getDefaultInstance());
                    }
                });
    }

    /**
     * Item to hand out for a box definition: the fixed legacy item when the box
     * id is one that ships with the mod, otherwise the generic
     * {@code csgo_box} / {@code terminal} item (identity travels in the
     * {@code csgobox:box_id} data component). Used by {@code /csbox give} and
     * the creative tab.
     */
    public static Item itemForBox(ResourceLocation boxId, boolean terminal) {
        // NeoForge 1.21.1: BuiltInRegistries.ITEM is the item registry
        // ({@code ForgeRegistries.ITEMS} on the 1.20.1 module); it defaults to
        // AIR, which is not an ItemCsgoBox, so the fallback below is safe.
        Item fixed = BuiltInRegistries.ITEM.get(boxId);
        if (fixed instanceof ItemCsgoBox) {
            return fixed;
        }
        return terminal ? ITEM_TERMINAL.get() : ITEM_CSGOBOX.get();
    }

    // NOTE: the village-exclusive "premium_supply_box" item was permanently
    // removed on 2026-08-19. Do NOT reintroduce it.

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
