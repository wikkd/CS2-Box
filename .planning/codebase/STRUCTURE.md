# Codebase Structure

**Analysis Date:** 2026-06-28
**Last Updated:** 2026-06-29 (incremental)

## Directory Layout

```
CSBox/
├── .planning/                # GSD planning artefacts (codebase docs, etc.)
├── .gradle/                  # Gradle cache (generated, not committed)
├── build/                    # Gradle build outputs (generated)
├── runs/                     # Local dev runs (generated)
├── gradle/                   # Gradle wrapper
├── docs/                     # Update/port notes
│   ├── update-1.0.4.md
│   ├── update-1.0.5.md
│   └── port-26.1.2.md
├── src/
│   └── main/
│       ├── java/com/reclizer/csbox/
│       │   ├── box/          # BoxDefinition, BoxRegistry, BoxJsonLoader, GradeGroup
│       │   ├── capability/   # ModCapability, CsboxPlayerData
│       │   ├── command/      # /csbox Brigadier command
│       │   ├── config/       # CsboxConfig (ModConfigSpec) [package: com.reclizer.csgobox.config]
│       │   ├── event/        # ModEvents (LivingDeath), ClickEvent (RightClickItem)
│       │   ├── gui/          # CsboxScreen, CsboxProgressScreen, CsLookItemScreen
│       │   ├── item/         # ItemCsBox, ItemCs2Key, ModItems
│       │   ├── packet/       # 4 payloads + PacketValidation
│       │   ├── sounds/       # ModSounds
│       │   ├── utils/        # RandomItem, render helpers, colours, font
│       │   └── CsBox.java  # @Mod entry point
│       └── resources/
│           ├── META-INF/
│           │   └── neoforge.mods.toml    # mod metadata (template-substituted)
│           ├── assets/
│           │   ├── csbox/
│           │   │   ├── lang/             # en_us.json, zh_cn.json
│           │   │   ├── models/item/      # csbox, csbox_key0..3 (csbox_test also exists)
│           │   │   ├── sounds/           # cs_open.ogg, cs_finish.ogg, cs_dita.ogg
│           │   │   ├── textures/
│           │   │   │   ├── item/         # csbox.png, csbox_key0..3.png
│           │   │   │   └── screens/      # csbox_table.png, csbox_background.png, gold_item.png + atlas/
│           │   │   └── sounds.json
│           │   └── minecraft/shaders/    # fade_in_blur post-processing
│           ├── data/csgobox/recipe/    # 4 vanilla recipes for keys (5 → 4 in v1.0.5; csgo_key3 workbench removed)
│           └── pack.mcmeta
├── AGENTS.md                 # Contributor conventions (config, packets, recipes)
├── CHANGELOG.md              # Version history
├── LICENSE                   # MIT
├── README.md                 # Project overview + EULA disclaimer
├── build.gradle              # NeoGradle config (Java 25 toolchain, placeholders)
├── gradle.properties         # mod_version, minecraft_version, etc.
├── gradlew / gradlew.bat     # Gradle wrappers
└── settings.gradle
```

## Directory Purposes

**`src/main/java/com/reclizer/csbox/`:**
- Purpose: All mod code under a single Java package.
- Contains: 9 sub-packages and one root file (`CsBox.java`).
- Key files: `CsBox.java` (entry point), `box/BoxDefinition.java`, `box/BoxRegistry.java`, `box/BoxJsonLoader.java`, `packet/PacketCs2Progress.java`, `packet/PacketBoxOpenResult.java`, `gui/CsboxScreen.java`, `gui/CsboxProgressScreen.java`.

**`box/`:**
- Purpose: Box/grade data model, JSON loader, and registry.
- Contains: 4 classes (3 records + 1 loader utility).
- Key files: `BoxDefinition.java` (`record`), `GradeGroup.java` (`record`), `BoxRegistry.java` (static singleton), `BoxJsonLoader.java` (Gson + `FMLPaths`).

**`packet/`:**
- Purpose: Server-authoritative network payloads.
- Contains: 5 classes (4 payload records + `PacketValidation` helper).
- Key files: `PacketCs2Progress.java`, `PacketBoxOpenResult.java`, `PacketRequestBoxItems.java`, `PacketSyncBoxItems.java`.

**`gui/`:**
- Purpose: Client-only screens; no business logic.
- Contains: 3 `Screen` subclasses.
- Key files: `CsboxScreen.java` (preview), `CsboxProgressScreen.java` (animation), `CsLookItemScreen.java` (reward display).

**`item/`:**
- Purpose: Item types and registration.
- Contains: 2 item classes + `ModItems` registrar.
- Key files: `ItemCsBox.java` (data component holder), `ItemCs2Key.java` (bare), `ModItems.java` (deferred registers + creative tab).

**`event/`:**
- Purpose: Lifecycle event subscribers.
- Contains: 2 `EventBusSubscriber` classes.
- Key files: `ModEvents.java` (server `LivingDeathEvent`), `ClickEvent.java` (client `PlayerInteractEvent.RightClickItem`).

