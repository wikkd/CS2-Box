package com.reclizer.csgobox.forge_1_20_1.item;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.box.GradeGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ItemCsgoBox extends Item {

    private static final ChatFormatting[] TOOLTIP_GRADE_COLORS = {
            ChatFormatting.BLUE,
            ChatFormatting.DARK_BLUE,
            ChatFormatting.DARK_PURPLE,
            ChatFormatting.RED,
            ChatFormatting.GOLD
    };

    public static final String TAG_BOX_ID = "csgobox:box_id";
    public static final String TAG_GRADE = "csgobox:grade";
    public static final String TAG_TERMINAL_UID = "csgobox:terminal_uid";
    public static final String TAG_TERMINAL_OWNER = "csgobox:terminal_owner";

    public ItemCsgoBox(Properties properties) {
        this(properties, 16);
    }

    protected ItemCsgoBox(Properties properties, int maxStack) {
        super(properties.stacksTo(maxStack).rarity(Rarity.EPIC));
    }

    // ---- NBT helpers ----

    public static Optional<BoxDefinition> getDefinition(ItemStack stack) {
        ResourceLocation id = getBoxId(stack);
        return id == null ? Optional.empty() : Optional.ofNullable(BoxRegistry.get(id));
    }

    public static ResourceLocation getBoxId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_BOX_ID)) {
            String idStr = tag.getString(TAG_BOX_ID);
            try {
                return new ResourceLocation(idStr);
            } catch (Exception e) {
                return null;
            }
        }
        if (stack.getItem() instanceof ItemCsgoBox) {
            return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        }
        return null;
    }

    public static Integer getGrade(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_GRADE)) {
            return tag.getInt(TAG_GRADE);
        }
        return null;
    }

    public static void setGrade(ItemStack stack, int grade) {
        stack.getOrCreateTag().putInt(TAG_GRADE, grade);
    }

    public static String getTerminalUid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_TERMINAL_UID)) {
            return tag.getString(TAG_TERMINAL_UID);
        }
        return null;
    }

    public static String ensureTerminalUid(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        String uid = tag.getString(TAG_TERMINAL_UID);
        if (uid == null || uid.isEmpty()) {
            uid = UUID.randomUUID().toString();
            tag.putString(TAG_TERMINAL_UID, uid);
        }
        return uid;
    }

    public static String getTerminalOwner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_TERMINAL_OWNER)) {
            return tag.getString(TAG_TERMINAL_OWNER);
        }
        return null;
    }

    public static void stampTerminalOwner(ItemStack stack, String name) {
        if (name != null && !name.isEmpty()) {
            stack.getOrCreateTag().putString(TAG_TERMINAL_OWNER, name);
        }
    }

    public static ItemStack setBoxId(ResourceLocation boxId, ItemStack stack) {
        if (stack.getItem() instanceof ItemCsgoBox) {
            stack.getOrCreateTag().putString(TAG_BOX_ID, boxId.toString());
            BoxDefinition def = BoxRegistry.get(boxId);
            if (def != null) {
                stack.setHoverName(def.name());
            }
        }
        return stack;
    }

    // ---- Open screen (client-side entry, overridden by ItemTerminal) ----

    /**
     * Client-side open entry: plays the open sound and opens the classic crate
     * screen (Shift → bulk overview). The terminal machine overrides this in
     * {@link ItemTerminal}. Only called from {@code ClickEvent} on the client;
     * never invoke on a dedicated server. The actual screen code lives in
     * {@link com.reclizer.csgobox.forge_1_20_1.gui.BoxScreenOpener} so server-side class loading stays client-free.
     */
    public void openScreen(ItemStack stack) {
        com.reclizer.csgobox.forge_1_20_1.gui.BoxScreenOpener.openClassic(stack);
    }

    public static int[] getRandom(ItemStack stack) {
        return getDefinition(stack)
                .map(BoxDefinition::getWeightArray)
                .orElseGet(() -> BoxGrades.DEFAULT_WEIGHTS.clone());
    }

    public static Map<ItemStack, Integer> getItemGroup(ItemStack stack) {
        Map<ItemStack, Integer> itemsMap = new LinkedHashMap<>();
        getDefinition(stack).ifPresent(def -> {
            for (GradeGroup grade : def.grades()) {
                int gradeLevel = BoxGrades.gradeLevel(grade.id());
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

    public static ResourceLocation getKey(ItemStack stack) {
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

    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.Level level,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltips.csgobox.item.cs_box").withStyle(ChatFormatting.GRAY));
        getDefinition(stack).ifPresent(def -> {
            for (int i = 0; i < def.grades().size(); i++) {
                GradeGroup grade = def.grades().get(i);
                ChatFormatting color = i < TOOLTIP_GRADE_COLORS.length ? TOOLTIP_GRADE_COLORS[i] : ChatFormatting.WHITE;
                for (ItemStack itemStack : grade.items()) {
                    tooltipComponents.add(itemStack.getItem().getName(itemStack).copy().withStyle(color));
                }
            }
            if (def.grades().size() >= BoxGrades.GRADE_COUNT) {
                tooltipComponents.add(Component.translatable("gui.csgobox.csgo_box.label_gold").withStyle(ChatFormatting.YELLOW));
            }
        });
    }
}
