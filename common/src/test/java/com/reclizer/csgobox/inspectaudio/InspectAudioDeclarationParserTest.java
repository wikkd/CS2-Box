package com.reclizer.csgobox.inspectaudio;

import com.reclizer.csgobox.inspectaudio.InspectAudioDeclarationParser.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L3 direct tests for the inspect-audio declaration parser (no MC classes). */
final class InspectAudioDeclarationParserTest {

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private InspectAudioDeclaration parse(String json) {
        return InspectAudioDeclarationParser.parse(json, "test.json", diagnostics);
    }

    private long errorCount() {
        return diagnostics.stream().filter(d -> !d.warning()).count();
    }

    @Test
    @DisplayName("valid declaration parses with defaults applied")
    void validDeclaration() {
        InspectAudioDeclaration d = parse("{\"items\":[\"a:item\"],\"sound\":\"a:my_sound\"}");
        assertNotNull(d);
        assertEquals(0, errorCount());
        assertEquals(List.of("a:item"), d.items());
        assertEquals("a:my_sound", d.soundId());
        assertEquals(0, d.priority());
        assertEquals("test.json", d.source());
    }

    @Test
    @DisplayName("one declaration can cover several items")
    void multipleItems() {
        InspectAudioDeclaration d = parse("{\"items\":[\"a:one\",\"b:two\"],\"sound\":\"a:s\"}");
        assertNotNull(d);
        assertEquals(0, errorCount());
        assertEquals(List.of("a:one", "b:two"), d.items());
    }

    @Test
    @DisplayName("priority parses and sound ids with dots are valid")
    void priorityAndDottedSoundId() {
        InspectAudioDeclaration d = parse("{\"items\":[\"a:item\"],\"sound\":\"minecraft:music_disc.pigstep\",\"priority\":7}");
        assertNotNull(d);
        assertEquals(0, errorCount());
        assertEquals("minecraft:music_disc.pigstep", d.soundId());
        assertEquals(7, d.priority());
    }

    @Test
    @DisplayName("missing sound is an error")
    void missingSound() {
        assertNull(parse("{\"items\":[\"a:item\"]}"));
        assertEquals(1, errorCount());
    }

    @Test
    @DisplayName("non-string sound is an error")
    void nonStringSound() {
        assertNull(parse("{\"items\":[\"a:item\"],\"sound\":5}"));
        assertEquals(1, errorCount());
    }

    @Test
    @DisplayName("missing items is an error")
    void missingItems() {
        assertNull(parse("{\"sound\":\"a:s\"}"));
        assertEquals(1, errorCount());
    }

    @Test
    @DisplayName("empty items array is an error")
    void emptyItems() {
        assertNull(parse("{\"items\":[],\"sound\":\"a:s\"}"));
        assertEquals(1, errorCount());
    }

    @Test
    @DisplayName("malformed item or sound id shape is an error")
    void malformedIds() {
        assertNull(parse("{\"items\":[\"item-without-colon\"],\"sound\":\"a:s\"}"));
        assertEquals(1, errorCount());
        diagnostics.clear();
        assertNull(parse("{\"items\":[\"a:item\"],\"sound\":\"a:\"}"));
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
    @DisplayName("non-integer priority is an error")
    void priorityTypeMismatch() {
        assertNull(parse("{\"items\":[\"a:item\"],\"sound\":\"a:s\",\"priority\":\"high\"}"));
        assertEquals(1, errorCount());
    }

    @Test
    @DisplayName("unknown top-level key warns but still parses")
    void unknownKeyWarns() {
        InspectAudioDeclaration d = parse("{\"items\":[\"a:item\"],\"sound\":\"a:s\",\"typo\":1}");
        assertNotNull(d);
        assertEquals(0, errorCount());
        assertEquals(1, diagnostics.stream().filter(Diagnostic::warning).count());
    }

    @Test
    @DisplayName("ORDER: priority descends, equal priority breaks by source path")
    void orderComparator() {
        InspectAudioDeclaration low = decl(0, "b/second.json");
        InspectAudioDeclaration high = decl(5, "z/first.json");
        InspectAudioDeclaration tieA = decl(0, "a/first.json");
        List<InspectAudioDeclaration> sorted = new ArrayList<>(List.of(low, high, tieA));
        sorted.sort(InspectAudioDeclaration.ORDER);
        assertEquals(List.of(high, tieA, low), sorted);
    }

    private static InspectAudioDeclaration decl(int priority, String source) {
        return new InspectAudioDeclaration(List.of("a:item"), "a:sound", priority, source);
    }
}
