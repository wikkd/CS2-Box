# External Integrations

**Analysis Date:** 2026-06-29 (incremental update; baseline 2026-06-28)

This mod is a self-contained client/server Minecraft mod. It runs entirely inside the Minecraft JVM and does not call out to any third-party SaaS, HTTP API, telemetry endpoint, or remote service. All "integrations" are against vanilla Minecraft / NeoForge runtime surfaces.

## APIs & External Services

**None.**

- No HTTP client (no OkHttp / Apache HttpClient / java.net.http imports anywhere in `src/main/java/**`)
- No REST/RPC/GraphQL SDK
- No analytics, telemetry, or crash-reporting SDK (no Sentry/Bugsnag/Rollbar)
- No cloud SDK (no AWS / Azure / GCP)
- No payment / auth SaaS (no Stripe / Auth0 / Firebase)

## Data Storage

**Databases:**
- None. There is no database server, no SQLite file, no embedded H2.

**File Storage:**
- Local filesystem only, scoped to the standard NeoForge config directory.
- Box definitions: `<config_dir>/csbox/*.json` - one file per registered box (`BoxJsonLoader.BOXES_DIR = FMLPaths.CONFIGDIR.get().resolve("csbox")` at `src/main/java/com/reclizer/csbox/box/BoxJsonLoader.java:41`)
- Default file auto-created on first run: `weapon_supply_box.json` (`BoxJsonLoader.writeDefaultIfEmpty()` lines 83-159)
- Writes use atomic move (`Files.move(... REPLACE_EXISTING, ATOMIC_MOVE)` at `BoxJsonLoader.java:448`)
- Path-traversal guard: `deleteFile()` rejects paths outside `BOXES_DIR.normalize()` (`BoxJsonLoader.java:455-469`)
- Server-authoritative box state: `CsboxPlayerData` attached to each `Player` via NeoForge `AttachmentType` (`ModCapability.PLAYER_DATA` in `src/main/java/com/reclizer/csbox/capability/ModCapability.java`). Serialized via `ValueInput`/`ValueOutput` using `CsboxPlayerData.CODEC` and saved with the player.

**Caching:**
- None. No in-memory cache beyond the static `BoxRegistry.BOX_REGISTRY` `LinkedHashMap` (`src/main/java/com/reclizer/csbox/box/BoxRegistry.java:16`).

## Authentication & Identity

**Auth Provider:**
- Inherits Minecraft / NeoForge's built-in authentication. The mod does not implement its own login or identity layer.
- Mojang / Microsoft account auth is performed entirely by the Minecraft launcher and online-mode server flag; the mod only reads `Minecraft.getInstance().getUser().getName()` for diagnostic logging at startup (`CsBox.java:89`).
- Single-player / LAN servers operate in offline mode and need no additional auth.

## Monitoring & Observability

**Error Tracking:**
- None. No Sentry/Bugsnag/etc. integration. All error reporting is local log lines.

**Logs:**
- SLF4J / `com.mojang.logging.LogUtils` - mod logger at `CsBox.LOGGER` (`src/main/java/com/reclizer/csbox/CsBox.java:33`)
- Log level controlled via `CONFIG.enableDebugLogging` (`CsboxConfig.java` `[advanced]` section; `CsboxConfig` lives in `com.reclizer.csgobox.config` since commit b7b11e5, 2026-06-29)
- Stdout: build runs emit REGISTRIES markers at debug level (`build.gradle` line 24: `systemProperty 'forge.logging.markers', 'REGISTRIES'`)

## CI/CD & Deployment

**Hosting:**
- None. The mod is distributed as a single jar (`build/libs/csbox-<mod_version>.jar`). Release workflow is manual / external (no CI files in the repo).

**CI Pipeline:**
- No CI configuration files present in the repo (no `.github/workflows`, `.gitlab-ci.yml`, `Jenkinsfile`, or `.circleci`).
- `docs/port-26.1.2.md` references a "GitHub Actions matrix" that distinguishes `1.0.5` vs `1.0.5-26.1.2` jars, but the workflow definition is not committed to this repository.
- Build verification command: `./gradlew build` (per `AGENTS.md` and `README.md`)
- Quick compile check: `./gradlew compileJava`
- The `runClient` Gradle run config is now pinned to JDK 21 via the toolchain specifier (commit a8bea6a, 2026-06-29) so the dev client JVM uses JDK 21 regardless of the shell's `JAVA_HOME`

## Environment Configuration

**Required env vars:**
- None. The mod is configured entirely through:
  1. `config/csbox-common.toml` (NeoForge `ModConfigSpec`, written by `ModConfig.Type.COMMON` registration in `CsBox.java:45`)
  2. `config/csbox/*.json` (user-editable box definitions)

**Build-time env vars (developer machine only, set in `gradle.properties`):**
- `org.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` (macOS JDK 21 path; falls back to Gradle toolchain resolution if the path is missing)

**Secrets location:**
- None. The repo has no API keys, tokens, or credentials. `.gitignore` explicitly ignores `config/` and `init.gradle`, so local config and any local Gradle init scripts stay out of version control.

## Webhooks & Callbacks

**Incoming:**
- None. The mod does not expose any HTTP listener.

**Outgoing:**
- None. No outbound HTTP calls.

## Game-Internal "Integrations"

These are the only external integrations in the project sense - dependencies on Minecraft / NeoForge runtime surfaces:

