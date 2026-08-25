package com.reclizer.csgobox.forge_1_20_1.advancement;

import com.google.gson.JsonObject;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Triggered when a player's terminal negotiation collapses: all 5 offers
 * rejected, the terminal machine self-destructs. Unconditional — drives
 * the hidden "deal's off" consolation advancement.
 */
public class TerminalBrokeTrigger extends SimpleCriterionTrigger<TerminalBrokeTrigger.TriggerInstance> {

    public static final TerminalBrokeTrigger INSTANCE = new TerminalBrokeTrigger();
    public static final ResourceLocation ID =
            new ResourceLocation(CsgoBox.MODID, "terminal_broke");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player,
                                          DeserializationContext context) {
        return new TriggerInstance(player);
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> true);
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {
        public TriggerInstance(ContextAwarePredicate player) {
            super(ID, player);
        }
    }
}
