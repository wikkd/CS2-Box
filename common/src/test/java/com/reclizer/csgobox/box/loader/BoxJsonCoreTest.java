package com.reclizer.csgobox.box.loader;

import com.google.gson.JsonElement;
import com.reclizer.csgobox.box.PriceRange;
import com.reclizer.csgobox.box.loader.BoxJsonCore.ItemResolution;
import com.reclizer.csgobox.box.loader.BoxJsonCore.LoadDiagnostic;
import com.reclizer.csgobox.box.loader.BoxJsonCore.ParsedBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct JUnit coverage of the era-neutral loader core (轮51 sink) — the box
 * JSON parsing itself, previously only exercised through the schema validator
 * and platform compile/lifecycle runs. The fake era validates ids with the MC
 * id grammar and resolves items from plain {@code id} / {@code tag} /
 * {@code loot_table} entries; the "definition" type is the parsed box itself.
 *
 * <p>Parse assertions go through {@link BoxJsonCore#validateFile}, which runs
 * the real per-file pipeline (price table load, diagnostics, rollback) and
 * hands back the produced {@link ParsedBox}.</p>
 */
final class BoxJsonCoreTest {

    private static final Logger LOG = LoggerFactory.getLogger("csgobox-test");

    @TempDir
    Path boxesDir;

    // ------------------------------------------------------------------
    // fake era / registry
    // ------------------------------------------------------------------

    private static final class FakeEra implements BoxJsonCore.Era<String> {
        final Path dir;
        boolean modLoaded = true;

        FakeEra(Path dir) {
            this.dir = dir;
        }

        @Override
        public Path boxesDir() {
            return dir;
        }

        @Override
        public boolean isModLoaded(String modId) {
            return modLoaded;
        }

        @Override
        public String requireId(String value) {
            // NB: '-' must stay last in the class — ":-/" would be a reversed
            // range and PatternSyntaxException would fail every requireId call.
            if (value == null || !value.contains(":") || !value.matches("[a-z0-9_.:/-]+")) {
                throw new IllegalArgumentException("Not a valid resource id: " + value);
            }
            return value;
        }

        @Override
        public ItemResolution<String> resolveItems(JsonElement elem) {
            if (elem.isJsonObject() && elem.getAsJsonObject().has("loot_table")) {
                String table = elem.getAsJsonObject().get("loot_table").getAsString();
                return ItemResolution.ok(List.of("loot:" + table), List.of("loot:" + table),
                        null, List.of(1), List.of());
            }
            if (elem.isJsonObject() && elem.getAsJsonObject().has("tag")) {
                // "#ns:tag" expands to two members, one of them unpriced.
                return ItemResolution.ok(List.of("minecraft:wooden_sword", "minecraft:diamond_sword"),
                        List.of("minecraft:wooden_sword", "minecraft:diamond_sword"),
                        null, List.of(1, 1), List.of());
            }
            String id = elem.isJsonObject() && elem.getAsJsonObject().has("id")
                    ? elem.getAsJsonObject().get("id").getAsString() : null;
            if (id == null || id.isBlank()) {
                return ItemResolution.fail("missing or blank 'id'");
            }
            int weight = elem.isJsonObject() && elem.getAsJsonObject().has("weight")
                    ? elem.getAsJsonObject().get("weight").getAsInt() : 1;
            return ItemResolution.ok(List.of(id), List.of(id), null, List.of(weight), List.of());
        }
    }

    /** Registry storing parsed boxes by file-stem id. */
    private static final class FakeRegistry
            implements BoxJsonCore.RegistryOps<String, ParsedBox<String>> {
        final Map<String, ParsedBox<String>> registered = new LinkedHashMap<>();

        @Override
        public ParsedBox<String> toDefinition(ParsedBox<String> parsed) {
            return parsed;
        }

        @Override
        public void register(ParsedBox<String> definition) {
            registered.put(definition.boxId(), definition);
        }

        @Override
        public Set<String> currentBoxIds() {
            return registered.keySet();
        }

        @Override
        public void removeBox(String boxId) {
            registered.remove(boxId);
        }
    }

    private BoxJsonCore<String, ParsedBox<String>> newCore() {
        return new BoxJsonCore<>(new FakeEra(boxesDir), new FakeRegistry(), LOG);
    }

    private static void write(Path dir, String name, String json) throws Exception {
        Files.writeString(dir.resolve(name), json, StandardCharsets.UTF_8);
    }

    private static void writePriceTable(Path dir, String json) throws Exception {
        write(dir, "_prices.json", json);
    }

    private static final String PRICES = """
            {
              "minecraft:stone": 10,
              "minecraft:diamond": [5, 8],
              "minecraft:diamond_sword": 100,
              "minecraft:dirt": 1
            }
            """;

    /** Parses one file through the real validate pipeline. */
    private record Parsed(BoxJsonCore.ValidateResult<ParsedBox<String>> result) {
        Optional<ParsedBox<String>> box() {
            return Optional.ofNullable(result().definition());
        }

        List<LoadDiagnostic> errors() {
            return result().errors();
        }
    }

    private Parsed parse(String fileName) throws Exception {
        return new Parsed(newCore().validateFile(boxesDir.resolve(fileName)));
    }

    // ------------------------------------------------------------------
    // parse: custom grades, full v2.x field set
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a full-featured custom-grades box parses every v2.x field")
    void customGradesFullFeature() throws Exception {
        writePriceTable(boxesDir, PRICES);
        write(boxesDir, "test_box.json", """
                {
                  "name": "#FF5555 测试箱",
                  "type": "csbox",
                  "key": "csgobox:csgo_key1",
                  "drop": 0.25,
                  "entity": ["minecraft:zombie", 0.5, "minecraft:skeleton", 0.25],
                  "icon": "1001",
                  "discount": 0.2,
                  "stock": 10,
                  "max_per_player": 5,
                  "cooldown_seconds": 300,
                  "permission": "csbox.vip",
                  "sound": "minecraft:block.note_block.pling",
                  "hide_odds": true,
                  "allow_bulk": false,
                  "keys": ["csgobox:csgo_key2", "  csgobox:csgo_key3  "],
                  "wear": [0.2, 0.8],
                  "bonus": {"chance": 0.5, "mode": "box"},
                  "announce_above": "rare",
                  "available_from": "2026-12-24T00:00:00+08:00",
                  "available_until": "2027-01-01T00:00:00Z",
                  "open_cost": 42,
                  "negotiation": {"rounds": 3, "timeout_minutes": 30, "price_bias": 0.5},
                  "upgrade": {"target": "bigger_box", "consume": 2},
                  "pity": {"grade": "rare", "every": 20},
                  "grades": [
                    {"id": "common", "name": "普通", "weight": 60, "items": [{"id": "minecraft:stone"}]},
                    {"id": "rare", "weight": 40, "items": [{"id": "minecraft:diamond", "weight": 3}]}
                  ]
                }
                """);
        Parsed p = parse("test_box.json");
        ParsedBox<String> box = p.box().orElseThrow();
        List<LoadDiagnostic> errors = p.errors();

        assertEquals("test_box", box.boxId());
        assertEquals("测试箱", box.name());
        assertEquals(OptionalInt.of(0xFF5555), box.nameColor());
        assertEquals("csbox", box.type());
        assertEquals("csgobox:csgo_key1", box.keyId());
        assertEquals(0.25F, box.dropRate(), 1e-6);
        assertEquals(List.of("minecraft:zombie", "minecraft:skeleton"), box.dropEntities());
        assertEquals(0.5F, box.entityDropRates().get("minecraft:zombie"), 1e-6);
        assertEquals(0.25F, box.entityDropRates().get("minecraft:skeleton"), 1e-6);
        assertEquals(Optional.of("1001"), box.icon());
        assertEquals(0.2F, box.discount(), 1e-6);
        assertEquals(10, box.stock());
        assertEquals(5, box.maxPerPlayer());
        assertEquals(300, box.cooldownSeconds());
        assertEquals("csbox.vip", box.permission());
        assertEquals("minecraft:block.note_block.pling", box.soundId());
        assertTrue(box.hideOdds());
        assertFalse(box.allowBulk());
        assertEquals(List.of("csgobox:csgo_key2", "csgobox:csgo_key3"), box.extraKeys());
        assertEquals(0.2F, box.wearMin(), 1e-6);
        assertEquals(0.8F, box.wearMax(), 1e-6);
        assertEquals(0.5F, box.bonusChance(), 1e-6);
        assertEquals(BoxJsonCore.BONUS_MODE_BOX, box.bonusMode());
        // tiers are stored highest → lowest (the custom array is lowest first)
        assertEquals("rare", box.grades().get(0).id());
        assertEquals("common", box.grades().get(1).id());
        assertEquals(40, box.grades().get(0).weight());
        assertEquals(60, box.grades().get(1).weight());
        assertEquals(2, box.announceLevel()); // tierLevelOf("rare") = levelAt(2, 0) = 2
        assertEquals(OffsetDateTime.parse("2026-12-24T00:00:00+08:00").toInstant().toEpochMilli(),
                box.availableFromMs());
        assertEquals(OffsetDateTime.parse("2027-01-01T00:00:00Z").toInstant().toEpochMilli(),
                box.availableUntilMs());
        assertEquals(42, box.openCost());
        assertEquals(3, box.negotiationRounds());
        assertEquals(30, box.negotiationTimeoutMinutes());
        assertEquals(0.5F, box.priceBias(), 1e-6);
        assertEquals("bigger_box", box.upgrade().target());
        assertEquals(2, box.upgrade().consume());
        assertNotNull(box.pity());
        assertEquals(2, box.pity().targetLevel());
        assertEquals(20, box.pity().every());
        // prices baked from the table: diamond = range [5, 8]
        PriceRange diamondPrice = box.grades().get(0).prices().get(0);
        assertFalse(diamondPrice.isUnpriced());
        assertEquals(5, diamondPrice.min());
        assertEquals(8, diamondPrice.max());
        assertEquals(3, box.grades().get(0).itemWeights().get(0));
        assertTrue(errors.isEmpty());
    }

    // ------------------------------------------------------------------
    // parse: legacy grades
    // ------------------------------------------------------------------

    @Test
    @DisplayName("legacy grade1..5 format maps to five tiers highest → lowest")
    void legacyGradesMapFiveTiers() throws Exception {
        writePriceTable(boxesDir, PRICES);
        write(boxesDir, "legacy_box.json", """
                {
                  "name": "legacy",
                  "random": [60, 25, 10, 4, 1],
                  "grade5": [{"id": "minecraft:diamond"}],
                  "grade3": [{"id": "minecraft:stone"}],
                  "grade2": [],
                  "grade1": [{"id": "minecraft:dirt"}]
                }
                """);
        Parsed p = parse("legacy_box.json");
        ParsedBox<String> box = p.box().orElseThrow();
        // tiers created highest → lowest; ABSENT tiers (grade4) are skipped
        // silently, present-but-empty tiers (grade2) warn
        assertEquals("classified", box.grades().get(0).id());
        assertEquals("mil_spec", box.grades().get(1).id());
        assertEquals("consumer", box.grades().get(2).id());
        // "random" is ordered grade1 → grade5: [60,25,10,4,1] gives
        // grade5(classified)=1, grade3(mil_spec)=10, grade1(consumer)=60.
        assertEquals(1, box.grades().get(0).weight());
        assertEquals(10, box.grades().get(1).weight());
        assertEquals(60, box.grades().get(2).weight());
        assertTrue(p.errors().stream().anyMatch(LoadDiagnostic::warning),
                "a present-but-empty tier warns");
    }

    @Test
    @DisplayName("explicit weight 0 stays 0; negative weight falls back to the default")
    void legacyWeightsEdgeCases() throws Exception {
        writePriceTable(boxesDir, PRICES);
        write(boxesDir, "weights_box.json", """
                {
                  "name": "w",
                  "random": [0, -5, 10, 4, 1],
                  "grade1": [{"id": "minecraft:dirt"}]
                }
                """);
        Parsed p = parse("weights_box.json");
        ParsedBox<String> box = p.box().orElseThrow();
        // grade1 slot (random[0]) is an EXPLICIT 0 — that tier never rolls;
        // grade2 slot (random[1]) is negative → falls back to default 625.
        assertEquals(0, box.grades().get(0).weight());
        assertTrue(p.errors().stream()
                .map(LoadDiagnostic::reason)
                .anyMatch(r -> r.contains("negative weight")));
    }

    // ------------------------------------------------------------------
    // gating / type rules
    // ------------------------------------------------------------------

    @Test
    @DisplayName("enabled:false skips silently; missing requires skips with a clear error")
    void gatingAndRequires() throws Exception {
        writePriceTable(boxesDir, PRICES);
        write(boxesDir, "off_box.json", "{\"name\":\"off\", \"enabled\": false, "
                + "\"grades\": [{\"id\": \"a\", \"items\": [{\"id\": \"minecraft:stone\"}]}]}");
        write(boxesDir, "dep_box.json", "{\"name\":\"dep\", \"requires\": [\"apotheosis\"], "
                + "\"grades\": [{\"id\": \"a\", \"items\": [{\"id\": \"minecraft:stone\"}]}]}");
        // Era with the optional dependency missing, so the requires gate fires.
        FakeEra era = new FakeEra(boxesDir);
        era.modLoaded = false;
        BoxJsonCore<String, ParsedBox<String>> core =
                new BoxJsonCore<>(era, new FakeRegistry(), LOG);
        Parsed off = new Parsed(core.validateFile(boxesDir.resolve("off_box.json")));
        assertTrue(off.box().isEmpty());
        assertTrue(off.errors().isEmpty(), "disabled box is not an error");
        Parsed dep = new Parsed(core.validateFile(boxesDir.resolve("dep_box.json")));
        assertTrue(dep.box().isEmpty());
        assertTrue(dep.errors().stream()
                .map(LoadDiagnostic::reason)
                .anyMatch(r -> r.contains("required mod 'apotheosis'")));
    }

    @Test
    @DisplayName("terminal.json without type:terminal refuses to load")
    void terminalTypeRule() throws Exception {
        writePriceTable(boxesDir, PRICES);
        write(boxesDir, "terminal.json", "{\"name\": \"terminal\", \"key\": \"minecraft:air\"}");
        Parsed p = parse("terminal.json");
        assertTrue(p.box().isEmpty());
        assertTrue(p.errors().stream()
                .map(LoadDiagnostic::reason)
                .anyMatch(r -> r.contains("terminal.json must declare")));
    }

    @Test
    @DisplayName("terminal boxes have no key even when the JSON carries one")
    void terminalIgnoresKey() throws Exception {
        writePriceTable(boxesDir, PRICES);
        write(boxesDir, "terminal2.json", "{\"name\": \"t\", \"type\": \"terminal\", "
                + "\"key\": \"csgobox:csgo_key1\", "
                + "\"grades\": [{\"id\": \"a\", \"items\": [{\"id\": \"minecraft:stone\"}]}]}");
        Parsed p = parse("terminal2.json");
        assertTrue(p.box().isPresent());
        assertNull(p.box().get().keyId());
    }

    @Test
    @DisplayName("invalid file names are rejected with a renaming hint")
    void fileNameValidation() throws Exception {
        writePriceTable(boxesDir, PRICES);
        write(boxesDir, "My Box.json", "{\"name\": \"bad\"}");
        Parsed p = parse("My Box.json");
        assertTrue(p.box().isEmpty());
        assertTrue(p.errors().stream()
                .map(LoadDiagnostic::reason)
                .anyMatch(r -> r.contains("Invalid box id from file name")));
    }

    @Test
    @DisplayName("invalid key ids surface with the offending field name")
    void invalidIdFieldNames() throws Exception {
        writePriceTable(boxesDir, PRICES);
        write(boxesDir, "badid_box.json", "{\"name\": \"b\", \"key\": \"NOT AN ID\", "
                + "\"grades\": [{\"id\": \"a\", \"items\": [{\"id\": \"minecraft:stone\"}]}]}");
        Parsed p = parse("badid_box.json");
        assertTrue(p.box().isEmpty());
        assertTrue(p.errors().stream()
                .map(LoadDiagnostic::reason)
                .anyMatch(r -> r.contains("Field 'key'")));
    }

    // ------------------------------------------------------------------
    // pricing
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an unpriced id entry rejects the whole box")
    void unpricedIdRejectsBox() throws Exception {
        writePriceTable(boxesDir, PRICES);
        write(boxesDir, "unpriced_box.json", "{\"name\": \"u\", "
                + "\"grades\": [{\"id\": \"a\", \"items\": [{\"id\": \"minecraft:wooden_sword\"}]}]}");
        Parsed p = parse("unpriced_box.json");
        assertTrue(p.box().isEmpty());
        assertTrue(p.errors().stream()
                .map(LoadDiagnostic::reason)
                .anyMatch(r -> r.contains("物品未定价")));
    }

    @Test
    @DisplayName("loot_table entries stay unpriced without rejecting the box")
    void lootTableEntriesStayUnpriced() throws Exception {
        writePriceTable(boxesDir, PRICES);
        write(boxesDir, "loot_box.json", "{\"name\": \"l\", "
                + "\"grades\": [{\"id\": \"a\", \"items\": [{\"loot_table\": \"minecraft:chests/simple\"}, "
                + "{\"id\": \"minecraft:stone\"}]}]}");
        Parsed p = parse("loot_box.json");
        ParsedBox<String> box = p.box().orElseThrow();
        assertTrue(box.grades().get(0).prices().get(0).isUnpriced());
        assertTrue(p.errors().stream()
                .map(LoadDiagnostic::reason)
                .anyMatch(r -> r.contains("loot_table 条目没有固定物品 id")));
    }

    // ------------------------------------------------------------------
    // pity warning
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pity targeting a zero-weight tier warns (guaranteed roll can never fire)")
    void pityZeroWeightWarning() throws Exception {
        writePriceTable(boxesDir, PRICES);
        // Custom grades array is lowest tier first: "low" is the lowest tier,
        // "top" the highest — carrying weight 0, so a pity on it never fires.
        write(boxesDir, "pity_box.json", "{\"name\": \"p\", "
                + "\"grades\": ["
                + "{\"id\": \"low\", \"weight\": 100, \"items\": [{\"id\": \"minecraft:dirt\"}]}, "
                + "{\"id\": \"top\", \"weight\": 0, \"items\": [{\"id\": \"minecraft:stone\"}]}], "
                + "\"pity\": {\"grade\": \"top\", \"every\": 10}}");
        Parsed p = parse("pity_box.json");
        ParsedBox<String> box = p.box().orElseThrow();
        assertNotNull(box.pity());
        assertTrue(p.errors().stream()
                .map(LoadDiagnostic::reason)
                .anyMatch(r -> r.contains("zero weight")));
    }

    // ------------------------------------------------------------------
    // price table
    // ------------------------------------------------------------------

    @Test
    @DisplayName("malformed price-table entries report issues; missing table just unprices")
    void priceTableHandling() throws Exception {
        write(boxesDir, "no_table_box.json", "{\"name\": \"n\", "
                + "\"grades\": [{\"id\": \"a\", \"items\": [{\"id\": \"minecraft:stone\"}]}]}");
        // No _prices.json at all: stone is unpriced → box rejected, no crash.
        Parsed none = parse("no_table_box.json");
        assertTrue(none.box().isEmpty());

        // Malformed table: the entry reports an issue and validation fails.
        writePriceTable(boxesDir, "{\"minecraft:stone\": \"free\"}");
        BoxJsonCore.ValidateResult<ParsedBox<String>> v =
                newCore().validatePriceTable();
        assertFalse(v.ok());
        assertTrue(v.errors().stream().map(LoadDiagnostic::reason)
                .anyMatch(r -> r.contains("PriceTable")));
    }

    // ------------------------------------------------------------------
    // lifecycle: loadAll / reloadPreserving / validate rollback
    // ------------------------------------------------------------------

    @Test
    @DisplayName("loadAll registers parseable boxes; reloadPreserving drops removed files")
    void loadAndReloadLifecycle() throws Exception {
        writePriceTable(boxesDir, PRICES);
        write(boxesDir, "box_a.json", "{\"name\": \"a\", "
                + "\"grades\": [{\"id\": \"a\", \"items\": [{\"id\": \"minecraft:stone\"}]}]}");
        write(boxesDir, "_metadata.json", "{}"); // underscore files are never boxes
        write(boxesDir, "notes.txt", "not json");

        FakeRegistry registry = new FakeRegistry();
        BoxJsonCore<String, ParsedBox<String>> core =
                new BoxJsonCore<>(new FakeEra(boxesDir), registry, LOG);
        core.loadAll();
        assertEquals(Set.of("box_a"), registry.registered.keySet());

        // box_a removed, box_b added: reloadPreserving swaps them in place.
        Files.delete(boxesDir.resolve("box_a.json"));
        write(boxesDir, "box_b.json", "{\"name\": \"b\", "
                + "\"grades\": [{\"id\": \"a\", \"items\": [{\"id\": \"minecraft:stone\"}]}]}");
        core.reloadPreserving();
        assertFalse(registry.registered.containsKey("box_a"));
        assertTrue(registry.registered.containsKey("box_b"));
    }

    @Test
    @DisplayName("validateFile never pollutes the live diagnostics list")
    void validateFileRollback() throws Exception {
        writePriceTable(boxesDir, PRICES);
        write(boxesDir, "broken.json", "{\"name\": ");
        BoxJsonCore<String, ParsedBox<String>> core = newCore();
        BoxJsonCore.ValidateResult<ParsedBox<String>> v =
                core.validateFile(boxesDir.resolve("broken.json"));
        assertFalse(v.ok());
        assertNull(v.definition());
        assertTrue(v.errors().stream().anyMatch(e -> e.reason().contains("Invalid JSON syntax")));
        assertTrue(core.getDiagnostics().isEmpty(), "dry-run diagnostics are rolled back");
        assertFalse(core.hasLoadErrors());
    }
}
