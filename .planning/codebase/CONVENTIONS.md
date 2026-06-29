# Coding Conventions

**Analysis Date:** 2026-06-28
**Last incremental update:** 2026-06-29

## Naming Patterns

**Packages:**
- All sources live under `src/main/java/com/reclizer/csgobox/`
- Subpackages are organized by responsibility: `box/`, `capability/`, `command/`, `config/`, `event/`, `gui/`, `item/`, `packet/`, `sounds/`, `utils/`
- Lowercase, single-word, no underscores (e.g., `packet` not `packets`)

**Files:**
- One top-level public type per file, filename matches the type name
- Package-private helpers sit in files named after the most relevant type (e.g., `PacketValidation.java` in `packet/`)
- Underscore is not used in any Java filename

**Classes / Records / Enums:**
- PascalCase; acronym prefixes kept lowercase when part of a longer identifier (`ItemCsgoBox`, `CsboxConfig`, `CsboxScreen`, `BoxJsonLoader`)
- Item classes prefixed with `Item` (e.g., `ItemCsgoBox`, `ItemCsgoKey`)
- Screen classes suffixed with `Screen` (e.g., `CsboxScreen`, `CsboxProgressScreen`, `CsLookItemScreen`)
- Packet classes prefixed with `Packet` (e.g., `PacketBoxOpenResult`, `PacketSyncBoxItems`, `PacketRequestBoxItems`, `PacketCsgoProgress`)
- Mod container and registration classes prefixed with `Mod` (e.g., `ModItems`, `ModCapability`, `ModSounds`, `ModEvents`)

**Static Utility Classes:**
- Always declared `public final class X { private X() {} ... }`
- All members are `public static`
- Examples: `src/main/java/com/reclizer/csgobox/utils/RandomItem.java`, `src/main/java/com/reclizer/csgobox/utils/ColorTools.java`, `src/main/java/com/reclizer/csgobox/utils/OverlayColor.java`, `src/main/java/com/reclizer/csgobox/utils/IconListTools.java`, `src/main/java/com/reclizer/csgobox/utils/RenderFontTool.java`, `src/main/java/com/reclizer/csgobox/utils/GuiItemMove.java`, `src/main/java/com/reclizer/csgobox/utils/EntityChineseMap.java`

**Records (immutable data):**
- `public record Name(...)` with compact constructor for normalization (`BoxDefinition`, `GradeGroup`, `CsboxPlayerData`, all four `Packet*` types)
- Mutating operations return new record instances via `with*` methods (see `BoxDefinition.withUpdatedGrade()`)

**Functions:**
- camelCase, no underscores
- Verbs preferred (`loadAll`, `saveToFile`, `setBoxId`, `getDefinition`, `consumeMatching`, `renderBg`, `addTutorial`)
- Booleans typically read as predicates (`isOpenBlocked`, `isPauseScreen`, `isCandidate`, `contains`)

**Variables / Fields:**
- `static final` constants in UPPER_SNAKE_CASE (e.g., `MODID`, `CONFIG`, `CONFIG_SPEC`, `BOX_REGISTRY`, `MAX_ITEMS`, `GRADE_COUNT`, `OPEN_BLOCKED_UNTIL_TICK`)
- Instance fields in lowerCamelCase (`itemGroup`, `syncRequestId`, `expectedBoxId`, `winningIndex`)
- Local-scope fields sometimes declared mid-class, separated by blank lines (see `src/main/java/com/reclizer/csgobox/gui/CsboxScreen.java:34-78`) — the codebase is inconsistent about field ordering

**Types:**
- `Identifier` (not `ResourceLocation` — used throughout after the NeoForge rename)
- `ItemStack`, `Item`, `Component`, `ServerPlayer`, `Player`, `Level` for Minecraft types
- `Optional<T>` used for nullable lookup results (`BoxDefinition.findGrade`, `BoxJsonLoader.loadFromFile`)

## Code Style

**Formatting:**
- 4-space indentation, no tabs
- K&R brace style (opening brace on the same line)
- One statement per line
- Blank line between logical blocks; up to two consecutive blank lines are tolerated in GUI files (e.g., `src/main/java/com/reclizer/csgobox/gui/CsboxScreen.java:40-41`)
- Long lines wrapped with trailing operators aligned to the next indent (see `BoxJsonLoader.java:228-241`)
- No automated formatter is configured — the project does not apply Spotless, Checkstyle, Errorprone, or google-java-format. Style discipline is enforced by convention only, documented here for new code to match.

**Linting:**
- No `.editorconfig`, `.checkstyle`, `checkstyle.xml`, `spotless.gradle`, or `errorprone` configuration in the repository (`/Users/shuangyuexingxun/Desktop/CS2-Box/.editorconfig` does not exist)
- Java compiler (`JavaCompile`) is configured only with `options.encoding = 'UTF-8'` (`build.gradle:96-98`)
- Toolchain: Java 25 language level (`build.gradle:17-20`) but actually built on JDK 21 (`gradle.properties:3`)
- Preview features enabled at recompile level (`gradle.properties:6-7`)

