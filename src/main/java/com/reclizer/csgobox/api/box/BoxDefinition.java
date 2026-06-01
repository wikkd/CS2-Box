package com.reclizer.csgobox.api.box;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record BoxDefinition(
        ResourceLocation id,
        Component name,
        ResourceLocation keyItem,
        float dropRate,
        List<ResourceLocation> dropEntities,
        List<GradeGroup> grades,
        Optional<ResourceLocation> texture,
        Optional<ResourceLocation> sound,
        Map<ResourceLocation, Float> entityDropRates
) {

    public static final Codec<BoxDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(BoxDefinition::id),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(BoxDefinition::name),
            ResourceLocation.CODEC.fieldOf("key").forGetter(BoxDefinition::keyItem),
            Codec.FLOAT.fieldOf("drop_rate").forGetter(BoxDefinition::dropRate),
            ResourceLocation.CODEC.listOf().fieldOf("drop_entities").forGetter(BoxDefinition::dropEntities),
            GradeGroup.CODEC.listOf().fieldOf("grades").forGetter(BoxDefinition::grades),
            ResourceLocation.CODEC.optionalFieldOf("texture").forGetter(BoxDefinition::texture),
            ResourceLocation.CODEC.optionalFieldOf("sound").forGetter(BoxDefinition::sound),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT).optionalFieldOf("entity_drop_rates", Map.of()).forGetter(BoxDefinition::entityDropRates)
    ).apply(instance, BoxDefinition::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoxDefinition> STREAM_CODEC = StreamCodec.of(
            (buf, def) -> {
                ResourceLocation.STREAM_CODEC.encode(buf, def.id());
                ByteBufCodecs.fromCodec(ComponentSerialization.CODEC).encode(buf, def.name());
                ResourceLocation.STREAM_CODEC.encode(buf, def.keyItem());
                buf.writeFloat(def.dropRate());
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, def.dropEntities());
                GradeGroup.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, def.grades());
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buf, def.texture());
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buf, def.sound());
                Map<ResourceLocation, Float> entityRates = def.entityDropRates();
                buf.writeVarInt(entityRates.size());
                for (Map.Entry<ResourceLocation, Float> entry : entityRates.entrySet()) {
                    ResourceLocation.STREAM_CODEC.encode(buf, entry.getKey());
                    buf.writeFloat(entry.getValue());
                }
            },
            buf -> {
                ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
                Component name = ByteBufCodecs.fromCodec(ComponentSerialization.CODEC).decode(buf);
                ResourceLocation keyItem = ResourceLocation.STREAM_CODEC.decode(buf);
                float dropRate = buf.readFloat();
                List<ResourceLocation> dropEntities = ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                List<GradeGroup> grades = GradeGroup.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                Optional<ResourceLocation> texture = ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buf);
                Optional<ResourceLocation> sound = ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buf);
                int entityRatesSize = buf.readVarInt();
                Map<ResourceLocation, Float> entityDropRates = new HashMap<>();
                for (int i = 0; i < entityRatesSize; i++) {
                    ResourceLocation entityId = ResourceLocation.STREAM_CODEC.decode(buf);
                    float rate = buf.readFloat();
                    entityDropRates.put(entityId, rate);
                }
                return new BoxDefinition(id, name, keyItem, dropRate, dropEntities, grades, texture, sound, entityDropRates);
            }
    );

    public BoxDefinition {
        if (dropEntities == null) {
            dropEntities = List.of();
        }
        if (grades == null) {
            grades = List.of();
        }
        if (entityDropRates == null) {
            entityDropRates = Map.of();
        }
    }

    public static Builder builder(ResourceLocation id, String name) {
        return new Builder(id, name);
    }

    public float getDropRateForEntity(ResourceLocation entityType) {
        if (dropRate > 0) {
            return dropRate;
        }
        Float entityRate = entityDropRates.get(entityType);
        if (entityRate != null && entityRate > 0) {
            return entityRate;
        }
        return 0;
    }

    public int[] getWeightArray() {
        int[] weights = new int[grades.size()];
        for (int i = 0; i < grades.size(); i++) {
            weights[i] = grades.get(i).weight();
        }
        return weights;
    }

    public static class Builder {
        private final ResourceLocation id;
        private Component name;
        private ResourceLocation keyItem = ResourceLocation.parse("minecraft:air");
        private float dropRate = 0.12F;
        private final List<ResourceLocation> dropEntities = new ArrayList<>();
        private final List<GradeGroup> grades = new ArrayList<>();
        private Optional<ResourceLocation> texture = Optional.empty();
        private Optional<ResourceLocation> sound = Optional.empty();
        private final Map<ResourceLocation, Float> entityDropRates = new HashMap<>();

        public Builder(ResourceLocation id, String name) {
            this.id = id;
            this.name = Component.literal(name);
        }

        public Builder name(Component name) {
            this.name = name;
            return this;
        }

        public Builder key(ResourceLocation keyItem) {
            this.keyItem = keyItem;
            return this;
        }

        public Builder dropRate(float rate) {
            this.dropRate = rate;
            return this;
        }

        public Builder dropFrom(String... entities) {
            for (String entity : entities) {
                this.dropEntities.add(ResourceLocation.parse(entity));
            }
            return this;
        }

        public Builder entityDropRate(String entityId, float rate) {
            this.entityDropRates.put(ResourceLocation.parse(entityId), rate);
            return this;
        }

        public Builder addGrade(GradeGroup grade) {
            this.grades.add(grade);
            return this;
        }

        public Builder texture(ResourceLocation texture) {
            this.texture = Optional.of(texture);
            return this;
        }

        public Builder sound(ResourceLocation sound) {
            this.sound = Optional.of(sound);
            return this;
        }

        public BoxDefinition build() {
            return new BoxDefinition(id, name, keyItem, dropRate,
                    List.copyOf(dropEntities), List.copyOf(grades), texture, sound,
                    Map.copyOf(entityDropRates));
        }
    }
}