**`config/`:**
- Purpose: NeoForge `ModConfigSpec` configuration.
- Contains: `CsboxConfig` (no sub-packages).

**`command/`:**
- Purpose: Brigadier command tree registration.
- Contains: `CsboxCommand` (`EventBusSubscriber` for `RegisterCommandsEvent`).

**`capability/`:**
- Purpose: Player-attached server state.
- Contains: `CsboxPlayerData` (`record`) + `ModCapability` (attachment type register).

**`sounds/`:**
- Purpose: `SoundEvent` registration.
- Contains: `ModSounds`.

**`utils/`:**
- Purpose: Stateless helpers used by GUI and packet handlers.
- Contains: `RandomItem`, `RenderFontTool`, `GuiItemMove`, `IconListTools`, `ColorTools`, `OverlayColor`, `EntityChineseMap`.

**`src/main/resources/data/csgobox/recipe/`:**
- Purpose: Vanilla recipe definitions for the 4 keys.
- Contains: `csbox_key0.json`, `csbox_key1.json`, `csbox_key2.json`, `csbox_key3_smithing.json`.

**`src/main/resources/assets/csbox/`:**
- Purpose: Client assets (textures, models, translations, sounds, OGGs).
- Contains: 5 leaf directories (`lang`, `models`, `sounds`, `textures`, plus `sounds.json`).

**`docs/`:**
- Purpose: Release and migration notes.
- Contains: `update-1.0.4.md`, `update-1.0.5.md`, `port-26.1.2.md`.

## Key File Locations

**Entry Points:**
- `src/main/java/com/reclizer/csbox/CsBox.java` — `@Mod` entry; static config init; registers deferred registers and payloads.
- `src/main/java/com/reclizer/csbox/event/ClickEvent.java` — client right-click listener that opens `CsboxScreen`.
- `src/main/java/com/reclizer/csbox/packet/PacketCs2Progress.java` — authoritative open-box server handler.
- `src/main/java/com/reclizer/csbox/event/ModEvents.java` — mob-death drop roller.

**Configuration:**
- `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java` — all `ModConfigSpec` keys.
- `src/main/resources/META-INF/neoforge.mods.toml` — mod metadata (placeholders substituted by `build.gradle`).
- `gradle.properties` — version + Minecraft/NeoForge range placeholders.
- `src/main/resources/pack.mcmeta` — resource-pack metadata (substituted).

**Core Logic:**
- `src/main/java/com/reclizer/csbox/box/BoxDefinition.java` — box record (codec + stream codec).
- `src/main/java/com/reclizer/csbox/box/BoxRegistry.java` — static registry.
- `src/main/java/com/reclizer/csbox/box/BoxJsonLoader.java` — load/save/delete JSON; auto-create default `weapon_supply_box.json`.
- `src/main/java/com/reclizer/csbox/utils/RandomItem.java` — weighted grade roll + item pick + fallback resolution.

**Testing:**
- No automated test sources are present. The `runs/` directory holds dev launch configurations from `./gradlew runClient`/`runServer`.

## Naming Conventions

**Files:**
- Class files match the public class name (`BoxDefinition.java` -> `BoxDefinition`).
- One top-level class per file; nested static classes stay in the same file (e.g. `BoxDefinition.Builder`, `PacketSyncBoxItems.BoxData`).
- Records are also class files; `PacketXxx` prefix for all custom payload types.

**Directories:**
- Lowercase single words (`box`, `gui`, `item`, `utils`).
- Java package structure mirrors directory structure.

**Recipes/assets:**
- Recipe JSONs use snake_case matching the resulting item id: `csbox_key0.json`, `csbox_key3_smithing.json`.
- Sound names are snake_case: `cs_open`, `cs_finish`, `cs_dita`.
- Models/textures follow `assets/csbox/<kind>/<name>.{json,png}` convention.
- Lang files use the standard `en_us.json`/`zh_cn.json` locale codes.
- Translation keys follow `gui.csbox.csbox.*`, `commands.csbox.*`, `tooltips.csbox.*`.

## Where to Add New Code

**New box:**
- Primary code: extend `BoxJsonLoader` if the new field is data-driven, or extend `BoxDefinition`/`GradeGroup` records if it's a config field. If adding a server-side behaviour, also touch `BoxRegistry` and the relevant handler.
- Tests: none in-repo; manual via `/csbox` + `config/csbox/<name>.json`.

**New key tier:**
- Primary code: add a new `Supplier<Item> ITEM_CSBOX_KEYn` in `src/main/java/com/reclizer/csbox/item/ModItems.java` and add it to the creative tab display list.
- Recipe: add `src/main/resources/data/csgobox/recipe/csgo_keyN.json` (crafting_shaped or smithing_transform).
- Texture: `src/main/resources/assets/csbox/textures/item/csbox_keyN.png` and model `assets/csbox/models/item/csbox_keyN.json`.
- Lang: add entry to `src/main/resources/assets/csbox/lang/en_us.json` and `zh_cn.json`.
- Update `BoxJsonLoader.writeDefaultIfEmpty` if the default box should reference the new key.

