package com.reclizer.csgobox.forge_26_2.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reclizer.csgobox.forge_26_2.CsgoBox;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;

import java.util.Optional;

/**
 * Triggered when a player opens any CS:GO Box. The {@code count} field on
 * {@link TriggerInstance} is optional: when absent or 0, the trigger fires
 * unconditionally (drives the "first box" advancement). When set to a
 * positive integer, the instance only matches once the player's
 * {@code csgobox:opened_boxes} custom stat has reached that threshold
 * (drives the "shopper" advancement at count=200).
 */
public class OpenedBoxTrigger extends SimpleCriterionTrigger<OpenedBoxTrigger.TriggerInstance> {

    public static final OpenedBoxTrigger INSTANCE = new OpenedBoxTrigger();
    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "opened_box");

    public static final Identifier STAT_ID =
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "opened_boxes");

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, (java.util.function.Predicate<TriggerInstance>) instance -> instance.matches(player));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, int count) implements SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ContextAwarePredicate.CODEC.optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        Codec.INT.optionalFieldOf("count", 0)
                                .forGetter(TriggerInstance::count)
                ).apply(instance, TriggerInstance::new)
        );

        public boolean matches(ServerPlayer player) {
            if (count <= 0) {
                return true;
            }
            Stat<Identifier> stat = CsgoBox.OPENED_BOXES_STAT;
            if (stat == null) {
                return false;
            }
            return player.getStats().getValue(stat) >= count;
        }
    }
}
