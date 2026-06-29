package com.reclizer.csgobox.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reclizer.csgobox.CsgoBox;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Always-true trigger fired once when a player joins a world.
 * Sole purpose: drive csgobox:root tab discovery so the achievements page
 * shows a CS2 Box tab. Replaces the unreliable minecraft:tick-based root.
 */
public class ModLoadedTrigger extends SimpleCriterionTrigger<ModLoadedTrigger.TriggerInstance> {

    public static final ModLoadedTrigger INSTANCE = new ModLoadedTrigger();
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "mod_loaded");

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> true);
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
