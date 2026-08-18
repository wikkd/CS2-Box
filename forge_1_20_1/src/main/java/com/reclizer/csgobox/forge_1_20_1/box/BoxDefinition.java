package com.reclizer.csgobox.forge_1_20_1.box;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reclizer.csgobox.box.BoxGrades;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record BoxDefinition(
        ResourceLocation id,
        Component name,
        String type,
        ResourceLocation keyItem,
        float dropRate,
        List<ResourceLocation> dropEntities,
        List<GradeGroup> grades,
        Optional<ResourceLocation> texture,
        Optional<ResourceLocation> sound,
        Map<ResourceLocation, Float> entityDropRates
) {

    private static final ResourceLocation NO_KEY = new ResourceLocation("minecraft:air");

    private static final Codec<Component> COMPONENT_CODEC = Codec.STRING.xmap(
            Component.Serializer::fromJson,
            Component.Serializer::toJson
    );

    public static final Codec<BoxDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(BoxDefinition::id),
            COMPONENT_CODEC.fieldOf("name").forGetter(BoxDefinition::name),
            Codec.STRING.optionalFieldOf("type", "csbox").forGetter(BoxDefinition::type),
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

    public BoxDefinition {
        id = Objects.requireNonNull(id, "box id");
        name = Objects.requireNonNull(name, "box name");
        type = (type == null || type.isBlank()) ? "csbox" : type;
        keyItem = keyItem == null ? NO_KEY : keyItem;
        dropRate = BoxGrades.clampDropRate(dropRate);
        dropEntities = dropEntities == null ? List.of() : List.copyOf(dropEntities);
        grades = grades == null ? List.of() : List.copyOf(grades);
        texture = texture == null ? Optional.empty() : texture;
        sound = sound == null ? Optional.empty() : sound;
        entityDropRates = entityDropRates == null ? Map.of() : Map.copyOf(entityDropRates);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeComponent(name);
        buf.writeUtf(type);
        buf.writeResourceLocation(keyItem);
        buf.writeFloat(dropRate);
        buf.writeVarInt(dropEntities.size());
        for (ResourceLocation entityId : dropEntities) {
            buf.writeResourceLocation(entityId);
        }
        buf.writeVarInt(grades.size());
        for (GradeGroup grade : grades) {
            grade.encode(buf);
        }
        buf.writeBoolean(texture.isPresent());
        texture.ifPresent(buf::writeResourceLocation);
        buf.writeBoolean(sound.isPresent());
        sound.ifPresent(buf::writeResourceLocation);

        Map<ResourceLocation, Float> entityRates = entityDropRates;
        if (entityRates.size() > BoxGrades.MAX_ENTITY_DROP_RATES) {
            throw new IllegalArgumentException("Too many entity drop rates: " + entityRates.size());
        }
        buf.writeVarInt(entityRates.size());
        for (Map.Entry<ResourceLocation, Float> entry : entityRates.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeFloat(entry.getValue());
        }
    }

    public static BoxDefinition decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        Component name = buf.readComponent();
        String type = buf.readUtf();
        ResourceLocation keyItem = buf.readResourceLocation();
        float dropRate = buf.readFloat();
        int dropEntitiesSize = buf.readVarInt();
        List<ResourceLocation> dropEntities = new ArrayList<>(dropEntitiesSize);
        for (int i = 0; i < dropEntitiesSize; i++) {
            dropEntities.add(buf.readResourceLocation());
        }
        int gradesSize = buf.readVarInt();
        List<GradeGroup> grades = new ArrayList<>(gradesSize);
        for (int i = 0; i < gradesSize; i++) {
            grades.add(GradeGroup.decode(buf));
        }
        Optional<ResourceLocation> texture = buf.readBoolean() ? Optional.of(buf.readResourceLocation()) : Optional.empty();
        Optional<ResourceLocation> sound = buf.readBoolean() ? Optional.of(buf.readResourceLocation()) : Optional.empty();

        int entityRatesSize = buf.readVarInt();
        Map<ResourceLocation, Float> entityDropRates = new HashMap<>();
        for (int i = 0; i < entityRatesSize; i++) {
            ResourceLocation entityId = buf.readResourceLocation();
            entityDropRates.put(entityId, buf.readFloat());
        }
        return new BoxDefinition(id, name, type, keyItem, dropRate, dropEntities, grades, texture, sound, entityDropRates);
    }

    public static Builder builder(ResourceLocation id, String name) {
        return new Builder(id, name);
    }

    public boolean isTerminal() {
        return "terminal".equals(type);
    }

    public String type() {
        return type;
    }

    public float getDropRateForEntity(ResourceLocation entityType) {
        Float entityRate = entityDropRates.get(entityType);
        return Math.min(entityRate != null ? entityRate : dropRate, 1.0F);
    }

    public int[] getWeightArray() {
        int[] weights = new int[BoxGrades.GRADE_COUNT];
        for (GradeGroup grade : grades) {
            int gradeLevel = BoxGrades.gradeLevel(grade.id());
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
        return new BoxDefinition(id, name, type, keyItem, dropRate, dropEntities, newGrades, texture, sound, entityDropRates);
    }

    public static class Builder {
        private final ResourceLocation id;
        private Component name;
        private OptionalInt nameColor = OptionalInt.empty();
        private String type = "csbox";
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

        public Builder type(String type) {
            this.type = (type == null || type.isBlank()) ? "csbox" : type;
            return this;
        }

        public Builder nameColor(int rgb) {
            this.nameColor = OptionalInt.of(rgb & 0xFFFFFF);
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
                this.dropEntities.add(new ResourceLocation(entity));
            }
            return this;
        }

        public Builder entityDropRate(String entityId, float rate) {
            this.entityDropRates.put(new ResourceLocation(entityId), BoxGrades.clampDropRate(rate));
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
            Component finalName = name;
            if (nameColor.isPresent()) {
                int argb = 0xFF000000 | nameColor.getAsInt();
                finalName = name.copy().withStyle(s -> s.withColor(argb));
            }
            return new BoxDefinition(id, finalName, type, keyItem, dropRate,
                    List.copyOf(dropEntities), List.copyOf(grades), texture, sound,
                    Map.copyOf(entityDropRates));
        }
    }
}
