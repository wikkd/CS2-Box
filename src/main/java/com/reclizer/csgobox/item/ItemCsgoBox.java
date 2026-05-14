package com.reclizer.csgobox.item;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;
import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.utils.ItemNBT;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;

import java.util.*;

import static net.minecraft.nbt.Tag.TAG_STRING;

public class ItemCsgoBox extends Item {
    public ItemCsgoBox() {
        super(new Properties().stacksTo(16).rarity(Rarity.EPIC));
    }

    @Override
    public boolean canPerformAction(ItemStack stack, net.neoforged.neoforge.common.ItemAbility itemAbility) {
        return false;
    }

    public static int[] getRandom(ItemStack stack) {
        BoxInfo info = getBoxInfo(stack);
        int[] array = {2, 5, 25, 125, 625};
        if (info != null && info.boxRandom != null && info.boxRandom.length > 4) {
            array = info.boxRandom;
        }
        return array;
    }

    public static ListTag getItemName(ItemStack stack) {
        ListTag item = null;
        try {
            item = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getList("items_list", TAG_STRING);
        } catch (Exception exception) {
        }
        return item;
    }

    public static int[] getItemGrade(ItemStack stack) {
        int[] value = new int[2];
        try {
            value = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntArray("items_grade");
        } catch (Exception exception) {
        }
        return value;
    }

    public static Map<ItemStack, Integer> getItemGroup(ItemStack stack) {
        Map<ItemStack, Integer> ItemsMap = new LinkedHashMap<>();
        BoxInfo info = getBoxInfo(stack);

        if (info != null) {
            if (info.grade1 != null && info.grade1.size() > 0) {
                for (int i = 0; i < info.grade1.size(); i++) {
                    ItemsMap.put(ItemNBT.getStacks(info.grade1.get(i)), 1);
                }
            }
            if (info.grade2 != null && info.grade2.size() > 0) {
                for (int i = 0; i < info.grade2.size(); i++) {
                    ItemsMap.put(ItemNBT.getStacks(info.grade2.get(i)), 2);
                }
            }
            if (info.grade3 != null && info.grade3.size() > 0) {
                for (int i = 0; i < info.grade3.size(); i++) {
                    ItemsMap.put(ItemNBT.getStacks(info.grade3.get(i)), 3);
                }
            }
            if (info.grade4 != null && info.grade4.size() > 0) {
                for (int i = 0; i < info.grade4.size(); i++) {
                    ItemsMap.put(ItemNBT.getStacks(info.grade4.get(i)), 4);
                }
            }
            if (info.grade5 != null && info.grade5.size() > 0) {
                for (int i = 0; i < info.grade5.size(); i++) {
                    ItemsMap.put(ItemNBT.getStacks(info.grade5.get(i)), 5);
                }
            }
        }

        return ItemsMap;
    }

    public static String getKey(ItemStack stack) {
        BoxInfo info = getBoxInfo(stack);
        if (info != null && info.boxKey != null) {
            return info.boxKey;
        }
        return null;
    }

    public static final String BOX_INFO_TAG = "BoxItemInfo";

