package com.reclizer.csgobox.v26_2.box;

import com.reclizer.csgobox.terminal.NegotiationModel;
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
 * {@code icon} sets CustomModelData, {@code discount}/{@code stock} drive
 * terminal economy, and {@code max_per_player}/{@code cooldown_seconds}/
 * {@code permission} constrain opening.</p>
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
        int maxPerPlayer,
        int cooldownSeconds,
        String permission,
        boolean hideOdds,
        boolean allowBulk,
        List<String> extraKeys,
        float wearMin,
        float wearMax,
        float bonusChance,
        int bonusMode,
        int announceLevel,
        long availableFromMs,
        long availableUntilMs,
        int openCost,
        int negotiationRounds,
        int negotiationTimeoutMinutes,
        float priceBias,
        Optional<UpgradeRule> upgrade,
        Optional<PityPolicy> pity
) {

    private static final Identifier NO_KEY = Identifier.parse("minecraft:air");

    /** Sentinel values: -1 = unlimited (stock / maxPerPlayer). */
    public static final int UNLIMITED = -1;

    /** v2.x bonus modes (config "bonus.mode": "double" | "box"). */
    public static final int BONUS_NONE = 0;
    public static final int BONUS_DOUBLE = 1;
    public static final int BONUS_BOX = 2;

    /** Cap on configured extra keys (network sanity). */
    public static final int MAX_KEYS = 8;

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
        extraKeys = extraKeys == null ? List.of() : List.copyOf(extraKeys);
        wearMin = Math.min(Math.max(wearMin, 0.0F), 1.0F);
        wearMax = Math.min(Math.max(wearMax, 0.0F), 1.0F);
        if (wearMax < wearMin) {
            wearMax = wearMin;
        }
        bonusChance = BoxGrades.clampDropRate(bonusChance);
        negotiationRounds = Math.max(0, negotiationRounds);
        negotiationTimeoutMinutes = Math.max(0, negotiationTimeoutMinutes);
        priceBias = Math.min(Math.max(priceBias, -0.9F), 9.0F);
        negotiationRounds = Math.max(0, negotiationRounds);
        negotiationTimeoutMinutes = Math.max(0, negotiationTimeoutMinutes);
        priceBias = Math.min(Math.max(priceBias, -0.9F), 9.0F);
        negotiationRounds = Math.max(0, negotiationRounds);
        negotiationTimeoutMinutes = Math.max(0, negotiationTimeoutMinutes);
        priceBias = Math.min(Math.max(priceBias, -0.9F), 9.0F);
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
        buf.writeVarInt(def.maxPerPlayer());
        buf.writeVarInt(def.cooldownSeconds());
        ByteBufCodecs.STRING_UTF8.encode(buf, def.permission());
        // v2.x gameplay fields.
        buf.writeBoolean(def.hideOdds());
        buf.writeBoolean(def.allowBulk());
        int extraKeyCount = Math.min(def.extraKeys().size(), BoxDefinition.MAX_KEYS);
        buf.writeVarInt(extraKeyCount);
        for (int i = 0; i < extraKeyCount; i++) {
            buf.writeUtf(def.extraKeys().get(i));
        }
        buf.writeFloat(def.wearMin());
        buf.writeFloat(def.wearMax());
        buf.writeFloat(def.bonusChance());
        buf.writeByte(def.bonusMode());
        buf.writeVarInt(def.announceLevel());
        buf.writeLong(def.availableFromMs());
        buf.writeLong(def.availableUntilMs());
        buf.writeVarInt(def.openCost());
        buf.writeVarInt(def.negotiationRounds());
        buf.writeVarInt(def.negotiationTimeoutMinutes());
        buf.writeFloat(def.priceBias());
        buf.writeBoolean(def.upgrade().isPresent());
        if (def.upgrade().isPresent()) {
            buf.writeUtf(def.upgrade().get().target(), 256);
            buf.writeVarInt(def.upgrade().get().consume());
        }
        Optional<PityPolicy> pity = def.pity();
        buf.writeBoolean(pity.isPresent());
        if (pity.isPresent()) {
            ByteBufCodecs.INT.encode(buf, pity.get().targetLevel());
            ByteBufCodecs.INT.encode(buf, pity.get().every());
            buf.writeBoolean(pity.get().firstOpen());
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
        int maxPerPlayer = buf.readVarInt();
        int cooldownSeconds = buf.readVarInt();
        String permission = ByteBufCodecs.STRING_UTF8.decode(buf);

        // v2.x gameplay fields.
        boolean hideOdds = buf.readBoolean();
        boolean allowBulk = buf.readBoolean();
        int extraKeysSize = Math.min(buf.readVarInt(), BoxDefinition.MAX_KEYS);
        List<String> extraKeys = new ArrayList<>(extraKeysSize);
        for (int i = 0; i < extraKeysSize; i++) {
            extraKeys.add(buf.readUtf(256));
        }
        float wearMin = buf.readFloat();
        float wearMax = buf.readFloat();
        float bonusChance = buf.readFloat();
        int bonusMode = buf.readUnsignedByte();
        int announceLevel = buf.readVarInt();
        long availableFromMs = buf.readLong();
        long availableUntilMs = buf.readLong();
        int openCost = buf.readVarInt();
        // v2.x negotiation/upgrade fields (server-side only).
        int negotiationRounds = buf.readVarInt();
        int negotiationTimeoutMinutes = buf.readVarInt();
        float priceBias = buf.readFloat();
        boolean upgradePresent = buf.readBoolean();
        String upgradeTarget = upgradePresent ? buf.readUtf(256) : "";
        int upgradeConsume = upgradePresent ? buf.readVarInt() : 0;
        java.util.Optional<UpgradeRule> upgrade = upgradePresent
                ? java.util.Optional.of(new UpgradeRule(upgradeTarget, upgradeConsume))
                : java.util.Optional.empty();
        Optional<PityPolicy> pity = Optional.empty();
        if (buf.readBoolean()) {
            int targetLevel = ByteBufCodecs.INT.decode(buf);
            int every = ByteBufCodecs.INT.decode(buf);
            boolean firstOpen = buf.readBoolean();
            // Invalid combinations (unknown tier, every < 2) silently degrade
            // to no pity — the schema validator reports them at load time.
            pity = Optional.ofNullable(PityPolicy.ofLevel(targetLevel, every, firstOpen));
        }

        return new BoxDefinition(id, name, type, keyItem, dropRate, dropEntities, grades,
                texture, sound, entityDropRates, enabled, requires, icon, discount,
                stock, maxPerPlayer, cooldownSeconds, permission,
                hideOdds, allowBulk, extraKeys, wearMin, wearMax, bonusChance,
                bonusMode, announceLevel, availableFromMs, availableUntilMs, openCost,
                negotiationRounds, negotiationTimeoutMinutes, priceBias, upgrade,
                pity);
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

    /**
     * Tier weights indexed by level-1. Tiers are positional: the list runs
     * highest → lowest, so the tier at index i has level {@code size - i}
     * (see {@link BoxGrades#levelAt}) — custom tier counts just work, and a
     * legacy five-tier box maps to the same array as the old id lookup.
     */
    public int[] getWeightArray() {
        int[] weights = new int[grades.size()];
        for (int i = 0; i < grades.size(); i++) {
            weights[grades.size() - 1 - i] = Math.max(0, grades.get(i).weight());
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

    /** Effective negotiation round count (0 = the model's built-in 5). */
    public int effectiveRounds() {
        return negotiationRounds > 0 ? Math.min(negotiationRounds, NegotiationModel.MAX_ROUNDS) : NegotiationModel.MAX_ROUNDS;
    }

    /** Effective countdown minutes (0 = the built-in 180). */
    public long effectiveTimeoutMs() {
        return negotiationTimeoutMinutes > 0 ? negotiationTimeoutMinutes * 60_000L
                : NegotiationModel.COUNT_INITIAL_MS;
    }

    /** Price bias applied when sampling terminal offer prices
     *  (1.0 = table price, <1 cheaper, >1 pricier; clamped [-0.9, 9]). */
    public float biasedPrice(float basePrice) {
        return Math.max(1, Math.round(basePrice * (1.0F + priceBias)));
    }

    /** v2.x upgrade rule (config "upgrade": {target, consume}); empty =
     *  this box cannot be upgraded. */
    public Optional<UpgradeRule> upgradeRule() {
        return upgrade;
    }

    /** Upgrade rule: {@code consume} copies of this box grant one
     *  {@code target} box (id + definition validated at command time). */
    public record UpgradeRule(String target, int consume) {
    }

    /** Accepted key item ids: the primary key (when set) plus configured
     *  extras, deduplicated, order preserved. */
    public List<String> acceptedKeys() {
        String primary = keyItem.toString();
        List<String> keys = new ArrayList<>(1 + extraKeys.size());
        if (!NO_KEY.toString().equals(primary)) {
            keys.add(primary);
        }
        for (String extra : extraKeys) {
            if (!keys.contains(extra)) {
                keys.add(extra);
            }
        }
        return keys;
    }

    /** Display name of the tier at the given 1-based level, or null. */
    public String tierDisplayName(int level) {
        for (int i = 0; i < grades.size(); i++) {
            if (BoxGrades.levelAt(grades.size(), i) == level) {
                return grades.get(i).displayName();
            }
        }
        return null;
    }

    /** Wear fraction for one open of a damageable item (v2.x "wear" range). */
    public float sampleWear(java.util.Random rng) {
        float span = wearMax - wearMin;
        return span <= 0.0F ? wearMin : wearMin + rng.nextFloat() * span;
    }

    /** True when the "bonus" mode grants an extra box (vs duplicating the item). */
    public boolean bonusIsBox() {
        return bonusMode == BONUS_BOX;
    }

    /** Whether the box may be opened at the given wall-clock millis
     *  (v2.x availability window; 0 bounds = unbounded). */
    public boolean isAvailableNow(long nowMillis) {
        return (availableFromMs <= 0L || nowMillis >= availableFromMs)
                && (availableUntilMs <= 0L || nowMillis <= availableUntilMs);
    }

    /** Formatted wall-clock text of the availability bounds (v2.x tooltip;
     *  rendered in the server's default timezone). */
    public String windowText() {
        return formatWindow(availableFromMs);
    }

    public String windowTextUntil() {
        return formatWindow(availableUntilMs);
    }

    private static String formatWindow(long epochMs) {
        if (epochMs <= 0L) {
            return "-";
        }
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(epochMs));
    }

    /** Whether any of the {@code requires} mods is missing. */
    public boolean missingRequirement() {
        for (String modId : requires) {
            if (!com.reclizer.csgobox.v26_2.CsgoBox.isModLoaded(modId)) {
                return true;
            }
        }
        return false;
    }

    /** First missing required mod id, or null when all are loaded. */
    public String firstMissingRequirement() {
        for (String modId : requires) {
            if (!com.reclizer.csgobox.v26_2.CsgoBox.isModLoaded(modId)) {
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
        private int maxPerPlayer = UNLIMITED;
        private int cooldownSeconds = 0;
        private String permission = "";
        private boolean hideOdds = false;
        private boolean allowBulk = true;
        private final List<String> extraKeys = new ArrayList<>();
        private float wearMin = 0.0F;
        private float wearMax = 1.0F;
        private float bonusChance = 0.0F;
        private int bonusMode = BONUS_NONE;
        private int announceLevel = 0;
        private long availableFromMs = 0L;
        private long availableUntilMs = 0L;
        private int openCost = 0;
        private int negotiationRounds = 0;
        private int negotiationTimeoutMinutes = 0;
        private float priceBias = 0.0F;
        private Optional<UpgradeRule> upgrade = Optional.empty();
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

        public Builder maxPerPlayer(int maxPerPlayer) {
            this.maxPerPlayer = maxPerPlayer;
            return this;
        }

        public Builder cooldownSeconds(int cooldownSeconds) {
            this.cooldownSeconds = Math.max(0, cooldownSeconds);
            return this;
        }

        public Builder hideOdds(boolean hideOdds) {
            this.hideOdds = hideOdds;
            return this;
        }
        public Builder allowBulk(boolean allowBulk) {
            this.allowBulk = allowBulk;
            return this;
        }

        public Builder extraKey(String keyId) {
            if (keyId != null && !keyId.isBlank()) {
                this.extraKeys.add(keyId.trim());
            }
            return this;
        }
        public Builder wearRange(float min, float max) {
            this.wearMin = min;
            this.wearMax = max;
            return this;
        }
        public Builder bonus(float chance, String mode) {
            this.bonusChance = chance;
            this.bonusMode = "box".equalsIgnoreCase(mode) ? BONUS_BOX : BONUS_DOUBLE;
            return this;
        }
        public Builder announceAbove(int level) {
            this.announceLevel = Math.max(0, level);
            return this;
        }
        public Builder availableFrom(long epochMs) {
            this.availableFromMs = Math.max(0L, epochMs);
            return this;
        }
        public Builder availableUntil(long epochMs) {
            this.availableUntilMs = Math.max(0L, epochMs);
            return this;
        }
        /** v2.x negotiation params: 0 = the built-in defaults. */
        /** v2.x negotiation params: 0 = the built-in defaults. */
        public Builder negotiation(int rounds, int timeoutMinutes, float bias) {
            this.negotiationRounds = rounds;
            this.negotiationTimeoutMinutes = timeoutMinutes;
            this.priceBias = bias;
            return this;
        }

        public Builder upgrade(UpgradeRule rule) {
            this.upgrade = rule == null ? Optional.empty() : Optional.of(rule);
            return this;
        }

        /** v2.x negotiation params: 0 = the built-in defaults. */
        /** v2.x negotiation params: 0 = the built-in defaults. */
        public Builder openCost(int cost) {
            this.openCost = Math.max(0, cost);
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
                    discount, stock, maxPerPlayer, cooldownSeconds,
                    permission, hideOdds, allowBulk, extraKeys, wearMin, wearMax,
                    bonusChance, bonusMode, announceLevel, availableFromMs,
                    availableUntilMs, openCost, negotiationRounds,
                    negotiationTimeoutMinutes, priceBias, upgrade, pity);
        }
    }
}
