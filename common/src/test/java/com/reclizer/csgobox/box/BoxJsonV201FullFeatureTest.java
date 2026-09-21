package com.reclizer.csgobox.box;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v2.0.1 end-to-end schema agreement: the full-feature box JSON that
 * {@code scripts/boxgen.py} emits (every new field at once) must pass the
 * runtime {@link BoxJsonSchemaValidator} with zero issues. This keeps the
 * authoring tools, the docs ({@code docs/box-schema/box.schema.json}) and the
 * in-game validator from drifting apart.
 */
final class BoxJsonV201FullFeatureTest {

    /** Mirrors the boxgen.py output exercised by the toolchain smoke test. */
    private static final String FULL_FEATURE_JSON = """
            {
              "name": "#FF5555 全字段测试箱",
              "key": "csgobox:csgo_key1",
              "drop": 0.25,
              "random": [625, 125, 25, 6, 4],
              "requires": ["apotheosis"],
              "icon": 1001,
              "discount": 0.2,
              "stock": 10,
              "max_per_player": 5,
              "cooldown_seconds": 300,
              "permission": "csbox.vip",
              "grade1": [
                {"id": "minecraft:iron_ingot", "count": 4, "weight": 2},
                {"id": "minecraft:bread", "count": 8}
              ],
              "grade2": [{"id": "minecraft:golden_apple", "count": [2, 5]}],
              "grade3": [{"id": "minecraft:diamond_sword", "weight": 3, "enchant": true}],
              "grade4": [{"id": "minecraft:enchanted_book", "enchant": {"level": [1, 3]}}],
              "grade5": [{"tag": "#minecraft:swords"}]
            }
            """;

    @Test
    @DisplayName("a full-feature v2.0.1 box JSON passes the runtime validator cleanly")
    void fullFeatureBoxIsValid() {
        JsonObject json = JsonParser.parseString(FULL_FEATURE_JSON).getAsJsonObject();
        var issues = BoxJsonSchemaValidator.validate(json);
        assertTrue(issues.isEmpty(),
                "expected a clean schema pass, got: " + issues);
    }

    @Test
    @DisplayName("optional new fields may be omitted entirely (backward compatible)")
    void minimalBoxStillValid() {
        JsonObject json = JsonParser.parseString("""
                {"name": "minimal", "key": "minecraft:air", "grade1": [{"id": "minecraft:diamond"}]}
                """).getAsJsonObject();
        assertTrue(BoxJsonSchemaValidator.validate(json).isEmpty());
    }

    @Test
    @DisplayName("loot_table entries are accepted as a valid item source")
    void lootTableEntryValid() {
        JsonObject json = JsonParser.parseString("""
                {"name": "loot", "key": "minecraft:air",
                 "grade5": [{"loot_table": "minecraft:chests/simple_dungeon", "weight": 3}]}
                """).getAsJsonObject();
        assertTrue(BoxJsonSchemaValidator.validate(json).isEmpty());
    }
}
