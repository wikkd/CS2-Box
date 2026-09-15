# Context — Synthesized from DOC ingestion (18 entries, 5 topics)

Each entry preserves `source: {path}` for downstream traceability.

---

## Topic 1 — Refactor baseline (Phase 0 + MultiLoader plan)

### CTX-001 — Phase 0 baseline audit

**Source**: `.planning/phase0-audit.md`
**Summary**: Multi-loader refactor Phase 0 baseline audit covering current module structure, resource distribution, build configuration, and class-level ownership. Documents现状清单 / 类级归属表 / 构建阻塞点 / 失败处理预案.
**Scope**: baseline audit, module structure, resource distribution, build config, class ownership, common/v1_21_1 modules

### CTX-002 — MultiLoader refactor complete plan

**Source**: `.planning/multiloader-refactor-plan.md`
**Summary**: Complete multiloader refactor plan: 9-phase task breakdown, risk inventory, file-level migration list, phase acceptance gate. Baseline uses current working tree state, not historical "completed" descriptions.
**Scope**: MultiLoader refactor, common/v1_21_1/v26_1_2 modules, phase 0-9 plan, constraints, file-level migration, verification gate
**Note**: Contains `## 关键技术决策` section with 4 embedded decisions, but they lack formal ADR Status/Decision/Consequences form. Embedded decisions: (1) keep Java 21 as primary toolchain, (2) preserve `CsgoBox.java` config init semantics, (3) `CONFIG` is final, (4) `common` cannot import `net.minecraft.*` / `net.neoforged.*`.

---

## Topic 2 — GUI fix guide (v26_1_2)

### CTX-003 — 26.1.2 GUI 修复指南

**Source**: `.planning/csbox-gui-26.1.2-fix-guide.md`
**Summary**: GUI fix guide with P0/P1/P2 priority tiers, root cause analysis (layout / rendering / text / item grid / state / visual), fix plan, verification standard. Covers button text visibility, main preview centering, unconfigured-state banner, button color tokens, text width limit.
**Scope**: P0-1, P0-2, P0-3, P1-2, P2-1, extractRenderState, ButtonPalette, RenderFontTool.drawStringClamped, CsLookItemScreen, CsboxScreen, CsboxProgressScreen, IconListTools
**Status**: P0-1 / P0-2 / P0-3 / P1-2 / P2-1 all implemented in v26_1_2 module. P1-1 / P1-3 / P2-2 explicitly deferred.

### CTX-004 — Runtime verification checklist

**Source**: `.planning/runtime-verification-checklist.md`
**Summary**: Records runtime visual verification items (RV-1 ~ RV-4), log-pattern checks, and root-cause notes for v26.1.2 3D rendering and config-loading fixes.
**Scope**: CsboxProgressScreen, CsboxScreen, RenderFontTool, GuiItemMove, BoxJsonLoader, RenderPipelines blit, csgo_background.png, DisplayInfo.background, Holder.Reference, ItemStack serialization, advancement system, weapon_supply_box.json

---

## Topic 3 — v1.0.5 review & changelog

### CTX-005 — v1.0.5 code review findings

**Source**: `.planning/v1.0.5-REVIEW.md`
**Summary**: v1.0.5 code review covering Cloth Config → ModConfigSpec migration and smithing recipes, with 7 findings (4 critical, 2 warnings, 1 info).
**Scope**: CsboxConfig, ModConfigSpec, csgo_key3_smithing, Cloth Config migration, server-authoritative RNG, NeoForge config API, data pack recipe path, CR-001, CR-002, CR-003

### CTX-006 — CHANGELOG (release history)

**Source**: `CHANGELOG.md`
**Summary**: Multi-version release history (v1.0.5 / v1.0.4 / v1.0.2): added features, fixes, changes, removals.
**Scope**: achievements, ModConfigSpec, csgo_key3 smithing recipe, box opening animation, /csbox command, KubeJS integration, client-server network, 1.20.1→1.21.1 migration

### CTX-007 — v1.0.5 release notes

**Source**: `docs/update-1.0.5.md`
**Summary**: Release notes for v1.0.5: achievements system (A Fresh Start + Shopper), Cloth Config removal, csgo_key3 smithing recipe, config path standardization.
**Scope**: v1.0.5, achievement system, Cloth Config removal, ModConfigSpec, smithing recipe, csgo_key3, config path

### CTX-008 — v1.0.4 release notes

**Source**: `docs/update-1.0.4.md`
**Summary**: Release notes for v1.0.4: server-authoritative opening, request-ID matching, JSON tutorial, animation correctness, ESC-cancel non-blocking, network safety.
**Scope**: v1.0.4, server-authoritative, PacketBoxOpenResult, JSON tutorial, animation, ESC cancel, RenderFontTool null fix

