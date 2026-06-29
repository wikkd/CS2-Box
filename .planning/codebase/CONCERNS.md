<!-- refreshed: 2026-06-28 -->
<!-- updated: 2026-06-29 (incremental — recipe dir rename, CsboxConfig relocation, Cloth Config removal, csgo_key3 workbench recipe removal, JDK 21 runConfig pin) -->
# Codebase Concerns

**Analysis Date:** 2026-06-28
**Last Mapped Commit:** `a8bea6a` (HEAD, 2026-06-29)

## Tech Debt

### BoxJsonLoader is a god class (489 lines)
- Issue: `BoxJsonLoader.java` handles default JSON authoring, schema tutorial, file IO, weight parsing, entity parsing, item parsing (DataComponent + legacy NBT), and serialization to disk — all in one class.
- Files: `src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java`
- Impact: High. Single point of change for any schema/format evolution. Touching one concern risks regressing the others.
- Fix approach: Split into `BoxDefaultsWriter`, `BoxJsonParser`, `BoxJsonSerializer`, `BoxFileIO`. Each becomes independently testable.

### Hardcoded GRADE_COLORS array contains duplicate values
- Issue: `BoxJsonLoader.java:45` defines `GRADE_COLORS = {0xFFD32CE6, 0xFF8847FF, 0xFF4B69FF, 0xFF4B69FF, 0xFF4B69FF}` — entries 3-5 (`mil_spec`, `industrial`, `consumer`) all share `0xFF4B69FF`. Either an oversight, or grades were meant to be visually distinguished but never were.
- Files: `src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java:43-45`
- Impact: Medium. Users see no visual progression for 3 of 5 rarities in the UI.
- Fix approach: Define distinct colors per grade (cool → warm gradient: purple → blue → cyan → teal → green), or extract to `CsboxConfig` so designers can change without recompile.

### `OPEN_BLOCKED_UNTIL_TICK` uses non-thread-safe `HashMap`
- Issue: `PacketCsgoProgress.java:46` declares `private static final Map<UUID, Long> OPEN_BLOCKED_UNTIL_TICK = new HashMap<>()`. Packet handlers run on the netty event loop; multiple inbound packets can hit the map concurrently.
- Files: `src/main/java/com/reclizer/csgobox/packet/PacketCsgoProgress.java`
- Impact: Medium-to-high. Race-condition corruption, lost updates, or `ConcurrentModificationException` under load (many players opening rapidly on a server).
- Fix approach: Use `ConcurrentHashMap` + atomic `compute()` for put-if-absent, or move to per-player capability (`CsboxPlayerData` already exists).

### CsboxConfig field defaults hardcoded in builder
- Issue: All defaults (cooldown, volume, animation speed) are baked into `CsboxConfig.java` builder calls. No migration path when defaults change between releases.
- Files: `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java`
- Impact: Low. Players keep their overridden values; new defaults only apply to fresh installs.
- Fix approach: Document the expected "first install" defaults in `AGENTS.md`; consider auto-resetting missing keys on upgrade.

## Known Bugs