**Modern Java Features Used:**
- Records for immutable data (`BoxDefinition`, `GradeGroup`, `CsboxPlayerData`, all `Packet*` records)
- Pattern-matching `switch` expressions (`BoxDefinition.gradeLevel`, `ColorTools.colorItems`)
- `instanceof` pattern matching (`ModEvents.lootingMultiplier`, `PacketCsgoProgress.handleServer`)
- `Math.clamp` (Java 21+)
- `var` for local type inference (used heavily in `CsgoBox.java`, `BoxJsonLoader.java`, `BoxDefinition.java`, `CsboxCommand.java`)
- Compact record constructors for normalization
- Sealed-like safety via `private` constructors on utility classes
- No stream-only style: code mixes streams and imperative loops. When deterministic ordering or early-exit matters, prefer `for` loops (see `BoxDefinition.getWeightArray`, `RandomItem.findFallback`)

## Import Organization

**Order (consistent across files):**
1. `java.*` and `javax.*` (e.g., `java.util.*`, `java.nio.file.*`)
2. Third-party (e.g., `com.google.gson.*`, `com.mojang.*`, `io.netty.*`)
3. Project imports (`com.reclizer.csgobox.*`)
4. Minecraft / NeoForge (`net.minecraft.*`, `net.neoforged.*`)

Each group is separated by a blank line. Wildcards (`java.util.*`) appear in `src/main/java/com/reclizer/csgobox/gui/CsboxScreen.java:30` and `src/main/java/com/reclizer/csgobox/packet/PacketCsgoProgress.java:23` — uncommon elsewhere.

**Path Aliases:**
- None. Fully qualified package paths are used for every import.
- The `modid` constant `CsgoBox.MODID` is the standard way to reference the namespace (`"csgobox"`) — never hardcode the literal `"csgobox"` outside the constant.

## Configuration

**Framework:** NeoForge native `ModConfigSpec` (`net.neoforged.neoforge.common.ModConfigSpec`). Cloth Config (`me.shedaniel.cloth:cloth-config-neoforge`) was removed in 1.0.5 (commit `862ab1f`) and is **not** a dependency. New config keys must use the `ModConfigSpec.Builder` fluent API — do not reintroduce Cloth Config types.

**Spec construction** (`src/main/java/com/reclizer/csgobox/CsgoBox.java:39-44`):
```java
static {
    var pair = new ModConfigSpec.Builder()
            .configure(CsboxConfig::new);
    CONFIG = pair.getLeft();
    CONFIG_SPEC = pair.getRight();
}
```

**Registration** (`src/main/java/com/reclizer/csgobox/CsgoBox.java:47`):
```java
ModLoadingContext.get().getActiveContainer()
        .registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC, "csgobox-common.toml");
```

**Style rules for config classes** (model: `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java`):
- Class is `public`, package-private constructor takes `ModConfigSpec.Builder`
- Public mutable fields mirror the resolved values (so callers read `CONFIG.fieldName` directly, no nested `CONFIG.section.fieldName`)
- Private `*Value` fields hold the raw `ModConfigSpec.BooleanValue` / `IntValue` / `EnumValue<T>` handles
- `push("groupName")` / `pop()` bracket each TOML section (`general`, `advanced`, `sound`, `animation`)
- `comment("...")` precedes every `define` / `defineInRange` / `defineEnum` call
- `init()` copies `*Value.get()` results into the public fields; call `init()` after `registerConfig` returns
- The generated config file is `config/csgobox-common.toml` (replaces the old `config/csgobox.toml`)

## Error Handling

**Strategy:** Defensive normalization at trust boundaries, fail-fast on malformed network packets, log-and-continue on user-config JSON.

**Patterns:**

**1. Record normalization in compact constructor** (`PacketBoxOpenResult`, `PacketSyncBoxItems`, `BoxDefinition`, `GradeGroup`, `CsboxPlayerData`):
- Null `ItemStack` becomes `ItemStack.EMPTY`
- Null collections become `List.of()` / `Map.of()`
- Defensive copies via `List.copyOf` / `Map.copyOf`
- Numeric clamps via `Mth.clamp` and `Math.clamp`

**2. Trust boundary validation** (`PacketBoxOpenResult.read`, `PacketSyncBoxItems.read`, `BoxDefinition.read`, `PacketValidation`):
- `throw new DecoderException(...)` when reading invalid sizes from network buffers
- `throw new IllegalArgumentException(...)` when invariants are violated by caller-supplied data
- `requireSameSize`, `requireMaxSize`, `copyStacks`, `copyClampedInts`, `copyNonNegativeInts`, `trimQueue` live in `src/main/java/com/reclizer/csgobox/packet/PacketValidation.java`

