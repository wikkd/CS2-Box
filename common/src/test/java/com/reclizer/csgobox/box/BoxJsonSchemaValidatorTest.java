package com.reclizer.csgobox.box;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BoxJsonSchemaValidator}.
 *
 * <p>Covers the structural rules:</p>
 * <ol>
 *   <li>type (v2.0.0): must be "csbox" or "terminal"; a terminal must not declare key</li>
 *   <li>random: array length must be 5; each element must be a number</li>
 *   <li>drop: must be a number</li>
 *   <li>grade1..grade5: must be arrays</li>
 *   <li>entity: elements must be string (id) or number (rate)</li>
 *   <li>name: a hex color prefix must be followed by a single ASCII space</li>
 * </ol>
 *
 * <p>Plus happy paths and empty/optional field boundaries.</p>
 */
final class BoxJsonSchemaValidatorTest {

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static List<BoxJsonSchemaValidator.SchemaIssue> validate(String json) {
        return BoxJsonSchemaValidator.validate(parseObj(json));
    }

    private static void assertSingleIssue(List<BoxJsonSchemaValidator.SchemaIssue> issues,
                                          String expectedField) {
        assertEquals(1, issues.size(),
                "Expected exactly one issue, got " + issues.size() + ": " + issues);
        assertEquals(expectedField, issues.get(0).field(),
                "Wrong field on issue: " + issues.get(0));
    }

    @Nested
    @DisplayName("happy paths")
    class HappyPath {

        @Test
        @DisplayName("fully populated valid JSON returns no issues")
        void fullyValid() {
            String json = """
                    {
                      "name": "#FF5555 测试箱子",
                      "key": "csgobox:csgo_key0",
                      "drop": 0.12,
                      "random": [625, 125, 25, 5, 2],
                      "entity": ["minecraft:zombie", "minecraft:skeleton"],
                      "grade1": [{"id": "minecraft:diamond"}],
                      "grade5": [{"id": "minecraft:dirt"}]
                    }
                    """;
            assertTrue(validate(json).isEmpty());
        }

        @Test
        @DisplayName("empty object — every field is optional, returns no issues")
        void emptyObject() {
            assertTrue(validate("{}").isEmpty());
        }

        @Test
        @DisplayName("plain name without color prefix is fine")
        void plainName() {
            String json = """
                    { "name": "weapon_supply_box", "key": "csgobox:csgo_key0" }
                    """;
            assertTrue(validate(json).isEmpty());
        }

        @Test
        @DisplayName("name color prefix with space is fine")
        void coloredNameWithSpace() {
            String json = """
                    { "name": "#FF5555 高级补给箱", "key": "csgobox:csgo_key0" }
                    """;
            assertTrue(validate(json).isEmpty());
        }

        @Test
        @DisplayName("entity array of all strings is fine (drop rate defaults)")
        void entityAllStrings() {
            String json = """
                    { "entity": ["minecraft:zombie", "minecraft:skeleton", "minecraft:creeper"] }
                    """;
            assertTrue(validate(json).isEmpty());
        }
    }

    @Nested
    @DisplayName("random array")
    class Random {

        @Test
        @DisplayName("length != 5 reports random field")
        void wrongLength() {
            String json = """
                    { "random": [625, 125, 25] }
                    """;
            assertSingleIssue(validate(json), "random");
        }

        @Test
        @DisplayName("length > 5 reports random field")
        void tooLong() {
            String json = """
                    { "random": [1, 2, 3, 4, 5, 6] }
                    """;
            assertSingleIssue(validate(json), "random");
        }

        @Test
        @DisplayName("element type mismatch reports random[i] (one issue per bad slot)")
        void wrongElementType() {
            String json = """
                    { "random": ["high", "low", 1, 2, 3] }
                    """;
            List<BoxJsonSchemaValidator.SchemaIssue> issues = validate(json);
            assertEquals(2, issues.size(),
                    "Expected 2 issues (random[0] + random[1]), got: " + issues);
            assertEquals("random[0]", issues.get(0).field());
            assertEquals("random[1]", issues.get(1).field());
        }

