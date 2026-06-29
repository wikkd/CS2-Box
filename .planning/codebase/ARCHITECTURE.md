<!-- refreshed: 2026-06-28 -->
<!-- updated: 2026-06-29 (incremental) -->
# Architecture

**Analysis Date:** 2026-06-28
**Last Updated:** 2026-06-29 (incremental)

## System Overview

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│                              Client (Player Side)                            │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │  CsboxScreen         (preview / "open" button)                          │  │
│  │  CsboxProgressScreen (rolling animation strip)                          │  │
│  │  CsLookItemScreen    (final reward display)                             │  │
│  │  ClickEvent          (right-click box -> open CsboxScreen)              │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│              │ send                                                 │ receive
│              ▼                                                      ▲
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │  PacketRequestBoxItems (C->S)   PacketSyncBoxItems    (S->C preview)    │  │
│  │  PacketCs2Progress    (C->S)   PacketBoxOpenResult   (S->C result)     │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                              Server (Authoritative)                          │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │  PacketCs2Progress.handleServer  (validate, consume key, roll items)   │  │
│  │  ModEvents.livingDeath            (entity death -> drop roll)           │  │
│  │  CsboxCommand                     (/csbox admin command tree)           │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│              │                                            │                   │
│              ▼                                            ▼                   │
│  ┌─────────────────────────┐                  ┌──────────────────────────┐     │
│  │  RandomItem             │                  │  BoxRegistry (static)    │     │
│  │  CsboxPlayerData        │◄────────────────►│  BoxDefinition (record)  │     │
│  │  ModCapability          │  attachment      │  GradeGroup    (record)  │     │
│  └─────────────────────────┘                  └──────────────────────────┘     │
│                                                              ▲                │
│                                                              │                │
│                                       BoxJsonLoader (config/csbox/*.json)     │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| `CsBox` | Mod entry point. Registers config spec, deferred registers, sounds, capabilities, data components, creative tab, payload handlers, server-start logging. | `src/main/java/com/reclizer/csbox/CsBox.java` |
| `BoxDefinition` | Immutable record describing one box: id, name, key item, drop rate, drop entities, grades (with weights/items), per-entity drop rate overrides. Provides Mojang `Codec` + `StreamCodec`. | `src/main/java/com/reclizer/csbox/box/BoxDefinition.java` |
| `BoxRegistry` | Static singleton `Map<Identifier, BoxDefinition>`. Read-rebuild-replace pattern for runtime mutation. | `src/main/java/com/reclizer/csbox/box/BoxRegistry.java` |
| `BoxJsonLoader` | Loads `config/csbox/*.json` into `BoxRegistry`. Auto-creates `weapon_supply_box.json` when dir is empty. Supports both Minecraft 1.21+ `components` and legacy `tag` strings for items. Saves boxes back to disk via atomic temp-file replace. | `src/main/java/com/reclizer/csbox/box/BoxJsonLoader.java` |
| `GradeGroup` | Immutable record for one grade tier: id (`consumer`/`industrial`/`mil_spec`/`restricted`/`classified`), display name, ARGB color, weight, item list. Defensive `.copy()` on all `ItemStack`s. | `src/main/java/com/reclizer/csbox/box/GradeGroup.java` |
| `CsboxConfig` | `ModConfigSpec` for general/advanced/sound/animation settings. Static-initialized in `CsBox`. | `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java` |
| `ItemCsBox` | Custom `Item`. Holds `box_id` data component. Looks up `BoxDefinition` from registry. Tooltip iterates grades. | `src/main/java/com/reclizer/csbox/item/ItemCsBox.java` |
| `ItemCs2Key` | Bare `Item` subclass (rarity COMMON). Four variants registered: `csbox_key0/1/2/3`. | `src/main/java/com/reclizer/csbox/item/ItemCs2Key.java` |
| `ModItems` | Deferred registers for `csbox` + 4 keys, plus the `EQUIPMENT_TAB` creative tab. | `src/main/java/com/reclizer/csbox/item/ModItems.java` |
| `CsboxScreen` | Client preview screen. Sends `PacketRequestBoxItems` on open; on tick consumes matching `PacketSyncBoxItems` reply. Has open/back buttons, draggable 3D box model, key count, grade-coloured item frames. | `src/main/java/com/reclizer/csbox/gui/CsboxScreen.java` |
| `CsboxProgressScreen` | Client animation screen. Polls `PacketBoxOpenResult.consumeMatching(requestId)`; once received animates the server-supplied item strip toward `winningIndex`. Replaces `tick` per frame to drive `renderWidthAdd`. | `src/main/java/com/reclizer/csbox/gui/CsboxProgressScreen.java` |
| `CsLookItemScreen` | Client reward display. Drag-rotatable 3D reward item with grade-coloured rarity line. | `src/main/java/com/reclizer/csbox/gui/CsLookItemScreen.java` |
| `ModEvents` | Server `LivingDeathEvent` subscriber. Rolls each matching box's drop rate (modulated by Looting + `globalDropRatePercent`). Drops configured box items. | `src/main/java/com/reclizer/csbox/event/ModEvents.java` |
| `ClickEvent` | Client `RightClickItem` subscriber. Plays `CS_OPEN` and opens `CsboxScreen` on `mc.execute()`. | `src/main/java/com/reclizer/csbox/event/ClickEvent.java` |
| `CsboxCommand` | Brigadier `/csbox` tree: `list`, `info`, `add`, `set`, `give`, `reload`. Mutates registry + persists via `BoxJsonLoader.saveToFile`. | `src/main/java/com/reclizer/csbox/command/CsboxCommand.java` |
| `PacketCs2Progress` | C→S open-box request. Server is authoritative: validates player state, anti-spam cooldown (10 ticks), box non-empty, key presence; rolls `ANIMATION_ITEM_COUNT=50` items, picks `MIN_WINNING_INDEX..MAX_WINNING_INDEX=35..44`, replies `PacketBoxOpenResult`, gives item, decrements box. | `src/main/java/com/reclizer/csbox/packet/PacketCs2Progress.java` |
| `PacketBoxOpenResult` | S→C authoritative reward. Includes `serverSeed`, `winningIndex`, full animation strip (`animationItems`, `animationGrades`), and the matched `requestId`. Queues into client-side `ArrayDeque`, consumed by `CsboxProgressScreen` by id. | `src/main/java/com/reclizer/csbox/packet/PacketBoxOpenResult.java` |
| `PacketRequestBoxItems` | C→S preview request. Echoes `requestId`. Server replies `PacketSyncBoxItems` with copy of items/grades/weights/key for the held box. | `src/main/java/com/reclizer/csbox/packet/PacketRequestBoxItems.java` |
| `PacketSyncBoxItems` | S→C preview payload. Holds box id, items, grades, weights, key item. Queued client-side and matched by `requestId + boxId` from `CsboxScreen.containerTick`. | `src/main/java/com/reclizer/csbox/packet/PacketSyncBoxItems.java` |
| `PacketValidation` | Internal helper. Bounds-checks list sizes, clamps grade integers, defensive `.copy()` of `ItemStack` lists, trims client queues to `MAX_PENDING_*` entries. | `src/main/java/com/reclizer/csbox/packet/PacketValidation.java` |
| `CsboxPlayerData` | Record attached to `Player` via NeoForge attachment: `seed`, `mode`, `item`, `grade`. Stored under `ModCapability.PLAYER_DATA`. | `src/main/java/com/reclizer/csbox/capability/CsboxPlayerData.java` |
| `ModCapability` | Registers `PLAYER_DATA` `AttachmentType<CsboxPlayerData>` with `ValueInput`/`ValueOutput` serializer. | `src/main/java/com/reclizer/csbox/capability/ModCapability.java` |
| `RandomItem` | Pure helpers: weighted grade roll, item pick by grade, nearest-non-empty clamp, fallback resolution (same grade -> lower grades -> any). | `src/main/java/com/reclizer/csbox/utils/RandomItem.java` |
| `RenderFontTool` | Scaled text drawing with null-font fallback to `Minecraft.getInstance().font`. | `src/main/java/com/reclizer/csbox/utils/RenderFontTool.java` |
| `GuiItemMove` | Drag-rotation accumulator + 3D item render in inventory. | `src/main/java/com/reclizer/csbox/utils/GuiItemMove.java` |
| `IconListTools` | Item frame rendering (rarity gradient, gold tier, progress strip). | `src/main/java/com/reclizer/csbox/utils/IconListTools.java` |
| `ColorTools` / `OverlayColor` | Grade ARGB colours and background fill. | `src/main/java/com/reclizer/csbox/utils/ColorTools.java`, `OverlayColor.java` |
| `EntityChineseMap` | Entity id -> Chinese display name lookup (used by GUI). | `src/main/java/com/reclizer/csbox/utils/EntityChineseMap.java` |
| `ModSounds` | Deferred registers for `cs_open`, `cs_finish`, `cs_dita` `SoundEvent`s. | `src/main/java/com/reclizer/csbox/sounds/ModSounds.java` |

## Pattern Overview

**Overall:** Server-authoritative mod with immutable data model + read-rebuild-replace mutation + client/server packet symmetry.

**Key Characteristics:**
- **Server is the source of truth.** All rewards (final item, grade, animation strip) come from the server. The client only renders.
- **Immutable data records.** `BoxDefinition`, `GradeGroup`, `CsboxPlayerData`, all packet payloads are Java `record`s. Mutating any one produces a new instance.
- **Read-rebuild-replace.** Runtime mutation never edits an existing record in place — `BoxDefinition.withUpdatedGrade()` rebuilds, then `BoxRegistry.register()` replaces. `/csbox` and JSON reload follow this pattern.
- **Request-id matching.** Every client-initiated action generates a `long requestId` echoed back by the server; the client dequeues only matching packets, preventing stale responses from being consumed by the wrong screen.
- **Defensive `.copy()` at boundaries.** `ItemStack`s entering registry, packets, attachments, and JSON loader output are all deep-copied. `GradeGroup` constructor copies every item.
- **Deferred registers over manual IDs.** All items, sounds, attachments, data components, and creative tab use NeoForge `DeferredRegister`.
- **Mojang codecs + StreamCodec.** `BoxDefinition`/`GradeGroup`/`CsboxPlayerData` ship both `Codec` and `StreamCodec`; payloads with >6 fields use manual `StreamCodec.of()`.

## Layers

**Data definition layer (`box/`):**
- Purpose: Define immutable box/grade data structures, load/persist JSON, and serve as the single read source for everything else.
- Location: `src/main/java/com/reclizer/csbox/box/`
- Contains: `BoxDefinition`, `GradeGroup`, `BoxRegistry`, `BoxJsonLoader`
- Depends on: Minecraft codec API, Gson, `FMLPaths`
- Used by: items, packets, command, events, GUI

**Configuration layer (`config/`):**
- Purpose: TOML-backed config via `ModConfigSpec`. Stored as `config/csbox-common.toml`.
- Location: `src/main/java/com/reclizer/csbox/config/`
- Contains: `CsboxConfig`
- Depends on: NeoForge `ModConfigSpec`
- Used by: `CsBox`, `ClickEvent`, `CsboxProgressScreen`, `CsLookItemScreen`, `ModEvents`

**Item/registry layer (`item/`, `sounds/`):**
- Purpose: Register game-content objects and adapt between ItemStacks and BoxDefinitions.
- Location: `src/main/java/com/reclizer/csbox/item/`, `src/main/java/com/reclizer/csbox/sounds/`
- Contains: `ItemCsBox`, `ItemCs2Key`, `ModItems`, `ModSounds`
- Depends on: `box/`, data components
- Used by: `ClickEvent`, packets, command, GUI

**Network layer (`packet/`):**
- Purpose: Server-authoritative C<->S communication with request-id matching and bounded queues.
- Location: `src/main/java/com/reclizer/csbox/packet/`
- Contains: `PacketCs2Progress`, `PacketBoxOpenResult`, `PacketRequestBoxItems`, `PacketSyncBoxItems`, `PacketValidation`
- Depends on: `item/`, `box/`, `capability/`, `utils/RandomItem`
- Used by: GUI screens, `ModEvents`

**Player state layer (`capability/`):**
- Purpose: Attach server-side open-result data to players.
- Location: `src/main/java/com/reclizer/csbox/capability/`
- Contains: `CsboxPlayerData`, `ModCapability`
- Depends on: NeoForge attachment API
- Used by: `PacketCs2Progress.handleServer` (writes on successful open)

**GUI layer (`gui/`):**
- Purpose: Three-screen client flow (preview -> progress -> reward) using server-supplied data only.
- Location: `src/main/java/com/reclizer/csbox/gui/`
- Contains: `CsboxScreen`, `CsboxProgressScreen`, `CsLookItemScreen`
- Depends on: packets, utils, sounds, config
- Used by: `ClickEvent`

**Server events layer (`event/`):**
- Purpose: React to player input and mob death server-side.
- Location: `src/main/java/com/reclizer/csbox/event/`
- Contains: `ClickEvent` (client), `ModEvents` (server/common)
- Depends on: items, sounds, GUI, registry, config

**Command layer (`command/`):**
- Purpose: Admin/server console management via Brigadier.
- Location: `src/main/java/com/reclizer/csbox/command/`
- Contains: `CsboxCommand`
- Depends on: registry, JSON loader, items

**Utilities (`utils/`):**
- Purpose: Stateless helpers (rendering, math, randomness, colours, font).
- Location: `src/main/java/com/reclizer/csbox/utils/`
- Contains: `RandomItem`, `GuiItemMove`, `IconListTools`, `RenderFontTool`, `ColorTools`, `OverlayColor`, `EntityChineseMap`

## Data Flow

### Primary Request Path — "Open Box" -> "Roll Reward" -> "Give Item"

1. **Player right-clicks with `csbox` in main hand.**
   - `ClickEvent.onRightClick` (`src/main/java/com/reclizer/csbox/event/ClickEvent.java:23`) — client only.
   - Plays `ModSounds.CS_OPEN` at `CONFIG.openSoundVolume`.
   - Schedules `mc.setScreen(new CsboxScreen())` on the client executor.

2. **Preview screen requests box contents.**
   - `CsboxScreen` constructor (`src/main/java/com/reclizer/csbox/gui/CsboxScreen.java:54`) generates `syncRequestId = ThreadLocalRandom.nextLong()`, captures `expectedBoxId = ItemCsBox.getBoxId(itemMenu)`.
   - Sends `PacketRequestBoxItems(syncRequestId)` via `ServerboundCustomPayloadPacket`.

3. **Server responds with snapshot.**
   - `PacketRequestBoxItems.handle` (`src/main/java/com/reclizer/csbox/packet/PacketRequestBoxItems.java:38`) reads the held box, calls `ItemCsBox.getItemGroup` + `getRandom`, replies with `PacketSyncBoxItems(requestId, boxId, items, grades, weights, keyStack)`.

4. **Client receives preview (eventually).**
   - `PacketSyncBoxItems.handle` enqueues into a bounded `ArrayDeque` (max 8).
   - `CsboxScreen.containerTick` calls `PacketSyncBoxItems.consumeMatching(syncRequestId, expectedBoxId)` and populates `itemGroup`, `itemsList`, `gradeList`, `itemKey`, `boxKeyCount`. Buttons become clickable.

5. **Player clicks "open".**
   - `CsboxScreen.mouseClicked` (`src/main/java/com/reclizer/csbox/gui/CsboxScreen.java:368`) checks key presence client-side as a UX guard.
   - Generates a new `openRequestId`, opens `CsboxProgressScreen`, sends `PacketCs2Progress(openRequestId)`.

6. **Server rolls the reward.**
   - `PacketCs2Progress.handleServer` (`src/main/java/com/reclizer/csbox/packet/PacketCs2Progress.java:51`):
     - Rejects if box isn't `ItemCsBox`, player is removed/dead, or 10-tick cooldown is active.
     - Reads `ItemCsBox.getItemGroup` + `getRandom`.
     - Consumes key from inventory if required (`tryConsumeKeys`).
     - Generates `serverSeed = SECURE_RANDOM.nextLong()`, rolls `ANIMATION_ITEM_COUNT=50` items, picks `winningIndex` from `[35,44]`.
     - Stores result in `ModCapability.PLAYER_DATA` attachment (defensive copy).
     - Replies `PacketBoxOpenResult` with seed, index, item strip, grades, and matching `requestId`.
     - Adds item to player inventory (`player.getInventory().add(toGive)`; drops if full).
     - `box.shrink(1)`.
     - `blockFurtherOpens(player)` sets a 10-tick per-player cooldown.

7. **Client animates the strip.**
   - `CsboxProgressScreen.tick` (`src/main/java/com/reclizer/csbox/gui/CsboxProgressScreen.java:128`) calls `PacketBoxOpenResult.consumeMatching(expectedRequestId)`.
     - Empty `result.item()` -> immediate close (server rejected).
     - Otherwise populates `itemInput`, `gradeInput`, `serverWinningIndex`.
   - Animation runs for `readAnimationTicks()` (respects `CONFIG.totalAnimationTicks`, `animationSpeedMultiplier`, `animationSpeed` enum).
   - When complete, opens `CsLookItemScreen(resultItem, resultGrade)`.

8. **Reward display.**
   - `CsLookItemScreen` (`src/main/java/com/reclizer/csbox/gui/CsLookItemScreen.java`) shows the server-authoritative item; user can drag-rotate and click "back" to close.

**State Management:**
- Server: `BoxRegistry` (static map), per-player `OPEN_BLOCKED_UNTIL_TICK` in `PacketCs2Progress`, per-player `CsboxPlayerData` via `ModCapability.PLAYER_DATA`.
- Client: bounded `ArrayDeque<PacketSyncBoxItems>` and `ArrayDeque<PacketBoxOpenResult>` are drained by id from each screen's `tick`/`containerTick`.

### Secondary Flow — Mob Drop

1. `LivingDeathEvent` fires (`ModEvents.livingDeath`).
2. For every registered `BoxDefinition`, check if its `dropEntities` contains the dead entity's id.
3. Compute `effectiveRate = entityDropRate * lootingMultiplier * (CONFIG.globalDropRatePercent / 100)`, clamp to `1.0`.
4. On roll success, `mob.spawnAtLocation(serverLevel, new ItemStack(ITEM_CSBOX) with box_id set)`.

### Secondary Flow — `/csbox` Admin

1. Brigadier dispatches in `CsboxCommand.register` (`src/main/java/com/reclizer/csbox/command/CsboxCommand.java:63`).
2. Subcommands `add`/`set`/`addByName` build a new `BoxDefinition` (via `withUpdatedGrade`) and `BoxRegistry.register(updatedBox)`.
3. `BoxJsonLoader.saveToFile(updatedBox)` writes to `config/csbox/<path>.json` via temp-file + atomic move.
4. `/csbox reload` clears registry, then `BoxJsonLoader.loadAll()` re-parses every file.

## Key Abstractions

**`BoxDefinition`:**
- Purpose: Immutable per-box configuration and reward table.
- Examples: `src/main/java/com/reclizer/csbox/box/BoxDefinition.java`
- Pattern: Java `record` with Mojang `Codec` and `StreamCodec`; constructor enforces invariants (null key -> `minecraft:air`, drop rate clamped to `[0,1]`, lists copied).

**`GradeGroup`:**
- Purpose: One rarity tier within a box (id, display name, colour, weight, item pool).
- Examples: `src/main/java/com/reclizer/csbox/box/GradeGroup.java`
- Pattern: Record; grade ids map to numeric levels via `BoxDefinition.gradeLevel(id)` (`consumer`=1, ..., `classified`=5). Used as the lookup key for weighted rolls.

**`BoxRegistry`:**
- Purpose: Process-wide lookup for box definitions.
- Examples: `src/main/java/com/reclizer/csbox/box/BoxRegistry.java`
- Pattern: Static `LinkedHashMap` wrapped in `unmodifiableCollection` for reads. Mutations (`register`/`clear`/`remove`) happen in `BoxJsonLoader.loadAll`, `CsboxCommand`, and reload.

**Request-id matching (cross-cutting):**
- Purpose: Ensure that an in-flight reply is consumed by the screen that asked for it, not a stale one.
- Examples: `PacketSyncBoxItems.consumeMatching`, `PacketBoxOpenResult.consumeMatching`, `PacketValidation.trimQueue`.
- Pattern: Client sends `long requestId`; server echoes it back unchanged; client dequeues only entries with matching id (+ `boxId` for preview).

## Entry Points

**Mod entry:**
- Location: `src/main/java/com/reclizer/csbox/CsBox.java`
- Triggers: `@Mod(CsBox.MODID)` discovered by NeoForge.
- Responsibilities: Build `CsboxConfig` + `ModConfigSpec` in `static {}`; in constructor register config (`csbox-common.toml`), add `commonSetup`/`registerPayloads`/`ClientModEvents` listeners, register all deferred registers, register on `NeoForge.EVENT_BUS`. `FMLCommonSetupEvent` triggers `BoxJsonLoader.loadAll()` if `CONFIG.loadDefaultBoxes`. `ServerStartingEvent` logs registered box count.

**Client entry:**
- Location: `ClickEvent.onRightClick` (`src/main/java/com/reclizer/csbox/event/ClickEvent.java:23`)
- Triggers: Right-click in main hand with `csbox`.
- Responsibilities: Play open sound and open `CsboxScreen`.

**Server entry:**
- Location: `PacketCs2Progress.handleServer` (`src/main/java/com/reclizer/csbox/packet/PacketCs2Progress.java:51`)
- Triggers: `PacketCs2Progress` payload from a client.
- Responsibilities: Authoritative open-box logic — see Primary Request Path step 6.

**Mob-drop entry:**
- Location: `ModEvents.livingDeath` (`src/main/java/com/reclizer/csbox/event/ModEvents.java:31`)
- Triggers: `LivingDeathEvent` on the common bus.
- Responsibilities: Roll configured boxes for matching entities with Looting + global multiplier.

## Architectural Constraints

- **Threading:** Minecraft's main client/server threads only. `IPayloadContext.enqueueWork` schedules packet handlers onto the correct thread. `PacketBoxOpenResult.consumeMatching` / `PacketSyncBoxItems.consumeMatching` MUST be called from the main client thread (see comments in `PacketBoxOpenResult.java:106`).
- **Global state:**
  - `BoxRegistry.BOX_REGISTRY` (`src/main/java/com/reclizer/csbox/box/BoxRegistry.java:16`) — mutable static `LinkedHashMap`.
  - `PacketCs2Progress.OPEN_BLOCKED_UNTIL_TICK` (`src/main/java/com/reclizer/csbox/packet/PacketCs2Progress.java:36`) — `HashMap<UUID, Long>` for per-player open cooldown.
  - `PacketBoxOpenResult.sPendingResults`, `PacketSyncBoxItems.sPendingResponses` — `ArrayDeque` bounded to 8.
  - `BoxJsonLoader.BOXES_DIR` — constant path; thread-unsafe file IO runs on whatever thread `loadAll`/`saveToFile` is called from (init: main thread; command: server thread).
- **Circular imports:** None observed. Layering flows one-way: `box/` -> `item/`/`packet/`/`event/`/`command/`/`gui/`.
- **Immutability of data records:** Every record enforces invariants in its compact constructor (`Objects.requireNonNull`, `.copy()`, `List.copyOf`, `Math.clamp`). Any change yields a new instance.

## Anti-Patterns

### Server-trust gap on the preview path

**What happens:** `CsboxScreen.mouseClicked` (`src/main/java/com/reclizer/csbox/gui/CsboxScreen.java:378`) re-checks the held item is `ItemCsBox` and that a matching key is in the inventory before sending `PacketCs2Progress`. The server still re-validates, but client-side checks duplicate logic.
**Why it's wrong:** Two sources of truth for "can I open?". If the server's check ever changes (e.g. extra cooldown), the client button may stay enabled when the server will reject.
**Do this instead:** Treat the client guard as UX-only. The single authoritative check is `PacketCs2Progress.handleServer` — every reason to reject (`isOpenBlocked`, empty item list, missing key, etc.) sends a matching empty `PacketBoxOpenResult`, which the client surfaces as a silent close. `CsboxScreen` button should always send the open packet when clicked.

### Direct mutation of `BoxRegistry` from network handlers

**What happens:** Nothing currently mutates the registry on packet receipt (only `BoxJsonLoader.loadAll` and `CsboxCommand` do), but the registry has no locking.
**Why it's wrong (latent):** Any future code path that mutates `BoxRegistry` while a packet handler is iterating `getAll()` would race.
**Do this instead:** Keep registry mutations confined to `BoxJsonLoader.loadAll`, `BoxJsonLoader.saveToFile`, and `CsboxCommand` (single-threaded server-side). If async mutation is ever needed, wrap the map in a `ConcurrentHashMap` and audit every iteration.

### Wide visibility on `CsboxScreen.itemMenu`

**What happens:** `CsboxScreen.itemMenu` (`src/main/java/com/reclizer/csbox/gui/CsboxScreen.java:78`) is package-private and assigned in the constructor without `final`.
**Why it's wrong:** It's effectively immutable after construction but doesn't communicate that.
**Do this instead:** Declare `private final ItemStack itemMenu;` and assign in the constructor only. The constructor already takes the snapshot from `mc.player.getItemInHand(MAIN_HAND)`.

### Hardcoded literal config fallback

**What happens:** `CsboxProgressScreen.readAnimationTicks()` (`src/main/java/com/reclizer/csbox/gui/CsboxProgressScreen.java:237`) returns `145` if `CsBox.CONFIG == null`.
**Why it's wrong:** `CONFIG` is `public static final` and never null per the AGENTS.md contract. The guard is dead code that masks refactor mistakes.
**Do this instead:** Read `CsBox.CONFIG.totalAnimationTicks` directly; remove the null check (AGENTS.md is explicit on this).

## Error Handling

**Strategy:** Fail closed on the server, fail loud in JSON loading, validate at packet boundaries.

**Patterns:**
- Server-side open (`PacketCs2Progress.handleServer`): every rejection (not a box, player invalid, cooldown, empty list, missing key, empty reward after fallback) sends a `PacketBoxOpenResult` with `ItemStack.EMPTY` so the client always gets a matching packet and never hangs. The animation screen then closes immediately.
- JSON loader (`BoxJsonLoader.loadFromFile`): any per-file exception is caught and logged at `ERROR`; the loader continues with other files. Unknown item ids and unparseable `components`/`tag` are skipped with `WARN`.
- Packet codecs (`BoxDefinition`, `PacketSyncBoxItems`, `PacketBoxOpenResult`): explicit list-size caps (`MAX_ITEMS`, `MAX_GRADES`, `ANIMATION_ITEM_COUNT`) throw `DecoderException` from `StreamCodec` reads, preventing oversized wire payloads.
- `PacketValidation`: every record constructor validates sizes, clamps grade integers to `[1,5]`, copies item stacks, and trims the client pending queues to `MAX_PENDING_*`.
- Defensive RNG fallbacks (`RandomItem.clampToValidItem`, `findFallback`, `findFallbackFromGradeMap`) walk to lower grades and then any non-empty entry before giving up.

## Cross-Cutting Concerns

**Logging:** `CsBox.LOGGER = LogUtils.getLogger()` (SLF4J). `INFO` for lifecycle + box load, `WARN` for malformed JSON / unknown item ids, `ERROR` for IO failures, `DEBUG` for `BoxRegistry.register`.

**Validation:** Triple-layered: TOML config (`CsboxConfig`), JSON config (`BoxJsonLoader` weight clamping, name fallback, unknown id warning), packet wire (`PacketValidation`, manual `StreamCodec` bounds).

**Authentication:** None — Minecraft's session auth is the only gate. The `csbox` Brigadier command requires `PermissionLevel.GAMEMASTERS`. The forge permission set is `Permission.HasCommandLevel`.

---

*Architecture analysis: 2026-06-28*

## Update 2026-06-29

Incremental refresh — surgical updates only. All other content from the 2026-06-28 baseline is preserved verbatim.

### CsboxConfig package relocation

`CsboxConfig` relocated from the `csbox.config` package to the `csgobox.config` package. Commit: `b7b11e5` ("fix(config): relocate CsboxConfig to csgobox package").

- **Previous path:** `src/main/java/com/reclizer/csbox/config/CsboxConfig.java`
- **New path:** `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java`

`CsBox` static initialiser imports the class from the new package; no other code changes. The configuration layer (`config/`) remains a top-level package under `csgobox/` and continues to house the single `CsboxConfig` class (the file moved; the layer did not).

### Recipe directory rename

Data-pack recipe directory renamed from `data/csgobox/recipes/` to `data/csgobox/recipe/` (singular). All four recipe JSONs moved into the new directory:

- `csgo_key0.json`
- `csgo_key1.json`
- `csgo_key2.json`
- `csgo_key3_smithing.json`

No code references the directory by literal path — Minecraft's data-pack discovery finds it by convention — so no source-file changes were required. Affects: `src/main/resources/data/csgobox/recipe/` (new location).

### csgo_key3 workbench recipe removed

Commit `be40e5a` ("feat(recipe)!: remove workbench 3x netherite recipe for csgo_key3") removed the original workbench recipe that crafted `csgobox:csgo_key3` from 3x netherite ingots. The netherite key is now exclusively obtainable via the smithing transform that upgrades `csgobox:csgo_key2`. This drops the recipe count from 5 to 4 files.

- `data/csgobox/recipe/csgo_key3_smithing.json` — sole remaining `csgo_key3` recipe (template = `minecraft:netherite_upgrade_smithing_template`, base = `csgobox:csgo_key2`, addition = `minecraft:netherite_ingot`).
- `data/csgobox/recipe/csgo_key0.json`, `csgo_key1.json`, `csgo_key2.json` — unchanged shaped recipes (iron / gold / diamond ingots).

No data migration is required; existing worlds continue to function. See `docs/update-1.0.5.md` for the user-facing release notes and `docs/port-26.1.2.md` for the MC 26.1.2 port.
