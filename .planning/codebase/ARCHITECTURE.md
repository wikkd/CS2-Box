<!-- refreshed: 2026-06-29 -->
# Architecture

**Analysis Date:** 2026-06-29

## System Overview

```text
┌─────────────────────────────────────────────────────────────────┐
│                    ENTRY: Right-click Box Item                   │
│       `event/ClickEvent.java` (client-side @SubscribeEvent)      │
└────────────────────────────┬────────────────────────────────────┘
                             │ opens
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     GUI LAYER (client-only)                      │
│   `gui/CsboxScreen.java`       -> preview & open button          │
│   `gui/CsboxProgressScreen.java` -> server-driven animation      │
│   `gui/CsLookItemScreen.java`    -> 3D result display            │
└────────────────────────────┬────────────────────────────────────┘
                             │ sends payloads
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                  PACKET LAYER (bidirectional)                    │
│   `packet/PacketRequestBoxItems`  (C -> S, preview fetch)        │
│   `packet/PacketSyncBoxItems`     (S -> C, preview data)         │
│   `packet/PacketCsgoProgress`     (C -> S, open request)         │
│   `packet/PacketBoxOpenResult`    (S -> C, animation + reward)   │
│   `packet/PacketValidation.java`  (shared defensive helpers)     │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                  SERVER-AUTHORITATIVE LAYER                      │
│   `packet/PacketCsgoProgress.handleServer` (the heart)           │
│   - validates held box, key, cooldown                            │
│   - computes animation strip + winning item via `RandomItem`     │
│   - consumes key, shrinks box, gives reward                     │
│   - awards custom stat `csgobox:opened_boxes`                    │
│   - fires `OpenedBoxTrigger` for advancement progress            │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                       DOMAIN MODEL                               │
│   `box/BoxDefinition` (record, JSON-serializable)                 │
│   `box/GradeGroup`     (record, one per rarity tier)             │
│   `box/BoxRegistry`    (in-memory map, loaded from JSON)         │
│   `box/BoxJsonLoader`  (filesystem read/write, default seeding)  │
│   `item/ItemCsgoBox`   (Item, references box by box_id)          │
│   `item/ItemCsgoKey`   (Item, key variants 0..3)                 │
│   `capability/CsboxPlayerData` + `ModCapability`                 │
│   `advancement/OpenedBoxTrigger`  + `ModLoadedTrigger`           │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  SIDE EFFECTS / OUTPUT                                            │
│  - Item drop on mob death      (`event/ModEvents.livingDeath`)    │
│  - Custom stat persisted       (`Stats.CUSTOM` registry)         │
│  - Advancement progress        (`CriteriaTriggers`)              │
│  - Player data attachment      (`CsboxPlayerData`, codec)        │
│  - Saved config + box JSON     (`config/csgobox.toml`,           │
│                                  `config/csbox/*.json`)           │
└─────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| `CsgoBox` | Mod entrypoint; registers registries, payloads, config, sounds, attachments | `src/main/java/com/reclizer/csgobox/CsgoBox.java` |
| `ItemCsgoBox` | The box item; reads/writes the `box_id` data component, tooltip of contents | `src/main/java/com/reclizer/csgobox/item/ItemCsgoBox.java` |
| `ItemCsgoKey` | Key item (4 tiers: iron/gold/diamond/netherite) | `src/main/java/com/reclizer/csgobox/item/ItemCsgoKey.java` |
| `ModItems` | `DeferredRegister` for items + the custom creative tab `EQUIPMENT_TAB` | `src/main/java/com/reclizer/csgobox/item/ModItems.java` |
| `BoxDefinition` | Immutable box spec (id, name, key, drop rate, drop entities, grades) | `src/main/java/com/reclizer/csgobox/box/BoxDefinition.java` |
| `GradeGroup` | Per-rarity tier (id, display name, color, weight, items) | `src/main/java/com/reclizer/csgobox/box/GradeGroup.java` |
| `BoxRegistry` | In-memory `LinkedHashMap` registry of loaded boxes | `src/main/java/com/reclizer/csgobox/box/BoxRegistry.java` |
| `BoxJsonLoader` | Filesystem loader/saver; seeds a default `weapon_supply_box.json` if folder is empty | `src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java` |
| `ModEvents` | Mob death -> box drop; player login -> `ModLoadedTrigger` | `src/main/java/com/reclizer/csgobox/event/ModEvents.java` |
| `ClickEvent` | Client-side right-click -> open `CsboxScreen` | `src/main/java/com/reclizer/csgobox/event/ClickEvent.java` |
| `CsboxScreen` | Preview GUI: shows contents, key, open/back buttons | `src/main/java/com/reclizer/csgobox/gui/CsboxScreen.java` |
| `CsboxProgressScreen` | Server-driven scrolling animation between preview and reward | `src/main/java/com/reclizer/csgobox/gui/CsboxProgressScreen.java` |
| `CsLookItemScreen` | Final 3D-rotatable reward display | `src/main/java/com/reclizer/csgobox/gui/CsLookItemScreen.java` |
| `PacketRequestBoxItems` | C->S: request preview data for held box | `src/main/java/com/reclizer/csgobox/packet/PacketRequestBoxItems.java` |
| `PacketSyncBoxItems` | S->C: preview items/grades/weights/key for the held box | `src/main/java/com/reclizer/csgobox/packet/PacketSyncBoxItems.java` |
| `PacketCsgoProgress` | C->S: client requests to actually open the held box | `src/main/java/com/reclizer/csgobox/packet/PacketCsgoProgress.java` |
| `PacketBoxOpenResult` | S->C: 50-item animation strip + winning index + final reward | `src/main/java/com/reclizer/csgobox/packet/PacketBoxOpenResult.java` |
| `PacketValidation` | Defensive helpers: list-size checks, defensive copies, queue trim | `src/main/java/com/reclizer/csgobox/packet/PacketValidation.java` |
| `RandomItem` | Pure deterministic weighted-rarity item picking (no networking) | `src/main/java/com/reclizer/csgobox/utils/RandomItem.java` |
| `IconListTools` | GUI rendering: item frames, rarity backgrounds | `src/main/java/com/reclizer/csgobox/utils/IconListTools.java` |
| `GuiItemMove` | 3D-rotatable item render used by preview/result screens | `src/main/java/com/reclizer/csgobox/utils/GuiItemMove.java` |
| `RenderFontTool` | Scaled `FormattedCharSequence` drawing | `src/main/java/com/reclizer/csgobox/utils/RenderFontTool.java` |
| `ColorTools` | ARGB utilities + grade-to-color mapping | `src/main/java/com/reclizer/csgobox/utils/ColorTools.java` |
| `OverlayColor` | Constant background color (`0xFF333333`) | `src/main/java/com/reclizer/csgobox/utils/OverlayColor.java` |
| `EntityChineseMap` | Hardcoded zh_CN display names for vanilla entity ids | `src/main/java/com/reclizer/csgobox/utils/EntityChineseMap.java` |
| `ModSounds` | `DeferredRegister` for `cs_dita`, `cs_open`, `cs_finish` | `src/main/java/com/reclizer/csgobox/sounds/ModSounds.java` |
| `CsboxCommand` | `/csbox ...` operator command tree (list/info/add/set/give/reload) | `src/main/java/com/reclizer/csgobox/command/CsboxCommand.java` |
| `CsboxConfig` | `ModConfigSpec` with sections `[general]`, `[advanced]`, `[sound]`, `[animation]` | `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java` |
| `CsboxPlayerData` | Record attached to player (seed, mode, item, grade) | `src/main/java/com/reclizer/csgobox/capability/CsboxPlayerData.java` |
| `ModCapability` | Registers `csgobox:player_data` attachment type | `src/main/java/com/reclizer/csgobox/capability/ModCapability.java` |
| `OpenedBoxTrigger` | `SimpleCriterionTrigger` for `csgobox:opened_box`, supports `count` threshold | `src/main/java/com/reclizer/csgobox/advancement/OpenedBoxTrigger.java` |
| `ModLoadedTrigger` | Always-true trigger for `csgobox:mod_loaded`; drives `csgobox:root` tab | `src/main/java/com/reclizer/csgobox/advancement/ModLoadedTrigger.java` |

## Pattern Overview

**Overall:** Layered event-driven client/server mod with a thin shared domain model.

**Key Characteristics:**
- **Server-authoritative random outcomes.** Client never decides the winning item; it renders whatever the server sends.
- **Registry-driven content.** Box definitions live in `BoxRegistry` (in-memory) and on disk in `config/csbox/*.json`; the rest of the code references boxes by `ResourceLocation` only.
- **Data components over NBT.** Box identity is stored as the typed `csgobox:box_id` `DataComponentType<ResourceLocation>` (set in `ItemCsgoBox.BOX_ID`, persisted + network-synchronized). Legacy NBT-style `tag` strings in `config/csbox/*.json` are still accepted for back-compat (`BoxJsonLoader.java:362-372`).
- **Custom statistic as advancement source of truth.** The server awards `Stats.CUSTOM.get(csgobox:opened_boxes)` (`PacketCsgoProgress.java:164`); `OpenedBoxTrigger.TriggerInstance.matches()` reads that stat, letting one trigger class drive both the unconditional "first box" advancement and the `count=200` "shopper" advancement.
- **Defensive network layer.** All four packets run their fields through `PacketValidation` (size caps, list consistency, defensive copies) before exposing data to consumers.

## Layers

**Entry / Event layer:**
- Purpose: Listen to NeoForge events and dispatch them to the right subsystem.
- Location: `src/main/java/com/reclizer/csgobox/event/` (`ClickEvent.java`, `ModEvents.java`)
- Contains: `@SubscribeEvent` static handlers, marked with `@EventBusSubscriber(modid = CsgoBox.MODID)`.
- Depends on: `CsgoBox`, `BoxRegistry`, `BoxDefinition`, `ItemCsgoBox`.
- Used by: NeoForge game event bus only.

**Network / Packet layer:**
- Purpose: Marshal typed payloads between client and server with size-bounded, validated records.
- Location: `src/main/java/com/reclizer/csgobox/packet/`
- Contains: `record` payloads implementing `CustomPacketPayload`, each with its own `Type`, `STREAM_CODEC`, and either `handle` (client side) or `handleServer` (server side).
- Depends on: `ItemCsgoBox`, `BoxRegistry`, `RandomItem`, `OpenedBoxTrigger`, `CsboxPlayerData`.
- Used by: `CsgoBox.registerPayloads()` registers every payload, `CsboxScreen` / `CsboxProgressScreen` send/await them.

**GUI / Rendering layer:**
- Purpose: All `net.minecraft.client.gui.screens.Screen` subclasses that the user sees.
- Location: `src/main/java/com/reclizer/csgobox/gui/`
- Contains: Three screens (`CsboxScreen`, `CsboxProgressScreen`, `CsLookItemScreen`). Imports `RenderSystem`, `GuiGraphics`, `PoseStack` etc.
- Depends on: `CsgoBox.CONFIG`, packet queues (`PacketSyncBoxItems.sPendingResponses`, `PacketBoxOpenResult.sPendingResults`), utility renderers.
- Used by: `ClickEvent` opens `CsboxScreen`; `CsboxScreen` transitions to `CsboxProgressScreen`; `CsboxProgressScreen` transitions to `CsLookItemScreen`.

**Domain / Box layer:**
- Purpose: Box content model and persistence.
- Location: `src/main/java/com/reclizer/csgobox/box/`
- Contains: `BoxDefinition`, `GradeGroup`, `BoxRegistry`, `BoxJsonLoader`.
- Depends on: Gson, `FMLPaths`, `BuiltInRegistries`.
- Used by: `ItemCsgoBox` (resolves box by id), `ModEvents` (loops over `BoxRegistry.getAll()`), `PacketRequestBoxItems.handle` (sends box preview), `PacketCsgoProgress.handleServer` (computes animation + reward), `CsboxCommand` (CRUD on box defs).

**Capability layer:**
- Purpose: Per-player persistent state (current open seed, mode, reward in flight, grade).
- Location: `src/main/java/com/reclizer/csgobox/capability/`
- Contains: `CsboxPlayerData` record + `ModCapability` registering the `AttachmentType`.
- Used by: `PacketCsgoProgress.handleServer` writes the seed + reward so a later reconnect can resume (line 139-142). The `mode` field is reserved/unused by current logic.

**Advancement layer:**
- Purpose: Vanilla-style progression tracked by `Stats.CUSTOM` + `CriteriaTriggers`.
- Location: `src/main/java/com/reclizer/csgobox/advancement/`
- Contains: `OpenedBoxTrigger` (with optional `count` threshold), `ModLoadedTrigger` (always-true), both `extends SimpleCriterionTrigger`.
- Used by: `CsgoBox` registers both under `Registries.TRIGGER_TYPE` and resolves `Stats.CUSTOM.get(STAT_ID)` on common setup.

**Command layer:**
- Purpose: Operator (/op level 2) in-game box CRUD.
- Location: `src/main/java/com/reclizer/csgobox/command/CsboxCommand.java`
- Contains: Single class with a static `@SubscribeEvent register(RegisterCommandsEvent)` that builds the entire Brigadier tree.
- Depends on: `BoxRegistry`, `BoxJsonLoader`, `BoxDefinition`, `GradeGroup`, `ItemCsgoBox`, `ModItems`.

**Item layer:**
- Purpose: The two item types and their data component.
- Location: `src/main/java/com/reclizer/csgobox/item/`
- Contains: `ItemCsgoBox` (the box), `ItemCsgoKey` (the key, all 4 tiers share this class), `ModItems` (deferred registers + creative tab).

**Config layer:**
- Purpose: Runtime user-configurable knobs.
- Location: `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java`
- Contains: Single class with section-grouped `ModConfigSpec.*Value` fields. Constructor is invoked eagerly from `CsgoBox`'s static initializer (line 50-54) so all `.get()` calls work synchronously - this was the v1.0.5 init() fix per `CHANGELOG.md`.

**Utility layer:**
- Purpose: Pure helpers, no I/O.
- Location: `src/main/java/com/reclizer/csgobox/utils/`
- Contains: `RandomItem`, `IconListTools`, `GuiItemMove`, `RenderFontTool`, `ColorTools`, `OverlayColor`, `EntityChineseMap`.
- Used by: GUI and packet layers.

## Data Flow

### Primary Request Path (player opens a box)

1. **Trigger** - Player right-clicks while holding an `ItemCsgoBox`. `event/ClickEvent.onRightClick` (`ClickEvent.java:23-47`) plays `cs_open` and pushes `CsboxScreen` (`Minecraft.getInstance().setScreen(new CsboxScreen())`).
2. **Preview fetch (C->S)** - `CsboxScreen` constructor (`CsboxScreen.java:52-72`) sends `PacketRequestBoxItems` with a client-generated `syncRequestId` and remembers the held box's `expectedBoxId`.
3. **Preview data (S->C)** - `PacketRequestBoxItems.handle` (`PacketRequestBoxItems.java:38-78`) reads the held box, builds `items + grades + weights + key`, and sends `PacketSyncBoxItems` to the requesting player.
4. **Preview render** - `CsboxScreen.containerTick` (`CsboxScreen.java:329-343`) calls `PacketSyncBoxItems.consumeMatching(syncRequestId, expectedBoxId)` and, on match, populates the item grid, key count, and grade list. `renderBg` then draws the frames via `IconListTools`.
5. **Open click** - User clicks the green "OPEN" button. `CsboxScreen.mouseClicked` (`CsboxScreen.java:367-388`) verifies the player has the required key in inventory, generates an `openRequestId`, transitions to `CsboxProgressScreen`, and sends `PacketCsgoProgress(openRequestId)`.
6. **Authoritative server roll** - `PacketCsgoProgress.handleServer` (`PacketCsgoProgress.java:53-170`):
   - Rejects if not holding `ItemCsgoBox`, if player is dead/removed, if cooldown (`isOpenBlocked`) is active, if box is empty, if no weights, or if no key is available.
   - Generates `serverSeed = SecureRandom.nextLong()`.
   - Pre-computes a 50-item animation strip via `RandomItem.precomputeGradeMap` + `randomItemsGrade` + `randomItemsFromGradeMap` with fallback to `findFallbackFromGradeMap`.
   - Picks `winningIndex` in `[35, 44]` via `SECURE_RANDOM.nextInt`.
   - Consumes one key, shrinks the held box by 1.
   - Awards `Stats.CUSTOM.get(csgobox:opened_boxes)` by 1.
   - Fires `OpenedBoxTrigger.INSTANCE.trigger(sp)` (when `enableAchievements` is true).
   - Sends `PacketBoxOpenResult` with the winning item + animation strip back to the player.
7. **Animation consume** - `CsboxProgressScreen.tick` (`CsboxProgressScreen.java:136-221`) calls `PacketBoxOpenResult.consumeMatching(expectedRequestId)`, fills the 50-item strip into `itemInput / gradeInput`, then drives an easing function (`easedScroll`) over `totalTicks` (derived from config).
8. **Final reveal** - When `startTime == totalTicks`, `CsboxProgressScreen.tick:191-196` opens `CsLookItemScreen(resultItem, resultGrade)`.
9. **Result dismiss** - User clicks BACK in `CsLookItemScreen` (`CsLookItemScreen.java:139-149`) which closes the screen and returns to gameplay.

### Secondary Flow: Mob drop

1. Any mob kill fires `LivingDeathEvent`. `ModEvents.livingDeath` (`ModEvents.java:29-50`) iterates every `BoxDefinition` in `BoxRegistry.getAll()`.
2. Per-definition roll: skips unless entity id is in `dropEntities`. Effective rate = `entityDropRate * lootingMultiplier` (looting gives +50% per level, up to 2.5x at looting III) * `CsgoBox.CONFIG.globalDropRatePercent() / 100F` global cap. Clamped to `1.0`.
3. On success, spawns an `ItemCsgoBox` with `boxId` set via `ItemCsgoBox.setBoxId`.

### Secondary Flow: Advancement / stat tracking

1. Every successful open, server `sp.awardStat(CsgoBox.OPENED_BOXES_STAT, 1)`.
2. If `CsgoBox.CONFIG.enableAchievements()` is true, `OpenedBoxTrigger.INSTANCE.trigger(sp)` matches both `csgobox:first_box` (no count) and `csgobox:shopper` (count=200) on the client.
3. `ModLoadedTrigger` (`ModEvents.java:65-71`) fires on `PlayerEvent.PlayerLoggedInEvent` so the `csgobox:root` advancement node is granted, which is the only reliable way to surface the CS2 Box tab in the advancement UI (replaces the legacy `minecraft:tick`-based root that did not work in 1.21+).

### Secondary Flow: Command-driven config edit

1. Op runs `/csbox add <box> <grade> hand <count>`.
2. `CsboxCommand.addHandItem` (`CsboxCommand.java:287-308`) reads the held item, builds a new `GradeGroup`, calls `BoxDefinition.withUpdatedGrade` -> `BoxRegistry.register(updatedBox)` -> `BoxJsonLoader.saveToFile(updatedBox)` for atomic write (`BoxJsonLoader.java:438-447` writes `.json.tmp` then `Files.move(...ATOMIC_MOVE)`).
3. Subsequent reload uses `/csbox reload` -> `BoxRegistry.clear()` + `BoxJsonLoader.loadAll()` (`CsboxCommand.java:427-433`).

**State Management:**
- Box definitions: persistent (JSON on disk) + cached in-memory (`BoxRegistry`).
- Per-player state: `CsboxPlayerData` attachment (persistent via `DataComponentPatch` codec).
- Animation request matching: in-process FIFO queues (`PacketBoxOpenResult.sPendingResults`, `PacketSyncBoxItems.sPendingResponses`) trimmed to 8 entries each via `PacketValidation.trimQueue`.
- Per-player open cooldown: in-memory `HashMap<UUID, Long> OPEN_BLOCKED_UNTIL_TICK` (`PacketCsgoProgress.java:38`). Not persisted; resets on server restart.

## Key Abstractions

**BoxDefinition (record):**
- Purpose: Immutable, serializable specification of one box type.
- Examples: `BoxDefinition.java`, `BoxJsonLoader.loadFromFile()`, `BoxRegistry`, `ItemCsgoBox.getDefinition()`.
- Pattern: Java `record` + Mojang `Codec` + custom `StreamCodec<RegistryFriendlyByteBuf>` for the same record. Includes a `Builder` (`BoxDefinition.java:166-229`) for code-driven construction.

**GradeGroup (record):**
- Purpose: One rarity tier (consumer / industrial / mil_spec / restricted / classified) inside a box.
- Examples: `BoxJsonLoader.writeDefaultIfEmpty` seeds five tiers; `RandomItem` rolls over them.
- Pattern: Java `record` + `Codec` + `STREAM_CODEC` + `StreamCodec.composite`.

**CustomPacketPayload (record):**
- Purpose: Each of the four packets is a Java `record` implementing `CustomPacketPayload`, with its own `Type<>` (namespace `csgobox`) and `STREAM_CODEC`. Validation is centralised in the record's compact constructor calling `PacketValidation`.

**SimpleCriterionTrigger subclass:**
- Purpose: Pattern for `OpenedBoxTrigger` and `ModLoadedTrigger` - extending `SimpleCriterionTrigger<T extends SimpleInstance>` and providing a `RecordCodecBuilder` `CODEC` plus a `trigger(ServerPlayer)` method.

## Entry Points

**Mod entry:**
- Location: `src/main/java/com/reclizer/csgobox/CsgoBox.java`
- Trigger: `@Mod(CsgoBox.MODID)` on the class.
- Responsibilities: Registers config, deferred registers, payload handlers, common setup, custom stat, criterion triggers.

**GUI entry:**
- Location: `src/main/java/com/reclizer/csgobox/event/ClickEvent.java`
- Trigger: `PlayerInteractEvent.RightClickItem` (client side only, via `@EventBusSubscriber(value = Dist.CLIENT, ...)`).
- Responsibilities: Plays open sound and opens `CsboxScreen`.

**Mob-drop entry:**
- Location: `src/main/java/com/reclizer/csgobox/event/ModEvents.java`
- Trigger: `LivingDeathEvent` (both sides).
- Responsibilities: Rolls per-box drop chance and spawns the box item.

**Command entry:**
- Location: `src/main/java/com/reclizer/csgobox/command/CsboxCommand.java`
- Trigger: `RegisterCommandsEvent`.
- Responsibilities: Builds `/csbox ...` Brigadier tree.

## Architectural Constraints

- **Threading:** All NeoForge payload handlers use `context.enqueueWork(...)` to bounce onto the main thread before touching game state (`PacketRequestBoxItems.java:39`, `PacketSyncBoxItems.java:122`, `PacketBoxOpenResult.java:107`, `PacketCsgoProgress.java:54`). The static queues `sPendingResults` / `sPendingResponses` are therefore mutated only on the main thread; consumer screens (`CsboxScreen.tick`, `CsboxProgressScreen.tick`) also tick on the main thread.
- **Global state:** `BoxRegistry.BOX_REGISTRY` (`BoxRegistry.java:16`), `PacketBoxOpenResult.sPendingResults`, `PacketSyncBoxItems.sPendingResponses`, `PacketCsgoProgress.OPEN_BLOCKED_UNTIL_TICK`, `CsgoBox.OPENED_BOXES_STAT`. All single-process, not synchronised.
- **Circular imports:** None detected; dependencies flow one-way (event -> packet -> domain).
- **Box identity stability:** The `ResourceLocation` of a box is its JSON file basename (without `.json`), prefixed with the `csgobox` namespace (`BoxJsonLoader.java:249`). Renaming a JSON file = new box id = all existing held items break.
- **Side parity:** The mod is `BOTH` sided. All client-only classes (`gui/*`, `event/ClickEvent`) reference `Dist.CLIENT` explicitly. `ModEvents.livingDeath` and `ModEvents.playerLoggedIn` are both sides, but only `PlayerLoggedInEvent` is fired on the server.

## Anti-Patterns

### Init() that is never called

**What happens:** `CsboxConfig` previously used a deferred `init()` pattern, but no caller invoked it, leaving every config field at its `.get()` default of `false/0/null` at runtime.
**Why it's wrong:** Animation speed was null -> NPE on first box open; drops were disabled; sounds were silent; debug logging off. Caused a server-crash regression caught in v1.0.5.
**Do this instead:** Inline the `.get()` calls in the constructor (the current pattern in `CsboxConfig.java:19-64`), which is what the AGENTS.md line 21 convention mandates.

### Client-trusted RNG / Client-picked winning index

**What happens:** The animation strip and the final reward must come from the server, never the client.
**Why it's wrong:** Without server authority, the player could just send `PacketBoxOpenResult` claiming a grade-5 reward.
**Do this instead:** Server (`PacketCsgoProgress.handleServer:96-135`) generates `serverSeed`, computes all 50 animation items + the winning index, then sends `PacketBoxOpenResult`. Client (`CsboxProgressScreen.tick:146-167`) only consumes and displays. The `requestId` is for matching only - never for security (`PacketCsgoProgress.java:33-34`).

### Long-blocking inventory scans in render code

**What happens:** `countKeys()` (`CsboxScreen.java:122-132`) and `tryConsumeKeys()` (`PacketCsgoProgress.java:222-234`) scan all inventory slots for the key item.
**Why it's wrong:** This is fine for a small inventory and a per-screen check, but it is O(n) per open attempt and O(n) per tick in the preview screen.
**Do this instead:** Currently acceptable (max 36 slots). If expanded to a larger storage, refactor to an index lookup.

## Error Handling

**Strategy:** Fail loud during dev (throw `IllegalStateException`, `DecoderException`), fail soft during gameplay (log + send empty result back to client).

**Patterns:**
- Network deserialisation: explicit bounds checks throw `io.netty.handler.codec.DecoderException` (`PacketBoxOpenResult.java:87`, `PacketSyncBoxItems.java:92, 103`).
- Server-side rejection paths all funnel through `sendRejected` (`PacketCsgoProgress.java:182-192`) which sends an empty `PacketBoxOpenResult` so the client animation can exit cleanly instead of timing out.
- Box JSON parse errors are caught per-file (`BoxJsonLoader.java:71-74`) so one bad file does not break the rest.
- Path traversal in `deleteFile` is rejected with a warn log rather than throwing (`BoxJsonLoader.java:456`).

## Cross-Cutting Concerns

**Logging:** SLF4J via Mojang `LogUtils.getLogger()` (`CsgoBox.LOGGER`). Standard `info/warn/error/debug` levels. No structured fields.
**Validation:** Centralised in `packet/PacketValidation.java` (size caps, list consistency, defensive copies). Box JSON parsing has its own defensive try/catch in `BoxJsonLoader.parseItem`.
**Authentication:** Minecraft session only; command layer requires op level 2 (`CsboxCommand.java:65`). No custom identity.

---

*Architecture analysis: 2026-06-29*