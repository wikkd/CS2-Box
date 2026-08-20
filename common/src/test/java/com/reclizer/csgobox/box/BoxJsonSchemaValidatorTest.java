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
 *   <li>type (v1.0.8): must be "csbox" or "terminal"; a terminal must not declare key</li>
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
        return BoxJsonSchemaValidator.validate(parse(json));
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
    @DisplayName("type field (v1.0.8 strict separation)")
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
        @DisplayName("non-negative integer price is fine")
        void validPrice() {
            assertTrue(validate("""
                    { "grade1": [ { "id": "x", "price": 12 } ] }
                    """).isEmpty(), "valid price must produce no issues");
        }

        @Test
        @DisplayName("negative price reports price")
        void negativePrice() {
            assertSingleIssue(validate("""
                    { "grade1": [ { "id": "x", "price": -3 } ] }
                    """), "grade1[0].price");
        }

        @Test
        @DisplayName("non-integer price reports price")
        void fractionalPrice() {
            assertSingleIssue(validate("""
                    { "grade1": [ { "id": "x", "price": 2.5 } ] }
                    """), "grade1[0].price");
        }

        @Test
        @DisplayName("non-numeric price reports price")
        void nonNumericPrice() {
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
}
