package com.reclizer.csgobox.forge_26_2.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.forge_26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_26_2.item.ModItems;
import java.util.Set;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Loot {@link LootItemFunction} behind the 26.x arms-dealer's pooled box /
 * terminal sales (Forge 26.2 flavour).
 *
 * <p>The pooled {@code villager_trade} JSON references this function under
 * {@code "given_item_modifiers": [{"function": "csgobox:villager_box",
 * "terminal": false}]}: it replaces the template item with the villager's
 * assigned box / terminal from {@link VillagerBoxSell} (deterministic
 * per-UUID pick — stable across level-ups) and stamps
 * {@code csgobox:box_id}. An empty pool returns an empty stack, which drops
 * the whole trade (verified {@code VillagerTrade.getOffer} null path) — no
 * more selling unbound empty boxes.</p>
 *
 * <p>The trade's cost side uses {@link ArmsDealerBoxPriceProvider}, which
 * derives the same offer via the same shared resolve path, so the item and
 * the price always agree.</p>
 */
public record ArmsDealerBoxFunction(boolean terminal) implements LootItemFunction {

    public static final MapCodec<ArmsDealerBoxFunction> MAP_CODEC =
            Codec.BOOL.fieldOf("terminal")
                    .xmap(ArmsDealerBoxFunction::new, ArmsDealerBoxFunction::terminal);

    @Override
    public MapCodec<? extends LootItemFunction> codec() {
        return MAP_CODEC;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY);
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        Entity entity = context.hasParameter(LootContextParams.THIS_ENTITY)
                ? context.getParameter(LootContextParams.THIS_ENTITY)
                : null;
        if (!(entity instanceof Villager villager)) {
            return ItemStack.EMPTY;
        }
        VillagerBoxSell.Offer offer = VillagerBoxSell.resolve(
                villager.getUUID().getLeastSignificantBits(), terminal,
                ArmsDealerPriceProvider.config(), PriceTableRegistry.get());
        if (offer == null) {
            return ItemStack.EMPTY;
        }
        ItemStack box = new ItemStack(ModItems.itemForBox(offer.boxId(), offer.terminal()), 1);
        ItemCsgoBox.setBoxId(offer.boxId(), box);
        return box;
    }
}
