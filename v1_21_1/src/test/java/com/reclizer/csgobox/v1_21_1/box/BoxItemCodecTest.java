package com.reclizer.csgobox.v1_21_1.box;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.reclizer.csgobox.v1_21_1.box.BoxItemCodec.DecodeResult;
import com.reclizer.csgobox.v1_21_1.box.BoxItemCodec.GunCheckResult;
import com.reclizer.csgobox.v1_21_1.box.BoxItemCodec.ParseOutcome;
import com.reclizer.csgobox.v1_21_1.box.BoxItemCodec.TaczNbtCheck;
import com.reclizer.csgobox.v1_21_1.box.BoxItemCodec.TaczValidator;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BoxItemCodec} TACZ NBT handling. TACZ classes are
 * never touched: pure functions ({@code decodeComponents}, {@code checkTaczNbt})
 * and a fake {@link TaczValidator} keep the tests independent of the TACZ
 * runtime; only Minecraft's own registries are bootstrapped.
 */
class BoxItemCodecTest {

    /** No TACZ check at all — item passes through unchanged. */
    private static final TaczValidator NO_TACZ = stack -> Optional.empty();

    @BeforeAll
    static void bootstrapRegistries() {
        // Populates BuiltInRegistries.ITEM etc. so parseItem can resolve ids.
        // Idempotent: guarded by an internal isBootstrapped flag.
        installFakeFmlEnvironment();
        // DataFixers (pulled in by EntityType init during Bootstrap) needs a
        // game version; DetectedVersion.BUILT_IN carries the real 1.21.1
        // values (dataVersion 3955, packs 34/48) baked in.
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    /**
     * Minecraft's {@code FeatureFlags} initializer calls
     * {@code FeatureFlagLoader.loadModdedFlags}, which needs an FML
     * {@code LoadingModList}. None of that exists in a plain JUnit JVM, so
     * install an empty one (no mods => no modded flags) via its public
     * {@code of(...)} factory, which also sets the singleton {@code INSTANCE}.
     */
    private static void installFakeFmlEnvironment() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    // ------------------------------------------------------------------
    // decodeComponents: per-entry decoding
    // ------------------------------------------------------------------

    @Test
    void decodeComponents_allValid_returnsFullPatch() {
        JsonObject customData = new JsonObject();
        customData.addProperty("GunId", "tacz:ak47");
        customData.addProperty("GunFireMode", "AUTO");
        customData.addProperty("GunCurrentAmmoCount", 30);
        customData.addProperty("HasBulletInBarrel", 1);
        JsonObject scope = new JsonObject();
        scope.addProperty("ItemId", "tacz:scope_8x");
        customData.add("AttachmentSCOPE", scope);

        JsonObject json = new JsonObject();
        json.add("minecraft:max_stack_size", new JsonPrimitive(32));
        json.add("minecraft:custom_data", customData);

        DecodeResult dr = BoxItemCodec.decodeComponents(json);

        assertFalse(dr.hasErrors());
        assertEquals(32, dr.patch().get(DataComponents.MAX_STACK_SIZE).map(Integer::intValue).orElse(-1));

        CustomData cd = dr.patch().get(DataComponents.CUSTOM_DATA).orElse(null);
        assertNotNull(cd);
        CompoundTag tag = cd.copyTag();
        assertEquals("tacz:ak47", tag.getString("GunId"));
        assertEquals("AUTO", tag.getString("GunFireMode"));
        assertEquals(30, tag.getInt("GunCurrentAmmoCount"));
        assertEquals(1, tag.getInt("HasBulletInBarrel"));
        assertEquals("tacz:scope_8x", tag.getCompound("AttachmentSCOPE").getString("ItemId"));
    }

    @Test
    void decodeComponents_badKeyAndBadValue_keepGoodAndCollectErrors() {
        JsonObject json = new JsonObject();
        json.add("minecraft:max_stack_size", new JsonPrimitive(16));
        json.add("minecraft:no_such_component", new JsonPrimitive(1));
        json.add("minecraft:rarity", new JsonPrimitive("NOT_A_RARITY"));

        DecodeResult dr = BoxItemCodec.decodeComponents(json);

        // Good entry survives the two failures.
        assertEquals(16, dr.patch().get(DataComponents.MAX_STACK_SIZE).map(Integer::intValue).orElse(-1));
        assertEquals(2, dr.errors().size());
        assertTrue(dr.errors().get(0).contains("no_such_component"),
                () -> "expected unknown-key error, got: " + dr.errors().get(0));
        assertTrue(dr.errors().get(1).contains("rarity"),
                () -> "expected bad-value error, got: " + dr.errors().get(1));
    }

    @Test
    void decodeComponents_nullValue_removesComponent() {
        JsonObject json = new JsonObject();
        json.add("minecraft:max_stack_size", JsonNull.INSTANCE);

        DecodeResult dr = BoxItemCodec.decodeComponents(json);

        assertFalse(dr.hasErrors());
        assertTrue(dr.patch().get(DataComponents.MAX_STACK_SIZE).isEmpty());
    }

    @Test
    void decodeComponents_nonObjectComponents_emitsWarning() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "minecraft:stone");
        json.add("components", new JsonPrimitive("nope"));

