package com.reclizer.csgobox.v26_1_2.item;

import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_1_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_1_2.box.GradeGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ItemCsgoBox extends Item {

    private static final ChatFormatting[] TOOLTIP_GRADE_COLORS = {
            ChatFormatting.BLUE,
            ChatFormatting.DARK_BLUE,
            ChatFormatting.DARK_PURPLE,
            ChatFormatting.RED,
            ChatFormatting.GOLD
    };

    public static final DeferredRegister<DataComponentType<?>> BOX_DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CsgoBox.MODID);

    public static final Supplier<DataComponentType<Identifier>> BOX_ID =
            BOX_DATA_COMPONENTS.register("box_id", () ->
                    DataComponentType.<Identifier>builder()
                            .persistent(Identifier.CODEC)
                            .networkSynchronized(Identifier.STREAM_CODEC)
                            .build());

    public static void registerDataComponents(IEventBus bus) {
        BOX_DATA_COMPONENTS.register(bus);
    }

    public ItemCsgoBox(Properties properties) {
        super(properties.stacksTo(16).rarity(Rarity.EPIC));
    }

    public static Optional<BoxDefinition> getDefinition(ItemStack stack) {
        Identifier id = getBoxId(stack);
        return id == null ? Optional.empty() : Optional.ofNullable(BoxRegistry.get(id));
    }

    public static Identifier getBoxId(ItemStack stack) {
        Identifier id = stack.get(BOX_ID.get());
        if (id != null) {
            return id;
        }
        // Fallback for vanilla /give: use the item's own registry id as the
        // default box_id so `/give @p csgobox:csgo_box` Just Works without any
        // components syntax. The player can still override via vanilla
        // components: `/give @p csgobox:csgo_box[csgobox:box_id='"csgobox:..."']`
        if (stack.getItem() instanceof ItemCsgoBox) {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        }
        return null;
    }

    public static ItemStack setBoxId(Identifier boxId, ItemStack stack) {
        if (stack.getItem() instanceof ItemCsgoBox) {
            stack.set(BOX_ID.get(), boxId);
            BoxDefinition def = BoxRegistry.get(boxId);
            if (def != null) {
                stack.set(DataComponents.CUSTOM_NAME, def.name());
            }
        }
        return stack;
    }

    public static int[] getRandom(ItemStack stack) {
        return getDefinition(stack)
                .map(BoxDefinition::getWeightArray)
                .orElseGet(() -> BoxDefinition.DEFAULT_WEIGHTS.clone());
    }

    public static Map<ItemStack, Integer> getItemGroup(ItemStack stack) {
        Map<ItemStack, Integer> itemsMap = new LinkedHashMap<>();
        getDefinition(stack).ifPresent(def -> {
            for (GradeGroup grade : def.grades()) {
                int gradeLevel = BoxDefinition.gradeLevel(grade.id());
                if (gradeLevel == 0) continue;
                for (ItemStack item : grade.items()) {
                    if (!item.isEmpty()) {
                        itemsMap.put(item.copy(), gradeLevel);
                    }
                }
            }
        });
        return itemsMap;
    }

    public static Identifier getKey(ItemStack stack) {
        return getDefinition(stack)
                .map(BoxDefinition::keyItem)
                .orElse(null);
    }

    @Override
    public Component getName(ItemStack stack) {
        return getDefinition(stack)
                .map(BoxDefinition::name)
                .orElseGet(() -> super.getName(stack));
    }

    /** Adds the configured box contents to the item tooltip. */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (display.hideTooltip()) {
            return;
        }
        tooltipComponents.accept(Component.translatable("tooltips.csgobox.item.cs_box").withStyle(ChatFormatting.GRAY));
        getDefinition(stack).ifPresent(def -> {
            for (int i = 0; i < def.grades().size(); i++) {
                GradeGroup grade = def.grades().get(i);
                ChatFormatting color = i < TOOLTIP_GRADE_COLORS.length ? TOOLTIP_GRADE_COLORS[i] : ChatFormatting.WHITE;
                for (ItemStack itemStack : grade.items()) {
                    tooltipComponents.accept(itemStack.getItem().getName(itemStack).copy().withStyle(color));
                }
            }
            if (def.grades().size() >= BoxDefinition.GRADE_COUNT) {
                tooltipComponents.accept(Component.translatable("gui.csgobox.csgo_box.label_gold").withStyle(ChatFormatting.YELLOW));
            }
        });
    }
}
