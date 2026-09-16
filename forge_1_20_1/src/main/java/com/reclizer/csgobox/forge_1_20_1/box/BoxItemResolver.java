package com.reclizer.csgobox.forge_1_20_1.box;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * v2.0.1 open-time item resolution: applies the {@code csgobox:item_spec}
 * marker carried by box JSON items that cannot be fixed at load time —
 * count ranges ({@code {"c":[min,max]}}), random enchant
 * ({@code {"e":...}}) and loot-table references ({@code {"l":"ns:path"}}).
 *
 * <p>Server-authoritative: only the server resolves specs (the open handler
 * and the terminal buy handler), so crafted packets cannot force a reroll or
 * a specific loot outcome. The strip preview shows the parsed base item
 * (minimum count / base model / barrel placeholder).</p>
 *
 * <p>1.20.1 port: the marker lives in the item's {@code CompoundTag} under
 * {@link ItemCsgoBox#TAG_ITEM_SPEC} (and {@link ItemCsgoBox#TAG_LOOT_TABLE}
 * for loot entries) instead of a data component.</p>
 */
public final class BoxItemResolver {

    private BoxItemResolver() {
    }

    /**
     * Resolves the spec of {@code stack} in place and returns it. Plain items
     * (no marker) pass through unchanged. Loot-table entries return a random
     * draw of the referenced table (possibly multiple stacks merged into the
     * first slot; empty tables return an empty stack).
     */
    public static ItemStack resolve(ItemStack stack, ServerLevel level, Random rng) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }
        String spec = BoxItemCodec.readItemSpec(stack);
        if (spec == null || spec.isBlank()) {
            return stack;
        }
        try {
            JsonObject obj = JsonParser.parseString(spec).getAsJsonObject();
            // Loot table first: it fully replaces the placeholder stack.
            if (obj.has("l")) {
                return resolveLootTable(obj.get("l").getAsString(), level, rng);
            }
            if (obj.has("c")) {
                JsonArray range = obj.get("c").getAsJsonArray();
                int min = range.get(0).getAsInt();
                int max = range.get(1).getAsInt();
                int count = min + (max > min ? rng.nextInt(max - min + 1) : 0);
                stack.setCount(count);
            }
            if (obj.has("e")) {
                applyRandomEnchant(stack, obj.get("e"), rng);
            }
            stack.removeTagKey(ItemCsgoBox.TAG_ITEM_SPEC);
            stack.removeTagKey(ItemCsgoBox.TAG_LOOT_TABLE);
            return stack;
        } catch (Exception e) {
            CsgoBox.LOGGER.warn("Failed to resolve item spec '{}': {}", spec, e.getMessage());
            stack.removeTagKey(ItemCsgoBox.TAG_ITEM_SPEC);
            stack.removeTagKey(ItemCsgoBox.TAG_LOOT_TABLE);
            return stack;
        }
    }

    /** Rolls the loot table once. Returns the first stack (or a merged stack
     *  when multiple items drop); an empty draw yields an empty stack. */
    private static ItemStack resolveLootTable(String tableId, ServerLevel level, Random rng) {
        try {
            ResourceLocation rl = new ResourceLocation(tableId);
            LootTable table = level.getServer().getLootData()
                    .getElement(new LootDataId<>(LootDataType.TABLE, rl));
            if (table == null) {
                return ItemStack.EMPTY;
            }
            LootParams params = new LootParams.Builder(level).create(LootTable.DEFAULT_PARAM_SET);
            ObjectArrayList<ItemStack> drops = table.getRandomItems(params, rng.nextLong());
            if (drops == null || drops.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack first = drops.get(0).copy();
            for (int i = 1; i < drops.size(); i++) {
                if (!first.isEmpty()) {
                    if (ItemStack.isSameItemSameTags(first, drops.get(i))) {
                        int added = Math.min(first.getMaxStackSize() - first.getCount(), drops.get(i).getCount());
                        first.grow(added);
                    }
                    // different items in one draw: keep only the first (the
                    // box grants one item slot per roll).
                }
            }
            return first;
        } catch (Exception e) {
            CsgoBox.LOGGER.warn("Loot table '{}' failed to resolve: {}", tableId, e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    /**
     * Applies a random enchantment to {@code stack}: pick a random enchantment
     * that can enchant it, roll a level in [minLevel, maxLevel] (or the
     * requested range/level), and add it to the item's enchantments. 1.20.1
     * uses the flat {@link ForgeRegistries#ENCHANTMENTS} registry and
     * {@link ItemStack#enchant}.
     */
    private static void applyRandomEnchant(ItemStack stack, com.google.gson.JsonElement enchantSpec, Random rng) {
        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment ench : ForgeRegistries.ENCHANTMENTS.getValues()) {
            if (ench != null && ench.canEnchant(stack)) {
                candidates.add(ench);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        Enchantment pick = candidates.get(rng.nextInt(candidates.size()));
        int minLevel = pick.getMinLevel();
        int maxLevel = pick.getMaxLevel();
        if (enchantSpec.isJsonObject()) {
            JsonObject eo = enchantSpec.getAsJsonObject();
            try {
                if (eo.has("level") && eo.get("level").isJsonArray()) {
                    JsonArray lr = eo.get("level").getAsJsonArray();
                    int lo = Math.max(minLevel, lr.get(0).getAsInt());
                    int hi = Math.min(maxLevel, lr.get(1).getAsInt());
                    if (hi < lo) hi = lo;
                    minLevel = lo;
                    maxLevel = hi;
                } else if (eo.has("level") && eo.get("level").isJsonPrimitive()) {
                    int lvl = eo.get("level").getAsInt();
                    minLevel = Math.max(minLevel, Math.min(maxLevel, lvl));
                    maxLevel = minLevel;
                }
                if (eo.has("id")) {
                    // Restrict to the requested enchantment id when present.
                    String wantId = eo.get("id").getAsString();
                    Enchantment match = null;
                    for (Enchantment ench : candidates) {
                        ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(ench);
                        if (key != null && key.toString().equals(wantId) && ench.canEnchant(stack)) {
                            match = ench;
                            break;
                        }
                    }
                    if (match == null) {
                        return;
                    }
                    pick = match;
                    minLevel = pick.getMinLevel();
                    maxLevel = pick.getMaxLevel();
                    if (eo.has("level")) {
                        if (eo.get("level").isJsonArray()) {
                            JsonArray lr = eo.get("level").getAsJsonArray();
                            int lo = Math.max(minLevel, lr.get(0).getAsInt());
                            int hi = Math.min(maxLevel, lr.get(1).getAsInt());
                            if (hi < lo) hi = lo;
                            minLevel = lo;
                            maxLevel = hi;
                        } else if (eo.get("level").isJsonPrimitive()) {
                            int lvl = eo.get("level").getAsInt();
                            minLevel = Math.max(minLevel, Math.min(maxLevel, lvl));
                            maxLevel = minLevel;
                        }
                    }
                }
            } catch (Exception ignored) {
                // fall back to the full random range
            }
        }
        int level = minLevel + (maxLevel > minLevel ? rng.nextInt(maxLevel - minLevel + 1) : 0);
        stack.enchant(pick, level);
    }
}