        @Test
        @DisplayName("random is not an array reports random")
        void notAnArray() {
            String json = """
                    { "random": "not-an-array" }
                    """;
            assertSingleIssue(validate(json), "random");
        }

        @Test
        @DisplayName("all-zero grade weights report the un-openable box issue")
        void allZeroWeights() {
            String json = """
                    { "random": [0, 0, 0, 0, 0] }
                    """;
            List<BoxJsonSchemaValidator.SchemaIssue> issues = validate(json);
            assertEquals(1, issues.size(),
                    "Expected exactly 1 issue (all-zero random), got: " + issues);
            assertEquals("random", issues.get(0).field());
        }

        @Test
        @DisplayName("negative grade weights also report the un-openable box issue")
        void allNegativeWeights() {
            String json = """
                    { "random": [-1, -2, 0, -3, -4] }
                    """;
            List<BoxJsonSchemaValidator.SchemaIssue> issues = validate(json);
            assertEquals(1, issues.size(),
                    "Expected exactly 1 issue (all non-positive random), got: " + issues);
            assertEquals("random", issues.get(0).field());
        }

        @Test
        @DisplayName("a single positive weight keeps the box openable")
        void singlePositiveWeightDoesNotReport() {
            String json = """
                    { "random": [0, 0, 25, 0, 0] }
                    """;
            List<BoxJsonSchemaValidator.SchemaIssue> issues = validate(json);
            assertEquals(0, issues.size(),
                    "Expected no issues for an openable box, got: " + issues);
        }
    }

    @Nested
    @DisplayName("drop field")
    class Drop {

        @Test
        @DisplayName("non-number drop reports drop")
        void nonNumeric() {
            String json = """
                    { "drop": "0.12" }
                    """;
            assertSingleIssue(validate(json), "drop");
        }

        @Test
        @DisplayName("boolean drop reports drop")
        void booleanDrop() {
            String json = """
                    { "drop": true }
                    """;
            assertSingleIssue(validate(json), "drop");
        }

        @Test
        @DisplayName("numeric drop is fine")
        void numericFine() {
            assertTrue(validate("{ \"drop\": 0.5 }").isEmpty());
        }
    }

    @Nested
    @DisplayName("grade fields")
    class Grades {

        @Test
        @DisplayName("grade5 as object reports grade5")
        void gradeAsObject() {
            String json = """
                    { "grade5": {"id": "minecraft:diamond"} }
                    """;
            assertSingleIssue(validate(json), "grade5");
        }

        @Test
        @DisplayName("grade1 as string reports grade1")
        void gradeAsString() {
            String json = """
                    { "grade1": "diamond" }
                    """;
            assertSingleIssue(validate(json), "grade1");
        }

        @Test
        @DisplayName("only present grades are validated")
        void onlyPresentGradesChecked() {
            String json = """
                    {
                      "grade1": [],
                      "grade5": {"id": "x"}
                    }
                    """;
            List<BoxJsonSchemaValidator.SchemaIssue> issues = validate(json);
            assertEquals(1, issues.size());
            assertEquals("grade5", issues.get(0).field());
        }
    }

    @Nested
    @DisplayName("entity array")
    class Entity {

        @Test
        @DisplayName("boolean entity element reports entity[i]")
        void booleanElement() {
            String json = """
                    { "entity": ["minecraft:zombie", true, "minecraft:skeleton"] }
                    """;
            assertSingleIssue(validate(json), "entity[1]");
        }

        @Test
        @DisplayName("array entity element reports entity[i]")
        void arrayElement() {
            String json = """
                    { "entity": ["minecraft:zombie", ["nested"]] }
                    """;
            assertSingleIssue(validate(json), "entity[1]");
        }

        @Test
        @DisplayName("entity is not an array reports entity")
        void notAnArray() {
            String json = """
                    { "entity": "minecraft:zombie" }
                    """;
            assertSingleIssue(validate(json), "entity");
        }
    }

    @Nested
    @DisplayName("name color prefix")
    class NameColor {

        @Test
        @DisplayName("hex prefix without trailing space reports name")
        void missingSpace() {
            String json = """
                    { "name": "#FF5555高级补给箱" }
                    """;
            assertSingleIssue(validate(json), "name");
        }