    public static BoxInfo getBoxInfo(ItemStack stack) {
        if (stack.getItem() == ModItems.ITEM_CSGOBOX.get()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag != null && tag.contains(BOX_INFO_TAG, Tag.TAG_COMPOUND)) {
                CompoundTag infoTag = tag.getCompound(BOX_INFO_TAG);
                return BoxInfo.deserializeNBT(infoTag);
            }
        }
        return null;
    }

    public static ItemStack setBoxInfo(BoxInfo info, ItemStack stack) {
        if (stack.getItem() == ModItems.ITEM_CSGOBOX.get()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag == null) {
                tag = new CompoundTag();
            }
            CompoundTag infoTag = new CompoundTag();
            BoxInfo.serializeNBT(info, infoTag);
            tag.put(BOX_INFO_TAG, infoTag);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        BoxInfo info = getBoxInfo(stack);
        if (info != null) {
            String name = info.boxName;
            return Component.literal(name);
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        BoxInfo info = getBoxInfo(stack);
        tooltipComponents.add(Component.translatable("tooltips.csgobox.item.cs_box").withStyle(ChatFormatting.GRAY));
        if (info != null) {
            List<String> item = info.grade1;
            for (String value : item) {
                ItemStack itemStack = ItemNBT.getStacks(value);
                Component component = itemStack.getItem().getName(itemStack);
                MutableComponent mutableComponent = component.copy();
                tooltipComponents.add(mutableComponent.withStyle(ChatFormatting.BLUE));
            }
            List<String> item2 = info.grade2;
            for (String value : item2) {
                ItemStack itemStack = ItemNBT.getStacks(value);
                Component component = itemStack.getItem().getName(itemStack);
                MutableComponent mutableComponent = component.copy();
                tooltipComponents.add(mutableComponent.withStyle(ChatFormatting.DARK_BLUE));
            }
            List<String> item3 = info.grade3;
            for (String value : item3) {
                ItemStack itemStack = ItemNBT.getStacks(value);
                Component component = itemStack.getItem().getName(itemStack);
                MutableComponent mutableComponent = component.copy();
                tooltipComponents.add(mutableComponent.withStyle(ChatFormatting.DARK_PURPLE));
            }
            List<String> item4 = info.grade4;
            for (String value : item4) {
                ItemStack itemStack = ItemNBT.getStacks(value);
                Component component = itemStack.getItem().getName(itemStack);
                MutableComponent mutableComponent = component.copy();
                tooltipComponents.add(mutableComponent.withStyle(ChatFormatting.RED));
            }
            tooltipComponents.add(Component.translatable("gui.csgobox.csgo_box.label_gold").withStyle(ChatFormatting.YELLOW));
        }
    }

    public static class BoxInfo {

        @SerializedName("name")
        public String boxName;
        @SerializedName("drop")
        public float dropRandom;
        @SerializedName("key")
        public String boxKey;
        @SerializedName("random")
        public int[] boxRandom;
        @SerializedName("grade1")
        public List<String> grade1 = Lists.newArrayList();
        @SerializedName("grade2")
        public List<String> grade2 = Lists.newArrayList();
        @SerializedName("grade3")
        public List<String> grade3 = Lists.newArrayList();
        @SerializedName("grade4")
        public List<String> grade4 = Lists.newArrayList();
        @SerializedName("grade5")
        public List<String> grade5 = Lists.newArrayList();
        @SerializedName("entity")
        public List<String> dropEntity = Lists.newArrayList();

        public BoxInfo() {
        }

        public BoxInfo(CompoundTag tag) {
            this.boxName = tag.getString("name");

            if (tag.contains("key", TAG_STRING)) {
                this.boxKey = tag.getString("key");
            }

            if (tag.contains("drop", Tag.TAG_FLOAT)) {
                this.dropRandom = tag.getFloat("drop");
            }

            if (tag.contains("random", Tag.TAG_INT_ARRAY)) {
                this.boxRandom = tag.getIntArray("random");
            }
            if (tag.contains("grade1", Tag.TAG_LIST)) {
                ListTag tagList = tag.getList("grade1", Tag.TAG_STRING);
                this.grade1 = Lists.newArrayList();
                tagList.forEach(nbt -> this.grade1.add(nbt.getAsString()));
            }
            if (tag.contains("grade2", Tag.TAG_LIST)) {
                ListTag tagList = tag.getList("grade2", Tag.TAG_STRING);
                this.grade2 = Lists.newArrayList();
                tagList.forEach(nbt -> this.grade2.add(nbt.getAsString()));
            }
            if (tag.contains("grade3", Tag.TAG_LIST)) {
                ListTag tagList = tag.getList("grade3", Tag.TAG_STRING);
                this.grade3 = Lists.newArrayList();
                tagList.forEach(nbt -> this.grade3.add(nbt.getAsString()));
            }
            if (tag.contains("grade4", Tag.TAG_LIST)) {
                ListTag tagList = tag.getList("grade4", Tag.TAG_STRING);
                this.grade4 = Lists.newArrayList();
                tagList.forEach(nbt -> this.grade4.add(nbt.getAsString()));
            }
            if (tag.contains("grade5", Tag.TAG_LIST)) {
                ListTag tagList = tag.getList("grade5", Tag.TAG_STRING);
                this.grade5 = Lists.newArrayList();
                tagList.forEach(nbt -> this.grade5.add(nbt.getAsString()));
            }

            if (tag.contains("entity", Tag.TAG_LIST)) {
                ListTag tagList = tag.getList("entity", Tag.TAG_STRING);
                this.dropEntity = Lists.newArrayList();
                tagList.forEach(nbt -> this.dropEntity.add(nbt.getAsString()));
            }
        }

        public static BoxInfo deserializeNBT(CompoundTag tag) {
            return new BoxInfo(tag);
        }

        public static void serializeNBT(BoxInfo info, CompoundTag tag) {
            if (info.boxName != null) {
                tag.putString("name", info.boxName);
            }
            if (info.boxKey != null) {
                tag.putString("key", info.boxKey);
            }
            tag.putFloat("drop", info.dropRandom);
            tag.putIntArray("random", info.boxRandom);

            if (info.grade1 != null && !info.grade1.isEmpty()) {
                ListTag nbt = new ListTag();
                info.grade1.forEach(name -> nbt.add(StringTag.valueOf(name)));
                tag.put("grade1", nbt);
            }

            if (info.grade2 != null && !info.grade2.isEmpty()) {
                ListTag nbt = new ListTag();
                info.grade2.forEach(name -> nbt.add(StringTag.valueOf(name)));
                tag.put("grade2", nbt);
            }

            if (info.grade3 != null && !info.grade3.isEmpty()) {
                ListTag nbt = new ListTag();
                info.grade3.forEach(name -> nbt.add(StringTag.valueOf(name)));
                tag.put("grade3", nbt);
            }

            if (info.grade4 != null && !info.grade4.isEmpty()) {
                ListTag nbt = new ListTag();
                info.grade4.forEach(name -> nbt.add(StringTag.valueOf(name)));
                tag.put("grade4", nbt);
            }

            if (info.grade5 != null && !info.grade5.isEmpty()) {
                ListTag nbt = new ListTag();
                info.grade5.forEach(name -> nbt.add(StringTag.valueOf(name)));
                tag.put("grade5", nbt);
            }

            if (info.dropEntity != null && !info.dropEntity.isEmpty()) {
                ListTag nbt = new ListTag();
                info.dropEntity.forEach(name -> nbt.add(StringTag.valueOf(name)));
                tag.put("entity", nbt);
            }
        }
    }
}