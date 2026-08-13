package com.reclizer.csgobox.v1_21_1.box;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses and serializes individual ItemStack entries within box JSON.
 *
 * <p>Both directions tolerate the legacy "tag" NBT string format and the
 * modern "components" data-component patch format. Components are decoded
 * entry-by-entry so a single malformed component no longer drops the whole
 * patch (MC's {@code DataComponentPatch.CODEC} is all-or-nothing); failed
 * entries are reported as warnings. TACZ guns additionally get an identity
 * check: a gun whose {@code GunId} is missing (e.g. its custom_data was
 * dropped by a component failure) is rejected with an actionable error
 * instead of silently becoming a useless "bare gun".</p>
 */
public final class BoxItemCodec {

    private static final Gson GSON = new Gson();
    private static final String TACZ_MOD_ID = "tacz";
    /** Mirrors com.tacz.guns.api.item.nbt.GunItemDataAccessor constants. */
    private static final String TACZ_GUN_ID_TAG = "GunId";
    private static final String TACZ_FIRE_MODE_TAG = "GunFireMode";
    private static final String TACZ_ATTACHMENT_PREFIX = "Attachment";
    private static final String TACZ_EMPTY_GUN_ID = "tacz:empty";
    private static final Set<String> TACZ_FIRE_MODES =
            Set.of("AUTO", "SEMI", "BURST", "UNKNOWN");

    public static Gson gson() {
        return GSON;
    }

    private BoxItemCodec() {
    }

    /**
     * Result of {@link #parseItem}: the parsed {@link ItemStack} on success,
     * or a human-readable error string on failure. {@code error == null}
     * indicates success; callers route the error into the box loader's
     * LoadError list so it surfaces via {@code /csbox info error} and the
     * join-time announcement. Warnings are non-fatal diagnostics (kept items,
     * migrated formats, partially dropped components) surfaced the same way.
     */
    record ParseOutcome(ItemStack stack, String error, List<String> warnings) {
        boolean isSuccess() { return error == null; }
        static ParseOutcome ok(ItemStack stack) { return ok(stack, List.of()); }
        static ParseOutcome ok(ItemStack stack, List<String> warnings) {
            return new ParseOutcome(stack, null, List.copyOf(warnings));
        }
        static ParseOutcome fail(String message) {
            return new ParseOutcome(ItemStack.EMPTY, message, List.of());
        }
    }

    /** Per-component decode output: the patch of successful entries plus one
     *  error string per failed entry. */
    record DecodeResult(DataComponentPatch patch, List<String> errors) {
        boolean hasErrors() { return !errors.isEmpty(); }
    }

    /** TACZ identity/state check result. {@code rejectReason != null} means
     *  the entry must be dropped; otherwise {@code warnings} are non-fatal. */
    record GunCheckResult(@Nullable String rejectReason, List<String> warnings) {
        static GunCheckResult reject(String reason) { return new GunCheckResult(reason, List.of()); }
        static GunCheckResult warn(List<String> warnings) { return new GunCheckResult(null, List.copyOf(warnings)); }
    }

    /** Pure-NBT analysis of a TACZ gun's custom_data compound. */
    record TaczNbtCheck(
            @Nullable ResourceLocation gunId,
            boolean gunIdMissing,
            @Nullable String fireModeCorrection,
            boolean fireModeWarned,
            List<String> warnings
    ) {
    }

    /**
     * Injectable TACZ validator so {@link #parseItem} orchestration is
     * testable without a TACZ runtime. The default implementation follows the
     * repo's optional-dependency discipline: ModList gate first, TACZ classes
     * only touched inside try/catch, degrade to no-op on any failure.
     */
    interface TaczValidator {
        Optional<GunCheckResult> check(ItemStack stack);
    }

    private static final TaczValidator DEFAULT_TACZ_VALIDATOR = BoxItemCodec::validateTacz;

    /**
     * Parses an item object, or a legacy JSON string containing that object.
     * Returns a {@link ParseOutcome} that distinguishes a clean parse from a
     * skipped item (missing id, unknown item id, rejected TACZ gun, malformed
     * components). Component failures are non-fatal per entry and surface as
     * warnings; TACZ guns without a usable {@code GunId} are rejected so a
     * config can never silently deliver a bare gun.
     */
    static ParseOutcome parseItem(JsonElement elem) {
        return parseItem(elem, DEFAULT_TACZ_VALIDATOR);
    }

    static ParseOutcome parseItem(JsonElement elem, TaczValidator taczValidator) {
        List<String> warnings = new ArrayList<>();
        try {
            JsonObject obj;
            if (elem.isJsonPrimitive()) {
                // Legacy configs stored the item object as a JSON string.
                obj = GSON.fromJson(elem.getAsString(), JsonObject.class);
            } else {
                obj = elem.getAsJsonObject();
            }
            if (obj == null) {
                return ParseOutcome.fail("item JSON is null");
            }
            if (!obj.has("id")) {
                CsgoBox.LOGGER.warn("Skipping item JSON without id: {}", elem);
                return ParseOutcome.fail("missing 'id' field");
            }

            String id = obj.get("id").getAsString();
            int count = obj.has("count") ? obj.get("count").getAsInt() : 1;

            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item == null) {
                CsgoBox.LOGGER.warn("Unknown item in box JSON: {}", id);
                return ParseOutcome.fail("unknown item id: " + id);
            }

            ItemStack stack = new ItemStack(item, count);

            if (obj.has("components")) {
                JsonElement componentsElem = obj.get("components");
                if (componentsElem.isJsonObject()) {
                    DecodeResult dr = decodeComponents(componentsElem.getAsJsonObject());
                    warnings.addAll(dr.errors());
                    if (!dr.patch().isEmpty()) {
                        stack.applyComponents(dr.patch());
                    }
                } else {
                    warnings.add("'components' must be a JSON object, got "
                            + componentsElem.getClass().getSimpleName());
                }
            } else if (obj.has("tag")) {
                try {
                    String tagStr = obj.get("tag").getAsString();
                    var tag = TagParser.parseTag(tagStr);
                    if (tag.contains(TACZ_GUN_ID_TAG, 8)) {
                        // TACZ 1.20-era configs stored the gun NBT as a plain
                        // top-level tag ({GunId:...}). 1.21.1 TACZ reads it from
                        // minecraft:custom_data, so migrate the whole compound.
                        boolean legacyAttachments = tag.getAllKeys().stream()
                                .anyMatch(k -> k.startsWith(TACZ_ATTACHMENT_PREFIX));
                        if (legacyAttachments) {
                            warnings.add("legacy 'tag' attachment data cannot be read in 1.21.1; attachments were dropped");
                        }
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                    } else {
                        DataResult<DataComponentPatch> result =
                                DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, tag);
                        result.resultOrPartial(err -> warnings.add(
                                "NBT 'tag' components parse failed: " + err))
                                .filter(p -> !p.isEmpty())
                                .ifPresent(stack::applyComponents);
                    }
                } catch (Exception e) {
                    CsgoBox.LOGGER.warn("Failed to parse NBT tag for item {}: {}", id, e.getMessage());
                    warnings.add("invalid NBT tag: " + e.getMessage());
                }
            }

            Optional<GunCheckResult> gunCheck = taczValidator.check(stack);
            if (gunCheck.isPresent()) {
                GunCheckResult result = gunCheck.get();
                if (result.rejectReason() != null) {
                    CsgoBox.LOGGER.warn("Rejecting TACZ item {}: {}", id, result.rejectReason());
                    return ParseOutcome.fail(result.rejectReason());
                }
                warnings.addAll(result.warnings());
            }

            return ParseOutcome.ok(stack, warnings);
        } catch (Exception e) {
            // Use Throwable variant so the real cause is not silently dropped
            // when the format string has only one {} placeholder.
            CsgoBox.LOGGER.warn("Failed to parse item JSON: {}", elem, e);
            return ParseOutcome.fail("parse failed: " + e.getMessage());
        }
    }

    /**
     * Decodes a "components" JSON object entry-by-entry. Unlike
     * {@code DataComponentPatch.CODEC} — whose dispatchedMap fails the entire
     * patch on the first bad key or value — successful entries are kept and
     * each failure is collected with the offending component key. A
     * {@code null} value means "remove this component" (the JSON form
     * {@code DataComponentPatch} itself encodes removals as).
     */
    static DecodeResult decodeComponents(JsonObject componentsJson) {
        DataComponentPatch.Builder builder = DataComponentPatch.builder();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : componentsJson.entrySet()) {
            String key = entry.getKey();
            try {
                DataComponentType<?> type =
                        BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse(key));
                if (type == null) {
                    errors.add("components." + key + ": unknown data component");
                    continue;
                }
                Codec<?> codec = type.codec();
                if (codec == null) {
                    errors.add("components." + key + ": transient component cannot be stored in box JSON");
                    continue;
                }
                JsonElement value = entry.getValue();
                if (value.isJsonNull()) {
                    builder.remove(type);
                    continue;
                }
                DataResult<?> result = codec.parse(JsonOps.INSTANCE, value);
                result.resultOrPartial(err -> errors.add("components." + key + ": " + err))
                        .ifPresent(v -> setComponent(builder, type, v));
            } catch (Exception e) {
                errors.add("components." + key + ": " + e.getMessage());
            }
        }
        return new DecodeResult(builder.build(), errors);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setComponent(DataComponentPatch.Builder builder,
                                     DataComponentType type, Object value) {
        builder.set(type, value);
    }

    /**
     * Pure-NBT analysis of a TACZ gun's custom_data compound: whether the gun
     * identity is usable and whether GunFireMode needs normalizing. No TACZ
     * classes are touched, so this is unit-testable without a TACZ runtime.
     */
    static TaczNbtCheck checkTaczNbt(CompoundTag tag) {
        List<String> warnings = new ArrayList<>();
        ResourceLocation gunId = null;
        boolean gunIdMissing = true;
        if (tag.contains(TACZ_GUN_ID_TAG, 8)) {
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString(TACZ_GUN_ID_TAG));
            if (parsed != null && !TACZ_EMPTY_GUN_ID.equals(parsed.toString())) {
                gunId = parsed;
                gunIdMissing = false;
            }
        }
        String fireModeCorrection = null;
        boolean fireModeWarned = false;
        if (tag.contains(TACZ_FIRE_MODE_TAG, 8)) {
            String fireMode = tag.getString(TACZ_FIRE_MODE_TAG);
            if (!TACZ_FIRE_MODES.contains(fireMode)) {
                String upper = fireMode.toUpperCase(Locale.ROOT);
                if (TACZ_FIRE_MODES.contains(upper)) {
                    fireModeCorrection = upper;
                } else {
                    fireModeCorrection = "UNKNOWN";
                    fireModeWarned = true;
                    warnings.add("invalid GunFireMode '" + fireMode + "' reset to UNKNOWN");
                }
            }
        }
        return new TaczNbtCheck(gunId, gunIdMissing, fireModeCorrection,
                fireModeWarned, List.copyOf(warnings));
    }

    /**
     * Default TACZ validator. Optional-dependency discipline: gated on
     * {@code ModList#isLoaded} before any TACZ class is touched, everything
     * wrapped in {@code catch (Throwable)} so a missing/incompatible TACZ
     * degrades to a silent no-op (same pattern as TaczInspectViewportImpl).
     * Rejects guns whose custom_data is missing or lacks a usable GunId;
     * warns (keeping the item) when the GunId points at a gun pack that is
     * not loaded, and normalizes GunFireMode in place when needed.
     */
    private static Optional<GunCheckResult> validateTacz(ItemStack stack) {
        try {
            if (ModList.get() == null || !ModList.get().isLoaded(TACZ_MOD_ID)) {
                return Optional.empty();
            }
            if (!(stack.getItem() instanceof IGun)) {
                return Optional.empty();
            }
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag tag = customData != null ? customData.copyTag() : new CompoundTag();
            TaczNbtCheck check = checkTaczNbt(tag);
            if (check.gunIdMissing()) {
                String reason = tag.isEmpty()
                        ? "TACZ gun has no custom_data (components parse failed or missing?)"
                        : "TACZ gun missing GunId in custom_data";
                return Optional.of(GunCheckResult.reject(reason));
            }
            List<String> warnings = new ArrayList<>(check.warnings());
            if (check.fireModeCorrection() != null) {
                String correction = check.fireModeCorrection();
                stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY,
                        data -> data.update(t -> t.putString(TACZ_FIRE_MODE_TAG, correction)));
            }
            if (TimelessAPI.getCommonGunIndex(check.gunId()).isEmpty()) {
                warnings.add("TACZ gun '" + check.gunId()
                        + "' is not loaded (missing gun pack?); item kept but cannot be used");
            }
            return Optional.of(GunCheckResult.warn(warnings));
        } catch (Throwable t) {
            CsgoBox.LOGGER.warn("TACZ item validation failed, treating as plain item", t);
            return Optional.empty();
        }
    }

    public static JsonObject serializeItemStack(ItemStack stack) {
        JsonObject obj = new JsonObject();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        obj.addProperty("id", itemId.toString());
        obj.addProperty("count", stack.getCount());

        DataComponentPatch patch = stack.getComponentsPatch();
        if (!patch.isEmpty()) {
            try {
                var result = DataComponentPatch.CODEC.encodeStart(JsonOps.INSTANCE, patch);
                result.resultOrPartial(err -> CsgoBox.LOGGER.warn(
                                "Partial components serialization for item {}: {}", itemId, err))
                        .ifPresent(elem -> obj.add("components", elem));
            } catch (Exception e) {
                CsgoBox.LOGGER.warn("Failed to serialize components for item: {}", itemId, e.getMessage());
            }
        }

        return obj;
    }
}
