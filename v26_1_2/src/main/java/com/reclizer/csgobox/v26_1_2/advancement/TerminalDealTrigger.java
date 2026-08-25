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
 * Triggered when a player closes a deal on a terminal machine (accepted an
 * offer and paid armory points). The {@code count} field is optional: when
 * absent or 0, the instance matches every deal (drives the "first deal"
 * advancement). When positive, it only matches once the player's
 * {@code csgobox:terminal_buys} custom stat has reached that threshold.
 */
public class TerminalDealTrigger extends SimpleCriterionTrigger<TerminalDealTrigger.TriggerInstance> {

    public static final TerminalDealTrigger INSTANCE = new TerminalDealTrigger();
    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "terminal_deal");

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> instance.matches(player));
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
            Stat<Identifier> stat = CsgoBox.TERMINAL_BUYS_STAT;
            if (stat == null) {
                return false;
            }
            return player.getStats().getValue(stat) >= count;
        }
    }
}
