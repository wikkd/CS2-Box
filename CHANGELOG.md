# Changelog

## [1.0.2] - 2026-06-01

### Added
- **NeoForge 1.21.1 Port** — Full migration from Forge 1.20.1 (ChloePrime/CS2-Box) to NeoForge 21.1.115+
- **`/csbox` Command System** — In-game box management commands:
  - `/csbox list` — List all registered boxes with grade summary
  - `/csbox info <box>` — Show detailed configuration of a specific box
  - `/csbox add <box> <grade> hand <count>` — Add held item to a box's grade pool
  - `/csbox give <box> [count] [player]` — Give box items to players
  - `/csbox reload` — Reload box definitions from KubeJS scripts
  - Full TAB-completion support for box IDs and grade IDs
- **Box JSON Loader (`BoxJsonLoader`)** — Load box configurations from `config/csbox/*.json` files at runtime, supporting both `components` (DataComponent) and legacy `tag` (NBT) item formats
- **Entity Drop Rate System** — Per-entity drop rate override via `entity_drop_rates` in JSON configs; looting enchantment bonus (×0.5 per level, capped at 100%)
- **KubeJS Integration** — Script-based box creation API:
  - `BoxBuilderJS` / `GradeBuilderJS` — Fluent builder for boxes and grades
  - `CsboxRegistryEventJS` — KubeJS event for registering custom boxes
  - `DefaultBoxes.js` — Built-in default box definitions
  - `KubeJsPlugin` — Plugin entry point compatible with KubeJS 2101.x
- **Box Registry API** — `BoxDefinition`, `GradeGroup`, `BoxRegistry` — Immutable data model with read-rebuild-replace pattern for safe runtime modification
- **`PacketBoxOpenResult`** — Dedicated server→client packet to guarantee data delivery after opening a box, fixing race conditions in UI rendering
- **Chinese (zh_cn) Translation** — Full localization including command messages and UI strings

### Fixed
- **Grade Level Mapping Reversal** — Grade levels in JSON configs (grade5 = rarest, grade1 = common) were displayed incorrectly: AWP/netherite gear showed as "grade 1" (blue) while junk items showed as "grade 5" (gold). Fixed in both `ItemCsgoBox.getItemGroup()` and `RandomItem.randomItemsGrade()`
- **Client-Server Data Sync** — Resolved race condition where server data could arrive before screen creation, causing UI rendering failure. Dedicated packet ensures 100% delivery before display
- **ItemStack Pollution** — ItemStack instances from config are now `.copy()`-ed before storage/modification to prevent corrupting original configuration data
- **JSON Entity List Parsing** — Crashed on pure entity ID arrays (e.g., `["minecraft:zombie"]`). Now supports both alternating `[id, rate]` format and pure `[id]` format (falls back to global drop rate)

### Changed
- **Removed deprecated classes** — `CsgoBoxCraftMenu`, `CsgoBoxCraftScreen`, `RecModMenus`, `RecModScreens`, `ItemOpenBox`, `PacketUpdateMode`, `ItemNBT`
- **Removed craft recipe/model** — `csgo_box_craft` recipe, model, and texture removed
- **Updated dependencies** — NeoForge 21.1.115, Cloth Config 15.0.130, KubeJS 2101.7.2-build.368, Rhino 2101.2.7-build.82
- **Gradle toolchain** — JDK 21 required (via `org.gradle.java.home`)
- **StreamCodec** — Uses manual `StreamCodec.of()` encode/decode for classes with >6 fields (NeoForge 1.21.1 requirement)

### Requirements
- Minecraft 1.21.1
- NeoForge 21.1.115+
- Java 21
- Optional: Cloth Config [15,) / KubeJS [2101,)