        @Test
        @DisplayName("hex prefix with multiple spaces is accepted (validator only checks separator presence)")
        void multipleSpacesAccepted() {
            // Validator requires a single ASCII space at position 7 but does not
            // forbid additional whitespace after — the box loader does not split
            // the name anyway, so consecutive spaces are cosmetic.
            String json = """
                    { "name": "#FF5555  高级补给箱" }
                    """;
            assertTrue(validate(json).isEmpty());
        }

        @Test
        @DisplayName("non-hex prefix is ignored (no '#RRGGBB' shape)")
        void nonHexPrefix() {
            // "name" looks like "#Z" not "#RRGGBB" — validator should leave it alone
            String json = """
                    { "name": "#X 高级补给箱" }
                    """;
            assertTrue(validate(json).isEmpty());
        }

        @Test
        @DisplayName("plain name with no # is fine")
        void noHash() {
            assertTrue(validate("{ \"name\": \"weapon_supply_box\" }").isEmpty());
        }
    }

    @Nested
    @DisplayName("type field (v2.0.0 strict separation)")
    class Type {

        @Test
        @DisplayName("terminal without key is fine")
        void terminalWithoutKey() {
            String json = """
                    {
                      "name": "#00E5FF CS2 终端机",
                      "type": "terminal",
                      "random": [20, 40, 80, 160, 300]
                    }
                    """;
            assertTrue(validate(json).isEmpty());
        }

        @Test
        @DisplayName("terminal with leftover key reports key")
        void terminalWithKey() {
            String json = """
                    {
                      "name": "CS2 终端机",
                      "type": "terminal",
                      "key": "minecraft:air"
                    }
                    """;
            assertSingleIssue(validate(json), "key");
        }

        @Test
        @DisplayName("csbox with explicit type and key is fine")
        void csboxWithKey() {
            String json = """
                    {
                      "name": "普通宝箱",
                      "type": "csbox",
                      "key": "csgobox:csgo_key0"
                    }
                    """;
            assertTrue(validate(json).isEmpty());
        }

        @Test
        @DisplayName("unknown type value reports type")
        void unknownType() {
            assertSingleIssue(validate("{ \"type\": \"battle_pass\" }"), "type");
        }

        @Test
        @DisplayName("non-string type reports type")
        void nonStringType() {
            assertSingleIssue(validate("{ \"type\": 42 }"), "type");
        }
    }

    @Nested
    @DisplayName("multi-issue composition")
    class MultiIssue {

        @Test
        @DisplayName("multiple problems in same JSON all surface")
        void multiple() {
            String json = """
                    {
                      "drop": "0.12",
                      "random": ["high", 1, 2, 3, 4],
                      "grade5": "diamond",
                      "entity": ["minecraft:zombie", true]
                    }
                    """;
            List<BoxJsonSchemaValidator.SchemaIssue> issues = validate(json);
            assertEquals(4, issues.size(), "Expected 4 issues, got: " + issues);
            // Every issue should be in the expected set
            assertTrue(issues.stream().anyMatch(i -> "drop".equals(i.field())));
            assertTrue(issues.stream().anyMatch(i -> "random[0]".equals(i.field())));
            assertTrue(issues.stream().anyMatch(i -> "grade5".equals(i.field())));
            assertTrue(issues.stream().anyMatch(i -> "entity[1]".equals(i.field())));
        }

        @Test
        @DisplayName("structural problems are non-blocking (descriptive, not fatal)")
        void nonFatal() {
            // Validator must never throw on malformed JSON; only flag issues.
            String malformed = """
                    { "random": [1, 2, 3], "grade5": "x" }
                    """;
            List<BoxJsonSchemaValidator.SchemaIssue> issues = validate(malformed);
            assertFalse(issues.isEmpty());
        }
    }

    @Nested
    @DisplayName("item price field")
    class ItemPrice {
        @Test
        @DisplayName("any leftover price field is reported (moved to _prices.json)")
        void leftoverPriceReported() {
            assertSingleIssue(validate("""
                    { "grade1": [ { "id": "x", "price": 12 } ] }
                    """), "grade1[0].price");
        }