        ParseOutcome out = BoxItemCodec.parseItem(json, NO_TACZ);

        assertTrue(out.isSuccess());
        assertTrue(out.warnings().stream().anyMatch(w -> w.contains("components")),
                () -> "expected components warning, got: " + out.warnings());
    }

    // ------------------------------------------------------------------
    // checkTaczNbt: pure NBT analysis
    // ------------------------------------------------------------------

    @Test
    void checkTaczNbt_missingGunId_isMissing() {
        TaczNbtCheck check = BoxItemCodec.checkTaczNbt(new CompoundTag());
        assertTrue(check.gunIdMissing());
        assertNull(check.gunId());
        assertTrue(check.warnings().isEmpty());
    }

    @Test
    void checkTaczNbt_emptyGunId_isMissing() {
        CompoundTag tag = new CompoundTag();
        tag.putString("GunId", "tacz:empty");
        TaczNbtCheck check = BoxItemCodec.checkTaczNbt(tag);
        assertTrue(check.gunIdMissing());
        assertNull(check.gunId());
    }

    @Test
    void checkTaczNbt_malformedGunId_isMissing() {
        CompoundTag tag = new CompoundTag();
        tag.putString("GunId", "not a valid id!!");
        TaczNbtCheck check = BoxItemCodec.checkTaczNbt(tag);
        assertTrue(check.gunIdMissing());
    }

    @Test
    void checkTaczNbt_validGun_noCorrection() {
        CompoundTag tag = new CompoundTag();
        tag.putString("GunId", "tacz:ak47");
        tag.putString("GunFireMode", "AUTO");
        TaczNbtCheck check = BoxItemCodec.checkTaczNbt(tag);
        assertFalse(check.gunIdMissing());
        assertEquals("tacz:ak47", check.gunId().toString());
        assertNull(check.fireModeCorrection());
        assertFalse(check.fireModeWarned());
    }

    @Test
    void checkTaczNbt_lowercaseFireMode_normalizedWithoutWarning() {
        CompoundTag tag = new CompoundTag();
        tag.putString("GunId", "tacz:ak47");
        tag.putString("GunFireMode", "semi");
        TaczNbtCheck check = BoxItemCodec.checkTaczNbt(tag);
        assertEquals("SEMI", check.fireModeCorrection());
        assertFalse(check.fireModeWarned());
    }

    @Test
    void checkTaczNbt_invalidFireMode_resetToUnknownWithWarning() {
        CompoundTag tag = new CompoundTag();
        tag.putString("GunId", "tacz:ak47");
        tag.putString("GunFireMode", "BURSTMODE");
        TaczNbtCheck check = BoxItemCodec.checkTaczNbt(tag);
        assertEquals("UNKNOWN", check.fireModeCorrection());
        assertTrue(check.fireModeWarned());
        assertFalse(check.warnings().isEmpty());
    }

    // ------------------------------------------------------------------
    // legacy "tag" migration
    // ------------------------------------------------------------------

    @Test
    void parseItem_legacyGunTag_migratesIntoCustomData() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "minecraft:stone");
        json.addProperty("tag", "{GunId:\"tacz:ak47\",GunFireMode:\"AUTO\"}");

        ParseOutcome out = BoxItemCodec.parseItem(json, NO_TACZ);

        assertTrue(out.isSuccess());
        CustomData cd = out.stack().get(DataComponents.CUSTOM_DATA);
        assertNotNull(cd, "legacy GunId tag must be wrapped into minecraft:custom_data");
        assertEquals("tacz:ak47", cd.copyTag().getString("GunId"));
        assertEquals("AUTO", cd.copyTag().getString("GunFireMode"));
        assertTrue(out.warnings().isEmpty());
    }

    @Test
    void parseItem_legacyGunTagWithAttachments_warnsMigrationLoss() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "minecraft:stone");
        json.addProperty("tag", "{GunId:\"tacz:ak47\",AttachmentSCOPE:\"tacz:scope_8x\"}");

        ParseOutcome out = BoxItemCodec.parseItem(json, NO_TACZ);

        assertTrue(out.isSuccess());
        assertNotNull(out.stack().get(DataComponents.CUSTOM_DATA));
        assertTrue(out.warnings().stream().anyMatch(w -> w.toLowerCase().contains("attachment")),
                () -> "expected attachment migration warning, got: " + out.warnings());
    }

    @Test
    void parseItem_fullComponentNameTag_parsesViaOriginalPath() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "minecraft:stone");
        json.addProperty("tag", "{\"minecraft:max_stack_size\":5}");

        ParseOutcome out = BoxItemCodec.parseItem(json, NO_TACZ);

        assertTrue(out.isSuccess());
        Integer maxStack = out.stack().get(DataComponents.MAX_STACK_SIZE);
        assertEquals(5, maxStack == null ? -1 : maxStack);
    }

    // ------------------------------------------------------------------
    // parseItem orchestration with fake TaczValidator
    // ------------------------------------------------------------------

    @Test
    void parseItem_taczRejected_returnsErrorAndEmptyStack() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "minecraft:stone");
        TaczValidator rejecting = stack ->
                Optional.of(GunCheckResult.reject("gun has no identity"));

        ParseOutcome out = BoxItemCodec.parseItem(json, rejecting);

        assertFalse(out.isSuccess());
        assertTrue(out.error().contains("gun has no identity"));
        assertTrue(out.stack().isEmpty());
    }

    @Test
    void parseItem_taczWarned_keepsStackAndSurfacesWarnings() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "minecraft:stone");
        TaczValidator warning = stack ->
                Optional.of(GunCheckResult.warn(List.of("gun pack not loaded")));

        ParseOutcome out = BoxItemCodec.parseItem(json, warning);

        assertTrue(out.isSuccess());
        assertFalse(out.stack().isEmpty());
        assertTrue(out.warnings().contains("gun pack not loaded"));
    }

    @Test
    void parseItem_nonTacz_cleanPassThrough() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "minecraft:stone");
        json.addProperty("count", 3);

        ParseOutcome out = BoxItemCodec.parseItem(json, NO_TACZ);

        assertTrue(out.isSuccess());
        assertEquals(3, out.stack().getCount());
        assertTrue(out.warnings().isEmpty());
    }

    @Test
    void parseItem_badComponentValue_keepsGoodComponentsAndWarns() {
        // A malformed value must not drag down the whole patch: the good
        // custom_data (TACZ GunId carrier) survives, the bad entry is
        // reported as a warning.
        JsonObject components = new JsonObject();
        components.add("minecraft:custom_data", gunCustomData("tacz:ak47"));
        components.add("minecraft:max_stack_size", new JsonPrimitive("not-a-number"));
        JsonObject json = new JsonObject();
        json.addProperty("id", "minecraft:stone");
        json.add("components", components);

        ParseOutcome out = BoxItemCodec.parseItem(json, NO_TACZ);

        assertTrue(out.isSuccess());
        CustomData cd = out.stack().get(DataComponents.CUSTOM_DATA);
        assertNotNull(cd, "good component must survive a sibling component failure");
        assertEquals("tacz:ak47", cd.copyTag().getString("GunId"));
        assertTrue(out.warnings().stream().anyMatch(w -> w.contains("max_stack_size")),
                () -> "expected per-component failure warning, got: " + out.warnings());
    }

    @Test
    void parseItem_missingGunId_bareGunRejectedNotSilentlyKept() {
        // A gun whose custom_data is present but carries no usable GunId must
        // NOT be silently delivered as a bare gun: the validator rejects the
        // entry, mirroring the production TACZ validator's checkTaczNbt gate.
        JsonObject customData = new JsonObject();
        customData.addProperty("GunDisplayId", "tacz:ak47");
        JsonObject components = new JsonObject();
        components.add("minecraft:custom_data", customData);
        JsonObject json = new JsonObject();
        json.addProperty("id", "minecraft:stone");
        json.add("components", components);

        TaczValidator identityValidator = stack -> {
            CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
            return cd != null && cd.copyTag().contains("GunId")
                    ? Optional.of(GunCheckResult.warn(List.of()))
                    : Optional.of(GunCheckResult.reject("TACZ gun missing GunId in custom_data"));
        };

        ParseOutcome out = BoxItemCodec.parseItem(json, identityValidator);

        assertFalse(out.isSuccess());
        assertTrue(out.error().contains("GunId"));
        assertTrue(out.stack().isEmpty());
    }

    private static JsonObject gunCustomData(String gunId) {
        JsonObject customData = new JsonObject();
        customData.addProperty("GunId", gunId);
        customData.addProperty("GunFireMode", "AUTO");
        return customData;
    }

    // ------------------------------------------------------------------
    // serialize round trip
    // ------------------------------------------------------------------

    @Test
    void serializeItemStack_roundTrip_preservesTaczCustomData() {
        JsonObject customData = new JsonObject();
        customData.addProperty("GunId", "tacz:ak47");
        customData.addProperty("GunFireMode", "AUTO");
        customData.addProperty("GunCurrentAmmoCount", 30);
        JsonObject components = new JsonObject();
        components.add("minecraft:custom_data", customData);
        components.add("minecraft:max_stack_size", new JsonPrimitive(32));
        JsonObject json = new JsonObject();
        json.addProperty("id", "minecraft:stone");
        json.add("components", components);

        ParseOutcome out = BoxItemCodec.parseItem(json, NO_TACZ);
        assertTrue(out.isSuccess());

        JsonObject serialized = BoxItemCodec.serializeItemStack(out.stack());

        assertTrue(serialized.has("components"),
                () -> "serialized item lost its components: " + serialized);
        JsonObject serializedComponents = serialized.getAsJsonObject("components");
        assertEquals(32, serializedComponents.get("minecraft:max_stack_size").getAsInt());
        assertEquals("tacz:ak47",
                serializedComponents.getAsJsonObject("minecraft:custom_data")
                        .get("GunId").getAsString());
    }
}
