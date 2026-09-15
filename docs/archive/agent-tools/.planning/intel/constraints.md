# Constraints — Synthesized from SPEC ingestion

Source: `/Users/shuangyuexingxun/Desktop/CS2-Box/.planning/multiloader-execution-spec.md`
Type: SPEC
Confidence: high
Precedence: ADR (none ingested) > SPEC > PRD (none ingested) > DOC

## CONSTRAINT-001 — MultiLoader module dependency direction

**Type**: api-contract
**Source**: `.planning/multiloader-execution-spec.md` §1.2, §2

The `common` module MUST NOT import any `net.minecraft.*` or `net.neoforged.*` symbols at compile time. All version-sensitive code (GUI rendering, attachment registration, network context, registry access) lives in platform modules (`v1_21_1/`, `v26_1_2/`).

**Verification**: `common/src/main/java/` must contain only Java that compiles without `net.minecraft` on classpath. Grep verification: `grep -r "import net\." common/src/main/java/` returns empty.

## CONSTRAINT-002 — Resource layering (common > platform > run overlay)

**Type**: schema
**Source**: `.planning/multiloader-execution-spec.md` §2 (阶段 2-3)

`common/src/main/resources/` holds version-shared assets (textures, sounds, lang, recipes, advancements, items). Platform modules add overlays for version-specific assets. `runs/client/` and `runs/server/` provide runtime test data overlays.

**Recipe path invariant**: `data/csgobox/recipe/` (singular `recipe`, NOT `recipes`) — Minecraft RecipeManager scans via `Registries.elementsDirPath(Registries.RECIPE)`. Source: `AGENTS.md` line 59.

## CONSTRAINT-003 — Build artifact naming

**Type**: protocol
**Source**: `.planning/multiloader-execution-spec.md` §2 + `gradle.properties`

- `v1_21_1/build/libs/csbox-1.21.1-1.0.5.jar`
- `v26_1_2/build/libs/csbox-26.1.2-1.0.5.jar`

Active version selected by `active_versions=` property in `gradle.properties`. Only one platform module is built per Gradle invocation.

## CONSTRAINT-004 — Java toolchain divergence

**Type**: nfr (non-functional)
**Source**: `.planning/multiloader-execution-spec.md` §1.1 + `docs/port-26.1.2.md`

- `v1_21_1/` — Java 21 (toolchain)
- `v26_1_2/` — Java 25 (toolchain) with `--enable-preview` flag (NeoForm recompilation requirement)

NeoGradle versions: `v1_21_1` uses 7.0.171, `v26_1_2` uses 7.1.38.

## CONSTRAINT-005 — Phase acceptance gate (阶段 0-5)

**Type**: nfr
**Source**: `.planning/multiloader-execution-spec.md` §2

Each refactor phase has explicit acceptance criteria. Must not proceed to next phase when:
- `common` compilation requires large one-shot platform shims
- A class moves between `common` and platform modules more than twice
- Build-system failures and business-migration failures interleave, making root cause unidentifiable

## CONSTRAINT-006 — Non-negotiable runtime invariants

**Type**: nfr
**Source**: `.planning/multiloader-execution-spec.md` (不可违反的约束) + `AGENTS.md`

- Java 21 stays as primary build toolchain unless `v26_1_2` empirically requires Java 25
- NeoForge native `ModConfigSpec`, NO Cloth Config
- `config/csgobox.toml` filename unchanged
- `data/csgobox/recipe/` path unchanged
- `CsgoBox.java` config initialization order semantics preserved in new platform entry
- `CONFIG` is `final` — delete all `null` guards
- v1.0.5 functionality preserved: opening animation, mob drop, command, achievements, four keys, network sync