        @Test
        @DisplayName("leftover negative price is still reported as removed")
        void negativePriceStillRemoved() {
            assertSingleIssue(validate("""
                    { "grade1": [ { "id": "x", "price": -3 } ] }
                    """), "grade1[0].price");
        }

        @Test
        @DisplayName("leftover fractional price is still reported as removed")
        void fractionalPriceStillRemoved() {
            assertSingleIssue(validate("""
                    { "grade1": [ { "id": "x", "price": 2.5 } ] }
                    """), "grade1[0].price");
        }

        @Test
        @DisplayName("non-numeric leftover price is still reported as removed")
        void nonNumericPriceStillRemoved() {
            assertSingleIssue(validate("""
                    { "grade1": [ { "id": "x", "price": "abc" } ] }
                    """), "grade1[0].price");
        }

        @Test
        @DisplayName("missing price is skipped (no issue)")
        void missingPriceSkipped() {
            assertTrue(validate("""
                    { "grade1": [ { "id": "x" } ] }
                    """).isEmpty(), "item without price must be skipped");
        }
    }

// ---- v2.0.1 field validation ----

    @Test
    @DisplayName("requires must be an array of strings")
    void requiresArray() {
        var issues = BoxJsonSchemaValidator.validate(parseObj("{\"requires\": \"apotheosis\"}"));
        assertTrue(issues.stream().anyMatch(i -> i.field().equals("requires")),
                "non-array requires must be flagged: " + issues);
    }

    @Test
    @DisplayName("enabled must be boolean")
    void enabledBoolean() {
        var issues = BoxJsonSchemaValidator.validate(parseObj("{\"enabled\": \"yes\"}"));
        assertTrue(issues.stream().anyMatch(i -> i.field().equals("enabled")),
                "non-boolean enabled must be flagged: " + issues);
    }

    @Test
    @DisplayName("numeric constraint fields reject negatives below -1 and non-integers")
    void numericConstraints() {
        for (String field : new String[]{"stock", "restock_minutes", "max_per_player", "cooldown_seconds"}) {
            var issues = BoxJsonSchemaValidator.validate(parseObj("{\"" + field + "\": -5}"));
            assertTrue(issues.stream().anyMatch(i -> i.field().equals(field)),
                    field + " = -5 must be flagged");
            issues = BoxJsonSchemaValidator.validate(parseObj("{\"" + field + "\": 1.5}"));
            assertTrue(issues.stream().anyMatch(i -> i.field().equals(field)),
                    field + " = 1.5 must be flagged");
        }
    }

    @Test
    @DisplayName("icon accepts string model id or integer CMD; rejects booleans")
    void iconType() {
        // numeric CMD and string model id are both valid
        assertTrue(BoxJsonSchemaValidator.validate(parseObj("{\"icon\": 12}")).isEmpty());
        assertTrue(BoxJsonSchemaValidator.validate(parseObj("{\"icon\": \"minecraft:item/barrel\"}")).isEmpty());
        var issues = BoxJsonSchemaValidator.validate(parseObj("{\"icon\": true}"));
        assertTrue(issues.stream().anyMatch(i -> i.field().equals("icon")),
                "boolean icon must be flagged: " + issues);
    }

    @Test
    @DisplayName("item entry rejects multiple sources id + tag + loot_table")
    void itemSourceExclusivity() {
        var issues = BoxJsonSchemaValidator.validate(parseObj(
                "{\"grade1\": [{\"id\": \"minecraft:diamond\", \"tag\": \"#minecraft:swords\", \"loot_table\": \"minecraft:chests/simple_dungeon\"}]}"));
        assertTrue(issues.stream().anyMatch(i -> i.field().contains("grade1[0]") && i.reason().contains("exactly one")),
                "multi-source item must be flagged: " + issues);
    }

    @Test
    @DisplayName("count array must be [min,max] with min>=1")
    void countRange() {
        var issues = BoxJsonSchemaValidator.validate(parseObj(
                "{\"grade1\": [{\"id\": \"minecraft:diamond\", \"count\": [5, 2]}]}"));
        assertTrue(issues.stream().anyMatch(i -> i.field().equals("grade1[0].count")),
                "reversed count range must be flagged: " + issues);
    }

