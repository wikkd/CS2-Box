package com.reclizer.csgobox.box;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the terminal.json default-write and legacy migration paths
 * in {@link BoxDefaults} (v2.0.0 strict type separation).
 */
class BoxDefaultsTest {

    @TempDir
    Path tempDir;

    private Path terminalJson() {
        return tempDir.resolve("terminal.json");
    }

    private String readTerminal() throws IOException {
        return Files.readString(terminalJson());
    }

    private JsonObject parseTerminal() throws IOException {
        return JsonParser.parseString(readTerminal()).getAsJsonObject();
    }

    @Test
    @DisplayName("missing terminal.json is written with the default type-driven config")
    void missingWritesDefault() throws IOException {
        BoxDefaults.writeDefaultTerminalIfMissing(tempDir);
        String content = readTerminal();
        assertTrue(content.contains("\"type\": \"terminal\""), "default must declare terminal type");
        assertFalse(content.contains("\"key\""), "default must not carry a key field");
    }

    @Test
    @DisplayName("existing terminal.json is never overwritten by writeDefaultTerminalIfMissing")
    void existingUserConfigIsKept() throws IOException {
        Files.writeString(terminalJson(), "{\"name\": \"custom\", \"type\": \"terminal\"}");
        BoxDefaults.writeDefaultTerminalIfMissing(tempDir);
        assertTrue(readTerminal().contains("\"custom\""));
    }

    @Test
    @DisplayName("empty terminal.json is recovered with the default config")
    void emptyFileIsRecovered() throws IOException {
        Files.writeString(terminalJson(), "");
        BoxDefaults.upgradeLegacyTerminalConfig(tempDir);
        assertTrue(parseTerminal().has("type"), "recovered file must declare a type");
        assertTrue(parseTerminal().get("type").getAsString().equals("terminal"));
    }

    @Test
    @DisplayName("corrupt non-empty terminal.json is backed up and replaced with the default")
    void corruptFileIsBackedUpAndRecovered() throws IOException {
        Files.writeString(terminalJson(), "{ not json !!!");
        BoxDefaults.upgradeLegacyTerminalConfig(tempDir);
        assertTrue(parseTerminal().get("type").getAsString().equals("terminal"));
        try (Stream<Path> files = Files.list(tempDir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("terminal.json.corrupt-")),
                    "corrupt file must be kept as a backup");
        }
    }

    @Test
    @DisplayName("legacy terminal.json without type gains type and drops key")
    void legacyConfigIsMigrated() throws IOException {
        Files.writeString(terminalJson(),
                "{\"name\": \"#00E5FF Legacy\", \"key\": \"minecraft:air\", \"drop\": 0.06}");
        BoxDefaults.upgradeLegacyTerminalConfig(tempDir);
        JsonObject json = parseTerminal();
        assertTrue(json.get("type").getAsString().equals("terminal"), "legacy config must gain the terminal type");
        assertFalse(json.has("key"), "legacy key must be removed");
        assertTrue(json.get("name").getAsString().contains("Legacy"));
    }

    @Test
    @DisplayName("already-type-typed terminal.json is left untouched")
    void typedConfigIsUntouched() throws IOException {
        Files.writeString(terminalJson(),
                "{\"name\": \"mine\", \"type\": \"terminal\", \"random\": [1,2,3,4,5]}");
        BoxDefaults.upgradeLegacyTerminalConfig(tempDir);
        String content = readTerminal();
        assertTrue(content.contains("\"mine\""));
        assertTrue(content.contains("\"random\""));
        assertNotNull(content);
    }
}
