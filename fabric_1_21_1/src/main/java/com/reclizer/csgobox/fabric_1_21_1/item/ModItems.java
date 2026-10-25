package com.reclizer.csgobox.fabric_1_21_1.item;

import com.google.common.base.Suppliers;
import com.reclizer.csgobox.fabric_1_21_1.CsgoBox;
import com.reclizer.csgobox.fabric_1_21_1.block.ModBlocks;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.function.Supplier;

public final class ModItems {
    private ModItems() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, path);
    }

    private static Supplier<Item> registerItem(String name, Supplier<? extends Item> factory) {
        ResourceLocation itemId = id(name);
        return Suppliers.memoize(() -> Registry.register(BuiltInRegistries.ITEM, itemId, factory.get()));
    }

    public static final Supplier<Item> ITEM_CSGOBOX = registerItem("csgo_box", ItemCsgoBox::new);
    public static final Supplier<Item> ITEM_CSGO_KEY0 = registerItem("csgo_key0", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_CSGO_KEY1 = registerItem("csgo_key1", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_CSGO_KEY2 = registerItem("csgo_key2", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_CSGO_KEY3 = registerItem("csgo_key3", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_CSGO_KEY_COPPER = registerItem("csgo_key_copper", ItemCsgoKey::new);
    public static final Supplier<Item> ITEM_ARMORY_POINT = registerItem("armory_point",
            () -> new Item(new Item.Properties().rarity(Rarity.COMMON)));
    /** Terminal machine — statically registered so it always exists even
     *  without a {@code terminal.json}; the loot pool (BoxDefinition) still
     *  comes from {@code config/csbox/terminal.json} at server start. */
    public static final Supplier<Item> ITEM_TERMINAL = registerItem("terminal", ItemTerminal::new);

    // ===== Fixed box items (v2.0.1, registry-safety hotfix) =====
    // The item registry is synced over the network and frozen at startup, so
    // its contents MUST NOT depend on the local config/csbox/ folder. Boxes are
    // data now — the box identity lives in the csgobox:box_id data component
    // plus the server-synced BoxRegistry. Only the ids that ship with the mod
    // keep a dedicated item; custom boxes use the generic csgo_box / terminal
    // item and are handed out with /csbox give.
    public static final Supplier<Item> ITEM_AMMO_CRATE = fixedBoxItem("ammo_crate", false);
    public static final Supplier<Item> ITEM_ATTACHMENT_CRATE = fixedBoxItem("attachment_crate", false);
    public static final Supplier<Item> ITEM_GUN_CRATE = fixedBoxItem("gun_crate", false);
    public static final Supplier<Item> ITEM_NORMAL_CRATE = fixedBoxItem("normal_crate", false);
    public static final Supplier<Item> ITEM_TACZ_TERMINAL = fixedBoxItem("tacz_terminal", true);

    // Declared AFTER the item suppliers (vanilla CreativeModeTab.builder takes
    // an explicit Row/column on Fabric; NeoForge's no-arg overload does not).
    public static final Supplier<CreativeModeTab> EQUIPMENT_TAB = Suppliers.memoize(() -> {
        CreativeModeTab tab = FabricItemGroup.builder()
                .title(Component.translatable("itemGroup." + CsgoBox.MODID + ".cs_tab"))
                .icon(() -> new ItemStack(ITEM_CSGO_KEY0.get()))
                .displayItems((enabledFeatures, entries) -> {
                    entries.accept(ITEM_CSGOBOX.get());
                    entries.accept(ITEM_CSGO_KEY0.get());
                    entries.accept(ITEM_CSGO_KEY1.get());
                    entries.accept(ITEM_CSGO_KEY2.get());
                    entries.accept(ITEM_CSGO_KEY3.get());
                    entries.accept(ITEM_CSGO_KEY_COPPER.get());
                    entries.accept(ITEM_ARMORY_POINT.get());
                    entries.accept(ITEM_TERMINAL.get());
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
                .build();
        return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id(CsgoBox.MODID), tab);
    });

    /** Registers a compile-time box item whose default instance carries its
     *  own box id in the {@code csgobox:box_id} data component. */
    private static Supplier<Item> fixedBoxItem(String name, boolean terminal) {
        ResourceLocation boxId = id(name);
        return registerItem(name, () -> terminal
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
        Item fixed = BuiltInRegistries.ITEM.get(boxId);
        if (fixed instanceof ItemCsgoBox) {
            return fixed;
        }
        return terminal ? ITEM_TERMINAL.get() : ITEM_CSGOBOX.get();
    }

    // NOTE: the village-exclusive "premium_supply_box" item was permanently
    // removed on 2026-08-19. Do NOT reintroduce it.

    /** Eager registration during mod init (mirrors the DeferredRegister flush). */
    public static void register() {
        ITEM_CSGOBOX.get();
        ITEM_CSGO_KEY0.get();
        ITEM_CSGO_KEY1.get();
        ITEM_CSGO_KEY2.get();
        ITEM_CSGO_KEY3.get();
        ITEM_CSGO_KEY_COPPER.get();
        ITEM_ARMORY_POINT.get();
        ITEM_TERMINAL.get();
        ITEM_AMMO_CRATE.get();
        ITEM_ATTACHMENT_CRATE.get();
        ITEM_GUN_CRATE.get();
        ITEM_NORMAL_CRATE.get();
        ITEM_TACZ_TERMINAL.get();
    }

    public static void registerTab() {
        EQUIPMENT_TAB.get();
    }
}