**3. Config / JSON tolerance** (`BoxJsonLoader`):
- `try`/`catch (Exception e)` around each item, file, and component parsing step
- Failure logged at `WARN` with the offending input, parsing continues
- Default values are substituted for missing or invalid fields (weights, item IDs)
- Path-traversal check in `BoxJsonLoader.deleteFile`: rejects paths that escape `BOXES_DIR.normalize()`

**4. Command exceptions** (`CsboxCommand`):
- `DynamicCommandExceptionType` for translatable user-facing errors (`BOX_NOT_FOUND`, `GRADE_NOT_FOUND`, `ITEM_NOT_FOUND`)
- Throws via `EXCEPTION.create(arg)` then caught at Brigadier's edge

**5. Logging on error:**
- `CsgoBox.LOGGER.error(...)` for unrecoverable I/O failures
- `CsgoBox.LOGGER.warn(...)` for recoverable config / parse failures
- `CsgoBox.LOGGER.info(...)` for normal lifecycle events (load, save, register, server start)
- `CsgoBox.LOGGER.debug(...)` for high-frequency diagnostics (e.g., `BoxRegistry.register`)

## Logging

**Framework:** SLF4J via Mojang's `LogUtils` (`com.mojang.logging.LogUtils`).

**Setup:** Single shared logger in `src/main/java/com/reclizer/csgobox/CsgoBox.java:35`:
```java
public static final Logger LOGGER = LogUtils.getLogger();
```

**Usage rule:** Every class that logs imports and uses `CsgoBox.LOGGER`. There is no per-class logger. This is the project's established convention; do not introduce per-class `Logger` fields.

**Patterns:**
- Use parameterized messages: `LOGGER.info("Loaded {} box(es) from {}", loaded[0], BOXES_DIR);` — do not concatenate
- Exception log calls pass the throwable last: `LOGGER.error("Failed to save box JSON: {}", file, e);`
- `info` = lifecycle milestones (init, load, save, server start)
- `warn` = recoverable problems with config / data parsing
- `error` = unrecoverable I/O or initialization failures
- `debug` = high-frequency diagnostic (registry add, tracing)

## Comments

**When to Comment:**
- Javadoc on every public record and every public packet class — see `BoxDefinition.java:20-22`, `PacketBoxOpenResult.java:19-25`, `PacketCsgoProgress.java:27-32`
- Javadoc on public methods only when behavior is non-obvious (e.g., `RandomItem.clampToValidItem`, `RandomItem.findFallback`)
- Inline `//` comments used sparingly to flag security-relevant behavior (`BoxDefinition.java:67` for `List.copyOf` defensive copy) or thread-safety invariants (`PacketBoxOpenResult.java:106-107`)

**Style:**
- Sentence case, terminal period
- Multi-paragraph Javadoc uses `<p>` tags
- Section dividers in Chinese (`// ----`) are not used; the codebase favors tight, purposeful comments

## Function Design

**Size:** Most methods are 10-30 lines. GUI rendering helpers (`CsboxScreen.renderBg`, `CsboxScreen.renderLabels`) and the command tree in `CsboxCommand.register` are intentionally larger. Single-purpose methods (e.g., `PacketValidation.copyStacks`) are preferred where possible.

**Parameters:** Avoid more than 5 parameters. The codebase uses small record carriers (`BoxData`, `CsboxPlayerData`) when many values must travel together.

**Return Values:**
- `Optional<T>` for nullable lookup (`BoxDefinition.findGrade`, `BoxJsonLoader.loadFromFile`)
- Sentinel `ItemStack.EMPTY` for "no item" (matches Minecraft idiom) — used by `RandomItem.randomItems`, `RandomItem.findFallback`
- Empty `List.of()` / `Map.of()` for absent collections rather than `null`
- `null` only used for `Identifier` lookups that have a meaningful "absent" state (`ItemCsgoBox.getKey`)

**Static vs Instance:**
- Pure functions → `public static` on a `final` utility class
- Stateful services → `public final class` with `private static final` state (see `BoxRegistry.BOX_REGISTRY`, `EntityChineseMap.ZH_MAP`)
- Records → always immutable
- Singletons with side effects on init → `private static final` field + private constructor (e.g., `EntityChineseMap` uses `static {}` block to populate the map)

## Module Design

**Exports:** All public types are top-level and exported. There are no `module-info.java` files (NeoForge mods run on the unnamed module).

**Package-Private Helpers:** Used for cross-class internals that don't belong in the public API:
- `PacketValidation` (package-private, in `packet/`) — used by all four packet records
- `PacketSyncBoxItems.BoxData` — nested record, public, used by callers to receive unboxed packet data