    @Test
    @DisplayName("weight must be non-negative integer")
    void itemWeight() {
        var issues = BoxJsonSchemaValidator.validate(parseObj(
                "{\"grade1\": [{\"id\": \"minecraft:diamond\", \"weight\": -1}]}"));
        assertTrue(issues.stream().anyMatch(i -> i.field().equals("grade1[0].weight")),
                "negative weight must be flagged: " + issues);
    }

    @Test
    @DisplayName("enchant must be boolean or object")
    void enchantType() {
        var issues = BoxJsonSchemaValidator.validate(parseObj(
                "{\"grade1\": [{\"id\": \"minecraft:diamond\", \"enchant\": 7}]}"));
        assertTrue(issues.stream().anyMatch(i -> i.field().equals("grade1[0].enchant")),
                "numeric enchant must be flagged: " + issues);
    }

// ---- v2.0.1 pity (保底) ----

    @Test
    @DisplayName("valid pity with positive target weights produces no issues")
    void validPity() {
        var issues = BoxJsonSchemaValidator.validate(parseObj(
                "{\"random\": [625, 125, 25, 6, 4], \"pity\": {\"grade\": \"classified\", \"every\": 20}}"));
        assertTrue(issues.isEmpty(), "valid pity must be clean, got: " + issues);
    }

    @Test
    @DisplayName("unknown pity grade is flagged")
    void pityUnknownGrade() {
        var issues = BoxJsonSchemaValidator.validate(parseObj(
                "{\"random\": [625, 125, 25, 6, 4], \"pity\": {\"grade\": \"legendary\", \"every\": 20}}"));
        assertTrue(issues.stream().anyMatch(i -> i.field().equals("pity.grade")),
                "unknown grade must be flagged: " + issues);
    }

    @Test
    @DisplayName("every < 2 is flagged")
    void pityBadEvery() {
        var issues = BoxJsonSchemaValidator.validate(parseObj(
                "{\"random\": [625, 125, 25, 6, 4], \"pity\": {\"grade\": \"classified\", \"every\": 1}}"));
        assertTrue(issues.stream().anyMatch(i -> i.field().equals("pity.every")),
                "every=1 must be flagged: " + issues);
    }

    @Test
    @DisplayName("missing pity fields are flagged")
    void pityMissingFields() {
        var issues = BoxJsonSchemaValidator.validate(parseObj(
                "{\"random\": [625, 125, 25, 6, 4], \"pity\": {}}"));
        assertTrue(issues.stream().anyMatch(i -> i.field().equals("pity.grade")),
                "missing grade must be flagged: " + issues);
        assertTrue(issues.stream().anyMatch(i -> i.field().equals("pity.every")),
                "missing every must be flagged: " + issues);
    }

    @Test
    @DisplayName("pity that can never fire (no positive weight at/above target) is flagged")
    void pityCannotFire() {
        var issues = BoxJsonSchemaValidator.validate(parseObj(
                "{\"random\": [625, 125, 25, 0, 0], \"pity\": {\"grade\": \"classified\", \"every\": 20}}"));
        assertTrue(issues.stream().anyMatch(i -> i.field().equals("pity") && i.reason().contains("never fire")),
                "dead pity must be flagged: " + issues);
    }

    @Test
    @DisplayName("pity with positive target weights is not flagged as dead")
    void pityCanFire() {
        var issues = BoxJsonSchemaValidator.validate(parseObj(
                "{\"random\": [625, 125, 25, 6, 0], \"pity\": {\"grade\": \"restricted\", \"every\": 10}}"));
        assertTrue(issues.stream().noneMatch(i -> i.reason().contains("never fire")),
                "restricted target has weight 6 > 0, pity can fire: " + issues);
    }

    @Test
    @DisplayName("pity that is not an object is flagged")
    void pityNotObject() {
        var issues = BoxJsonSchemaValidator.validate(parseObj(
                "{\"random\": [625, 125, 25, 6, 4], \"pity\": \"classified\"}"));
        assertTrue(issues.stream().anyMatch(i -> i.field().equals("pity")),
                "non-object pity must be flagged: " + issues);
    }

    private static com.google.gson.JsonObject parseObj(String json) {
        return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
    }
}
