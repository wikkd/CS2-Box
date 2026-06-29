# External Integrations

**Analysis Date:** 2026-06-29

This mod is a self-contained client/server Minecraft mod. It runs entirely inside the Minecraft JVM and does not call out to any third-party SaaS, HTTP API, telemetry endpoint, or remote service. All "integrations" are against vanilla Minecraft / NeoForge runtime surfaces.

## APIs & External Services

**HTTP / REST:**
- None. No `java.net.http`, `okhttp`, `HttpClient`, or `URLConnection` usage anywhere in `src/`.

**Telemetry / Analytics:**
- None. No opt-in data collection.

**Authentication:**
- None. Mod does not implement login. It only relies on Minecraft's built-in session and the `Player` object available on the server thread.

**SaaS / Paid services:**
- None.

## Data Storage

**Databases:**
- None. The mod has no SQL or NoSQL layer.

**File Storage:**
- Local filesystem only.
  - `config/csgobox.toml` - runtime config written by `ModConfigSpec` (see `CsgoBox.java:57`)
  - `config/csbox/*.json` - box definitions loaded on startup by `BoxJsonLoader.java:40` (path `FMLPaths.CONFIGDIR.get().resolve("csbox")`)
  - Path-traversal protection: `BoxJsonLoader.deleteFile()` (`BoxJsonLoader.java:453-467`) validates the resolved path stays inside the `csbox/` directory before deletion.

**Caching:**
- None. In-process only. `BoxRegistry` (`src/main/java/com/reclizer/csgobox/box/BoxRegistry.java`) holds an in-memory `LinkedHashMap<ResourceLocation, BoxDefinition>`; on `/csbox reload` it is cleared and re-populated.

## Authentication & Identity

**Auth Provider:**
- Minecraft session (built-in Mojang account). The mod does not perform auth itself.

**Authorization (in-game):**
- Command-level: `/csbox ...` requires `hasPermission(2)` (op level 2) - declared in `src/main/java/com/reclizer/csgobox/command/CsboxCommand.java:65`.
- Network-level: server is authoritative for box opening outcomes (`PacketCsgoProgress.handleServer` rejects invalid requests, `PacketCsgoProgress.java:53-170`). The client `requestId` is used only for matching the response to the visible animation, never for security decisions (`PacketCsgoProgress.java:33-34`, `PacketBoxOpenResult.java:21-25`).

## Monitoring & Observability

**Error Tracking:**
- None. No Sentry, no Bugsnag, no equivalent.

**Logs:**
- SLF4J via `com.mojang.logging.LogUtils.getLogger()` (`CsgoBox.java:44`).
- All log lines go through NeoForge's log4j configuration. Mod uses standard `LOGGER.info`, `LOGGER.warn`, `LOGGER.error`, `LOGGER.debug`.
- No structured logging. No MDC.

## CI/CD & Deployment

**Hosting:**
- No hosted backend. Mod is distributed as a `.jar` artifact consumers install into the Minecraft `mods/` folder.

**CI Pipeline:**
- None detected in this repo (no `.github/workflows/`, no `.gitlab-ci.yml`, no `Jenkinsfile`). Build is run manually by a developer invoking `./gradlew build`.

**Release artifacts:**
- Built jar: `build/libs/csgobox-1.0.5.jar` (`archivesName = mod_id` in `build.gradle:13`).
- `maven-publish` plugin declared in `build.gradle:5` but no `publishing {}` block configured - jar is published only locally.

## Game Engine Integration Points

These are not "external" services but are the canonical surfaces the mod integrates with inside the Minecraft JVM:

**NeoForge event bus (`net.neoforged.bus.api.IEventBus`):**
- Mod bus: registration of `DeferredRegister`s, `RegisterPayloadHandlersEvent`, `FMLCommonSetupEvent`, `RegisterEvent`, `ModConfigEvent.Reloading`.
- Game bus (`NeoForge.EVENT_BUS`): `LivingDeathEvent` (mob drop), `PlayerInteractEvent.RightClickItem` (right-click to open box), `PlayerEvent.PlayerLoggedInEvent` (fire `ModLoadedTrigger` so `csgobox:root` advances).
- See `src/main/java/com/reclizer/csgobox/CsgoBox.java:56-83`, `src/main/java/com/reclizer/csgobox/event/ModEvents.java`, `src/main/java/com/reclizer/csgobox/event/ClickEvent.java`.