### CTX-009 — v1.0.5 manual test cases

**Source**: `docs/MANUAL-TESTING-v1.0.5.md`
**Summary**: Manual test cases for v1.0.5 covering regression, achievements, smithing recipes, commands, stability.
**Scope**: manual testing, v1.0.5, regression testing, achievement system, smithing recipe, config system, command system, stability

---

## Topic 4 — Architecture overview (1.21.1 + 26.1.2 porting)

### CTX-010 — Architecture overview (1.21.1)

**Source**: `docs/ARCHITECTURE.md`
**Summary**: CS2 Box NeoForge 1.21.1 mod architecture overview: component graph, box opening data flow, core abstractions (BoxDefinition/ItemCsgoBox/PacketCsgoProgress), directory structure.
**Scope**: CsgoBox, BoxRegistry, BoxDefinition, BoxJsonLoader, GradeGroup, ItemCsgoBox, ItemCsgoKey, ModItems, ModCapability, CsboxPlayerData, PacketCsgoProgress, PacketBoxOpenResult, PacketSyncBoxItems, PacketRequestBoxItems, PacketValidation, CsboxScreen, CsboxProgressScreen, CsLookItemScreen, ClickEvent, ModEvents, OpenedBoxTrigger, ModLoadedTrigger, CsboxConfig, CsboxCommand, ModSounds

### CTX-011 — 26.1.2 porting journal

**Source**: `docs/port-26.1.2.md`
**Summary**: MC 1.21.1 → 26.1.2 / NeoForge 21.1.115 → 26.1.2.76 migration journal: build environment changes, mod_version suffix, Gradle wrapper, settings.gradle.
**Scope**: MC 26.1.2, NeoForge 26.1.2.76, NeoGradle 7.1.38, Java 25 toolchain, --enable-preview, mod_version suffix, gradle.properties

---

## Topic 5 — Development & build documentation

### CTX-012 — Testing guide

**Source**: `docs/TESTING.md`
**Summary**: NeoForge GameTest testing guide covering run/write/debug and CI integration.
**Scope**: GameTest, NeoForge, Gradle, CI/CD, JUnit, @GameTestHolder, @GameTest, GitHub Actions

### CTX-013 — Quick-start guide

**Source**: `docs/GETTING-STARTED.md`
**Summary**: Java 21 / MC 1.21.1 / NeoForge 21.1.115 prerequisites, clone, build, runClient, /csbox give commands, first box opening.
**Scope**: Java 21, MC 1.21.1, NeoForge 21.1.115, Gradle 8.11, runClient, /csbox command

### CTX-014 — Configuration guide

**Source**: `docs/CONFIGURATION.md`
**Summary**: Guides configuring CS2 Box via csgobox.toml and custom box JSON files, all configurable fields, defaults, reload.
**Scope**: ModConfigSpec, csgobox.toml, config/csbox/*.json, animation settings, sound settings, box JSON schema

### CTX-015 — Development guide

**Source**: `docs/DEVELOPMENT.md`
**Summary**: Local development setup, build commands, data generation runbook.
**Scope**: JDK 21, Gradle, MC 1.21.1, NeoForge 21.1.115, runClient, runServer, runGameTestServer, data generation

### CTX-016 — README

**Source**: `README.md`
**Summary**: Project README: feature overview, installation, requirements, gameplay, configuration link.
**Scope**: CS2-Box, CS:GO box, NeoForge, MC 1.21.1, installation

### CTX-017 — Contributing guide

**Source**: `CONTRIBUTING.md`
**Summary**: Contributor guide: dev environment, code style, branch conventions, PR workflow, bug reporting.
**Scope**: contribution, code style, branch conventions, PR workflow, Java 21, Gradle 8.11

### CTX-018 — Agent guide (internal)

**Source**: `AGENTS.md`
**Summary**: Internal contributor / agent guide: build commands, ModConfigSpec pattern (no Cloth Config), package structure, key items & recipes, CONFIG final invariant.
**Scope**: ModConfigSpec, CsboxConfig, package structure, csgo_key0/1/2/3, smithing recipe, build.gradle, java.toolchain
**Note**: Overlaps v1.0.5-REVIEW CR-001/CR-002/CR-003 — encodes "locked-feeling" invariants (CONFIG is final, no null guards). The synthesizer treats this as DOC, but downstream consumers may want to elevate it to a quasi-ADR.

---

## Decisions — none ingested

No ADR documents were discovered in this ingest pass. If `AGENTS.md` invariants (CONFIG is final, no Cloth Config, etc.) should be locked as decisions, the user should mark them in a future ADR or set `--manifest precedence=0` and re-run.

## Requirements — none ingested

No PRD documents were discovered in this ingest pass. PR-001-style acceptance criteria (if needed) must be defined in a future PRD document.