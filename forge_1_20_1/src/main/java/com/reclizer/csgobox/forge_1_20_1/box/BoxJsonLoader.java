package com.reclizer.csgobox.forge_1_20_1.box;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.reclizer.csgobox.box.BoxGrades;
import com.reclizer.csgobox.box.loader.BoxJsonCore;
import com.reclizer.csgobox.box.loader.BoxJsonCore.ItemResolution;
import com.reclizer.csgobox.box.loader.BoxJsonCore.ParsedBox;
import com.reclizer.csgobox.box.loader.BoxJsonCore.ParsedGrade;
import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Platform adapter over the common {@link BoxJsonCore} (2026-11-21 轮51 sink):
 * supplies the Forge config directory, {@link CsgoBox#isModLoaded}, the
 * {@link ResourceLocation} id validation, item resolution through the era
 * {@link BoxItemCodec}, and the {@link BoxRegistry} wiring. All directory
 * I/O, caching, diagnostics, top-level JSON shape and v2.x field parsing
 * live in the core, so a parsing fix lands once for all eight platforms.
 *
 * <p>This adapter additionally keeps the forge-line-only {@link #saveToFile}
 * (atomic JSON write-back of a live definition, legacy {@code random}/
 * {@code grade1..5} shape vs the v2.x {@code grades} array) — it has no
 * callers in the current codebase and is carried over verbatim from the
 * pre-sink loader pending a maintainer decision.</p>
 *
 * <p>Item parsing itself stays in {@link BoxItemCodec}, default config
 * generation in {@link BoxDefaults}.</p>
 */
public final class BoxJsonLoader {

    private static final Path BOXES_DIR = FMLPaths.CONFIGDIR.get().resolve("csbox");

    /** Pretty printer for {@link #saveToFile} (same config as the core's GSON). */
    private static final Gson SAVE_GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The five canonical legacy tier ids ({@link #isLegacyTierShape} / saveToFile). */
    private static final String[] GRADE_IDS = {"classified", "restricted", "mil_spec", "industrial", "consumer"};

    private static final BoxJsonCore<ItemStack, BoxDefinition> CORE =
            new BoxJsonCore<>(new Era(), new RegistryOps(), logger());

    private BoxJsonLoader() {
    }

    private static Logger logger() {
        return CsgoBox.LOGGER;
    }

    private static final class Era implements BoxJsonCore.Era<ItemStack> {
        @Override
        public Path boxesDir() {
            return BOXES_DIR;
        }

        @Override
        public boolean isModLoaded(String modId) {
            return CsgoBox.isModLoaded(modId);
        }

        /** Parses an {@link ResourceLocation}, canonicalizing to its string form. */
        @Override
        public String requireId(String value) {
            return ResourceLocation.parse(value).toString();
        }

        @Override
        public ItemResolution<ItemStack> resolveItems(JsonElement elem) {
            BoxItemCodec.ParseOutcome outcome = BoxItemCodec.parseItem(elem);
            if (!outcome.isSuccess()) {
                return ItemResolution.fail(outcome.error());
            }
            List<String> ids = new ArrayList<>(outcome.stacks().size());
            for (ItemStack stack : outcome.stacks()) {
                ids.add(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
            }
            return ItemResolution.ok(outcome.stacks(), ids, null,
                    outcome.weights(), outcome.warnings());
        }
    }

    private static final class RegistryOps implements BoxJsonCore.RegistryOps<ItemStack, BoxDefinition> {
        @Override
        public BoxDefinition toDefinition(ParsedBox<ItemStack> parsed) {
            return mapDefinition(parsed);
        }

        @Override
        public void register(BoxDefinition definition) {
            BoxRegistry.register(definition);
        }

        @Override
        public Set<String> currentBoxIds() {
            Set<String> ids = new HashSet<>();
            for (ResourceLocation id : BoxRegistry.getIds()) {
                ids.add(id.getPath());
            }
            return ids;
        }

        @Override
        public void removeBox(String boxId) {
            BoxRegistry.remove(ResourceLocation.parse("csgobox:" + boxId));
        }
    }

    /** Maps the core parse result onto the {@link BoxDefinition} builder.
     *  The ids inside are already validated; the builder's canonical
     *  constructor applies the same clamps as before the sink. */
    private static BoxDefinition mapDefinition(ParsedBox<ItemStack> p) {
        BoxDefinition.Builder builder = BoxDefinition.builder(
                ResourceLocation.parse("csgobox:" + p.boxId()), p.name());
        p.nameColor().ifPresent(builder::nameColor);
        builder.type(p.type());
        if (p.keyId() != null) {
            builder.key(ResourceLocation.parse(p.keyId()));
        }
        builder.dropRate(p.dropRate());
        for (String entityId : p.dropEntities()) {
            Float rate = p.entityDropRates().get(entityId);
            if (rate != null) {
                builder.entityDropRate(entityId, rate);
            }
            builder.dropFrom(entityId);
        }
        for (ParsedGrade<ItemStack> grade : p.grades()) {
            builder.addGrade(new GradeGroup(grade.id(), grade.displayName(), grade.color(),
                    grade.weight(), grade.stacks(), grade.prices(), grade.itemWeights()));
        }
        if (p.icon().isPresent()) {
            builder.icon(p.icon().get());
        }
        builder.discount(p.discount());
        builder.stock(p.stock());
        builder.maxPerPlayer(p.maxPerPlayer());
        builder.cooldownSeconds(p.cooldownSeconds());
        builder.permission(p.permission());
        if (p.soundId() != null) {
            builder.sound(ResourceLocation.parse(p.soundId()));
        }
        // v2.x gameplay fields (builder defaults match the absent-field
        // semantics: hideOdds=false, allowBulk=true, wear [0,1],
        // bonus=BONUS_NONE with chance 0, availability windows unbounded).
        builder.hideOdds(p.hideOdds());
        builder.allowBulk(p.allowBulk());
        for (String key : p.extraKeys()) {
            builder.extraKey(key);
        }
        builder.wearRange(p.wearMin(), p.wearMax());
        if (p.bonusMode() != BoxJsonCore.BONUS_MODE_NONE) {
            // "bonus" present: chance + mapped mode (double is the default
            // mode string, box the alternative — the core already mapped it).
            builder.bonus(p.bonusChance(),
                    p.bonusMode() == BoxJsonCore.BONUS_MODE_BOX ? "box" : "double");
        }
        builder.announceAbove(p.announceLevel());
        builder.availableFrom(p.availableFromMs());
        builder.availableUntil(p.availableUntilMs());
        builder.openCost(p.openCost());
        builder.negotiation(p.negotiationRounds(), p.negotiationTimeoutMinutes(), p.priceBias());
        if (p.upgrade() != null) {
            builder.upgrade(new BoxDefinition.UpgradeRule(p.upgrade().target(), p.upgrade().consume()));
        }
        builder.pity(p.pity());
        return builder.build();
    }

    // ------------------------------------------------------------------
    // forge-line-only: JSON write-back of a live definition
    // ------------------------------------------------------------------

    /** Inverse of {@code parseColoredName}: re-attaches the {@code "#RRGGBB "}
     *  prefix when the component carries a color. */
    private static String serializeColoredName(Component name) {
        if (name == null) return "";
        String text = name.getString();
        net.minecraft.network.chat.TextColor tc = name.getStyle().getColor();
        if (tc == null) return text;
        int rgb = tc.getValue() & 0xFFFFFF;
        return String.format("#%06X %s", rgb, text);
    }

    public static void saveToFile(BoxDefinition def) {
        try {
            Files.createDirectories(BOXES_DIR);
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to create boxes directory for save", e);
            return;
        }

        Path file = BOXES_DIR.resolve(def.id().getPath() + ".json");
        Path tempFile = BOXES_DIR.resolve(def.id().getPath() + ".json.tmp");

        JsonObject json = new JsonObject();
        json.addProperty("name", serializeColoredName(def.name()));
        json.addProperty("key", def.keyItem().toString());
        json.addProperty("drop", def.dropRate());

        if (isLegacyTierShape(def)) {
            JsonArray random = new JsonArray();
            for (int i = 4; i >= 0; i--) {
                GradeGroup g = def.findGrade(GRADE_IDS[i]).orElse(null);
                random.add(g != null ? g.weight() : 0);
            }
            json.add("random", random);
        }

        JsonArray entity = new JsonArray();
        if (!def.entityDropRates().isEmpty()) {
            for (Map.Entry<ResourceLocation, Float> entry : def.entityDropRates().entrySet()) {
                entity.add(entry.getKey().toString());
                entity.add(entry.getValue());
            }
        } else {
            for (ResourceLocation e : def.dropEntities()) {
                entity.add(e.toString());
                entity.add(1);
            }
        }
        json.add("entity", entity);

        if (isLegacyTierShape(def)) {
            for (int i = 0; i < 5; i++) {
                String gradeKey = "grade" + (5 - i);
                GradeGroup g = def.findGrade(GRADE_IDS[i]).orElse(null);
                JsonArray itemsArr = new JsonArray();
                if (g != null) {
                    for (ItemStack item : g.items()) {
                        itemsArr.add(BoxItemCodec.serializeItemStack(item));
                    }
                }
                json.add(gradeKey, itemsArr);
            }
        } else {
            writeCustomGrades(def, json);
        }

        try (Writer writer = Files.newBufferedWriter(tempFile)) {
            SAVE_GSON.toJson(json, writer);
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to save box JSON: {}", file, e);
            return;
        }

        try {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            CsgoBox.LOGGER.info("Saved box to JSON: {} -> {}", def.id(), file);
        } catch (IOException e) {
            CsgoBox.LOGGER.error("Failed to finalize box JSON: {}", file, e);
        }
    }

    /** True when the definition's tiers are a subset of the five canonical
     *  legacy tiers, i.e. it round-trips through the legacy
     *  random/grade1..5 JSON format losslessly (same ids, ≤5 tiers). */
    private static boolean isLegacyTierShape(BoxDefinition def) {
        if (def.grades().size() > BoxGrades.GRADE_COUNT) {
            return false;
        }
        for (GradeGroup g : def.grades()) {
            if (BoxGrades.gradeLevel(g.id()) <= 0) {
                return false;
            }
        }
        return true;
    }

    /** Serializes non-legacy tiers as the v2.x "grades" array (lowest tier
     *  first). Item-level fidelity matches the legacy writer path (whole
     *  stacks via the codec). */
    private static void writeCustomGrades(BoxDefinition def, JsonObject json) {
        JsonArray tiers = new JsonArray();
        for (int i = def.grades().size() - 1; i >= 0; i--) {
            GradeGroup g = def.grades().get(i);
            JsonObject tier = new JsonObject();
            tier.addProperty("id", g.id());
            if (!g.displayName().equals(g.id())) {
                tier.addProperty("name", g.displayName());
            }
            tier.addProperty("color", String.format(java.util.Locale.ROOT, "#%06X", g.color() & 0xFFFFFF));
            tier.addProperty("weight", g.weight());
            JsonArray itemsArr = new JsonArray();
            for (ItemStack item : g.items()) {
                itemsArr.add(BoxItemCodec.serializeItemStack(item));
            }
            tier.add("items", itemsArr);
            tiers.add(tier);
        }
        json.add("grades", tiers);
    }

    // ------------------------------------------------------------------
    // loader surface
    // ------------------------------------------------------------------

    private static LoadError toError(BoxJsonCore.LoadDiagnostic d) {
        return new LoadError(d.file(), d.boxId(), d.reason(), d.line(), d.column(), d.warning());
    }

    public static void loadAll() {
        CORE.loadAll();
    }

    public static void reloadPreserving() {
        CORE.reloadPreserving();
    }

    public static List<LoadError> getLastLoadErrors() {
        List<LoadError> errors = new ArrayList<>(CORE.getDiagnostics().size());
        for (BoxJsonCore.LoadDiagnostic d : CORE.getDiagnostics()) {
            errors.add(toError(d));
        }
        return List.copyOf(errors);
    }

    public static ValidateResult validateFile(Path file) {
        BoxJsonCore.ValidateResult<BoxDefinition> r = CORE.validateFile(file);
        List<LoadError> errors = new ArrayList<>(r.errors().size());
        for (BoxJsonCore.LoadDiagnostic d : r.errors()) {
            errors.add(toError(d));
        }
        return new ValidateResult(r.ok(), r.definition(), errors);
    }

    public static ValidateResult validatePriceTable() {
        BoxJsonCore.ValidateResult<BoxDefinition> r = CORE.validatePriceTable();
        List<LoadError> errors = new ArrayList<>(r.errors().size());
        for (BoxJsonCore.LoadDiagnostic d : r.errors()) {
            errors.add(toError(d));
        }
        return new ValidateResult(r.ok(), r.definition(), errors);
    }

    public static boolean hasLoadErrors() {
        return CORE.hasLoadErrors();
    }

    /** Result of a dry-run validation: whether the file parsed, the definition
     *  (null on failure) and the diagnostics that would be reported. */
    public record ValidateResult(boolean ok, BoxDefinition definition, List<LoadError> errors) {
    }
}
