package com.reclizer.csgobox.fabric_1_21_1.box;

import com.google.gson.JsonElement;
import com.reclizer.csgobox.box.loader.BoxJsonCore;
import com.reclizer.csgobox.box.loader.BoxJsonCore.ItemResolution;
import com.reclizer.csgobox.box.loader.BoxJsonCore.ParsedBox;
import com.reclizer.csgobox.box.loader.BoxJsonCore.ParsedGrade;
import com.reclizer.csgobox.fabric_1_21_1.CsgoBox;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Platform adapter over the common {@link BoxJsonCore} (2026-11-21 轮51 sink):
 * supplies the Fabric config directory, {@link CsgoBox#isModLoaded}, the
 * {@link ResourceLocation} id validation, item resolution through the era
 * {@link BoxItemCodec}, and the {@link BoxRegistry} wiring. All directory
 * I/O, caching, diagnostics, top-level JSON shape and v2.x field parsing
 * live in the core, so a parsing fix lands once for all eight platforms.
 *
 * <p>Item parsing itself stays in {@link BoxItemCodec}, default config
 * generation in {@link BoxDefaults}.</p>
 */
public final class BoxJsonLoader {

    private static final Path BOXES_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("csbox");

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

        /** Parses a {@link ResourceLocation}, canonicalizing to its string form. */
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
                ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
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
