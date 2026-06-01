package com.reclizer.csgobox.kubejs;

import com.reclizer.csgobox.api.box.BoxDefinition;
import com.reclizer.csgobox.api.box.BoxRegistry;
import com.reclizer.csgobox.api.box.GradeGroup;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class BoxBuilderJS {

    private final ResourceLocation id;
    private String name;
    private ResourceLocation keyItem = ResourceLocation.parse("minecraft:air");
    private float dropRate = 0.12F;
    private final List<ResourceLocation> dropEntities = new ArrayList<>();
    private final List<GradeGroup> grades = new ArrayList<>();
    private Optional<ResourceLocation> texture = Optional.empty();
    private Optional<ResourceLocation> sound = Optional.empty();
    private final Map<ResourceLocation, Float> entityDropRates = new HashMap<>();

    public BoxBuilderJS(String id, String name) {
        this.id = ResourceLocation.parse(id);
        this.name = name;
    }

    public BoxBuilderJS name(String name) {
        this.name = name;
        return this;
    }

    public BoxBuilderJS key(String key) {
        this.keyItem = ResourceLocation.parse(key);
        return this;
    }

    public BoxBuilderJS dropRate(float rate) {
        this.dropRate = rate;
        return this;
    }

    public BoxBuilderJS dropFrom(String... entities) {
        for (String entity : entities) {
            this.dropEntities.add(ResourceLocation.parse(entity));
        }
        return this;
    }

    public BoxBuilderJS entityDropRate(String entityId, float rate) {
        this.entityDropRates.put(ResourceLocation.parse(entityId), rate);
        return this;
    }

    public BoxBuilderJS grade(String id, String displayName, int color, int weight, Consumer<GradeBuilderJS> consumer) {
        GradeBuilderJS gradeBuilder = new GradeBuilderJS(id, displayName, color, weight);
        consumer.accept(gradeBuilder);
        grades.add(gradeBuilder.build());
        return this;
    }

    public BoxBuilderJS texture(String tex) {
        this.texture = Optional.of(ResourceLocation.parse(tex));
        return this;
    }

    public BoxBuilderJS openSound(String soundId) {
        this.sound = Optional.of(ResourceLocation.parse(soundId));
        return this;
    }

    public void build() {
        BoxDefinition def = new BoxDefinition(id,
                net.minecraft.network.chat.Component.literal(name),
                keyItem, dropRate,
                List.copyOf(dropEntities), List.copyOf(grades),
                texture, sound,
                Map.copyOf(entityDropRates));
        BoxRegistry.register(def);
    }
}
