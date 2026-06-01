package com.reclizer.csgobox.item;

import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.api.box.BoxDefinition;
import com.reclizer.csgobox.api.box.BoxRegistry;
import com.reclizer.csgobox.api.box.GradeGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.*;
import java.util.function.Supplier;

public class ItemCsgoBox extends Item {

    public static final DeferredRegister<DataComponentType<?>> BOX_DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CsgoBox.MODID);

    public static final Supplier<DataComponentType<ResourceLocation>> BOX_ID =
            BOX_DATA_COMPONENTS.register("box_id", () ->
                    DataComponentType.<ResourceLocation>builder()
                            .persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC)
                            .build());

    public static void registerDataComponents(IEventBus bus) {
        BOX_DATA_COMPONENTS.register(bus);
    }

    public ItemCsgoBox() {
        super(new Properties().stacksTo(16).rarity(Rarity.EPIC));
    }

    @Override
    public boolean canPerformAction(ItemStack stack, net.neoforged.neoforge.common.ItemAbility itemAbility) {
        return false;
    }

    public static BoxDefinition getDefinition(ItemStack stack) {
        if (stack.getItem() instanceof ItemCsgoBox) {
            ResourceLocation id = stack.get(BOX_ID.get());
            if (id != null) {
                return BoxRegistry.get(id);
            }
        }
        return null;
    }

    public static ResourceLocation getBoxId(ItemStack stack) {
        if (stack.getItem() instanceof ItemCsgoBox) {
            return stack.get(BOX_ID.get());
        }
        return null;
    }

    public static ItemStack setBoxId(ResourceLocation boxId, ItemStack stack) {
        if (stack.getItem() instanceof ItemCsgoBox) {
            stack.set(BOX_ID.get(), boxId);
        }
        return stack;
    }

    public static int[] getRandom(ItemStack stack) {
        BoxDefinition def = getDefinition(stack);
        if (def != null) {
            return def.getWeightArray();
        }
        return new int[]{625, 125, 25, 5, 2};
    }

    public static Map<ItemStack, Integer> getItemGroup(ItemStack stack) {
        Map<ItemStack, Integer> itemsMap = new LinkedHashMap<>();
        BoxDefinition def = getDefinition(stack);
        if (def != null) {
            int gradeLevel = def.grades().size();
            for (GradeGroup grade : def.grades()) {
                for (ItemStack item : grade.items()) {
                    if (!item.isEmpty()) {
                        itemsMap.put(item, gradeLevel);
                    }
                }
                gradeLevel--;
            }
        }
        return itemsMap;
    }

    public static ResourceLocation getKey(ItemStack stack) {
        BoxDefinition def = getDefinition(stack);
        if (def != null) {
            return def.keyItem();
        }
        return null;
    }

    @Override
    public Component getName(ItemStack stack) {
        BoxDefinition def = getDefinition(stack);
        if (def != null) {
            return def.name();
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        BoxDefinition def = getDefinition(stack);
        tooltipComponents.add(Component.translatable("tooltips.csgobox.item.cs_box").withStyle(ChatFormatting.GRAY));
        if (def != null) {
            ChatFormatting[] gradeColors = {
                    ChatFormatting.BLUE,
                    ChatFormatting.DARK_BLUE,
                    ChatFormatting.DARK_PURPLE,
                    ChatFormatting.RED,
                    ChatFormatting.GOLD
            };
            for (int i = 0; i < def.grades().size(); i++) {
                GradeGroup grade = def.grades().get(i);
                ChatFormatting color = i < gradeColors.length ? gradeColors[i] : ChatFormatting.WHITE;
                for (ItemStack itemStack : grade.items()) {
                    MutableComponent mutableComponent = itemStack.getItem().getName(itemStack).copy();
                    tooltipComponents.add(mutableComponent.withStyle(color));
                }
                if (i == def.grades().size() - 1 && i >= 4) {
                }
            }
            if (def.grades().size() >= 5) {
                tooltipComponents.add(Component.translatable("gui.csgobox.csgo_box.label_gold").withStyle(ChatFormatting.YELLOW));
            }
        }
    }
}
