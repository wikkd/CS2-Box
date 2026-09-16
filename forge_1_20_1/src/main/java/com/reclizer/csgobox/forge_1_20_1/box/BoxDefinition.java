package com.reclizer.csgobox.forge_1_20_1.box;

import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.logic.PityPolicy;
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

/**
 * Immutable box definition loaded from JSON and referenced by box ItemStacks.
 *
 * <p>v2.0.1 config additions: {@code enabled}/{@code requires} gate loading,
 * {@code icon} sets CustomModelData, {@code discount}/{@code stock}/
 * {@code restock_minutes} drive terminal economy, and {@code max_per_player}/
 * {@code cooldown_seconds}/{@code permission} constrain opening.</p>
 *
 * <p>This record carries no CODEC: the network path uses the manual
 * {@link #encode}/{@link #decode} pair which serializes every field including
 * the v2.0.1 additions (a 17-field {@code RecordCodecBuilder} group exceeds
 * the framework's arity limit and the codec is not used anywhere else).</p>
 */
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
        Map<ResourceLocation, Float> entityDropRates,
        boolean enabled,
        List<String> requires,
        Optional<String> icon,
        float discount,
        int stock,
        int restockMinutes,
        int maxPerPlayer,
        int cooldownSeconds,
        String permission,
        Optional<PityPolicy> pity
) {

    private static final ResourceLocation NO_KEY = new ResourceLocation("minecraft:air");

    /** Sentinel values: -1 = unlimited (stock / maxPerPlayer). */
    public static final int UNLIMITED = -1;

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
        requires = requires == null ? List.of() : List.copyOf(requires);
        icon = icon == null ? Optional.empty() : icon;
        discount = BoxGrades.clampDropRate(discount);
        permission = permission == null ? "" : permission;
        pity = pity == null ? Optional.empty() : pity;
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

        // v2.0.1 fields.
        buf.writeBoolean(enabled);
        buf.writeVarInt(requires.size());
        for (String req : requires) {
            buf.writeUtf(req);
        }
        buf.writeBoolean(icon.isPresent());
        icon.ifPresent(buf::writeUtf);
        buf.writeFloat(discount);
        buf.writeVarInt(stock);
        buf.writeVarInt(restockMinutes);
        buf.writeVarInt(maxPerPlayer);
        buf.writeVarInt(cooldownSeconds);
        buf.writeUtf(permission);
        Optional<PityPolicy> pity = this.pity;
        buf.writeBoolean(pity.isPresent());
        if (pity.isPresent()) {
            buf.writeVarInt(pity.get().targetLevel());
            buf.writeVarInt(pity.get().every());
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

        // v2.0.1 fields.
        boolean enabled = buf.readBoolean();
        int requiresSize = buf.readVarInt();
        List<String> requires = new ArrayList<>(requiresSize);
        for (int i = 0; i < requiresSize; i++) {
            requires.add(buf.readUtf());
        }
        Optional<String> icon = buf.readBoolean() ? Optional.of(buf.readUtf()) : Optional.empty();
        float discount = buf.readFloat();
        int stock = buf.readVarInt();
        int restockMinutes = buf.readVarInt();
        int maxPerPlayer = buf.readVarInt();
        int cooldownSeconds = buf.readVarInt();
        String permission = buf.readUtf();

        Optional<PityPolicy> pity = Optional.empty();
        if (buf.readBoolean()) {
            int targetLevel = buf.readVarInt();
            int every = buf.readVarInt();
            // Invalid combinations (unknown tier, every < 2) silently degrade
            // to no pity — the schema validator reports them at load time.
            pity = Optional.ofNullable(PityPolicy.ofLevel(targetLevel, every));
        }

        return new BoxDefinition(id, name, type, keyItem, dropRate, dropEntities, grades,
                texture, sound, entityDropRates, enabled, requires, icon, discount,
                stock, restockMinutes, maxPerPlayer, cooldownSeconds, permission, pity);
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

    /** Whether any of the {@code requires} mods is missing. */
    public boolean missingRequirement() {
        for (String modId : requires) {
            if (!com.reclizer.csgobox.forge_1_20_1.CsgoBox.isModLoaded(modId)) {
                return true;
            }
        }
        return false;
    }

    /** First missing required mod id, or null when all are loaded. */
    public String firstMissingRequirement() {
        for (String modId : requires) {
            if (!com.reclizer.csgobox.forge_1_20_1.CsgoBox.isModLoaded(modId)) {
                return modId;
            }
        }
        return null;
    }

    /** Terminal price after the configured discount (0..1 off). */
    public int discountedPrice(int basePrice) {
        if (discount <= 0F || basePrice <= 0) {
            return basePrice;
        }
        int price = (int) Math.floor(basePrice * (1.0F - discount));
        return Math.max(1, price);
    }

    public BoxDefinition withUpdatedGrade(String gradeId, GradeGroup updatedGrade) {
        List<GradeGroup> newGrades = new ArrayList<>(grades.size());
        for (GradeGroup grade : grades) {
            newGrades.add(grade.id().equals(gradeId) ? updatedGrade : grade);
        }
        return new BoxDefinition(id, name, type, keyItem, dropRate, dropEntities, newGrades,
                texture, sound, entityDropRates, enabled, requires, icon, discount,
                stock, restockMinutes, maxPerPlayer, cooldownSeconds, permission, pity);
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
        private boolean enabled = true;
        private final List<String> requires = new ArrayList<>();
        private Optional<String> icon = Optional.empty();
        private float discount = 0.0F;
        private int stock = UNLIMITED;
        private int restockMinutes = 0;
        private int maxPerPlayer = UNLIMITED;
        private int cooldownSeconds = 0;
        private String permission = "";
        private Optional<PityPolicy> pity = Optional.empty();

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

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder requires(List<String> requires) {
            if (requires != null) {
                this.requires.addAll(requires);
            }
            return this;
        }

        public Builder icon(String icon) {
            this.icon = icon == null || icon.isBlank() ? Optional.empty() : Optional.of(icon.trim());
            return this;
        }

        public Builder discount(float discount) {
            this.discount = BoxGrades.clampDropRate(discount);
            return this;
        }

        public Builder stock(int stock) {
            this.stock = stock;
            return this;
        }

        public Builder restockMinutes(int restockMinutes) {
            this.restockMinutes = Math.max(0, restockMinutes);
            return this;
        }

        public Builder maxPerPlayer(int maxPerPlayer) {
            this.maxPerPlayer = maxPerPlayer;
            return this;
        }

        public Builder cooldownSeconds(int cooldownSeconds) {
            this.cooldownSeconds = Math.max(0, cooldownSeconds);
            return this;
        }

        public Builder permission(String permission) {
            this.permission = permission == null ? "" : permission;
            return this;
        }

        public Builder pity(PityPolicy pity) {
            this.pity = Optional.ofNullable(pity);
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
                    Map.copyOf(entityDropRates), enabled, List.copyOf(requires), icon,
                    discount, stock, restockMinutes, maxPerPlayer, cooldownSeconds, permission, pity);
        }
    }
}
