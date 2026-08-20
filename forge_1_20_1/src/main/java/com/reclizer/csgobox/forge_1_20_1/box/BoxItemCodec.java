package com.reclizer.csgobox.forge_1_20_1.box;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class BoxItemCodec {

    private static final Gson GSON = new Gson();
    private static final String TACZ_MOD_ID = "tacz";
    /** Mirrors com.tacz.guns.api.item.nbt.GunItemDataAccessor constants. */
    private static final String TACZ_GUN_ID_TAG = "GunId";
    private static final String TACZ_FIRE_MODE_TAG = "GunFireMode";
    private static final String TACZ_EMPTY_GUN_ID = "tacz:empty";
    private static final Set<String> TACZ_FIRE_MODES =
            Set.of("AUTO", "SEMI", "BURST", "UNKNOWN");

    public static Gson gson() {
        return GSON;
    }

    private BoxItemCodec() {
    }

    record ParseOutcome(ItemStack stack, String error) {
        boolean isSuccess() { return error == null; }
        static ParseOutcome ok(ItemStack stack) { return new ParseOutcome(stack, null); }
        static ParseOutcome fail(String message) { return new ParseOutcome(ItemStack.EMPTY, message); }
    }

    /** TACZ identity/state check result. {@code rejectReason != null} means
     *  the entry must be dropped; otherwise {@code warnings} are non-fatal
     *  (surfaced via the box loader's warn channel). */
    record GunCheckResult(@Nullable String rejectReason, List<String> warnings) {
        static GunCheckResult reject(String reason) { return new GunCheckResult(reason, List.of()); }
        static GunCheckResult warn(List<String> warnings) { return new GunCheckResult(null, List.copyOf(warnings)); }
    }

    /** Pure-NBT analysis of a TACZ gun's top-level tag compound. This platform
     *  has no DataComponent system: TACZ 1.20.1 stores the gun NBT (GunId,
     *  GunFireMode, attachments…) directly on the ItemStack's root tag. */
    record TaczNbtCheck(
            @Nullable ResourceLocation gunId,
            boolean gunIdMissing,
            @Nullable String fireModeCorrection,
            boolean fireModeWarned,
            List<String> warnings
    ) {
    }

    /**
     * Injectable TACZ validator so {@link #parseItem} orchestration stays
     * testable without a TACZ runtime. The default implementation follows the
     * repo's optional-dependency discipline: ModList gate first, TACZ classes
     * only touched inside try/catch, degrade to no-op on any failure.
     */
    interface TaczValidator {
        Optional<GunCheckResult> check(ItemStack stack);
    }

    private static final TaczValidator DEFAULT_TACZ_VALIDATOR = BoxItemCodec::validateTacz;

    static ParseOutcome parseItem(JsonElement elem) {
        return parseItem(elem, DEFAULT_TACZ_VALIDATOR);
    }

    static ParseOutcome parseItem(JsonElement elem, TaczValidator taczValidator) {
        try {
            JsonObject obj;
            if (elem.isJsonPrimitive()) {
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

            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            if (item == null) {
                CsgoBox.LOGGER.warn("Unknown item in box JSON: {}", id);
                return ParseOutcome.fail("unknown item id: " + id);
            }

            ItemStack stack = new ItemStack(item, count);

            if (obj.has("tag")) {
                try {
                    String tagStr;
                    if (obj.get("tag").isJsonPrimitive()) {
                        tagStr = obj.get("tag").getAsString();
                    } else {
                        tagStr = obj.get("tag").toString();
                    }
                    CompoundTag tag = TagParser.parseTag(tagStr);
                    stack.setTag(tag);
                } catch (Exception e) {
                    CsgoBox.LOGGER.warn("Failed to parse NBT tag for item {}: {}", id, e.getMessage());
                }
            }

            Optional<GunCheckResult> gunCheck = taczValidator.check(stack);
            if (gunCheck.isPresent()) {
                GunCheckResult result = gunCheck.get();
                if (result.rejectReason() != null) {
                    CsgoBox.LOGGER.warn("Rejecting TACZ item {}: {}", id, result.rejectReason());
                    return ParseOutcome.fail(result.rejectReason());
                }
                for (String warning : result.warnings()) {
                    CsgoBox.LOGGER.warn("TACZ item {}: {}", id, warning);
                }
            }

            return ParseOutcome.ok(stack);
        } catch (Exception e) {
            CsgoBox.LOGGER.warn("Failed to parse item JSON: {}", elem, e);
            return ParseOutcome.fail("parse failed: " + e.getMessage());
        }
    }

    /**
     * Pure-NBT analysis of a TACZ gun's top-level tag compound: whether the
     * gun identity is usable and whether GunFireMode needs normalizing. No
     * TACZ classes are touched, so this is unit-testable without a TACZ
     * runtime.
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
     * Rejects guns whose root tag is missing or lacks a usable GunId;
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
            CompoundTag tag = stack.getTag() != null ? stack.getTag().copy() : new CompoundTag();
            TaczNbtCheck check = checkTaczNbt(tag);
            if (check.gunIdMissing()) {
                String reason = tag.isEmpty()
                        ? "TACZ gun has no NBT tag (config parse failed or missing?)"
                        : "TACZ gun missing GunId in NBT tag";
                return Optional.of(GunCheckResult.reject(reason));
            }
            List<String> warnings = new ArrayList<>(check.warnings());
            if (check.fireModeCorrection() != null) {
                tag.putString(TACZ_FIRE_MODE_TAG, check.fireModeCorrection());
                stack.setTag(tag);
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
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        obj.addProperty("id", itemId.toString());
        obj.addProperty("count", stack.getCount());

        CompoundTag tag = stack.getTag();
        if (tag != null && !tag.isEmpty()) {
            try {
                obj.addProperty("tag", tag.toString());
            } catch (Exception e) {
                CsgoBox.LOGGER.warn("Failed to serialize NBT tag for item: {}", itemId, e.getMessage());
            }
        }

        return obj;
    }
}
