package com.reclizer.csgobox.preview;

import com.reclizer.csgobox.preview.PreviewDeclarationParser.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L3 direct tests for the preview declaration parser (no MC classes). */
final class PreviewDeclarationParserTest {

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private PreviewDeclaration parse(String json) {
        return PreviewDeclarationParser.parse(json, "test.json", diagnostics);
    }

    private long errorCount() {
        return diagnostics.stream().filter(d -> !d.warning()).count();
    }

    @Test
    @DisplayName("valid entity declaration parses with defaults applied")
    void validEntityDeclaration() {
        PreviewDeclaration d = parse("{\"items\":[\"a:item\"],\"mode\":\"entity\",\"entity\":\"a:boss\"}");
        assertNotNull(d);
        assertEquals(0, errorCount());
        assertEquals(List.of("a:item"), d.items());
        assertTrue(d.isEntity());
        assertEquals("a:boss", d.entityId());
        assertNull(d.blockId());
        assertEquals(0F, d.yawDeg());
        assertEquals(1.0F, d.fitBoost());
        assertEquals(0, d.priority());
        assertNull(d.spawnDataJson());
        assertEquals("test.json", d.source());
    }

    @Test
    @DisplayName("valid block declaration parses properties and pose")
    void validBlockDeclaration() {
        PreviewDeclaration d = parse("{\"items\":[\"a:item\"],\"mode\":\"block\",\"block\":\"a:my_block\","
                + "\"block_properties\":{\"facing\":\"north\"},"
                + "\"rest_pose\":{\"yaw\":180,\"pitch\":-15.5,\"roll\":90}}");
        assertNotNull(d);
        assertEquals(0, errorCount());
        assertFalse(d.isEntity());
        assertEquals("a:my_block", d.blockId());
        assertEquals("north", d.blockProperties().get("facing"));
        assertEquals(180F, d.yawDeg());
        assertEquals(-15.5F, d.pitchDeg());
        assertEquals(90F, d.rollDeg());
    }

    @Test
    @DisplayName("spawn_data survives as raw JSON text")
    void spawnDataRoundTrip() {
        PreviewDeclaration d = parse("{\"items\":[\"a:item\"],\"mode\":\"entity\",\"entity\":\"a:boss\","
                + "\"spawn_data\":{\"id\":\"a:boss\",\"Health\":20.0}}");
        assertNotNull(d);
        assertNotNull(d.spawnDataJson());
        assertTrue(d.spawnDataJson().contains("\"Health\":20.0"));
    }

    @Test
    @DisplayName("missing items is an error")
    void missingItems() {
        assertNull(parse("{\"mode\":\"entity\",\"entity\":\"a:boss\"}"));
        assertEquals(1, errorCount());
    }

    @Test
    @DisplayName("empty items array is an error")
    void emptyItems() {
        assertNull(parse("{\"items\":[],\"mode\":\"entity\",\"entity\":\"a:boss\"}"));
        assertEquals(1, errorCount());
    }

    @Test
    @DisplayName("malformed item id shape is an error")
    void malformedItemId() {
        assertNull(parse("{\"items\":[\"item-without-colon\"],\"mode\":\"entity\",\"entity\":\"a:boss\"}"));
        assertEquals(1, errorCount());
    }

    @Test
    @DisplayName("uppercase item id is rejected by the vanilla id grammar")
    void uppercaseItemIdRejected() {
        assertNull(parse("{\"items\":[\"Mod:Item\"],\"mode\":\"entity\",\"entity\":\"a:boss\"}"));
        assertEquals(1, errorCount());
    }

    @Test
    @DisplayName("unknown mode is an error")
    void unknownMode() {
        assertNull(parse("{\"items\":[\"a:item\"],\"mode\":\"model\",\"entity\":\"a:boss\"}"));
        assertEquals(1, errorCount());
    }

    @Test
    @DisplayName("mode=entity without entity id is an error")
    void entityModeWithoutEntity() {
        assertNull(parse("{\"items\":[\"a:item\"],\"mode\":\"entity\"}"));
        assertEquals(1, errorCount());
    }

    @Test
    @DisplayName("mode=block without block id is an error")
    void blockModeWithoutBlock() {
        assertNull(parse("{\"items\":[\"a:item\"],\"mode\":\"block\"}"));
        assertEquals(1, errorCount());
    }

    @Test
    @DisplayName("malformed JSON text is an error, not an exception")
    void malformedJson() {
        assertNull(parse("{not json"));
        assertEquals(1, errorCount());
        assertTrue(diagnostics.get(0).reason().contains("not a JSON object"));
    }

    @Test
    @DisplayName("non-positive fit_boost is an error; fractional boost passes")
    void fitBoostBounds() {
        assertNull(parse("{\"items\":[\"a:item\"],\"mode\":\"entity\",\"entity\":\"a:boss\",\"fit_boost\":0}"));
        assertNull(parse("{\"items\":[\"a:item\"],\"mode\":\"entity\",\"entity\":\"a:boss\",\"fit_boost\":-1.5}"));
        assertEquals(2, errorCount());
        diagnostics.clear();
        PreviewDeclaration d = parse("{\"items\":[\"a:item\"],\"mode\":\"entity\",\"entity\":\"a:boss\",\"fit_boost\":1.3}");
        assertNotNull(d);
        assertEquals(1.3F, d.fitBoost());
    }

    @Test
    @DisplayName("non-numeric rest_pose field is an error")
    void restPoseTypeMismatch() {
        assertNull(parse("{\"items\":[\"a:item\"],\"mode\":\"entity\",\"entity\":\"a:boss\","
                + "\"rest_pose\":{\"yaw\":\"east\"}}"));
        assertEquals(1, errorCount());
    }

    @Test
    @DisplayName("unknown top-level key warns but still parses")
    void unknownKeyWarns() {
        PreviewDeclaration d = parse("{\"items\":[\"a:item\"],\"mode\":\"entity\",\"entity\":\"a:boss\",\"typo\":1}");
        assertNotNull(d);
        assertEquals(0, errorCount());
        assertEquals(1, diagnostics.stream().filter(Diagnostic::warning).count());
    }

    @Test
    @DisplayName("ORDER: priority descends, equal priority breaks by source path")
    void orderComparator() {
        PreviewDeclaration low = decl(0, "b/second.json");
        PreviewDeclaration high = decl(5, "z/first.json");
        PreviewDeclaration tieA = decl(0, "a/first.json");
        List<PreviewDeclaration> sorted = new ArrayList<>(List.of(low, high, tieA));
        sorted.sort(PreviewDeclaration.ORDER);
        assertEquals(List.of(high, tieA, low), sorted);
    }

    @Test
    @DisplayName("id grammar accepts the documented character set")
    void validResourceIds() {
        assertTrue(PreviewDeclarationParser.isValidResourceId("csgobox:box"));
        assertTrue(PreviewDeclarationParser.isValidResourceId("superbwarfare:container/t-90"));
        assertFalse(PreviewDeclarationParser.isValidResourceId("a:"));
        assertFalse(PreviewDeclarationParser.isValidResourceId(":b"));
        assertFalse(PreviewDeclarationParser.isValidResourceId("a:b:c"));
        assertFalse(PreviewDeclarationParser.isValidResourceId(""));
        assertFalse(PreviewDeclarationParser.isValidResourceId(null));
    }

    private static PreviewDeclaration decl(int priority, String source) {
        return new PreviewDeclaration(List.of("a:item"), "entity", "a:boss", null,
                Map.of(), null, 0F, 0F, 0F, 1.0F, priority, source);
    }
}
