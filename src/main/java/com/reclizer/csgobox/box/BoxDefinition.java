package com.reclizer.csgobox.box;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable box definition loaded from JSON and referenced by box ItemStacks.
 */
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

    public static final int GRADE_COUNT = 5;
    public static final int[] DEFAULT_WEIGHTS = new int[]{625, 125, 25, 5, 2};

    private static final ResourceLocation NO_KEY = ResourceLocation.parse("minecraft:air");
    private static final int MAX_DROP_ENTITIES = 256;
    private static final int MAX_GRADES = 16;
    private static final int MAX_ENTITY_DROP_RATES = 256;

    public static final Codec<BoxDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(BoxDefinition::id),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(BoxDefinition::name),
            ResourceLocation.CODEC.fieldOf("key").forGetter(BoxDefinition::keyItem),
            Codec.FLOAT.fieldOf("drop_rate").forGetter(BoxDefinition::dropRate),
            ResourceLocation.CODEC.listOf().fieldOf("drop_entities").forGetter(BoxDefinition::dropEntities),
            GradeGroup.CODEC.listOf().fieldOf("grades").forGetter(BoxDefinition::grades),
            ResourceLocation.CODEC.optionalFieldOf("texture").forGetter(BoxDefinition::texture),
            ResourceLocation.CODEC.optionalFieldOf("sound").forGetter(BoxDefinition::sound),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT)
                    .optionalFieldOf("entity_drop_rates", Map.of())
                    .forGetter(BoxDefinition::entityDropRates)
    ).apply(instance, BoxDefinition::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoxDefinition> STREAM_CODEC = StreamCodec.of(
            BoxDefinition::write,
            BoxDefinition::read
    );

    public BoxDefinition {
        id = Objects.requireNonNull(id, "box id");
        name = Objects.requireNonNull(name, "box name");
        keyItem = keyItem == null ? NO_KEY : keyItem;
        dropRate = Math.clamp(dropRate, 0.0F, 1.0F);
        dropEntities = dropEntities == null ? List.of() : List.copyOf(dropEntities);
        grades = grades == null ? List.of() : List.copyOf(grades);
        texture = texture == null ? Optional.empty() : texture;
        sound = sound == null ? Optional.empty() : sound;
        entityDropRates = entityDropRates == null ? Map.of() : Map.copyOf(entityDropRates);
    }

    private static void write(RegistryFriendlyByteBuf buf, BoxDefinition def) {
        ResourceLocation.STREAM_CODEC.encode(buf, def.id());
        ByteBufCodecs.fromCodec(ComponentSerialization.CODEC).encode(buf, def.name());
        ResourceLocation.STREAM_CODEC.encode(buf, def.keyItem());
        buf.writeFloat(def.dropRate());
        ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_DROP_ENTITIES)).encode(buf, def.dropEntities());
        GradeGroup.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_GRADES)).encode(buf, def.grades());
        ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buf, def.texture());
        ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buf, def.sound());

        Map<ResourceLocation, Float> entityRates = def.entityDropRates();
        if (entityRates.size() > MAX_ENTITY_DROP_RATES) {
            throw new IllegalArgumentException("Too many entity drop rates: " + entityRates.size());
        }
        buf.writeVarInt(entityRates.size());
        for (Map.Entry<ResourceLocation, Float> entry : entityRates.entrySet()) {
            ResourceLocation.STREAM_CODEC.encode(buf, entry.getKey());
            buf.writeFloat(entry.getValue());
        }
    }

    private static BoxDefinition read(RegistryFriendlyByteBuf buf) {
        ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
        Component name = ByteBufCodecs.fromCodec(ComponentSerialization.CODEC).decode(buf);
        ResourceLocation keyItem = ResourceLocation.STREAM_CODEC.decode(buf);
        float dropRate = buf.readFloat();
        List<ResourceLocation> dropEntities = ResourceLocation.STREAM_CODEC
                .apply(ByteBufCodecs.list(MAX_DROP_ENTITIES)).decode(buf);
        List<GradeGroup> grades = GradeGroup.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_GRADES)).decode(buf);
        Optional<ResourceLocation> texture = ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buf);
        Optional<ResourceLocation> sound = ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buf);

        int entityRatesSize = buf.readVarInt();
        if (entityRatesSize < 0 || entityRatesSize > MAX_ENTITY_DROP_RATES) {
            throw new DecoderException("Invalid entity drop rate count: " + entityRatesSize);
        }
        Map<ResourceLocation, Float> entityDropRates = new HashMap<>();
        for (int i = 0; i < entityRatesSize; i++) {
            ResourceLocation entityId = ResourceLocation.STREAM_CODEC.decode(buf);
            entityDropRates.put(entityId, buf.readFloat());
        }
        return new BoxDefinition(id, name, keyItem, dropRate, dropEntities, grades, texture, sound, entityDropRates);
    }

    public static Builder builder(ResourceLocation id, String name) {
        return new Builder(id, name);
    }

    public float getDropRateForEntity(ResourceLocation entityType) {
        Float entityRate = entityDropRates.get(entityType);
        return Math.min(entityRate != null ? entityRate : dropRate, 1.0F);
    }

    public int[] getWeightArray() {
        int[] weights = new int[GRADE_COUNT];
        for (GradeGroup grade : grades) {
            int gradeLevel = gradeLevel(grade.id());
            if (gradeLevel > 0) {
                weights[gradeLevel - 1] = Math.max(0, grade.weight());
            }
        }
        return weights;
    }

    public Optional<GradeGroup> findGrade(String gradeId) {
        for (GradeGroup grade : grades) {
            if (grade.id().equals(gradeId)) {
                return Optional.of(grade);
            }
        }
        return Optional.empty();
    }

    public BoxDefinition withUpdatedGrade(String gradeId, GradeGroup updatedGrade) {
        List<GradeGroup> newGrades = new ArrayList<>(grades.size());
        for (GradeGroup grade : grades) {
            newGrades.add(grade.id().equals(gradeId) ? updatedGrade : grade);
        }
        return new BoxDefinition(id, name, keyItem, dropRate, dropEntities, newGrades, texture, sound, entityDropRates);
    }

    public static int gradeLevel(String id) {
        return switch (id) {
            case "consumer" -> 1;
            case "industrial" -> 2;
            case "mil_spec" -> 3;
            case "restricted" -> 4;
            case "classified" -> 5;
            default -> 0;
        };
    }

    public static class Builder {
        private final ResourceLocation id;
        private Component name;
        private ResourceLocation keyItem = NO_KEY;
        private float dropRate = 0.12F;
        private final List<ResourceLocation> dropEntities = new ArrayList<>();
        private final List<GradeGroup> grades = new ArrayList<>();
        private Optional<ResourceLocation> texture = Optional.empty();
        private Optional<ResourceLocation> sound = Optional.empty();
        private final Map<ResourceLocation, Float> entityDropRates = new HashMap<>();

        public Builder(ResourceLocation id, String name) {
            this.id = Objects.requireNonNull(id, "box id");
            this.name = Component.literal(Objects.requireNonNull(name, "box name"));
        }

        public Builder name(Component name) {
            this.name = Objects.requireNonNull(name, "box name");
            return this;
        }

        public Builder key(ResourceLocation keyItem) {
            this.keyItem = keyItem == null ? NO_KEY : keyItem;
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
            this.entityDropRates.put(ResourceLocation.parse(entityId), Math.clamp(rate, 0.0F, 1.0F));
            return this;
        }

        public Builder addGrade(GradeGroup grade) {
            this.grades.add(Objects.requireNonNull(grade, "grade"));
            return this;
        }

        public Builder texture(ResourceLocation texture) {
            this.texture = Optional.ofNullable(texture);
            return this;
        }

        public Builder sound(ResourceLocation sound) {
            this.sound = Optional.ofNullable(sound);
            return this;
        }

        public BoxDefinition build() {
            return new BoxDefinition(id, name, keyItem, dropRate,
                    List.copyOf(dropEntities), List.copyOf(grades), texture, sound,
                    Map.copyOf(entityDropRates));
        }
    }
}
