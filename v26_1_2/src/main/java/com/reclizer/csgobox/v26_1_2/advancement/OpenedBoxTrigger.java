package com.reclizer.csgobox.v26_1_2.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reclizer.csgobox.v26_1_2.CsgoBox;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
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
 * (drives the "shopper" advancement at count=200). The {@code grade}
 * field is also optional: when positive, the instance only matches when
 * the unboxed item's grade equals it (drives the grade-line advancements).
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
        trigger(player, 0);
    }

    public void trigger(ServerPlayer player, int grade) {
        this.trigger(player, instance -> instance.matches(player, grade));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, int count, int grade) implements SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ContextAwarePredicate.CODEC.optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        Codec.INT.optionalFieldOf("count", 0)
                                .forGetter(TriggerInstance::count),
                        Codec.INT.optionalFieldOf("grade", 0)
                                .forGetter(TriggerInstance::grade)
                ).apply(instance, TriggerInstance::new)
        );

        public boolean matches(ServerPlayer player, int actualGrade) {
            if (grade > 0 && grade != actualGrade) {
                return false;
            }
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