**Minecraft registries (`net.minecraft.core.registries.BuiltInRegistries`):**
- `Registries.ITEM` - items registered via `ModItems.ITEMS` (`src/main/java/com/reclizer/csgobox/item/ModItems.java:46-51`)
- `Registries.SOUND_EVENT` - sounds registered via `ModSounds.SOUNDS` (`src/main/java/com/reclizer/csgobox/sounds/ModSounds.java:14`)
- `Registries.CREATIVE_MODE_TAB` - one custom tab `EQUIPMENT_TAB` (`ModItems.java:21-44`)
- `Registries.CUSTOM_STAT` - custom stat `csgobox:opened_boxes` registered in `CsgoBox.java:69-70`, resolved in `CsgoBox.java:101-108`
- `Registries.TRIGGER_TYPE` - `csgobox:opened_box` and `csgobox:mod_loaded` triggers (`CsgoBox.java:71-73`)
- `Registries.DATA_COMPONENT_TYPE` - `csgobox:box_id` data component (`src/main/java/com/reclizer/csgobox/item/ItemCsgoBox.java:36-44`)

**NeoForge payload network (`net.neoforged.neoforge.network`):**
- Four `CustomPacketPayload` packets registered in `CsgoBox.java:86-92`:
  - `csgobox:csgo_progress` (client -> server, opening request)
  - `csgobox:box_open_result` (server -> client, opening result + animation strip)
  - `csgobox:request_box_items` (client -> server, preview fetch)
  - `csgobox:sync_box_items` (server -> client, preview data)
- Payload delivery uses `PacketDistributor.sendToServer` / `sendToPlayer`.
- Validation is centralised in `src/main/java/com/reclizer/csgobox/packet/PacketValidation.java` (size caps, defensive copies, list-size consistency checks).

**NeoForge player data attachments (`net.neoforged.neoforge.attachment`):**
- One attachment type `csgobox:player_data` of `CsboxPlayerData` (`src/main/java/com/reclizer/csgobox/capability/ModCapability.java:17-22`). Persists per-player open-state seed across server restarts via `CsboxPlayerData.CODEC`.

**NeoForge config system (`net.neoforged.neoforge.common.ModConfigSpec`):**
- Single common config file `config/csgobox.toml`, four sections (`general`, `advanced`, `sound`, `animation`) - see `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java`.

## Environment Configuration

**Required env vars:**
- None. Mod does not read environment variables at runtime.

**Build-time env vars:**
- `JAVA_HOME` may be set, but `build.gradle:31-33` overrides it for JavaExec tasks using the JDK 21 toolchain.

**Secrets location:**
- None. Mod has no secrets, no API keys, no credentials. `BoxJsonLoader` rejects path-traversal in `deleteFile` but otherwise no auth is required for local file ops.

## Webhooks & Callbacks

**Incoming (HTTP):**
- None.

**Outgoing (HTTP):**
- None.

**In-game (NeoForge event listeners are the only "callbacks"):**
- `LivingDeathEvent` -> `ModEvents.livingDeath` (mob death -> possible box drop)
- `PlayerInteractEvent.RightClickItem` -> `ClickEvent.onRightClick` (client-side right-click -> open GUI)
- `PlayerEvent.PlayerLoggedInEvent` -> `ModEvents.playerLoggedIn` (player joins -> fire `ModLoadedTrigger`)
- `RegisterCommandsEvent` -> `CsboxCommand.register` (command tree registration)
- `FMLCommonSetupEvent` -> `CsgoBox.commonSetup` (load box JSONs) and `CsgoBox.resolveOpenedBoxesStat` (resolve custom stat)
- `RegisterPayloadHandlersEvent` -> `CsgoBox.registerPayloads` (network packet handlers)
- `ModConfigEvent.Reloading` -> `CsgoBox` anonymous listener (log when config reloads)

---

*Integration audit: 2026-06-29*