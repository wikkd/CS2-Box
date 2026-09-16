package com.reclizer.csgobox.v1_21_1.box;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

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
 * entries are reported as warnings. TACZ guns are handled leniently: small
 * NBT discrepancies (string numbers, boolean bytes, whitespace, attachment
 * shape) are repaired or warned, and an item is never rejected — a missing
 * {@code GunId} is reported as a warning and the gun stays (as a bare gun).
 * The legacy "tag" field additionally accepts a JSON object (no SNBT quoting)
 * which is migrated into {@code minecraft:custom_data} when it carries TACZ
 * data.</p>
 *
 * <p>v2.0.1 additions:</p>
 * <ul>
 *   <li>{@code weight} — intra-grade item weight (parallel to the item list).</li>
 *   <li>{@code count} may be {@code [min,max]} — a range resolved at open time
 *       via the {@code csgobox:item_spec} marker.</li>
 *   <li>{@code tag} starting with {@code #} — an item tag reference expanded at
 *       load time to its current member items.</li>
 *   <li>{@code loot_table} — a loot-table reference resolved at open time
 *       (placeholder barrel carries the {@code csgobox:item_spec} marker).</li>
 *   <li>{@code enchant} — a random-enchant shortcut resolved at open time.</li>
 *   <li>Unknown item ids now distinguish "target mod not installed" from "item
 *       id typo" using {@link CsgoBox#isModLoaded}.</li>
 * </ul>
 */
public final class BoxItemCodec {

    private static final Gson GSON = new Gson();
    private static final String TACZ_MOD_ID = "tacz";
    /** Mirrors com.tacz.guns.api.item.nbt.GunItemDataAccessor constants. */
    private static final String TACZ_GUN_ID_TAG = "GunId";
    private static final String TACZ_AMMO_ID_TAG = "AmmoId";
    private static final String TACZ_ATTACHMENT_ID_TAG = "AttachmentId";
    private static final String TACZ_FIRE_MODE_TAG = "GunFireMode";
    private static final String TACZ_ATTACHMENT_PREFIX = "Attachment";
    private static final String TACZ_EMPTY_GUN_ID = "tacz:empty";
    private static final Set<String> TACZ_FIRE_MODES =
            Set.of("AUTO", "SEMI", "BURST", "UNKNOWN");

    /** Shared Gson instance, reused by {@code CsboxCommand} for item JSON output. */
    public static Gson gson() {
        return GSON;
    }

    private BoxItemCodec() {
    }

    /**
     * Result of {@link #parseItem}: one or more {@link ItemStack}s (a tag
     * reference expands to its members) plus a parallel {@code weights} list.
     * {@code error == null} indicates success; callers route the error into the
     * box loader's LoadError list so it surfaces via {@code /csbox info error}
     * and the join-time announcement. Warnings are non-fatal diagnostics
     * (migrated formats, partially dropped components, kept TACZ guns) surfaced
     * the same way.
     */
    record ParseOutcome(List<ItemStack> stacks, List<Integer> weights,
                        String error, List<String> warnings) {
        boolean isSuccess() { return error == null; }
        boolean isEmpty() { return stacks.isEmpty(); }
        /** First stack (single-item convenience for callers/tests that parse
         *  one item at a time); {@link ItemStack#EMPTY} when the outcome failed
         *  or expanded to nothing. */
        ItemStack stack() { return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0); }
        static ParseOutcome ok(ItemStack stack) { return ok(List.of(stack), List.of(1), List.of()); }
        static ParseOutcome ok(ItemStack stack, int weight) { return ok(List.of(stack), List.of(weight), List.of()); }
        static ParseOutcome ok(ItemStack stack, int weight, List<String> warnings) {
            return ok(List.of(stack), List.of(weight), warnings);
        }
        static ParseOutcome ok(List<ItemStack> stacks, List<Integer> weights, List<String> warnings) {
            return new ParseOutcome(List.copyOf(stacks), List.copyOf(weights), null, List.copyOf(warnings));
        }
        static ParseOutcome fail(String message) {
            return new ParseOutcome(List.of(), List.of(), message, List.of());
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

            int weight = parseWeight(obj, warnings);

            // Loot-table reference: resolved at open time; the placeholder
            // barrel carries the spec so previews and the tooltip show it.
            if (obj.has("loot_table")) {
                return parseLootTable(obj, weight, warnings);
            }

            // Item-tag reference: expanded at load time to its members.
            if (obj.has("tag") && obj.get("tag").isJsonPrimitive()
                    && obj.get("tag").getAsString().startsWith("#")) {
                return parseItemTag(obj, weight, warnings, taczValidator);
            }

            if (!obj.has("id")) {
                CsgoBox.LOGGER.warn("Skipping item JSON without id: {}", elem);
                return ParseOutcome.fail("missing 'id' field");
            }

            String id = obj.get("id").getAsString();
            int count = parseCount(obj, warnings);

            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item == null) {
                CsgoBox.LOGGER.warn("Unknown item in box JSON: {}", id);
                return ParseOutcome.fail(unknownItemReason(id));
            }

            ItemStack stack = new ItemStack(item, count);

            applyItemSpec(obj, stack, warnings);
            applyComponents(obj, stack, warnings);

            // TACZ identity check on the single stack (tag expansion runs its
            // own per-member loop below).
            Optional<GunCheckResult> gunCheck = taczValidator.check(stack);
            if (gunCheck.isPresent()) {
                GunCheckResult result = gunCheck.get();
                if (result.rejectReason() != null) {
                    CsgoBox.LOGGER.warn("Rejecting TACZ item {}: {}", id, result.rejectReason());
                    return ParseOutcome.fail(result.rejectReason());
                }
                warnings.addAll(result.warnings());
            }

            return ParseOutcome.ok(stack, weight, warnings);
        } catch (Exception e) {
            // Use Throwable variant so the real cause is not silently dropped
            // when the format string has only one {} placeholder.
            CsgoBox.LOGGER.warn("Failed to parse item JSON: {}", elem, e);
            return ParseOutcome.fail("parse failed: " + e.getMessage());
        }
    }

    // ---- v2.0.1 per-item spec: weight / count-range / enchant / loot table ----

    /** Reads the optional {@code weight} (default 1). A weight of 0 disables
     *  the entry; negatives are rejected with a warning. */
    private static int parseWeight(JsonObject obj, List<String> warnings) {
        if (!obj.has("weight")) {
            return 1;
        }
        try {
            int w = obj.get("weight").getAsInt();
            if (w < 0) {
                warnings.add("weight < 0 (" + w + ") treated as 0 (disabled)");
                return 0;
            }
            if (w > 10000) {
                warnings.add("weight " + w + " exceeds 10000, clamped");
                return 10000;
            }
            return w;
        } catch (Exception e) {
            warnings.add("invalid weight: " + e.getMessage());
            return 1;
        }
    }

    /** Reads {@code count}: an integer, or a [min,max] array resolved at open
     *  time. Returns the display/minimum count for the stored stack. */
    private static int parseCount(JsonObject obj, List<String> warnings) {
        if (!obj.has("count")) {
            return 1;
        }
        try {
            JsonElement c = obj.get("count");
            if (c.isJsonArray()) {
                JsonArray arr = c.getAsJsonArray();
                if (arr.size() != 2) {
                    warnings.add("count array must be [min,max], got " + arr.size() + " entries — using 1");
                    return 1;
                }
                int min = arr.get(0).getAsInt();
                int max = arr.get(1).getAsInt();
                if (min < 1 || max < min) {
                    warnings.add("invalid count range [" + min + "," + max + "] — using 1");
                    return 1;
                }
                return min; // stored min; open-time resolution rolls the range
            }
            int n = c.getAsInt();
            return Math.max(1, n);
        } catch (Exception e) {
            warnings.add("invalid count: " + e.getMessage());
            return 1;
        }
    }

    /** Applies the {@code csgobox:item_spec} marker for count-range/enchant. */
    private static void applyItemSpec(JsonObject obj, ItemStack stack, List<String> warnings) {
        StringBuilder spec = new StringBuilder();
        try {
            if (obj.has("count") && obj.get("count").isJsonArray()) {
                JsonArray arr = obj.get("count").getAsJsonArray();
                if (arr.size() == 2) {
                    spec.append("{\"c\":[").append(arr.get(0).getAsInt())
                            .append(",").append(arr.get(1).getAsInt()).append("]}");
                }
            }
            if (obj.has("enchant")) {
                JsonElement e = obj.get("enchant");
                if (e.isJsonPrimitive() && e.getAsBoolean()) {
                    appendSpecField(spec, "e", "true");
                } else if (e.isJsonObject()) {
                    appendSpecField(spec, "e", e.toString());
                } else {
                    warnings.add("enchant must be true or an object, ignored");
                }
            }
        } catch (Exception ex) {
            warnings.add("invalid item spec: " + ex.getMessage());
        }
        if (spec.length() > 0) {
            stack.set(ItemCsgoBox.ITEM_SPEC.get(), spec.toString());
        }
    }

    private static void appendSpecField(StringBuilder spec, String key, String valueJson) {
        if (spec.length() > 0 && spec.charAt(0) == '{') {
            spec.insert(spec.length() - 1, ",\"" + key + "\":" + valueJson);
        } else {
            spec.append("{\"").append(key).append("\":").append(valueJson).append("}");
        }
    }

    /** Loot-table entry: a barrel placeholder carrying the {@code item_spec}
     *  marker {@code {"l":"<id>"}}. The open path resolves it via the server's
     *  loot data. */
    private static ParseOutcome parseLootTable(JsonObject obj, int weight, List<String> warnings) {
        String tableId = obj.get("loot_table").getAsString();
        try {
            ResourceLocation.parse(tableId);
        } catch (Exception e) {
            return ParseOutcome.fail("invalid loot_table id: " + tableId + " (" + e.getMessage() + ")");
        }
        ItemStack placeholder = new ItemStack(Items.BARREL, 1);
        placeholder.set(ItemCsgoBox.ITEM_SPEC.get(), "{\"l\":\"" + tableId + "\"}");
        return ParseOutcome.ok(placeholder, weight, warnings);
    }

    /** Item-tag reference {@code #namespace:path}: expands to the tag's current
     *  member items, each with the shared count/weight/components. The TACZ
     *  identity check runs per member (a tag can contain guns). */
    private static ParseOutcome parseItemTag(JsonObject obj, int weight, List<String> warnings,
                                             TaczValidator taczValidator) {
        String tagId = obj.get("tag").getAsString().substring(1);
        ResourceLocation tagResource;
        try {
            tagResource = ResourceLocation.parse(tagId);
        } catch (Exception e) {
            return ParseOutcome.fail("invalid tag id: " + tagId + " (" + e.getMessage() + ")");
        }
        int count = parseCount(obj, warnings);
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagResource);
        List<ItemStack> stacks = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        Iterable<Holder<Item>> members;
        try {
            members = BuiltInRegistries.ITEM.getTagOrEmpty(tagKey);
        } catch (Exception e) {
            return ParseOutcome.fail("invalid tag: " + tagId + " (" + e.getMessage() + ")");
        }
        for (Holder<Item> holder : members) {
            if (holder == null) continue;
            ItemStack stack = new ItemStack(holder, count);
            applyItemSpec(obj, stack, warnings);
            applyComponents(obj, stack, warnings);
            Optional<GunCheckResult> gunCheck = taczValidator.check(stack);
            if (gunCheck.isPresent()) {
                GunCheckResult result = gunCheck.get();
                if (result.rejectReason() != null) {
                    CsgoBox.LOGGER.warn("Rejecting TACZ item {} in tag #{}: {}",
                            BuiltInRegistries.ITEM.getKey(stack.getItem()), tagId, result.rejectReason());
                    return ParseOutcome.fail(result.rejectReason());
                }
                warnings.addAll(result.warnings());
            }
            if (!stack.isEmpty()) {
                stacks.add(stack);
                weights.add(weight);
            }
        }
        if (stacks.isEmpty()) {
            return ParseOutcome.fail("item tag is empty or unregistered: #" + tagId);
        }
        return ParseOutcome.ok(stacks, weights, warnings);
    }

    /** Distinguishes "target mod not installed" from "item id typo". */
    static String unknownItemReason(String id) {
        try {
            ResourceLocation parsed = ResourceLocation.parse(id);
            if (!parsed.getNamespace().equals("minecraft") && !CsgoBox.isModLoaded(parsed.getNamespace())) {
                return "unknown item id: " + id + " (mod '" + parsed.getNamespace()
                        + "' is not loaded — install it or remove this entry)";
            }
            return "unknown item id: " + id + " (mod '" + parsed.getNamespace()
                    + "' is loaded — check the item id spelling)";
        } catch (Exception e) {
            return "invalid item id: " + id;
        }
    }

    /** Applies {@code components}, or the legacy NBT {@code tag} — either an
     *  SNBT string or a JSON object (only when it is NOT a {@code "#tag"} item
     *  reference — those are handled earlier in parseItem). */
    private static void applyComponents(JsonObject obj, ItemStack stack, List<String> warnings) {
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
                JsonElement tagElem = obj.get("tag");
                CompoundTag tag;
                if (tagElem.isJsonObject()) {
                    // JSON-object form: no SNBT quoting, booleans become bytes,
                    // integral numbers ints — friendlier for hand-written
                    // TACZ entries.
                    tag = jsonToTag(tagElem.getAsJsonObject(), warnings);
                } else {
                    String tagStr = tagElem.isJsonPrimitive()
                            ? tagElem.getAsString()
                            : tagElem.toString();
                    try {
                        tag = TagParser.parseTag(tagStr);
                    } catch (Exception snbtFailure) {
                        // Tolerant fallback: the string may be JSON rather
                        // than SNBT (boolean literals, quoted keys).
                        try {
                            tag = jsonToTag(JsonParser.parseString(tagStr).getAsJsonObject(), warnings);
                        } catch (Exception jsonFailure) {
                            throw new IllegalArgumentException("invalid NBT tag (SNBT: "
                                    + snbtFailure.getMessage() + "; JSON fallback: "
                                    + jsonFailure.getMessage() + ")");
                        }
                    }
                }
                applyLegacyGunTag(stack, tag, warnings);
            } catch (Exception e) {
                CsgoBox.LOGGER.warn("Failed to parse NBT tag for item {}: {}", obj.get("id"), e.getMessage());
                warnings.add("invalid NBT tag: " + e.getMessage());
            }
        }
    }

    /** Routes a legacy NBT compound: TACZ 1.20-era gun tags are migrated into
     *  {@code minecraft:custom_data}, everything else is decoded as a
     *  {@link DataComponentPatch}. */
    private static void applyLegacyGunTag(ItemStack stack, CompoundTag tag, List<String> warnings) {
        // v2.0.1-fix(TACZ ammo/attachment): TACZ 1.20-era configs stored gun /
        // ammo / attachment data as plain top-level NBT ({GunId:...},
        // {AmmoId:...}, {AttachmentId:...}, {Attachment<SLOT>:...}). 1.21.1
        // TACZ reads all of them from minecraft:custom_data, so migrate the
        // whole compound whenever ANY TACZ identity key is present — only
        // then can a boxed ammo stack / attachment keep its id instead of
        // being mis-decoded as a DataComponentPatch.
        boolean taczLegacy = tag.contains(TACZ_GUN_ID_TAG, 8)
                || tag.contains(TACZ_AMMO_ID_TAG, 8)
                || tag.contains(TACZ_ATTACHMENT_ID_TAG, 8)
                || tag.getAllKeys().stream().anyMatch(k -> k.startsWith(TACZ_ATTACHMENT_PREFIX));
        if (taczLegacy) {
            // TACZ 1.20-era configs stored the gun NBT as a plain top-level
            // tag ({GunId:...}). 1.21.1 TACZ reads it from
            // minecraft:custom_data, so migrate the whole compound.
            // Nested per-slot attachment blocks ({AttachmentSCOPE:...}) cannot
            // be read in 1.21.1; the flat {AttachmentId:...} key is the new
            // carrier and must NOT be counted as a legacy nested block.
            boolean legacyAttachments = tag.getAllKeys().stream()
                    .anyMatch(k -> k.startsWith(TACZ_ATTACHMENT_PREFIX)
                            && !k.equals(TACZ_ATTACHMENT_ID_TAG));
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
    }

    /** Converts a box-JSON object into an NBT compound without SNBT quoting:
     *  booleans become bytes, integral numbers ints, fractional numbers
     *  doubles; nulls are skipped. A nested {@code "tag"} string inside an
     *  attachment item is parsed as SNBT so JSON-object entries can carry
     *  legacy TACZ attachments. */
    static CompoundTag jsonToTag(JsonObject obj, List<String> warnings) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (value.isJsonObject()) {
                tag.put(key, jsonToTag(value.getAsJsonObject(), warnings));
            } else if (value.isJsonArray()) {
                ListTag list = new ListTag();
                for (JsonElement item : value.getAsJsonArray()) {
                    if (item == null || item.isJsonNull()) {
                        continue;
                    }
                    try {
                        if (item.isJsonObject()) {
                            list.add(jsonToTag(item.getAsJsonObject(), warnings));
                        } else if (item.isJsonPrimitive()) {
                            Tag converted = primitiveToTag(item.getAsJsonPrimitive(), key, warnings);
                            if (converted != null) {
                                list.add(converted);
                            }
                        } else {
                            warnings.add("tag." + key + ": unsupported array element " + item);
                        }
                    } catch (Exception e) {
                        warnings.add("tag." + key + ": skipped malformed array element (" + e.getMessage() + ")");
                    }
                }
                tag.put(key, list);
            } else if (value.isJsonPrimitive()) {
                Tag converted = primitiveToTag(value.getAsJsonPrimitive(), key, warnings);
                if (converted != null) {
                    tag.put(key, converted);
                }
            } else {
                warnings.add("tag." + key + ": unsupported JSON element " + value);
            }
        }
        return tag;
    }

    /** JSON primitive → NBT tag with useful type coercion for TACZ fields. */
    private static Tag primitiveToTag(JsonPrimitive primitive, String key, List<String> warnings) {
        if (primitive.isString()) {
            String s = primitive.getAsString();
            // TACZ attachments nest the item NBT under "tag"; accept both a
            // JSON object (handled by jsonToTag) and an SNBT string.
            if (key.equals("tag") && s.startsWith("{")) {
                try {
                    return TagParser.parseTag(s);
                } catch (Exception e) {
                    warnings.add("tag." + key + ": invalid nested SNBT string (" + e.getMessage() + ")");
                    return null;
                }
            }
            return StringTag.valueOf(s);
        }
        if (primitive.isBoolean()) {
            return ByteTag.valueOf((byte) (primitive.getAsBoolean() ? 1 : 0));
        }
        if (primitive.isNumber()) {
            double d = primitive.getAsDouble();
            if (!Double.isNaN(d) && !Double.isInfinite(d)
                    && d == Math.rint(d)
                    && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return IntTag.valueOf(primitive.getAsInt());
            }
            return DoubleTag.valueOf(d);
        }
        warnings.add("tag." + key + ": unsupported primitive " + primitive);
        return null;
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

    /** Reads the {@code csgobox:item_spec} marker of a stack (may be null). */
    static String readItemSpec(ItemStack stack) {
        return stack.get(ItemCsgoBox.ITEM_SPEC.get());
    }

    /** The loot-table id from a placeholder stack, or null. */
    static String lootTableId(ItemStack stack) {
        String spec = readItemSpec(stack);
        if (spec == null || !spec.contains("\"l\"")) {
            return null;
        }
        try {
            JsonObject obj = JsonParser.parseString(spec).getAsJsonObject();
            return obj.has("l") ? obj.get("l").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Pure-NBT analysis of a TACZ gun's custom_data compound: whether the gun
     * identity is usable and whether GunFireMode needs normalizing. No TACZ
     * classes are touched, so this is unit-testable without a TACZ runtime.
     * Whitespace around GunId / GunFireMode is ignored so small hand-writing
     * differences do not break recognition.
     */
    static TaczNbtCheck checkTaczNbt(CompoundTag tag) {
        List<String> warnings = new ArrayList<>();
        ResourceLocation gunId = null;
        boolean gunIdMissing = true;
        if (tag.contains(TACZ_GUN_ID_TAG, 8)) {
            String gunIdStr = tag.getString(TACZ_GUN_ID_TAG).trim();
            ResourceLocation parsed = ResourceLocation.tryParse(gunIdStr);
            if (parsed != null && !TACZ_EMPTY_GUN_ID.equals(parsed.toString())) {
                gunId = parsed;
                gunIdMissing = false;
            }
        }
        String fireModeCorrection = null;
        boolean fireModeWarned = false;
        if (tag.contains(TACZ_FIRE_MODE_TAG, 8)) {
            String fireMode = tag.getString(TACZ_FIRE_MODE_TAG).trim();
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
     * Best-effort repair of small TACZ NBT discrepancies so a gun entry is
     * never lost over a cosmetic detail: numeric fields written as strings are
     * coerced to ints, boolean-ish fields to bytes (TACZ reads them with
     * {@code contains(key, BYTE)}), GunFireMode whitespace is trimmed, and
     * attachment slots with an invalid shape are reported (and dropped only
     * when they are not compounds at all — TACZ would ignore them anyway).
     * Mutates the given compound and returns non-fatal warnings.
     */
    static List<String> normalizeTaczNbt(CompoundTag tag) {
        List<String> warnings = new ArrayList<>();
        coerceIntField(tag, "GunCurrentAmmoCount", warnings);
        coerceIntField(tag, "DummyAmmo", warnings);
        coerceIntField(tag, "MaxDummyAmmo", warnings);
        coerceIntField(tag, "GunLevelExp", warnings);
        coerceIntField(tag, "LaserColor", warnings);
        coerceByteField(tag, "HasBulletInBarrel", warnings);
        coerceByteField(tag, "OverHeated", warnings);
        if (tag.contains(TACZ_FIRE_MODE_TAG, 8)) {
            String trimmed = tag.getString(TACZ_FIRE_MODE_TAG).trim();
            if (trimmed.isEmpty()) {
                warnings.add("TACZ field 'GunFireMode' is empty; removed");
                tag.remove(TACZ_FIRE_MODE_TAG);
            } else if (!trimmed.equals(tag.getString(TACZ_FIRE_MODE_TAG))) {
                tag.putString(TACZ_FIRE_MODE_TAG, trimmed);
            }
        }
        checkAttachments(tag, warnings);
        return warnings;
    }

    private static void coerceIntField(CompoundTag tag, String key, List<String> warnings) {
        Tag t = tag.get(key);
        if (t == null || t instanceof IntTag) {
            return;
        }
        if (t instanceof NumericTag numeric) {
            tag.putInt(key, numeric.getAsInt());
            return;
        }
        if (t instanceof StringTag string) {
            String raw = string.getAsString().trim();
            try {
                tag.putInt(key, Integer.parseInt(raw));
            } catch (Exception e) {
                warnings.add("TACZ field '" + key + "' is not an integer ('" + raw + "'); removed");
                tag.remove(key);
            }
            return;
        }
        warnings.add("TACZ field '" + key + "' has unsupported tag type; removed");
        tag.remove(key);
    }

    private static void coerceByteField(CompoundTag tag, String key, List<String> warnings) {
        Tag t = tag.get(key);
        if (t == null || t instanceof ByteTag) {
            return;
        }
        byte value;
        if (t instanceof NumericTag numeric) {
            value = (byte) (numeric.getAsInt() == 0 ? 0 : 1);
        } else if (t instanceof StringTag string) {
            String raw = string.getAsString().trim().toLowerCase(Locale.ROOT);
            switch (raw) {
                case "true", "1" -> value = 1;
                case "false", "0" -> value = 0;
                default -> {
                    try {
                        value = (byte) (Integer.parseInt(raw) == 0 ? 0 : 1);
                    } catch (Exception e) {
                        warnings.add("TACZ field '" + key + "' is not a boolean ('"
                                + string.getAsString() + "'); removed");
                        tag.remove(key);
                        return;
                    }
                }
            }
        } else {
            warnings.add("TACZ field '" + key + "' has unsupported tag type; removed");
            tag.remove(key);
            return;
        }
        tag.putByte(key, value);
    }

    private static void checkAttachments(CompoundTag tag, List<String> warnings) {
        List<String> keys = new ArrayList<>(tag.getAllKeys());
        for (String key : keys) {
            if (!key.startsWith(TACZ_ATTACHMENT_PREFIX)) {
                continue;
            }
            Tag value = tag.get(key);
            if (!(value instanceof CompoundTag attachment)) {
                warnings.add("attachment '" + key + "' is not an object and was ignored");
                tag.remove(key);
                continue;
            }
            if (!attachment.contains("id", 8)
                    || !"tacz:attachment".equals(attachment.getString("id"))) {
                warnings.add("attachment '" + key + "' has no valid id (tacz:attachment); it will not mount");
            }
            if (attachment.contains("tag", 10)) {
                CompoundTag inner = attachment.getCompound("tag");
                if (!inner.contains("AttachmentId", 8)) {
                    warnings.add("attachment '" + key + "' has no AttachmentId; it will not mount");
                }
            }
        }
    }

    /**
     * Default TACZ validator. Optional-dependency discipline: gated on
     * {@code ModList#isLoaded} before any TACZ class is touched, everything
     * wrapped in {@code catch (Throwable)} so a missing/incompatible TACZ
     * degrades to a silent no-op (same pattern as TaczInspectViewportImpl).
     * Lenient mode: the item is ALWAYS kept. A missing/unusable GunId is only
     * a warning (the gun stays, but fires as a bare gun), GunFireMode is
     * normalized in place, small NBT type differences are repaired, and an
     * unloaded gun pack is a warning too.
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
            List<String> warnings = new ArrayList<>(check.warnings());
            if (check.gunIdMissing()) {
                warnings.add(tag.isEmpty()
                        ? "TACZ gun has no custom_data (components parse failed or missing?); kept as bare gun (cannot fire)"
                        : "TACZ gun missing GunId in custom_data; kept as bare gun (cannot fire)");
            }
            warnings.addAll(normalizeTaczNbt(tag));
            if (check.fireModeCorrection() != null) {
                tag.putString(TACZ_FIRE_MODE_TAG, check.fireModeCorrection());
            }
            if (check.gunId() != null) {
                // Canonical form also strips accidental whitespace around GunId.
                tag.putString(TACZ_GUN_ID_TAG, check.gunId().toString());
            }
            if (customData != null || !tag.isEmpty()) {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            // The common gun index only exists where a real (integrated or
            // dedicated) server is present. Pure-client loads — creative tab
            // build, previews, client-side /csbox reload — have no server-side
            // index data and would misreport every gun as missing; the client
            // renders through TACZ's own ClientAssetsManager which falls back
            // gracefully. The authoritative check therefore runs only while a
            // server world is actually loaded.
            if (ServerLifecycleHooks.getCurrentServer() != null
                    && check.gunId() != null
                    && TimelessAPI.getCommonGunIndex(check.gunId()).isEmpty()) {
                warnings.add("TACZ gun '" + check.gunId()
                        + "' is not loaded (missing gun pack?); item kept but cannot be used");
            }
            return Optional.of(GunCheckResult.warn(warnings));
        } catch (Throwable t) {
            CsgoBox.LOGGER.warn("TACZ item validation failed, treating as plain item", t);
            return Optional.empty();
        }
    }

    /**
     * v2.0.1: price-table variant id of a stack — the legacy NBT {@code GunId}
     * or {@code AmmoId} (TACZ) that the central price table keys as
     * {@code id#variant}, or null. Best-effort and TACZ-class-free: reads the
     * item's {@code minecraft:custom_data}; a missing / blank field yields
     * null and the caller falls back to the plain id, then the grade ladder.
     */
    public static String priceVariantId(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag tag = customData != null ? customData.copyTag() : null;
            if (tag == null) {
                return null;
            }
            if (tag.contains(TACZ_GUN_ID_TAG, 8)) {
                String v = tag.getString(TACZ_GUN_ID_TAG).trim();
                return v.isEmpty() ? null : v;
            }
            if (tag.contains(TACZ_AMMO_ID_TAG, 8)) {
                String v = tag.getString(TACZ_AMMO_ID_TAG).trim();
                return v.isEmpty() ? null : v;
            }
            return null;
        } catch (Throwable t) {
            return null;
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
