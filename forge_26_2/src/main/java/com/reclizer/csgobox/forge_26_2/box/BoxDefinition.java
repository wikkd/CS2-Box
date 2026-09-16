package com.reclizer.csgobox.forge_26_2.box;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.logic.PityPolicy;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

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
 */
public record BoxDefinition(
        Identifier id,
        Component name,
        String type,
        Identifier keyItem,
        float dropRate,
        List<Identifier> dropEntities,
        List<GradeGroup> grades,
        Optional<Identifier> texture,
        Optional<Identifier> sound,
        Map<Identifier, Float> entityDropRates,
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

    private static final Identifier NO_KEY = Identifier.parse("minecraft:air");

    /** Sentinel values: -1 = unlimited (stock / maxPerPlayer). */
    public static final int UNLIMITED = -1;

    /**
     * This record carries no CODEC: the network path uses the manual
     * {@link #STREAM_CODEC} which serializes every field including the
     * v2.0.1 additions (a 19-field {@code RecordCodecBuilder} group exceeds
     * the framework's arity limit and the codec is not used anywhere else).
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, BoxDefinition> STREAM_CODEC = StreamCodec.of(
            BoxDefinition::write,
            BoxDefinition::read
    );

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

    private static void write(RegistryFriendlyByteBuf buf, BoxDefinition def) {
        Identifier.STREAM_CODEC.encode(buf, def.id());
        ByteBufCodecs.fromCodec(ComponentSerialization.CODEC).encode(buf, def.name());
        ByteBufCodecs.STRING_UTF8.encode(buf, def.type());
        Identifier.STREAM_CODEC.encode(buf, def.keyItem());
        buf.writeFloat(def.dropRate());
        Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(BoxGrades.MAX_DROP_ENTITIES)).encode(buf, def.dropEntities());
        GradeGroup.STREAM_CODEC.apply(ByteBufCodecs.list(BoxGrades.MAX_GRADES)).encode(buf, def.grades());
        ByteBufCodecs.optional(Identifier.STREAM_CODEC).encode(buf, def.texture());
        ByteBufCodecs.optional(Identifier.STREAM_CODEC).encode(buf, def.sound());

        Map<Identifier, Float> entityRates = def.entityDropRates();
        if (entityRates.size() > BoxGrades.MAX_ENTITY_DROP_RATES) {
            throw new IllegalArgumentException("Too many entity drop rates: " + entityRates.size());
        }
        buf.writeVarInt(entityRates.size());
        for (Map.Entry<Identifier, Float> entry : entityRates.entrySet()) {
            Identifier.STREAM_CODEC.encode(buf, entry.getKey());
            buf.writeFloat(entry.getValue());
        }

        buf.writeBoolean(def.enabled());
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)).encode(buf, def.requires());
        ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, def.icon());
        buf.writeFloat(def.discount());
        buf.writeVarInt(def.stock());
        buf.writeVarInt(def.restockMinutes());
        buf.writeVarInt(def.maxPerPlayer());
        buf.writeVarInt(def.cooldownSeconds());
        ByteBufCodecs.STRING_UTF8.encode(buf, def.permission());
        Optional<PityPolicy> pity = def.pity();
        buf.writeBoolean(pity.isPresent());
        if (pity.isPresent()) {
            ByteBufCodecs.INT.encode(buf, pity.get().targetLevel());
            ByteBufCodecs.INT.encode(buf, pity.get().every());
        }
    }

    private static BoxDefinition read(RegistryFriendlyByteBuf buf) {
        Identifier id = Identifier.STREAM_CODEC.decode(buf);
        Component name = ByteBufCodecs.fromCodec(ComponentSerialization.CODEC).decode(buf);
        String type = ByteBufCodecs.STRING_UTF8.decode(buf);
        Identifier keyItem = Identifier.STREAM_CODEC.decode(buf);
        float dropRate = buf.readFloat();
        List<Identifier> dropEntities = Identifier.STREAM_CODEC
                .apply(ByteBufCodecs.list(BoxGrades.MAX_DROP_ENTITIES)).decode(buf);
        List<GradeGroup> grades = GradeGroup.STREAM_CODEC.apply(ByteBufCodecs.list(BoxGrades.MAX_GRADES)).decode(buf);
        Optional<Identifier> texture = ByteBufCodecs.optional(Identifier.STREAM_CODEC).decode(buf);
        Optional<Identifier> sound = ByteBufCodecs.optional(Identifier.STREAM_CODEC).decode(buf);

        int entityRatesSize = buf.readVarInt();
        if (entityRatesSize < 0 || entityRatesSize > BoxGrades.MAX_ENTITY_DROP_RATES) {
            throw new DecoderException("Invalid entity drop rate count: " + entityRatesSize);
        }
        Map<Identifier, Float> entityDropRates = new HashMap<>();
        for (int i = 0; i < entityRatesSize; i++) {
            Identifier entityId = Identifier.STREAM_CODEC.decode(buf);
            entityDropRates.put(entityId, buf.readFloat());
        }

        boolean enabled = buf.readBoolean();
        List<String> requires = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)).decode(buf);
        Optional<String> icon = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf);
        float discount = buf.readFloat();
        int stock = buf.readVarInt();
        int restockMinutes = buf.readVarInt();
        int maxPerPlayer = buf.readVarInt();
        int cooldownSeconds = buf.readVarInt();
        String permission = ByteBufCodecs.STRING_UTF8.decode(buf);

        Optional<PityPolicy> pity = Optional.empty();
        if (buf.readBoolean()) {
            int targetLevel = ByteBufCodecs.INT.decode(buf);
            int every = ByteBufCodecs.INT.decode(buf);
            // Invalid combinations (unknown tier, every < 2) silently degrade
            // to no pity — the schema validator reports them at load time.
            pity = Optional.ofNullable(PityPolicy.ofLevel(targetLevel, every));
        }

        return new BoxDefinition(id, name, type, keyItem, dropRate, dropEntities, grades,
                texture, sound, entityDropRates, enabled, requires, icon, discount,
                stock, restockMinutes, maxPerPlayer, cooldownSeconds, permission, pity);
    }

    /** Whether this definition is a terminal machine: the JSON {@code type}
     *  field is the single source of truth (v2.0.0 strict separation) — only
     *  {@code "type": "terminal"} is a terminal, and terminals carry no
     *  {@code key} field at all. */
    public boolean isTerminal() {
        return "terminal".equals(type);
    }

    /** Box type: terminal machine or regular crate, straight from the JSON. */
    public String type() {
        return type;
    }

    public static Builder builder(Identifier id, String name) {
        return new Builder(id, name);
    }

    public float getDropRateForEntity(Identifier entityType) {
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
            if (!com.reclizer.csgobox.forge_26_2.CsgoBox.isModLoaded(modId)) {
                return true;
            }
        }
        return false;
    }

    /** First missing required mod id, or null when all are loaded. */
    public String firstMissingRequirement() {
        for (String modId : requires) {
            if (!com.reclizer.csgobox.forge_26_2.CsgoBox.isModLoaded(modId)) {
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

    public static class Builder {
        private final Identifier id;
        private Component name;
        private OptionalInt nameColor = OptionalInt.empty();
        private String type = "csbox";
        private Identifier keyItem = NO_KEY;
        private float dropRate = 0.12F;
        private final List<Identifier> dropEntities = new ArrayList<>();
        private final List<GradeGroup> grades = new ArrayList<>();
        private Optional<Identifier> texture = Optional.empty();
        private Optional<Identifier> sound = Optional.empty();
        private final Map<Identifier, Float> entityDropRates = new HashMap<>();
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

        public Builder(Identifier id, String name) {
            this.id = Objects.requireNonNull(id, "box id");
            this.name = Component.literal(Objects.requireNonNull(name, "box name"));
        }

        public Builder name(Component name) {
            this.name = Objects.requireNonNull(name, "box name");
            return this;
        }

        /** Box kind from the JSON {@code type} field; unknown values fall back
         *  to {@code "csbox"} (the schema validator reports them separately). */
        public Builder type(String type) {
            this.type = (type == null || type.isBlank()) ? "csbox" : type;
            return this;
        }

        /** Applies a 0xRRGGBB color to the box's display name when {@link #build()}
         *  is called. Pass a hex value such as {@code 0xFF5555}; alpha is forced to
         *  0xFF to match Minecraft's style layer. */
        public Builder nameColor(int rgb) {
            this.nameColor = OptionalInt.of(rgb & 0xFFFFFF);
            return this;
        }

        public Builder key(Identifier keyItem) {
            this.keyItem = keyItem == null ? NO_KEY : keyItem;
            return this;
        }

        public Builder dropRate(float rate) {
            this.dropRate = rate;
            return this;
        }

        public Builder dropFrom(String... entities) {
            for (String entity : entities) {
                this.dropEntities.add(Identifier.parse(entity));
            }
            return this;
        }

        public Builder entityDropRate(String entityId, float rate) {
            this.entityDropRates.put(Identifier.parse(entityId), BoxGrades.clampDropRate(rate));
            return this;
        }

        public Builder addGrade(GradeGroup grade) {
            this.grades.add(Objects.requireNonNull(grade, "grade"));
            return this;
        }

        public Builder texture(Identifier texture) {
            this.texture = Optional.ofNullable(texture);
            return this;
        }

        public Builder sound(Identifier sound) {
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

        /** Optional pity (保底) policy; pass {@code null} (or an invalid
         *  policy) to disable. */
        public Builder pity(PityPolicy pity) {
            this.pity = pity == null ? Optional.empty() : Optional.of(pity);
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
                    discount, stock, restockMinutes, maxPerPlayer, cooldownSeconds,
                    permission, pity);
        }
    }
}
