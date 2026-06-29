# Testing Patterns

**Analysis Date:** 2026-06-28
**Last incremental update:** 2026-06-29

## Test Framework

**Runner:** None present in this project.

The repository contains **no automated test code**. There is no `src/test/java/`, no `src/test/resources/`, no `src/gametest/java/`, no JUnit / TestNG / Jupiter dependencies, no `test {}` block in `build.gradle`, and no assertion library on the classpath. The only test-related configuration is the NeoForge `forge.enabledGameTestNamespaces` system property in `build.gradle:30-39`, which is wired to the `csgobox` namespace but has no game test classes to load.

**Assertion Library:** None.

**Run Commands:** None configured.

```bash
# Not applicable — there are no test tasks defined in build.gradle.
# The gradle build only exposes: build, clean build, compileJava (per AGENTS.md).
```

**Why no tests:** This is a Minecraft client/server mod. The team's stated verification approach (see `README.md:97-108`, `AGENTS.md`, and `CHANGELOG.md`) is **manual UAT** in a live Minecraft client, exercising the box-opening animation, the `/csbox` command tree, the JSON config auto-generation, and recipe crafting (workbench + smithing table).

## Test File Organization

**Location:** Not applicable — no tests exist.

**Naming:** Not applicable.

**Structure:** Not applicable.

## Test Structure

**Suite Organization:** Not applicable.

**Patterns:** Not applicable.

## Mocking

**Framework:** Not applicable.

**Patterns:** Not applicable.

**What to Mock / What NOT to Mock:** Not applicable.

## Fixtures and Factories

**Test Data:** None. The closest analogue is `BoxJsonLoader.writeDefaultIfEmpty()` (`src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java:83-159`), which generates `config/csgobox/weapon_supply_box.json` on first run. This is a runtime default, not a test fixture.

**Location:** Not applicable.

## Coverage

**Requirements:** None enforced.

**View Coverage:** Not applicable.

## Test Types

**Unit Tests:** Not present. Pure logic candidates that would benefit most from unit tests:
- `src/main/java/com/reclizer/csgobox/utils/RandomItem.java` — `randomItemsGrade`, `clampToValidItem`, `findFallback`, `findFallbackFromGradeMap`, `precomputeGradeMap`
- `src/main/java/com/reclizer/csgobox/packet/PacketValidation.java` — `requireSameSize`, `requireMaxSize`, `copyStacks`, `copyClampedInts`, `copyNonNegativeInts`, `trimQueue`
- `src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java` — `parseWeights`, `parseEntities`, `parseItem`, `addTutorial`
- `src/main/java/com/reclizer/csgobox/utils/ColorTools.java` — `argbColor`, `deepColor`, `colorItems`

**Integration Tests:** Not present. The closest integration exercise is the manual UAT documented in `README.md:97-108` (default JSON generation, item format variants, missing IDs, empty grades, key requirements, animation final item, ESC-then-reopen, empty-box warning overlay, netherite key smithing-only recipe).

**E2E Tests:** Not present. The NeoForge `gameTest` framework is enabled in `build.gradle:37-39` but the `csgobox` game-test namespace contains zero test classes. Game tests would let you start a fake server, register boxes, send packets, and assert on `BoxRegistry` state — but no such infrastructure has been written.

**Game Tests:** Framework enabled (`build.gradle:30, 34, 38`), namespace registered, content absent. To add game tests, create `src/gametest/java/com/reclizer/csgobox/` and place `GameTest`-annotated methods in classes registered via `RegisterGameTestsEvent`.

## Common Patterns

**Async Testing:** Not applicable.

**Error Testing:** Not applicable.

## Defensive Code That Functions Like Tests

Several methods encode invariants that effectively act as lightweight runtime tests:

- `PacketValidation.requireSameSize` / `requireMaxSize` — fails fast on malformed inputs at packet boundaries (`src/main/java/com/reclizer/csgobox/packet/PacketValidation.java:14-25`)
- `BoxDefinition.read` and `PacketSyncBoxItems.read` — `throw new DecoderException("Invalid X count: " + n)` when size fields are out of range (`src/main/java/com/reclizer/csgobox/box/BoxDefinition.java:107-109`, `src/main/java/com/reclizer/csgobox/packet/PacketSyncBoxItems.java:91-93, 102-104`)
- `BoxJsonLoader.parseWeights` — clamps or defaults any weight value, logs at WARN, never throws (`src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java:270-292`)
- `BoxJsonLoader.deleteFile` — guards against path traversal via `file.startsWith(BOXES_DIR.normalize())` (`src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java:455-469`)
- `RandomItem.randomItemsGrade` — returns `1` (lowest grade) when total weight is non-positive instead of throwing (`src/main/java/com/reclizer/csgobox/utils/RandomItem.java:33-54`)

