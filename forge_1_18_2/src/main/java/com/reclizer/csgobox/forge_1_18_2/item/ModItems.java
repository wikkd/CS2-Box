package com.reclizer.csgobox.forge_1_18_2.item;

import com.reclizer.csgobox.forge_1_18_2.CsgoBox;
import com.reclizer.csgobox.forge_1_18_2.block.ModBlocks;
import com.reclizer.csgobox.forge_1_18_2.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_18_2.box.BoxRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.core.NonNullList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;

import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public final class ModItems {
    private ModItems() {
    }



    // 1.18.2: CreativeModeTab is not a registry — a plain subclass instance,
    // with content assembled in makeIcon + fillItemList overrides. The String
    // ctor derives the title key "itemGroup.<name>", but the shared lang files
    // (every other platform) use "itemGroup.csgobox.cs_tab" — override the
    // display name to that key or the tab shows the raw key.
    public static final CreativeModeTab EQUIPMENT_TAB = new CreativeModeTab(CsgoBox.MODID) {
        @Override
        public net.minecraft.network.chat.Component getDisplayName() {
            return new net.minecraft.network.chat.TranslatableComponent(
                    "itemGroup." + CsgoBox.MODID + ".cs_tab");
        }

        @Override
        public ItemStack makeIcon() {
            return new ItemStack(ModItems.ITEM_CSGO_KEY0.get());
        }

        @Override
        public void fillItemList(NonNullList<ItemStack> items) {
            items.add(new ItemStack(ModItems.ITEM_CSGOBOX.get()));
            items.add(new ItemStack(ModItems.ITEM_CSGO_KEY0.get()));
            items.add(new ItemStack(ModItems.ITEM_CSGO_KEY1.get()));
            items.add(new ItemStack(ModItems.ITEM_CSGO_KEY2.get()));
            items.add(new ItemStack(ModItems.ITEM_CSGO_KEY3.get()));
            items.add(new ItemStack(ModItems.ITEM_CSGO_KEY_COPPER.get()));
            items.add(new ItemStack(ModItems.ITEM_ARMORY_POINT.get()));
            items.add(new ItemStack(ModItems.ITEM_TERMINAL.get()));
            items.add(new ItemStack(ModBlocks.ARMORY_RECYCLER_ITEM.get()));

            for (BoxDefinition def : BoxRegistry.getAll()) {
                // Fixed item when the box id ships with the mod, generic
                // csgo_box / terminal otherwise — box identity travels in
                // the csgobox:box_id NBT tag, never in the registry.
                ItemStack stack = new ItemStack(itemForBox(def.id(), def.isTerminal()));
                ItemCsgoBox.setBoxId(def.id(), stack);
                items.add(stack);
            }
        }
    };

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CsgoBox.MODID);
    public static final RegistryObject<Item> ITEM_CSGOBOX = ITEMS.register("csgo_box", () -> new ItemCsgoBox(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_CSGO_KEY0 = ITEMS.register("csgo_key0", () -> new ItemCsgoKey(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_CSGO_KEY1 = ITEMS.register("csgo_key1", () -> new ItemCsgoKey(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_CSGO_KEY2 = ITEMS.register("csgo_key2", () -> new ItemCsgoKey(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_CSGO_KEY3 = ITEMS.register("csgo_key3", () -> new ItemCsgoKey(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_CSGO_KEY_COPPER = ITEMS.register("csgo_key_copper", () -> new ItemCsgoKey(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_ARMORY_POINT = ITEMS.register("armory_point", () -> new Item(new Item.Properties().rarity(Rarity.COMMON)));
    /** Terminal machine — statically registered so it always exists even
     *  without a {@code terminal.json}; the loot pool (BoxDefinition) still
     *  comes from {@code config/csbox/terminal.json} at server start, and the
     *  dynamic item registration skips this id ({@code containsKey}). */
    public static final RegistryObject<Item> ITEM_TERMINAL = ITEMS.register("terminal", () -> new ItemTerminal(new Item.Properties()));

    // ===== Fixed box items (v2.0.1, registry-safety hotfix) =====
    // The item registry is synced over the network and frozen at startup, so
    // its contents MUST NOT depend on the local config/csbox/ folder: doing so
    // made a client and server registry diverge and killed the connection with
    // Forge's "Failed to synchronize registry data from server".
    // Boxes are data now — the box identity lives in the csgobox:box_id NBT tag
    // plus the server-synced BoxRegistry. Only the ids that ship with the mod
    // keep a dedicated item (so /give csgobox:gun_crate keeps working and old
    // stacks stay valid); custom boxes use the generic csgo_box / terminal
    // item and are handed out with /csbox give.
    public static final RegistryObject<Item> ITEM_AMMO_CRATE = fixedBoxItem("ammo_crate", false);
    public static final RegistryObject<Item> ITEM_ATTACHMENT_CRATE = fixedBoxItem("attachment_crate", false);
    public static final RegistryObject<Item> ITEM_GUN_CRATE = fixedBoxItem("gun_crate", false);
    public static final RegistryObject<Item> ITEM_NORMAL_CRATE = fixedBoxItem("normal_crate", false);
    public static final RegistryObject<Item> ITEM_TACZ_TERMINAL = fixedBoxItem("tacz_terminal", true);

    /** Registers a compile-time box item whose default instance carries its
     *  own box id in NBT. */
    private static RegistryObject<Item> fixedBoxItem(String id, boolean terminal) {
        ResourceLocation boxId = new ResourceLocation(CsgoBox.MODID, id);
        return ITEMS.register(id, () -> terminal
                ? new ItemTerminal(new Item.Properties()) {
                    @Override
                    public ItemStack getDefaultInstance() {
                        return ItemCsgoBox.setBoxId(boxId, super.getDefaultInstance());
                    }
                }
                : new ItemCsgoBox(new Item.Properties()) {
                    @Override
                    public ItemStack getDefaultInstance() {
                        return ItemCsgoBox.setBoxId(boxId, super.getDefaultInstance());
                    }
                });
    }

    /**
     * Item to hand out for a box definition: the fixed legacy item when the box
     * id is one that ships with the mod, otherwise the generic
     * {@code csgo_box} / {@code terminal} item (identity travels in NBT).
     * Used by {@code /csbox give} and the creative tab.
     */
    public static Item itemForBox(ResourceLocation boxId, boolean terminal) {
        Item fixed = ForgeRegistries.ITEMS.getValue(boxId);
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
