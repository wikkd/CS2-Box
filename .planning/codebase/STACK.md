# Technology Stack

**Analysis Date:** 2026-06-29

## Languages

**Primary:**
- Java 21 - All mod logic under `src/main/java/com/reclizer/csgobox/`

**Secondary:**
- JSON - Data-driven config (`config/csbox/*.json`), recipes (`data/csgobox/recipe/`), advancements (`data/csgobox/advancement/`), lang files (`assets/csgobox/lang/`), models (`assets/csgobox/models/`), sounds (`assets/csgobox/sounds.json`)
- Groovy - `build.gradle` (build script)
- TOML - Generated runtime config (`config/csgobox.toml`, written by NeoForge ModConfigSpec)
- GLSL (shader JSON) - `assets/minecraft/shaders/program/fade_in_blur.json` and `assets/minecraft/shaders/post/fade_in_blur.json` (vanilla-shader registration overlay)

## Runtime

**Environment:**
- Minecraft 1.21.1 (Mojang official mappings, `mapping_channel=official`, `mapping_version=1.21.1`)
- Java toolchain pinned to JDK 21 (`gradle.properties` line 3, `build.gradle` `JavaLanguageVersion.of(21)`)

**Package Manager:**
- Gradle wrapper (`gradlew`, `gradle/wrapper/`)
- Userdev plugin: `net.neoforged.gradle.userdev` version `7.0.171`
- No Maven or direct dependency manager; only the NeoForge userdev plugin resolves transitive deps from Maven Central + NeoForged repos.

**Lockfile:**
- None. `gradle.lockfile` is absent; dependency versions are pinned in `gradle.properties` only.

## Frameworks

**Core:**
- NeoForge `21.1.115` (modern Minecraft Forge successor) - sole compile-time dependency declared in `build.gradle:76` (`implementation "net.neoforged:neoforge:${neo_version}"`)
- Minecraft 1.21.1 vanilla code (resolved transitively via NeoForge userdev)

**Loader contract:**
- `modLoader="javafml"` (`src/main/resources/META-INF/neoforge.mods.toml:1`)
- Loader accepts `[4,)` (NeoForge `loader_version_range`)

**Build/Dev:**
- NeoForged userdev 7.0.171 - provides `runs { client, server, gameTestServer, data }` task graph
- Eclipse + IDEA IDE plugins (`build.gradle:3-4`)
- `maven-publish` plugin (`build.gradle:5`) - for publishing the jar artifact

**Test:**
- Minecraft GameTest framework registered (`forge.enabledGameTestNamespaces=mod_id` in `build.gradle:43-52`). No custom GameTest classes were detected under `src/`; framework is enabled but unused.

## Key Dependencies

**Critical:**
- `net.neoforged:neoforge:21.1.115` - full mod loader + game APIs (`net.neoforged.neoforge.*`, `net.neoforged.fml.*`, `net.neoforged.bus.api.*`)

**Infrastructure:**
- Cloth Config - explicitly REMOVED in v1.0.5 (per `CHANGELOG.md` 移除). Config now uses native NeoForge `ModConfigSpec` (`src/main/java/com/reclizer/csgobox/config/CsboxConfig.java`).
- Google Gson - transitively pulled in via NeoForge, used directly in `src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java` for box definition serialization.

**No external runtime services.** No HTTP clients, no database drivers, no analytics SDKs. Mod is 100% offline client/server logic.

## Configuration

**Environment:**
- No `.env` files. All config is in-game TOML written by `ModConfigSpec` to `config/csgobox.toml` (registered in `src/main/java/com/reclizer/csgobox/CsgoBox.java:57` with `registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC, "csgobox.toml")`).
- Box definitions loaded at runtime from `config/csbox/*.json` (path resolved via `FMLPaths.CONFIGDIR.get().resolve("csbox")` in `BoxJsonLoader.java:40`).

**Build configuration files:**
- `gradle.properties` - version pins for minecraft, neo, mappings, mod metadata
- `build.gradle` - plugin application, repository declarations, dependency block, run-config block, resource processing (token replacement in `META-INF/neoforge.mods.toml` and `pack.mcmeta`), jar manifest
- `settings.gradle` - plugin management (NeoForged repo), root project name
- `src/main/resources/META-INF/neoforge.mods.toml` - mod metadata, dependency declarations on `neoforge` and `minecraft` (both `mandatory=true`)
- `src/main/resources/pack.mcmeta` - resource-pack descriptor (`pack_format: 34`, supported `[34, 48]`)

**Resource processing (`build.gradle:108-124`):**
- `processResources` expands `${mod_id}`, `${mod_version}`, `${minecraft_version}`, etc. into `neoforge.mods.toml` and `pack.mcmeta`.
- `purgeStaleBuildResources` task deletes macOS auto-appended `name 2.ext` duplicates in `build/resources/main/`.

## Platform Requirements

**Development:**
- macOS-targeted: `gradle.properties:3-4` hardcodes `/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` and registers it via `foojay-resolver-convention`.
- `build.gradle:31-33` forces every `JavaExec` (including `runClient`, `runServer`) to use the JDK 21 binary resolved from the toolchain - bypasses whatever `JAVA_HOME` happens to point at.
- Min Gradle JVM heap: `-Xmx3G` (`gradle.properties:1`)
- Gradle daemon disabled (`org.gradle.daemon=false`) - required for stable toolchain resolution on macOS.

**Production:**
- Distribution target: `csgobox-1.0.5.jar` (per `CHANGELOG.md` 备注 + `archivesName = "csgobox"` in `build.gradle:13`)
- Sides: `BOTH` (`neoforge.mods.toml:18, 24`) - runs on dedicated servers AND clients
- Client requirements: Minecraft 1.21.1 + NeoForge >=21.1.115 + (optional) matching NeoForge on server

**Distribution repositories declared in `build.gradle:62-72`:**
- Maven Central
- BlameJared Maven (`https://maven.blamejared.com`)
- Modmaven (`https://modmaven.dev`)
- Curse Maven (`https://www.cursemaven.com`)
- Latvian Maven (`https://maven.latvian.dev/releases`) - scoped to `dev.latvian.mods` / `dev.latvian.apps` only

---

*Stack analysis: 2026-06-29*