These are the closest the project comes to "test the contract" — they are exercised on every load but never asserted by automated tests.

## Manual UAT Checklist (from `README.md`)

When changing box-opening, config, or animation behavior, the manual test points documented in `README.md:97-108` are:

1. Default JSON auto-generation and load (`config/csgobox/weapon_supply_box.json`)
2. Both object-style items (`{"id": ..., "count": ..., "components": ...}`) and legacy string-style items
3. Edge cases: missing item IDs, empty grade arrays, abnormal weights, odd-length entity lists
4. Box variants: required key, no key (`minecraft:air`), wrong key
5. Animation final item matches the actual reward received
6. ESC exit during animation does not break the next box open
7. Empty-box warning text is not occluded by the 3D model
8. Netherite key (`csgobox_key3`) craftable only via the smithing table (`data/csgobox/recipe/csgo_key3_smithing.json`); the workbench 3x netherite recipe was removed in 1.0.6

## Recommended Test Targets (if adding tests later)

If test infrastructure is added in a future phase, the highest-leverage starting points are:

1. **`RandomItem.randomItemsGrade`** — pure-function, easy to verify with seeded `Random`
2. **`PacketValidation`** — collection invariant helpers, trivial to unit test
3. **`BoxJsonLoader.parseItem` + `parseWeights` + `parseEntities`** — JSON parsing, defensive fallbacks, no Minecraft runtime needed
4. **`BoxJsonLoader.parseItem` legacy-string path** — backwards-compatibility branch worth pinning
5. **Game tests for `/csbox reload` and `/csbox add hand`** — would catch registry mutation regressions

Use **JUnit 5 (Jupiter)** as the test framework (most common for NeoForge 1.21+ projects), **AssertJ** for fluent assertions, and **`src/test/java/com/reclizer/csgobox/`** as the source root. Configure a `test { useJUnitPlatform() }` block in `build.gradle` and add `testImplementation 'org.junit.jupiter:junit-jupiter:5.x'` and `testImplementation 'org.assertj:assertj-core:3.x'` to the dependencies block.

---

## Update 2026-06-29

Incremental update applied to capture changes since the 2026-06-28 baseline. All unchanged sections above are preserved verbatim from the prior version.

**Path updates (recipe directory rename).** The only test-fixture-adjacent path reference in the doc — the smithing recipe location cited in UAT checklist item 8 — has been updated from `data/csbox/recipes/csgo_key3_smithing.json` to `data/csgobox/recipe/csgo_key3_smithing.json` (singular `recipe/`, `csgobox` namespace). This is a single-line correction; the manual test instruction itself (smithing-table-only crafting, workbench recipe removed) is unchanged.

**Java package renames in code references.** Every Java-source file path cited in the doc has been updated from `com.reclizer.csgobox` to `com.reclizer.csgobox`:
- `BoxJsonLoader` / `BoxDefinition` paths in the unit-test candidates list
- `RandomItem` / `PacketValidation` / `ColorTools` paths
- The "Defensive Code That Functions Like Tests" section
- The "Game Tests" recommended source-root path
- The "Recommended Test Targets" JUnit/AssertJ hint

**No new automated tests.** The 2026-06-28 baseline already documented the absence of `src/test/java/`, JUnit, AssertJ, and game tests. This remains true as of 2026-06-29 (HEAD `a8bea6a`); no new test scaffolding has been introduced. The UAT-driven verification posture is unchanged.

**Cloth Config → ModConfigSpec migration.** Although this is a quality / convention topic (covered in CONVENTIONS.md), it indirectly affects testing posture: any future test of the config loader should target `ModConfigSpec`-style config in `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java` rather than any Cloth-Config-era wrapper. No test scaffolding exists yet for either API; this is a forward-looking note for the Recommended Test Targets list.

**`docs/update-1.0.5.md` and `docs/port-26.1.2.md`** were read as part of this update but are version-history documents, not test specifications. They are not modified and are not cited in the testing checklist above.

---

*Testing analysis: 2026-06-28 (incremental update: 2026-06-29)*
