package com.reclizer.csgobox.forge_26_2.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reclizer.csgobox.forge_26_2.CsgoBox;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Triggered when a player's terminal negotiation collapses: all 5 offers
 * rejected, the terminal machine self-destructs. Unconditional — drives
 * the hidden "deal's off" consolation advancement.
 */
public class TerminalBrokeTrigger extends SimpleCriterionTrigger<TerminalBrokeTrigger.TriggerInstance> {

    public static final TerminalBrokeTrigger INSTANCE = new TerminalBrokeTrigger();
    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(CsgoBox.MODID, "terminal_broke");

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, (java.util.function.Predicate<TriggerInstance>) instance -> true);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ContextAwarePredicate.CODEC.optionalFieldOf("player")
                                .forGetter(TriggerInstance::player)
                ).apply(instance, TriggerInstance::new)
        );
    }
}
