package com.reclizer.csgobox.forge_1_18_2.advancement;

import com.google.gson.JsonObject;
import com.reclizer.csgobox.forge_1_18_2.CsgoBox;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.util.GsonHelper;

public class OpenedBoxTrigger extends SimpleCriterionTrigger<OpenedBoxTrigger.TriggerInstance> {

    public static final OpenedBoxTrigger INSTANCE = new OpenedBoxTrigger();
    public static final ResourceLocation ID =
            new ResourceLocation(CsgoBox.MODID, "opened_box");

    public static final ResourceLocation STAT_ID =
            new ResourceLocation(CsgoBox.MODID, "opened_boxes");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public TriggerInstance createInstance(JsonObject json, EntityPredicate.Composite player,
                                          DeserializationContext context) {
        int count = GsonHelper.getAsInt(json, "count", 0);
        int grade = GsonHelper.getAsInt(json, "grade", 0);
        return new TriggerInstance(player, count, grade);
    }

    public void trigger(ServerPlayer player) {
        trigger(player, 0);
    }

    public void trigger(ServerPlayer player, int grade) {
        this.trigger(player, instance -> instance.matches(player, grade));
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {
        private final int count;
        private final int grade;

        public TriggerInstance(EntityPredicate.Composite player, int count, int grade) {
            super(ID, player);
            this.count = count;
            this.grade = grade;
        }

        public boolean matches(ServerPlayer player, int actualGrade) {
            if (grade > 0 && grade != actualGrade) {
                return false;
            }
            if (count <= 0) {
                return true;
            }
            Stat<ResourceLocation> stat = CsgoBox.OPENED_BOXES_STAT;
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
            if (grade > 0) {
                json.addProperty("grade", grade);
            }
            return json;
        }
    }
}
