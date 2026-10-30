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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BoxDefaults}: the terminal.json legacy migration
 * paths (v2.0.0 strict type separation; since 2.0.0 the terminal ships
 * unconfigured — no default config is ever written) and the default-config
 * install directory handling.
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
    @DisplayName("missing terminal.json is never created — the terminal ships unconfigured")
    void missingTerminalIsNeverCreated() {
        BoxDefaults.upgradeLegacyTerminalConfig(tempDir);
        assertFalse(Files.exists(terminalJson()), "no default terminal.json may be generated");
    }

    @Test
    @DisplayName("empty terminal.json is left alone (valid unconfigured state)")
    void emptyFileStaysEmpty() throws IOException {
        Files.writeString(terminalJson(), "");
        BoxDefaults.upgradeLegacyTerminalConfig(tempDir);
        assertTrue(Files.exists(terminalJson()), "the empty file must not be deleted");
        assertTrue(readTerminal().isEmpty(), "the empty file must not gain content");
    }

    @Test
    @DisplayName("corrupt non-empty terminal.json is kept in place plus a backup copy, never replaced with a default")
    void corruptFileIsKeptInPlaceWithBackup() throws IOException {
        Files.writeString(terminalJson(), "{ not json !!!");
        BoxDefaults.upgradeLegacyTerminalConfig(tempDir);
        assertTrue(Files.exists(terminalJson()),
                "the corrupt file must be kept in place so /csbox info error keeps reporting it");
        try (Stream<Path> files = Files.list(tempDir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("terminal.json.corrupt-")),
                    "a backup copy must be preserved");
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
    }

    // --- default-config install directory ---

    @Test
    @DisplayName("ensureBoxesDir creates missing nested target directory (client-side callers have no pre-created config/csbox)")
    void ensureBoxesDirCreatesNestedMissingDirectory() throws IOException {
        Path nested = tempDir.resolve("does-not-exist").resolve("csbox");
        BoxDefaults.ensureBoxesDir(nested);
        assertTrue(Files.isDirectory(nested), "the nested config directory must be created");
    }
}
