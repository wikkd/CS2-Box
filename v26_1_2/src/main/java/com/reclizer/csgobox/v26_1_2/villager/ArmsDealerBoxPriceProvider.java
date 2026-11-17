package com.reclizer.csgobox.v26_1_2.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reclizer.csgobox.box.PriceTableRegistry;
import com.reclizer.csgobox.villager.VillagerBoxPool;
import java.util.Locale;
import java.util.Set;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

/**
 * Loot {@link NumberProvider} behind the 26.x arms-dealer's pooled box sale
 * costs (NeoForge 26.1.2 flavour).
 *
 * <p>The pool draw is the villager's deterministic box assignment
 * ({@link VillagerBoxSell}, per-UUID — stable across level-ups); this
 * provider prices it. Because a single trade input holds one 64-point stack,
 * expensive boxes split into a points part + gold-ingot change — a trade
 * shape vanilla can't express as one entry, so the JSON ships TWO variants
 * of the box trade and each self-selects via a 0 count (26.x drops a whole
 * trade whose cost resolves &lt; 1, verified {@code VillagerTrade.getOffer}):
 * the cheap variant covers prices ≤ 64, the change variant the rest.</p>
 *
 * <ul>
 *   <li>{@code cheap_points} — the full price when it fits one stack, else 0
 *       (drops this variant);</li>
 *   <li>{@code change_points} — the capped points part (always ≥ 1 for a
 *       sellable price);</li>
 *   <li>{@code change_gold} — the gold-ingot change (0 for cheap boxes →
 *       drops this variant).</li>
 * </ul>
 *
 * <p>The item side uses {@link ArmsDealerBoxFunction}, which derives the same
 * offer via the same shared resolve path, so item and price always agree.</p>
 */
public record ArmsDealerBoxPriceProvider(BoxPart part, boolean terminal) implements NumberProvider {

    /** Which slice of the resolved box price this provider returns. */
    public enum BoxPart {
        CHEAP_POINTS, CHANGE_POINTS, CHANGE_GOLD;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static BoxPart fromId(String id) {
            for (BoxPart p : values()) {
                if (p.id().equals(id)) {
                    return p;
                }
            }
            return null;
        }
    }

    private static final Codec<BoxPart> PART_CODEC = Codec.STRING.flatXmap(
            id -> {
                BoxPart p = BoxPart.fromId(id);
                return p != null
                        ? DataResult.success(p)
                        : DataResult.error(() -> "Unknown box part '" + id + "'");
            },
            p -> DataResult.success(p.id()));

    public static final MapCodec<ArmsDealerBoxPriceProvider> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    PART_CODEC.fieldOf("part").forGetter(ArmsDealerBoxPriceProvider::part),
                    Codec.BOOL.fieldOf("terminal").forGetter(ArmsDealerBoxPriceProvider::terminal)
            ).apply(i, ArmsDealerBoxPriceProvider::new));

    @Override
    public MapCodec<? extends NumberProvider> codec() {
        return MAP_CODEC;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY);
    }

    @Override
    public float getFloat(LootContext context) {
        return resolve(context);
    }

    private int resolve(LootContext context) {
        Entity entity = context.hasParameter(LootContextParams.THIS_ENTITY)
                ? context.getParameter(LootContextParams.THIS_ENTITY)
                : null;
        if (!(entity instanceof Villager villager)) {
            return 0;
        }
        VillagerBoxSell.Offer offer = VillagerBoxSell.resolve(
                villager.getUUID().getLeastSignificantBits(), terminal,
                ArmsDealerPriceProvider.config(), PriceTableRegistry.get());
        if (offer == null) {
            return 0;
        }
        return switch (part) {
            case CHEAP_POINTS -> offer.price() <= VillagerBoxPool.POINT_STACK_LIMIT
                    ? offer.price() : 0;
            case CHANGE_POINTS -> VillagerBoxPool.pointsPart(offer.price());
            case CHANGE_GOLD -> VillagerBoxPool.goldChange(offer.price(), offer.goldBase());
        };
    }
}