**New screen:**
- Primary code: extend `src/main/java/com/reclizer/csbox/gui/`. Reuse utils under `utils/` for font/item rendering. If the screen needs server data, add a new packet in `packet/` and register it in `CsBox.registerPayloads`.

**New payload:**
- Primary code: new record under `src/main/java/com/reclizer/csbox/packet/` implementing `CustomPacketPayload`. With >6 fields, write a manual `StreamCodec.of()` and reuse `PacketValidation` helpers.
- Register in `CsBox.registerPayloads` (`src/main/java/com/reclizer/csbox/CsBox.java:60`) via `registrar.playToServer` or `playToClient`.

**New config field:**
- Primary code: extend `CsboxConfig` (`src/main/java/com/reclizer/csgobox/config/CsboxConfig.java`) inside the appropriate `push`/`pop` block. Call `.get()` on every `define*`. Update `AGENTS.md` if it changes the public `CONFIG` contract.

**New command:**
- Primary code: extend `CsboxCommand` (`src/main/java/com/reclizer/csbox/command/CsboxCommand.java`) — add a new `.then(...)` branch in `register` and a private executor method. Add translation keys under `commands.csbox.*` in both `lang/*.json` files.

**New event hook:**
- Primary code: add a `@SubscribeEvent` static method on either `ModEvents` (server/common) or `ClickEvent` (client). Annotate the class with `@EventBusSubscriber(modid = CsBox.MODID)` (already present on both).

**Utilities:**
- Shared helpers: `src/main/java/com/reclizer/csbox/utils/`. Follow the existing `final class + private constructor` pattern for stateless helpers.

## Special Directories

**`build/`:**
- Purpose: Gradle output (classes, processed resources, jars).
- Generated: Yes.
- Committed: No (gitignored).

**`runs/`:**
- Purpose: Dev launch outputs (`./gradlew runClient`, `runServer`).
- Generated: Yes.
- Committed: No.

**`.gradle/`:**
- Purpose: Gradle daemon caches.
- Generated: Yes.
- Committed: No.

**`src/generated/resources/`:**
- Purpose: Optional datagen output (referenced in `build.gradle:46` but datagen task is commented out).
- Generated: Only if datagen is re-enabled.
- Committed: Not present currently.

**`.planning/`:**
- Purpose: GSD planning artefacts (including this document set).
- Generated: Partially (this file set is being written here).
- Committed: Project convention is to commit `.planning/` content.

---

*Structure analysis: 2026-06-28*

## Update 2026-06-29

Incremental refresh — surgical updates only. All other content from the 2026-06-28 baseline is preserved verbatim.

### CsboxConfig package relocation

`CsboxConfig` relocated from the `csbox.config` package to the `csgobox.config` package. Commit: `b7b11e5` ("fix(config): relocate CsboxConfig to csgobox package").

- **Previous path:** `src/main/java/com/reclizer/csbox/config/CsboxConfig.java`
- **New path:** `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java`

The class is the only file under `config/`, so the directory tree still shows a single entry for the package. The "Key File Locations" and "Where to Add New Code" sections have been updated to point at the new package path. The configuration layer's purpose and responsibilities are unchanged.

### Recipe directory rename + csgo_key3 workbench removal

The data-pack recipe directory was renamed from `data/csgobox/recipes/` to `data/csgobox/recipe/` (singular). Commit `be40e5a` ("feat(recipe)!: remove workbench 3x netherite recipe for csgo_key3") also dropped the only remaining non-`csbox_key3_smithing` recipe for the netherite key, so the directory now holds 4 files instead of 5:

- `data/csgobox/recipe/csgo_key0.json` — shaped recipe (3x iron ingot -> `csgobox:csgo_key0`).
- `data/csgobox/recipe/csgo_key1.json` — shaped recipe (3x gold ingot -> `csgobox:csgo_key1`).
- `data/csgobox/recipe/csgo_key2.json` — shaped recipe (3x diamond -> `csgobox:csgo_key2`).
- `data/csgobox/recipe/csgo_key3_smithing.json` — smithing transform (template = `minecraft:netherite_upgrade_smithing_template`, base = `csgobox:csgo_key2`, addition = `minecraft:netherite_ingot` -> `csgobox:csgo_key3`). Sole path to `csgo_key3` post-1.0.5.

The "Where to Add New Code > New key tier" guidance has been updated to reference the new directory and the new key-item id prefix (`csgo_keyN`). No data migration is needed; existing worlds continue to function. See `docs/update-1.0.5.md` and `docs/port-26.1.2.md` for the user-facing release / port notes.
