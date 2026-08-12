package com.reclizer.csgobox.forge_26_1_2.item;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.forge_26_1_2.CsgoBox;
import com.reclizer.csgobox.forge_26_1_2.box.BoxDefinition;
import com.reclizer.csgobox.forge_26_1_2.box.BoxRegistry;
import com.reclizer.csgobox.forge_26_1_2.box.GradeGroup;
import com.reclizer.csgobox.forge_26_1_2.gui.BoxScreenOpener;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    /**
     * Rarity grade (1=consumer .. 5=classified) stamped onto the opened item
     * when it is granted to the player. The Armory Recycler reads this to
     * decide how many Armory Points an item is worth, so recycling stays
     * rarity-accurate without re-deriving grade from the (ambiguous) item id.
     */
    public static final Supplier<DataComponentType<Integer>> GRADE =
            BOX_DATA_COMPONENTS.register("grade", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /**
     * Unique per-terminal id, stamped by the server the first time the player
     * opens that terminal. The locked session binds to it so a timeout can
     * destroy THE terminal that was opened (and only that one), even after a
     * rename or a trip through a chest.
     */
    public static final Supplier<DataComponentType<String>> TERMINAL_UID =
            BOX_DATA_COMPONENTS.register("terminal_uid", () ->
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build());

    /**
     * Display name of the player whose live negotiation owns this terminal,
     * stamped by the server when a fresh session is created for the terminal.
     * The dealer reads it when another player opens a locked terminal ("去问问
     * xxx吧"), so the owner's name survives hand-offs, server restarts and the
     * owner being offline without needing a runtime profile lookup.
     */
    public static final Supplier<DataComponentType<String>> TERMINAL_OWNER =
            BOX_DATA_COMPONENTS.register("terminal_owner", () ->
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build());

    public static void registerDataComponents(BusGroup bus) {
        BOX_DATA_COMPONENTS.register(bus);
    }

    public ItemCsgoBox(Properties properties) {
        this(properties, 16);
    }

    /** Terminal machines are unstackable — every terminal owns its own uid/lock. */
    protected ItemCsgoBox(Properties properties, int maxStack) {
        super(properties.stacksTo(maxStack).rarity(Rarity.EPIC));
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

    /** The terminal's unique id, or null when never opened (e.g. a fresh /give). */
    public static String getTerminalUid(ItemStack stack) {
        return stack.get(TERMINAL_UID.get());
    }

    /** Returns the terminal's unique id, stamping a fresh one on first use. */
    public static String ensureTerminalUid(ItemStack stack) {
        String uid = stack.get(TERMINAL_UID.get());
        if (uid == null) {
            uid = UUID.randomUUID().toString();
            stack.set(TERMINAL_UID.get(), uid);
        }
        return uid;
    }

    /** The stamped owner name of the terminal, or null when never opened. */
    public static String getTerminalOwner(ItemStack stack) {
        return stack.get(TERMINAL_OWNER.get());
    }

    /** Stamp the terminal's owner name (kept current on every fresh session). */
    public static void stampTerminalOwner(ItemStack stack, String name) {
        if (name != null && !name.isEmpty()) {
            stack.set(TERMINAL_OWNER.get(), name);
        }
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

    /**
     * Client-side open entry: plays the open sound and opens the classic crate
     * screen (Shift → bulk overview). The terminal machine overrides this in
     * {@link ItemTerminal}. Only called from {@code ClickEvent} on the client;
     * never invoke on a dedicated server. The actual screen code lives in
     * {@link BoxScreenOpener} so server-side class loading stays client-free.
     */
    public void openScreen(ItemStack stack) {
        BoxScreenOpener.openClassic(stack);
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
            if (def.grades().size() >= BoxGrades.GRADE_COUNT) {
                tooltipComponents.accept(Component.translatable("gui.csgobox.csgo_box.label_gold").withStyle(ChatFormatting.YELLOW));
            }
        });
    }
}