**DeferredRegister Pattern:** Every registry entry uses `DeferredRegister` with a `Supplier` field and a static `register(IEventBus)` method:
- `ModItems` (`src/main/java/com/reclizer/csgobox/item/ModItems.java`)
- `ModSounds` (`src/main/java/com/reclizer/csgobox/sounds/ModSounds.java`)
- `ModCapability` (`src/main/java/com/reclizer/csgobox/capability/ModCapability.java`)
- `ItemCsgoBox.BOX_DATA_COMPONENTS` (`src/main/java/com/reclizer/csgobox/item/ItemCsgoBox.java:36-48`)

**Barrel Files:** Not used. There are no `package-info.java` files. Consumers import individual classes directly.

**Mod Initialization:** Centralized in `src/main/java/com/reclizer/csgobox/CsgoBox.java:46-59` — the constructor registers config, common setup, payload handlers, sounds, capability, data components, items, and creative tab in that order. Preserve this ordering when adding new registries.

## Data Pack Paths

**Recipe directory:** `src/main/resources/data/csgobox/recipe/` (singular — was renamed from `recipes/` in commit `be40e5a` / the 1.0.5 release). All four `csgo_key*` recipes live under this singular folder. When adding new recipes, create the JSON under `data/csgobox/recipe/<name>.json`, not under a `recipes/` subfolder.

**Resource namespace:** All data-pack and asset paths use the lowercase modid `csgobox` (matches `CsgoBox.MODID`). Do not use the older `csbox` spelling in any new path.

## Threading Model

- Game-thread modifications only. UI work is dispatched via `Minecraft.getInstance().execute(...)` (see `src/main/java/com/reclizer/csgobox/event/ClickEvent.java:42-45`)
- Network handler work dispatched via `context.enqueueWork(...)` (all `Packet*::handle*` methods)
- Static mutable state (`BoxRegistry.BOX_REGISTRY`, `PacketBoxOpenResult.sPendingResults`, `PacketSyncBoxItems.sPendingResponses`, `PacketCsgoProgress.OPEN_BLOCKED_UNTIL_TICK`, `EntityChineseMap.ZH_MAP`) is touched only on the main thread; comment at `PacketBoxOpenResult.java:106-107` documents this contract explicitly

---

## Update 2026-06-29

Incremental update applied to capture changes since the 2026-06-28 baseline. All unchanged sections above are preserved verbatim from the prior version.

**Package rename.** Java source root was renamed from `com.reclizer.csbox` to `com.reclizer.csgobox`. Every code-path example, static-utility list, deferred-register list, import-order example, and `CsgoBox`/`ItemCsgoBox` reference has been updated to the new package. The modid remains `"csgobox"` — only the Java package spelling changed. Affected files: 40+ Java sources under `src/main/java/com/reclizer/csgobox/`.

**Config relocation.** `CsboxConfig` moved from `com.reclizer.csbox.config` to `com.reclizer.csgobox.config` (`src/main/java/com/reclizer/csgobox/config/CsboxConfig.java`, commit `b7b11e5`). A new "Configuration" section now documents the `ModConfigSpec` pattern, the `static { ... }` builder pair, the `registerConfig(..., "csgobox-common.toml")` call, and the field/`*Value` convention used in `CsboxConfig`.

**Cloth Config removal.** The previous 1.0.5 baseline already reflected Cloth Config removal in the dependency layer, but the convention doc had no section capturing the replacement pattern. The new "Configuration" section now spells out `ModConfigSpec` as the only acceptable config API. Cloth Config is explicitly called out as removed (commit `862ab1f`) and must not be reintroduced.

**Recipe directory rename.** `data/csgobox/recipes/` → `data/csgobox/recipe/` (commit `be40e5a` and the 1.0.5 release). A new "Data Pack Paths" section documents the singular folder convention so new recipes land in the right place.

**Logger / class-name touch-ups.** `BoxJsonLoader`, `RandomItem`, etc. package references now use `csgobox`. Item class names updated: `ItemCsBox` → `ItemCsgoBox` and `ItemCs2Key` → `ItemCsgoKey`. Packet class names updated: `PacketCs2Progress` → `PacketCsgoProgress`. These are mechanical follow-ons to the package rename; class spelling in code now matches the `csgobox` modid.

**Files not modified (intentionally):**
- `docs/port-26.1.2.md` describes the 1.21.1 → 26.1.2 port — it is version-history documentation, not a current-state reference, so no edits were applied.
- `docs/update-1.0.5.md` is the 1.0.5 release note — same reason. Its contents (Cloth Config removal, `csgobox-common.toml` path, `recipe/` singular folder) are now incorporated into the convention doc.

---

*Convention analysis: 2026-06-28 (incremental update: 2026-06-29)*