**Minecraft engine (vanilla):**
- Registry access via `BuiltInRegistries.ITEM` / `BuiltInRegistries.ENTITY_TYPE` (e.g., `BoxJsonLoader.java:11`, `ModEvents.java:33`)
- DataComponents / ItemStack components (`net.minecraft.core.component.DataComponentPatch`, `DataComponents.CUSTOM_NAME`)
- NBT codec ops (`NbtOps`, `TagParser.parseCompoundFully`) for legacy JSON item tags (`BoxJsonLoader.java:364-373`)
- Entity lifecycle: `LivingDeathEvent` for mob drops (`event/ModEvents.java:31`)
- Inventory: `player.getInventory().getContainerSize()` / `getItem()` (`PacketCs2Progress.java:216`, `CsboxCommand.java:324`)
- Command dispatcher: Mojang `brigadier` registered on `RegisterCommandsEvent` (`command/CsboxCommand.java:62-179`)
- `Registries.elementsDirPath(Registries.RECIPE)` -> `data/<namespace>/recipe/` (singular) is the data-pack directory scanned by `RecipeManager`; the mod's recipe files now live at `src/main/resources/data/csgobox/recipe/` after the 2026-06-29 rename

**NeoForge API:**
- `ModLoadingContext.get().getActiveContainer().registerConfig(...)` (`CsBox.java:45`)
- `RegisterPayloadHandlersEvent` for custom network payloads (`CsBox.java:60-66`)
- `AttachmentType` (`NeoForgeRegistries.ATTACHMENT_TYPES`) for player-attached state (`capability/ModCapability.java:17`)
- `IAttachmentSerializer` interface for value-input/output serialization (`capability/ModCapability.java:23-34`)
- `DeferredRegister` for items, sounds, creative tabs, data components, attachment types (`ModItems.java`, `ModSounds.java`, `ItemCsBox.java:36-44`, `ModCapability.java:17-18`)
- Event bus subscribers annotated with `@EventBusSubscriber` (e.g., `event/ModEvents.java:22`, `event/ClickEvent.java:17`, `command/CsboxCommand.java:42`)
- `FMLPaths.CONFIGDIR` for filesystem paths (`box/BoxJsonLoader.java:41`)
- `ModConfigSpec` (NeoForge-native, since v1.0.5) backs the runtime config — registered in `CsBox.java` against the `csgobox.config.CsboxConfig` instance

**Custom network payload protocols (this mod defines 4):**
- `PacketCs2Progress` - client->server, "open this held box" request (`packet/PacketCs2Progress.java`)
- `PacketBoxOpenResult` - server->client, server-authoritative animation items + reward (`packet/PacketBoxOpenResult.java`)
- `PacketRequestBoxItems` - client->server, request current box preview (`packet/PacketRequestBoxItems.java`)
- `PacketSyncBoxItems` - server->client, preview response (`packet/PacketSyncBoxItems.java`)
- All four use NeoForge `StreamCodec` with explicit `write`/`read` lambdas for manual encode/decode (per `docs/port-26.1.2.md` requirement for records with >6 fields)

**Recipe integrations (`data/csgobox/recipe/`, 4 files):**
- `csgo_key0.json` - crafting_shaped (iron key)
- `csgo_key1.json` - crafting_shaped (gold key)
- `csgo_key2.json` - crafting_shaped (diamond key)
- `csgo_key3_smithing.json` - smithing_transform: template `minecraft:netherite_upgrade_smithing_template` + base `csgobox:csgo_key2` + addition `minecraft:netherite_ingot` -> `csgobox:csgo_key3`
- The netherite workbench recipe (`csgo_key3.json`) was removed in v1.0.5 (commit be40e5a); `csgo_key3` is now obtainable only via the smithing table.

---

## Update 2026-06-29

Incremental update covering commits `862ab1f`, `be40e5a`, `b7b11e5`, `a8bea6a` (plus the uncommitted rename of `data/csgobox/recipes/` -> `data/csgobox/recipe/`).

- **Recipe directory renamed** `data/csgobox/recipes/` -> `data/csgobox/recipe/` (singular) to match the Minecraft data-pack layout that `RecipeManager` scans via `Registries.elementsDirPath(Registries.RECIPE)`. All four recipe files now live under the singular directory. Added a new "Recipe integrations" section listing the 4 remaining files.
- **`csgo_key3` workbench recipe removed** (commit be40e5a). The recipe list shrank from 5 entries (4 crafting_shaped + 1 smithing_transform) to 4 entries (3 crafting_shaped + 1 smithing_transform). `csgo_key3` is now exclusively obtainable via the smithing table.
- **Cloth Config fully removed** (commit 862ab1f, v1.0.5). `me.shedaniel.cloth:cloth-config-neoforge` is no longer a dependency. NeoForge's native `ModConfigSpec` (resolved transitively through NeoForge) is the sole runtime-config backend; the `NeoForge API` integration list now reflects this.
- **`CsboxConfig` package relocated** from `com.reclizer.csbox.config` to `com.reclizer.csgobox.config` (commit b7b11e5, 2026-06-29). The new path is `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java`. The `CsgoBox.java:7` import was updated to match; the `Monitoring & Observability` section's `CsboxConfig.java` reference was updated to note the new package.
- **`runClient` JVM forced to JDK 21** (commit a8bea6a, 2026-06-29). The `runClient` Gradle run config is pinned to JDK 21 via the toolchain specifier. The `CI/CD & Deployment` section now reflects this so future readers know `./gradlew runClient` always uses JDK 21 regardless of shell `JAVA_HOME`.

*Integration audit: 2026-06-28 (baseline), 2026-06-29 (incremental update)*
