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
 * unconfigured — no default config is ever written) and the tutorial
 * lifecycle (stale-deletion whitelist + current-version protection, v2.1.0).
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

    // --- tutorial lifecycle (v2.1.0 fixes) ---

    @Test
    @DisplayName("stale tutorial deletion keeps current-version files and user files, removes only other _tutorial_v*.md")
    void staleTutorialDeletionSparesCurrentVersionAndUserFiles() throws IOException {
        Files.writeString(tempDir.resolve("_tutorial_v1.0.6.md"), "old");
        Files.writeString(tempDir.resolve("_tutorial_v1.0.6_zh_cn.md"), "old zh");
        Files.writeString(tempDir.resolve("_tutorial_v2.0.0.md"), "current");
        Files.writeString(tempDir.resolve("notes.md"), "user data");

        BoxDefaults.deleteStaleTutorials(tempDir, "2.0.0");

        assertTrue(Files.exists(tempDir.resolve("_tutorial_v2.0.0.md")),
                "current-version tutorial must survive");
        assertFalse(Files.exists(tempDir.resolve("_tutorial_v1.0.6.md")),
                "older-version tutorial must be removed");
        assertFalse(Files.exists(tempDir.resolve("_tutorial_v1.0.6_zh_cn.md")),
                "older-version zh tutorial must be removed");
        assertTrue(Files.exists(tempDir.resolve("notes.md")),
                "user files (notes.md) must never be touched");
    }

    @Test
    @DisplayName("writeTutorialIfMissing with current-version files present never overwrites, cleans stale, keeps user files")
    void writeTutorialIfMissingWithCurrentVersionPresentIsNonDestructive() throws IOException {
        Files.writeString(tempDir.resolve("_tutorial_v2.0.0.md"), "current en");
        Files.writeString(tempDir.resolve("_tutorial_v2.0.0_zh_cn.md"), "current zh");
        Files.writeString(tempDir.resolve("_tutorial_v1.0.6.md"), "old");
        Files.writeString(tempDir.resolve("notes.md"), "user data");

        // No I/O is touched: both current-version files already exist, so
        // the flow skips copying and goes straight to stale cleanup.
        BoxDefaults.writeTutorialIfMissing(tempDir, "2.0.0");

        assertTrue(Files.exists(tempDir.resolve("_tutorial_v2.0.0.md")));
        assertTrue(Files.exists(tempDir.resolve("_tutorial_v2.0.0_zh_cn.md")));
        assertFalse(Files.exists(tempDir.resolve("_tutorial_v1.0.6.md")),
                "stale version must be removed only after current version is in place");
        assertTrue(Files.exists(tempDir.resolve("notes.md")),
                "user files must survive");
    }

    @Test
    @DisplayName("ensureBoxesDir creates missing nested target directory (client-side callers have no pre-created config/csbox)")
    void ensureBoxesDirCreatesNestedMissingDirectory() throws IOException {
        Path nested = tempDir.resolve("does-not-exist").resolve("csbox");
        BoxDefaults.ensureBoxesDir(nested);
        assertTrue(Files.isDirectory(nested), "the nested tutorial directory must be created");
    }

    @Test
    @DisplayName("writeTutorialIfMissing copies the bundled tutorials into an empty directory (no network involved)")
    void writeTutorialIfMissingCopiesBundledResources() throws IOException {
        BoxDefaults.writeTutorialIfMissing(tempDir, "2.0.0");

        Path en = tempDir.resolve("_tutorial_v2.0.0.md");
        Path zh = tempDir.resolve("_tutorial_v2.0.0_zh_cn.md");
        assertTrue(Files.exists(en), "EN tutorial must be copied from the bundled resource");
        assertTrue(Files.exists(zh), "ZH tutorial must be copied from the bundled resource");
        assertTrue(Files.readString(en).contains("CS2-Box Configuration Reference"),
                "copied content must come from the bundled resource");
        assertTrue(Files.readString(zh).contains("配置文件参考"),
                "copied zh content must come from the bundled resource");
    }
}
