package com.reclizer.csgobox.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reclizer.csgobox.CsgoBox;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;

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
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "opened_box");

    public static final ResourceLocation STAT_ID =
            ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "opened_boxes");
    public static final Stat<ResourceLocation> STAT;

    static {
        Registry.register(BuiltInRegistries.CUSTOM_STAT, STAT_ID, STAT_ID);
        STAT = Stats.CUSTOM.get(STAT_ID);
    }

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
            return player.getStats().getValue(STAT) >= count;
        }
    }
}
