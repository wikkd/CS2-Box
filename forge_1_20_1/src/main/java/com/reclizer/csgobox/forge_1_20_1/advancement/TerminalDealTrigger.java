package com.reclizer.csgobox.forge_1_20_1.advancement;

import com.google.gson.JsonObject;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.util.GsonHelper;

/**
 * Triggered when a player closes a deal on a terminal machine (accepted an
 * offer and paid armory points). The {@code count} field is optional: when
 * absent or 0, the instance matches every deal (drives the "first deal"
 * advancement). When positive, it only matches once the player's
 * {@code csgobox:terminal_buys} custom stat has reached that threshold.
 */
public class TerminalDealTrigger extends SimpleCriterionTrigger<TerminalDealTrigger.TriggerInstance> {

    public static final TerminalDealTrigger INSTANCE = new TerminalDealTrigger();
    public static final ResourceLocation ID =
            new ResourceLocation(CsgoBox.MODID, "terminal_deal");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player,
                                          DeserializationContext context) {
        int count = GsonHelper.getAsInt(json, "count", 0);
        return new TriggerInstance(player, count);
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> instance.matches(player));
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {
        private final int count;

        public TriggerInstance(ContextAwarePredicate player, int count) {
            super(ID, player);
            this.count = count;
        }

        public boolean matches(ServerPlayer player) {
            if (count <= 0) {
                return true;
            }
            Stat<ResourceLocation> stat = CsgoBox.TERMINAL_BUYS_STAT;
            if (stat == null) {
                return false;
            }
            return player.getStats().getValue(stat) >= count;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            if (count > 0) {
                json.addProperty("count", count);
            }
            return json;
        }
    }
}
