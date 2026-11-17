package com.reclizer.csgobox.v26_1_2.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reclizer.csgobox.villager.VillagerTradePool;
import java.util.Set;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Loot {@link LootItemCondition} gating the 26.x arms-dealer's pooled
 * mineral-buy trades (NeoForge 26.1.2 flavour).
 *
 * <p>The pooled {@code villager_trade} JSON references this condition under
 * {@code "merchant_predicate": {"condition": "csgobox:pool_member",
 * "mineral": "minecraft:coal"}}. When a villager rebuilds its offers, a trade
 * whose predicate fails is dropped before the draw, and the draw skips
 * nulls without consuming a slot (decompiled
 * {@code addOffersFromItemListingsWithoutDuplicates}) — so gating composes
 * with vanilla's random pool: the active minerals selected by
 * {@link VillagerTradePool} fill the offer slots while the always-ungated
 * sell trades stay present. {@code pool_enabled=false} makes the condition
 * report exactly the pre-pool fixed minerals as active.</p>
 *
 * <p>Non-villager contexts (the condition is also validatable in other loot
 * tables) are permissive so the trade stays visible for datapack debugging.</p>
 */
public record ArmsDealerPoolCondition(String mineral) implements LootItemCondition {

    public static final MapCodec<ArmsDealerPoolCondition> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.STRING.fieldOf("mineral").forGetter(ArmsDealerPoolCondition::mineral)
            ).apply(i, ArmsDealerPoolCondition::new));

    @Override
    public MapCodec<? extends LootItemCondition> codec() {
        return MAP_CODEC;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY);
    }

    @Override
    public boolean test(LootContext context) {
        if (!context.hasParameter(LootContextParams.THIS_ENTITY)) {
            return true;
        }
        Entity entity = context.getParameter(LootContextParams.THIS_ENTITY);
        if (!(entity instanceof Villager villager)) {
            return true;
        }
        return VillagerTradePool.isActive(
                villager.getUUID().getLeastSignificantBits(),
                villager.getVillagerData().level(),
                mineral,
                ArmsDealerPriceProvider.config().poolEnabled());
    }
}