### Empty `return null` in two packets survives hardening pass
- Issue: `PacketSyncBoxItems.java:145` and `PacketBoxOpenResult.java:125` both return `null` in some paths, despite CHANGELOG 1.0.4 emphasizing server-authority and validation.
- Files: `src/main/java/com/reclizer/csgobox/packet/PacketSyncBoxItems.java:145`, `src/main/java/com/reclizer/csgobox/packet/PacketBoxOpenResult.java:125`
- Trigger: Specific invalid box state combinations (deferred until call sites are inspected).
- Workaround: None documented. Likely causes a `NullPointerException` upstream that just crashes the packet handler silently (logs but doesn't surface).

### Config file rename not auto-migrated (round-trip csgobox.toml ↔ csgobox-common.toml)
- Issue: The config filename has been renamed twice within v1.0.5 (`csgobox.toml` → `csgobox-common.toml` → `csgobox.toml`). Neither direction auto-migrates existing values, so any player who customized their config on an intermediate build ends up with orphaned settings.
- Files: `src/main/java/com/reclizer/csgobox/CsgoBox.java` (registration call at line 49), `CHANGELOG.md` ([1.0.5] `### 更改` 段)
- Trigger: Players who customized their config while running the `csgobox-common.toml` build (or who still have a pre-1.0.5 `csgobox.toml`) upgrade to the current `csgobox.toml` build.
- Workaround: Players must manually rename or delete the old config file to pick up the new one. Two files may coexist indefinitely.

### Empty box warning text occluded by 3D model
- Issue: Pre-1.0.4 had a bug where empty-box warning was hidden behind the 3D model. Fixed by drawing in foreground overlay, but the original problem suggests Z-order / render-order is fragile in this screen.
- Files: `src/main/java/com/reclizer/csgobox/gui/CsboxScreen.java` (relevant render calls)
- Workaround: Trust the foreground-overlay fix until a different UI element triggers the same Z-ordering issue.

## Security Considerations

### Server-authority contract is sound but unevenly enforced
- Risk: `PacketCsgoProgress` correctly re-validates box, keys, weight, RNG, and cooldowns on server (CHANGELOG 1.0.4 added matching request IDs, animation item data, and `sendRejected` for failures). However, validation is split across `PacketValidation` helpers, per-packet `handleServer` methods, and `ItemCsgoBox` static methods — easy to forget a guard when adding a new packet.
- Files: `src/main/java/com/reclizer/csgobox/packet/PacketValidation.java`, `src/main/java/com/reclizer/csgobox/packet/PacketCsgoProgress.java`
- Current mitigation: Recent hardening (1.0.4) plus `PacketValidation` helpers for stack-copying and int clamping.
- Recommendations: Extract a single `OpenBoxValidator.validate(ServerPlayer, ItemStack, long requestId)` that returns a `ValidationResult` enum; have every open-related packet call it. Reduces drift.

### Client animation consumes server-authorized result, but `requestId` is forgeable
- Risk: Client sends `requestId` with each open. Server uses it only to route responses; the value is not authenticated. A client could replay a recent `requestId` to confuse the GUI logic.
- Files: `src/main/java/com/reclizer/csgobox/packet/PacketCsgoProgress.java`, `src/main/java/com/reclizer/csgobox/packet/PacketBoxOpenResult.java`
- Current mitigation: Server-side `OPEN_BLOCKED_UNTIL_TICK` map per player, and server's own choice of `requestId` to send back is unrelated.
- Recommendations: Either drop `requestId` and use monotonic counter per session, or sign it with a per-session HMAC. Low priority since the server is the only authority on actual rewards.

### KubeJS integration loads user-supplied JSON
- Risk: `BoxJsonLoader` parses `config/csbox/*.json` and trusts it implicitly for items, weights, entity IDs. A malicious or buggy KubeJS script could inject invalid items, NBT exploits, or extreme weights.
- Files: `src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java`
- Current mitigation: Weight clamping (max 10000), drop-rate clamping (0..1), `List.copyOf` / `Map.copyOf` defensive copies at trust boundaries.
- Recommendations: Item identifier existence check (`BuiltInRegistries.ITEM.containsKey(id)`) before accepting; reject empty grade lists explicitly.

## Performance Bottlenecks

### Per-tick static HashMap cleanup
- Problem: `OPEN_BLOCKED_UNTIL_TICK` is a `HashMap<UUID, Long>` that grows unbounded if entries are never evicted (no tick handler visible).
- Files: `src/main/java/com/reclizer/csgobox/packet/PacketCsgoProgress.java:46`
- Cause: Entries added on open but never removed on expiry.
- Improvement path: Add a `ServerTickEvent` handler that walks the map and removes entries older than the cooldown window. O(n) per tick where n = active recent players; trivially fine.

### `CsboxProgressScreen` interpolation computed per frame
- Problem: Animation interpolation and easing calculated inline each frame; no caching of the rolled result.
- Files: `src/main/java/com/reclizer/csgobox/gui/CsboxProgressScreen.java` (248 lines)
- Cause: Speed → position math is cheap per-frame but not pre-computed when the result lands near the start of the strip.
- Improvement path: After the server sends the rolled index, precompute easing curve once and just sample it during render.

### `BoxJsonLoader` reads + parses every JSON on every reload
- Problem: `/csbox reload` reads all JSON files and re-parses into `BoxDefinition`. For servers with many custom boxes (KubeJS-driven packs), reload is O(files × size).
- Files: `src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java`
- Cause: No incremental reload; no caching of parsed `BoxDefinition`.
- Improvement path: Cache `Path -> BoxDefinition` and only re-parse modified files (file mtime check).

## Fragile Areas

### Server-side `/csbox` command
- Files: `src/main/java/com/reclizer/csgobox/command/CsboxCommand.java` (472 lines)
- Why fragile: 5 subcommands (`list`, `info`, `add`, `give`, `reload`) with TAB completion; permission checks; hand-item parsing. High cyclomatic complexity.
- Safe modification: Add a new subcommand by copying the existing pattern; reuse `CsboxCommand.literal`/`argument` builders.
- Test coverage: None. Manual UAT only.

### Box animation selection (server rolls, client animates)
- Files: `src/main/java/com/reclizer/csgobox/packet/PacketCsgoProgress.java`, `src/main/java/com/reclizer/csgobox/gui/CsboxProgressScreen.java`
- Why fragile: The animation must visually land on the server-rolled index. CHANGELOG 1.0.4 fixed "animation speed behavior abnormal when winning item is near the start" — a previous regression that shipped. Any change to the easing or strip-window math can re-introduce visual mismatch.
- Safe modification: Keep the index-mapping math identical; only change visuals that don't affect landing position.
- Test coverage: None. Manual visual UAT per `README.md:97-108`.

### Json-defined box grade/item model
- Files: `src/main/java/com/reclizer/csgobox/box/BoxDefinition.java`, `src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java`
- Why fragile: Both `components` (modern DataComponent) and `tag` (legacy NBT) accepted. Schema evolution (e.g., adding a `count` field) requires careful default handling.
- Safe modification: Add new optional fields with sensible defaults; never remove a field without a migration step.
- Test coverage: None.

### Entity drop rate logic
- Files: `src/main/java/com/reclizer/csgobox/event/ModEvents.java:41`
- Why fragile: Uses `CONFIG.globalDropRatePercent` directly — no null check (CONFIG is `final`). Per-entity rates override via `entity_drop_rates` map. Looting-enchant scaling (`×0.5 per level, capped at 100%`) is hardcoded.
- Safe modification: Keep the formula; add a config entry for the looting multiplier if server admins want to tune it.
- Test coverage: None.

## Scaling Limits

### Static random state
- Current capacity: One `SecureRandom` and one cooldown map shared globally.
- Limit: For very high player counts (hundreds of concurrent openers on a single server), the cooldown map cleanup could become O(n) per tick.
- Scaling path: Already-trivial map cleanup is fine; if needed, shard by player UUID prefix.

### Animation strip rendering
- Current capacity: Strip width is screen-determined; safe for any GUI scale.
- Limit: None expected; client-side only.

## Dependencies at Risk

### NeoForge 26.1.2.76 (just-ported)
- Risk: Recent port (commit `bad19f2`); packaging-version-specific APIs may still have regressions.
- Impact: Future MC patches may require another port cycle.
- Migration plan: Already documented in `docs/port-26.1.2.md`. Watch for deprecation warnings on next NeoForge bump.

### Java 21 toolchain (forced via `org.gradle.java.home` + `javaToolchains.launcherFor`)
- Risk: `org.gradle.java.home` in `gradle.properties` points at a macOS-specific path; Linux/Windows contributors must override. As of commit `a8bea6a`, the build also pins the `runClient` `JavaExec` launcher via `javaToolchains.launcherFor` so it ignores the shell's `JAVA_HOME`/`PATH` (a shell pointed at JDK 25 used to launch the game with JDK 25 and crash, since NeoForge 1.21.1 requires JDK 21). The pin protects macOS/Windows developers whose shell defaults to a newer JDK.
- Impact: Cross-platform contributors still hit the `org.gradle.java.home` failure on a fresh checkout (the launcher pin only fixes the runConfig). The pinned JDK 21 home is also registered in `org.gradle.java.installations.paths` so the toolchain resolver finds the same JDK.
- Migration plan: Document the override in `AGENTS.md` (already done); consider removing the hardcoded `org.gradle.java.home` now that the project is on stable Java 21, and rely solely on `javaToolchains`.

## Missing Critical Features

### No automated test suite
- Problem: `src/test/java/` does not exist. No JUnit, no NeoForge `gameTest`, no unit tests anywhere. `PacketValidation` helpers act as if they were testable but are never called from a test.
- Files: `src/test/` (missing), `build.gradle` (no `test {}` block per quality agent report).
- Blocks: Safe refactoring of any packet class, the `/csbox` command, or `RandomItem`. Each of these is at "if it compiles, ship it" risk.

### No JSON schema validation for user box files
- Problem: `BoxJsonLoader` accepts malformed or missing-field JSON silently (defaults applied, warnings logged). Users editing `config/csbox/*.json` get no clear error.
- Files: `src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java`
- Blocks: Friendly in-game `/csbox reload` errors; better self-service box editing.

### No English README/CHANGELOG parallel
- Problem: All user-facing docs (`README.md`, `CHANGELOG.md`, `AGENTS.md`) are Chinese-only. Limits international contribution.
- Files: `README.md`, `CHANGELOG.md`, `AGENTS.md`
- Blocks: Wider contributor base; clearer cross-language support questions.

## Test Coverage Gaps

### All server-authority packet logic
- What's not tested: Every `handleServer` in `packet/` package. Server-side validation of weights, item lists, cooldowns, requestId matching.
- Files: `src/main/java/com/reclizer/csgobox/packet/*.java`
- Risk: High. Any future packet that forgets to call `PacketValidation.copyStacks` or skips the cooldown check will silently ship a server-authority regression.
- Priority: High.

### RandomItem roll distribution
- What's not tested: Weighted random sampling. Empty-input handling was added defensively in 1.0.4; no regression test locks that behavior.
- Files: `src/main/java/com/reclizer/csgobox/utils/RandomItem.java`
- Risk: Medium. A bad clamp or off-by-one would shift drop rates subtly without crash.
- Priority: Medium.

### `/csbox` command argument parsing
- What's not tested: TAB completion, subcommand dispatch, hand-item parsing for `/csbox add`.
- Files: `src/main/java/com/reclizer/csgobox/command/CsboxCommand.java`
- Risk: Medium. 472 lines of command logic with zero tests.
- Priority: Medium.

### BoxJsonLoader round-trip (parse → serialize → parse equality)
- What's not tested: `saveToFile(BoxDefinition)` followed by `loadAll()` should produce an equivalent registry.
- Files: `src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java`
- Risk: Low-to-medium. If serialization loses fields, reload after `/csbox add` would silently degrade box definitions.
- Priority: Medium.

## Update 2026-06-29

Incremental update applied against HEAD `a8bea6a`. The 2026-06-28 baseline remains authoritative for all unchanged sections; the notes below reflect only deltas captured in this pass.

### Recipe directory renamed: `data/csgobox/recipes/` → `data/csgobox/recipe/`

- **Status: Resolved.** The "stale recipes dir" path concern (if previously tracked under any data-pack path note) is no longer applicable. The directory has been renamed and the modid corrected to `recipe` (singular) to match Minecraft's data-pack convention (`Registries.elementsDirPath(Registries.RECIPE)`).
- **Current state:** `src/main/resources/data/csgobox/recipe/` contains 4 files: `csgo_key0.json`, `csgo_key1.json`, `csgo_key2.json`, `csgo_key3_smithing.json`. The old `recipes/` directory does not exist.
- **Cross-reference:** `AGENTS.md` now explicitly states "**注意是单数 `recipe`**，与 Minecraft 数据包规范一致" and references the `Registries.elementsDirPath(Registries.RECIPE)` lookup.
- **Risk if reintroduced:** A `git mv` to the pluralized `recipes/` would silently break recipe loading because NeoForge's RecipeManager only scans the singular `recipe/` path. Any PR touching recipe files must preserve the singular form.

### `csgo_key3` workbench recipe removed (commit `be40e5a`, breaking `!`)

- **Status: Breaking change shipped, documented in CHANGELOG and `docs/update-1.0.5.md`.**
- **What changed:** `data/csgobox/recipe/csgo_key3.json` (the 3× netherite-ingot crafting recipe) has been deleted. `csgo_key3` (netherite key) is now exclusively obtainable via the smithing table: `csgo_key2` + `netherite_upgrade_smithing_template` + `netherite_ingot`.
- **Files removed:** `src/main/resources/data/csgobox/recipe/csgo_key3.json` (was 18 lines).
- **Files amended:** `CHANGELOG.md`, `README.md`, `AGENTS.md`, `docs/update-1.0.5.md`.
- **Migration:** No data migration required; existing worlds continue to work. Existing `csgo_key3` items in inventories remain valid. Only the recipe book entry for the old workbench recipe disappears.
- **Documentation drift risk:** Player-facing notes in `README.md` were updated to drop the workbench row. Any re-port (`docs/port-26.1.2.md`) or downstream pack documentation must also drop the workbench reference — the `docs/port-26.1.2.md` file was authored before this amendment (still says "配方附录" implicitly assumes workbench) and should be reviewed for the same correction if 26.1.2 ships in lockstep with 1.0.5.

### `Cloth Config` dependency fully removed (commit `862ab1f`)

- **Status: Resolved.** The "Cloth Config dependency" tech-debt entry is no longer applicable.
- **What changed:** The `me.shedaniel.cloth:cloth-config-neoforge` dependency has been dropped from `build.gradle`. Config persistence now uses NeoForge's native `ModConfigSpec` API.
- **On-disk artifact:** Config path history within v1.0.5: `csgobox.toml` (pre-1.0.5) → `csgobox-common.toml` (v1.0.5 first wave) → `csgobox.toml` (v1.0.5 revert). Current registration in `CsgoBox.java:49` writes to `config/csgobox.toml` via `ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC, "csgobox.toml")`.
- **Validation:** A `grep -ri "cloth|shedaniel" src/` returns no matches — no residual references in source. The CHANGELOG `1.0.5` manual test step `确认启动期间 latest.log 中不出现任何 cloth 或 shedaniel 相关条目` provides a runtime check.
- **Note:** `ModConfigSpec`-based config is generally less ergonomic for complex nested sections. The current schema is flat (`CONFIG.fieldName`), which is appropriate for ~10 fields; if the config grows, consider whether a wrapper like Cloth Config should be re-evaluated or whether `ModConfigSpec`'s nested `Builder.push()` is sufficient.

### `CsboxConfig` relocated to `com.reclizer.csgobox.config` package (commit `b7b11e5`)

- **Status: Resolved.** The "config in wrong package" concern is closed.
- **What changed:** `CsboxConfig.java` moved from `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java` (package `com.reclizer.csgobox.config`) to `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java` (package `com.reclizer.csgobox.config`). Every other source on `main` already imported `com.reclizer.csgobox.config.CsboxConfig`, so the previous layout was a compile-time discrepancy.
- **Side fix in the same commit:** `.gitignore`'s `config/` entry was tightened to `/config/` so source-level `config/` directories (like the new `src/main/java/com/reclizer/csgobox/config/`) are no longer accidentally ignored by git.
- **Validation:** `ls src/main/java/com/reclizer/csgobox/config/` returns "No such file or directory"; `ls src/main/java/com/reclizer/csgobox/config/` lists only `CsboxConfig.java`. Package declaration on line 1 of `CsboxConfig.java` matches the directory path.
- **Risk if reintroduced:** Re-creating a `com.reclizer.csgobox.config` package (or any `csgobox`-prefixed package alongside the `csgobox`-prefixed tree) would re-introduce import drift and break the build. All new config files must go under `com.reclizer.csgobox.*`.

### JDK 21 runConfig pin (commit `a8bea6a`)

- **Status: Mitigated for the runClient path.** The previous concern (a shell with `JAVA_HOME=jdk-25` launching the game with JDK 25 and crashing) is resolved for the `runClient` IntelliJ runConfig and `./gradlew runClient`.
- **What changed:** `build.gradle` now resolves a JDK 21 launcher via `javaToolchains.launcherFor` and pins the executable on every `JavaExec` task, making the launcher independent of `JAVA_HOME`/`PATH`. The matching JDK 21 home is registered in `gradle.properties` via `org.gradle.java.installations.paths` so the toolchain resolver finds it.
- **Residual risk:** The `org.gradle.java.home` line in `gradle.properties` still points at a macOS-specific path. On a fresh Linux/Windows checkout the toolchain pin in the build will work, but `org.gradle.java.home` may still cause Gradle to hard-fail before reaching the launcher pin if the path doesn't exist locally. The `AGENTS.md` note ("`org.gradle.java.home` 已设置为 macOS 路径") is now slightly stale: the same path is also registered under `org.gradle.java.installations.paths`, but non-macOS contributors still need to override `org.gradle.java.home`.
- **Verification baseline:** Commit message records `JAVA_HOME=/jdk-25 ./gradlew runClient -> BUILD SUCCESSFUL, JVM 21.0.10 in latest.log`. Use the same incantation to re-verify after any change to `build.gradle` or `gradle.properties`.

### Summary of file-path refreshes applied

The following file paths in this document were updated to reflect the `com.reclizer.csgobox.*` → `com.reclizer.csgobox.*` correction (which was already applied repo-wide in 1.0.5 but the previous CONCERNS.md still used `csgobox` for the source paths):

- `src/main/java/com/reclizer/csgobox/...` → `src/main/java/com/reclizer/csgobox/...` across all 4 Tech Debt entries, all 4 Fragile Areas, the Server-authority / Client animation / KubeJS Security entries, the 3 Performance Bottleneck entries, and all 4 Test Coverage Gaps.
- `CHANGELOG.md:11` → `CHANGELOG.md:14` in the "Old config file not auto-migrated" entry, matching the new line number for the config-path note in the 1.0.5 section.

No semantic changes were made to the baseline concerns — only path corrections. The 2026-06-28 wording is preserved verbatim aside from those path updates.

---

*Concerns audit: 2026-06-28*
*Incremental update: 2026-06-29 (recipe dir rename, CsboxConfig relocation, Cloth Config removal, csgo_key3 workbench recipe removal, JDK 21 runConfig pin)*