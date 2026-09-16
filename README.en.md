<div align="center">

# 🎁 CS2-Box

**CS:GO-style crate opening for Minecraft**

[![Build](https://github.com/wikkd/CS2-Box/actions/workflows/build.yml/badge.svg)](https://github.com/wikkd/CS2-Box/actions/workflows/build.yml)
[![PR checks](https://github.com/wikkd/CS2-Box/actions/workflows/pr-checks.yml/badge.svg)](https://github.com/wikkd/CS2-Box/actions/workflows/pr-checks.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](./LICENSE)
[![Release](https://img.shields.io/github/v/release/wikkd/CS2-Box?sort=semver&label=release)](https://github.com/wikkd/CS2-Box/releases)
![Minecraft](https://img.shields.io/badge/MC-1.20.1%20%7C%201.21.1%20%7C%2026.1.2%20%7C%2026.2-blueviolet)
![Loader](https://img.shields.io/badge/loader-NeoForge%20%7C%20Forge-8A2BE2)
![Java](https://img.shields.io/badge/Java-17%20%7C%2021%20%7C%2025-critical)

[简体中文](./README.md) | **English**

</div>

---

CS2-Box brings CS:GO's crate-opening loop into Minecraft: right-click while holding a crate to preview it, drop in a key and hit **Open**, and a **server-authoritative RNG** decides the result while the client plays the rolling animation before revealing rarity-tiered loot. No real money involved — it all happens in-game.

- 🎲 **Five rarity tiers** + true server-side randomness + bulk opening + pity system
- 📝 **JSON-driven**: add new crates by editing files — no recompiling
- 🌐 **One codebase, six platforms**: NeoForge and Forge, all in sync
- 🖥️ **Online box editor**: [wikkd.github.io/CS2-Box](https://wikkd.github.io/CS2-Box/) — visually design crates in your browser, or type `/csbox editor` in-game for the link

Current version: **`2.0.1`**. This is a cleaned-up, maintained fork of Reclizer's original CsgoBox: the UI is reworked, a known exploit is fixed, NBT handling is rewritten, and CraftTweaker is no longer needed.

## 🚀 Quick Start

1. Install the NeoForge (or Forge) loader matching your Minecraft version, grab the jar from [Releases](https://github.com/wikkd/CS2-Box/releases), and drop it into `mods/`
2. Get your first crate and key:

   ```bash
   /give @p csgobox:csgo_box        # Weapon Supply Crate
   /give @p csgobox:csgo_key0       # Iron Key
   ```

3. Right-click while holding the crate → insert the key → hit **Open**
4. Hold the crate and **Shift+right-click** to bulk-open in one go

For installation details and per-platform requirements, see [Installation](#-installation) below.

## ✨ Features

### 🎲 Opening Experience

- **5 rarity tiers**: consumer / industrial / mil_spec / restricted / classified, each with its own weight
- **Server-authoritative RNG**: the server computes the winning index and item inside `PacketCsgoProgress`; the client only renders the animation
- **Bulk opening**: Shift+right-click opens the bulk overview screen; open a whole stack at once and scroll through the results feed
- **Checkable odds**: crate tooltips show all 5 tier probabilities (since v2.0.1, per-item odds when in-tier weights are used; enable vanilla advanced tooltips with <kbd>F3</kbd>+<kbd>H</kbd> to see probability lines), plus JEI / REI / EMI categories and Jade / WTHIT / The One Probe crosshair info
- **Achievements**: "A Brand New Start" (first active open) + hidden purple challenge "Salesperson" (200 active opens)
- **5 keys**: copper / iron / gold / diamond / netherite (copper key = 3 copper ingots; netherite **only** via smithing-table upgrade of `csgo_key2`)

### 📝 Configuration Power (v2.0.1)

- **JSON crate data**: add crate types via `config/csbox/*.json`, applied by `/csbox reload` or automatic hot reload — no recompiling
- **In-tier item weights**: per-item `weight`
- **`count:[min,max]` ranges**: random drop counts
- **`#tag` item-tag references / `loot_table` loot-table references**
- **Random enchantment `enchant`** shorthand
- **Minecraft 1.21+ Data Components**: `components` field (legacy `tag` strings still supported)
- **Compat gating & identity**: `requires` (skip a whole crate when a mod is missing), `enabled`, per-crate `icon`; unknown item ids distinguish "mod not installed" from "typo"
- **JSON Schema**: [docs/box-schema/box.schema.json](./docs/box-schema/box.schema.json) for IDE completion and validation
- **Tooling scripts**: `scripts/boxgen.py` generates crate configs, `scripts/check-ids.py` audits item ids

### 🏪 Terminal Economy (v2.0.1)

- `discount`, `stock` / `restock_minutes` — terminal inventory and restocking
- Opening constraints: `max_per_player`, `cooldown_seconds`, `permission`
- Arms-dealer villager trades, the Armory Recycler, and terminals all price against the `_prices.json` price table

### 🔌 Ecosystem

- **Recipe viewers**: JEI / REI / EMI (optional client mods, auto-detected)
- **Crosshair info**: Jade / WTHIT / The One Probe (optional, auto-detected)
- **Create compat**: the Armory Recycler exposes item handling (automatable with deployers/belts/pipes), and deployers can auto-open crates — see [docs/CREATE-COMPAT.md](./docs/CREATE-COMPAT.md)
- **TACZ (Timeless and Classics Zero)**: inspect-viewport integration on 1.21.1 and 1.20.1, graceful degradation when absent
- **KubeJS**: crate-opening and terminal events — see [docs/KUBEJS-EVENTS.md](./docs/KUBEJS-EVENTS.md)
- **Cloth Config (optional)**: install it for a config GUI; TOML remains the single source of truth
- **Official compat-pack examples**: [compat-packs/](./compat-packs/README.md) with Apotheosis / Iron's Spells / TACZ sample crates

## 🖥️ Platform Support

The repo is a multiloader workspace; switch the active build target via `active_versions` in `gradle.properties`:

| Module | Minecraft | NeoForge / Forge | Java | Role |
|---|---|---|---|---|
| `common/` | — | — | 21 | Cross-version game logic + shared resources (no MC/NeoForge imports) |
| `v1_21_1/` | 1.21.1 | 21.1.248 | 21 | Legacy-API platform |
| `v26_1_2/` | 26.1.2 | 26.1.2.95 | 25 `--enable-preview` | decoupled rendering API + PIP 3D |
| `v26_2/` | 26.2 | 26.2.0.59 | 25 `--enable-preview` | Latest; decoupled API + PIP 3D rewrite |
| `forge_26_1_2/` | 26.1.2 | MinecraftForge 26.1.2-64.1.0 | 25 `--enable-preview` | Released: developed in sync with v26_1_2 |
| `forge_26_2/` | 26.2 | MinecraftForge 26.2-65.1.1 | 25 `--enable-preview` | Released: parity with the 2.0.0 line |
| `forge_1_20_1/` | 1.20.1 | MinecraftForge 47.4.22 | 17 | Released: 1.20.1 backport (ForgeGradle 7.x) |

<details>
<summary><strong>Archived (EOL) platforms</strong></summary>

v1_21_0 / v1_21_3 / v1_21_4 / v1_21_5 / v1_21_8 / v1_21_10 / v1_21_11 were removed from the repo on 2026-08-09; their final state is preserved at tag [`eol-legacy-21x-1.0.6`](https://github.com/wikkd/CS2-Box/tree/eol-legacy-21x-1.0.6). Existing release jars for those versions remain available to players.

</details>

`common/src/main/resources/` is shared by all platforms via `srcDir project(':common').file('src/main/resources')` (v26_1_2 / v26_2 additionally set `duplicatesStrategy = EXCLUDE`).

## 📦 Installation

### 🎮 Players (release jars)

1. Make sure the matching loader is installed:

   | Minecraft | Loader |
   |---|---|
   | 1.21.1 | NeoForge **21.1.248+** |
   | 26.1.2 | NeoForge **26.1.2.95** (loader 11+) |
   | 26.2 | NeoForge **26.2.0.59** |
   | 1.20.1 (Forge) | MinecraftForge **47.4.22** |
   | 26.1.2 (Forge) | MinecraftForge **26.1.2-64.1.0** |
   | 26.2 (Forge) | MinecraftForge **26.2-65.1.1** |

2. Download the jar for your platform from [Releases](https://github.com/wikkd/CS2-Box/releases)
3. Drop it into your `mods/` folder
4. In a world, run `/give @p csgobox:csgo_box` to get a crate

<details>
<summary><strong>Jar naming scheme</strong></summary>

All platforms share one `mod_version`:

- NeoForge: `csgobox-<mc>-<mod_version>.jar` (e.g. `csgobox-26.1.2-2.0.1.jar`)
- Forge: `csgobox-forge-<mc>-<mod_version>.jar`
- **Sole exception**: Forge 1.20.1 ships as `csgobox-forge-1.20.1-<mod_version>-srg.jar` (SRG-remapped; required for 1.20.1 production)

Archived EOL versions (1.21.0/3/4/5/8/10/11) keep using their existing release jars.

</details>

### 🛠️ Developers (build from source)

**Requirements**

| Requirement | v1_21_1 | v26_1_2 / v26_2 | forge_26_1_2 / forge_26_2 | forge_1_20_1 |
|---|---|---|---|---|
| Java JDK | 21 | 25 (`--enable-preview`) | 25 (`--enable-preview`) | 17 |
| Minecraft | 1.21.1 | 26.1.2 / 26.2 | 26.1.2 / 26.2 | 1.20.1 |
| NeoForge / Forge | 21.1.248+ | 26.1.2.95 / 26.2.0.59 (loader 11+) | MinecraftForge 26.1.2-64.1.0 / 26.2-65.1.1 | MinecraftForge 47.4.22 |
| Gradle | 9.5.1 (wrapper downloads it) | same | same | same |
| NeoGradle / ForgeGradle | 7.1.38 | same | ForgeGradle 7.0.31/7.0.34 | ForgeGradle [7.0.17,8) |

> Internet access is needed for the first build (NeoForged userdev and dependencies).

**Build steps**

```bash
# 1. Clone the repo
git clone https://github.com/wikkd/CS2-Box.git
cd CS2-Box

# 2. Pick the active version (default 26.1.2): edit gradle.properties
#    active_versions=26.1.2   # options: 1.21.1 / 26.1.2 / 26.2 / forge-26.1.2

# 3. Build the jar for the active platform
./gradlew :v26_1_2:jar        # output: v26_1_2/build/libs/csgobox-26.1.2-2.0.1.jar

# 4. Launch the dev client (downloads and injects NeoForge into run/)
./gradlew :v26_1_2:runClient
```

Verify your Java version:

```bash
java -version   # v1_21_1 expects 21.x; v26_1_2 / v26_2 expect 25.x
```

> Due to a NeoGradle userdev limitation, **each Gradle invocation can only build one MC version** (historical constraint, see `settings.gradle`). Build platforms serially by switching `active_versions` (or overriding with `-Pactive_versions=<v>`); see [docs/RELEASE.md](./docs/RELEASE.md).

<details>
<summary><strong>TACZ dependency (v1_21_1 / forge_1_20_1)</strong></summary>

TACZ inspect-viewport integration: the jar is not committed (repo convention ignores `*.jar` globally). Before the first build run `scripts/download-tacz.sh` (v1_21_1) or `scripts/download-tacz-1201.sh` (forge_1_20_1) to populate `local-repo/com/tacz/` (CI does this automatically), extracting the compile-time `simplebedrockmodel` from its jarjar payload. Without TACZ installed, the related features **degrade gracefully** — compilation and running are unaffected.

</details>

## 🎮 Usage

### Getting items

Two ways to hand out crates:

- **Vanilla `/give`** (crate and key items):

```bash
/give @p csgobox:csgo_box          # Weapon Supply Crate
/give @p csgobox:csgo_key0 3       # Iron Key ×3
/give @p csgobox:csgo_key1         # Gold Key
/give @p csgobox:csgo_key2         # Diamond Key
# Netherite key csgo_key3 can only be obtained by upgrading csgo_key2 in a smithing table (smithing_transform)
```

- **`/csbox give <player> <boxId> [count]`** (v2.0.1, OP): hand out configured crates by ID, e.g.:

```bash
/csbox give @p csgobox:weapon_supply_box 1
```

Key ladder: iron (key0) → gold (key1) → diamond (key2) → netherite (key3, smithing table `smithing_transform`).

### Opening flow

1. Right-click while holding a crate to open the preview screen (2×10 item grid)
2. Insert the matching key into the key slot and hit **Open**
3. Server-authoritative RNG decides → the client plays the rolling animation → the rarity-tiered item is revealed
4. **Bulk opening**: Shift+right-click while holding the crate opens the bulk overview; hit "Open" to start directly (no confirmation screen), with a scrollable results feed

### `/csbox` command reference

| Command | Permission | Description |
|---|---|---|
| `/csbox help` | everyone | Help text with clickable links: tutorial folder, online tutorial, web box editor |
| `/csbox info [<boxId>]` | OP | List all crates and load errors (plus namespace statistics); with `<boxId>`, show weights, dropped entities and per-tier items |
| `/csbox info error` | OP | Show only current crate load errors (green message when none) |
| `/csbox reload` | OP | Reload `config/csbox/*.json` crate definitions |
| `/csbox reload tutorial` | OP | Reload crate definitions and force-refresh tutorial docs |
| `/csbox validate [<boxId>]` | OP | v2.0.1 dry-run validation, published to runtime only on a clean pass |
| `/csbox give <player> <boxId> [count]` | OP | v2.0.1 hand out configured crates |
| `/csbox nbt hand` | any player | Print the held item as serialized JSON (paste-ready for crate `items`) |
| `/csbox editor` | any player | Print a clickable link to the web box editor |

Examples:

```bash
/csbox info csgobox:weapon_supply_box
/csbox validate
/csbox give @p csgobox:weapon_supply_box 1
/csbox nbt hand
```

### Configuring a custom crate

Crate data lives in `config/csbox/<boxId>.json` — **the file name is the box ID**. Minimal example (full fields and `_tutorial` comments: see the samples under `common/src/main/resources/data/csgobox/` and [docs/CONFIGURATION.md](./docs/CONFIGURATION.md)):

```json
{
  "name": "My Crate",
  "key": "csgobox:csgo_key0",
  "drop": 1.0,
  "random": [625, 125, 25, 6, 4],
  "entity": ["minecraft:zombie", 1, "minecraft:skeleton", 1],
  "grade1": [
    { "id": "minecraft:iron_ingot", "count": 1 }
  ],
  "grade5": [
    { "id": "minecraft:netherite_ingot", "count": 1,
      "components": { "minecraft:custom_name": "{\"text\":\"VIP only\",\"italic\":false}" } }
  ]
}
```

- The crate type comes from the `type` field (the only mechanism since v2.0.0): `"type": "terminal"` registers a terminal (`ItemTerminal`, opens the negotiation screen), `"type": "csbox"` (or omitted, default) is a regular crate. **Terminal and regular crates have strictly separated fields**: terminals must not use `key` (schema error if present), regular crates use `key` to specify the required key (`minecraft:air` = keyless)
- `random` holds the 5 tier weights (grade1→grade5, higher = rarer); `grade1`~`grade5` are item arrays, each drawn with the matching weight
- `components` uses MC 1.21+ DataComponent syntax (legacy `tag` strings still supported)
- `entity` is an "entity id + drop chance" pair list; global `drop` is the default drop chance
- **Don't want to hand-write JSON?** Use the online editor at [wikkd.github.io/CS2-Box](https://wikkd.github.io/CS2-Box/) and drop the exported file into `config/csbox/`

> After editing, run `/csbox reload` for instant effect; when `enableHotReload` is on (default), file changes in `config/csbox/*.json` hot-reload automatically (300ms debounce). `/csbox nbt hand` exports any held item as a JSON snippet for reuse.

## ⚙️ Configuration

- `config/csgobox.toml`: TOML config (animation speed, rarity weights, volume, debug toggles…) — see [docs/CONFIGURATION.md](./docs/CONFIGURATION.md)
- `config/csbox/*.json`: crate data files, file name = box ID — schema in [docs/CONFIGURATION.md](./docs/CONFIGURATION.md)
- `config/csbox/_prices.json`: global price table (drives terminal prices and recycler yields; unpriced items cannot be recycled)
- Config persistence always goes through native NeoForge/Forge `ModConfigSpec` (TOML is the single source of truth); the optional Cloth Config mod adds a GUI on top
- **Resource paths must be singular**: `data/csgobox/recipe/` (required by `RecipeManager` via `Registries.elementsDirPath(Registries.RECIPE)`)

## 📚 Documentation

| Document | Contents |
|---|---|
| [docs/PLAYER-INTRO.md](./docs/PLAYER-INTRO.md) | Player-facing introduction and gameplay guide |
| [docs/PLAYER-CHANGELOG-2.0.1.md](./docs/PLAYER-CHANGELOG-2.0.1.md) | Player-facing 2.0.1 release notes |
| [docs/GETTING-STARTED.md](./docs/GETTING-STARTED.md) | Full installation and first-run steps |
| [docs/CONFIGURATION.md](./docs/CONFIGURATION.md) | TOML / JSON config reference (all v2.0.1 fields + [JSON Schema](./docs/box-schema/box.schema.json)) |
| [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) | Six-platform module topology, core abstractions, data flow, GUI rendering pipeline (legacy/decoupled eras), terminal subsystem and engineering gates |
| [docs/PLATFORM-APIS.md](./docs/PLATFORM-APIS.md) | Cross-platform API diff matrix + themed development guide (incl. the AnimRenderOps facade chapter) |
| [docs/DEVELOPMENT.md](./docs/DEVELOPMENT.md) | Local dev setup, build commands, datagen |
| [docs/ROADMAP.md](./docs/ROADMAP.md) | Roadmap: candidate directions, priorities, dependencies and rejected ideas |
| [docs/CREATE-COMPAT.md](./docs/CREATE-COMPAT.md) | Create (mechanical) compat notes |
| [docs/KUBEJS-EVENTS.md](./docs/KUBEJS-EVENTS.md) | KubeJS event reference |
| [docs/BIOME-INTEGRATION.md](./docs/BIOME-INTEGRATION.md) | Make the arms-dealer cabin generate in modded biomes/villages (pure data + `add-biome.py`) |
| [docs/SHADER-COMPAT.md](./docs/SHADER-COMPAT.md) | Iris/Oculus shader compat strategy and regression checklist (3D preview falls back to 2D) |
| [docs/TESTING.md](./docs/TESTING.md) | NeoForge GameTest guide |
| [CHANGELOG.md](./CHANGELOG.md) | Release history |

<details>
<summary><strong>Design docs (RFCs / drafts)</strong></summary>

- [docs/DESIGN-jade-wthit-integration.md](./docs/DESIGN-jade-wthit-integration.md) — Jade/WTHIT integration design (pending wiring)
- [docs/DESIGN-rarity-mapping.md](./docs/DESIGN-rarity-mapping.md) — Rarity mapping protocol RFC (draft)
- [docs/DESIGN-terminal-protocol.md](./docs/DESIGN-terminal-protocol.md) — Terminal negotiation generalization design (draft)

</details>

## 🤝 Contributing

Issues and PRs are welcome — full workflow in [CONTRIBUTING.md](./CONTRIBUTING.md).

### Development environment

- JDK 21 (v1_21_1) / JDK 25 (v26_1_2, v26_2 with `--enable-preview`)
- Gradle (wrapper ships 9.5.1, no manual install)
- Switch build targets via `active_versions` in `gradle.properties`

### Branch & commit conventions

- Long-lived branch: `main` (stable)
- Feature branch naming: `feat/<topic>`, `fix/<topic>`, `docs/<topic>`, `refactor/<topic>`
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/): `feat:` / `fix:` / `docs:` / `refactor:`

### PR process

1. Branch off the target branch (usually `main`)
2. Run `./gradlew :<module>:build` for every module you touched
3. Verify in-game with `./gradlew :<module>:runClient`
4. If `common/` changed, **verify every platform module** (CI only covers the 3 NeoForge platforms; the Forge modules have their own gates, see [docs/CODE-REVIEW.md](./docs/CODE-REVIEW.md); the default `active_versions` builds only one platform and incremental caches can lie — use `clean` when in doubt)
5. Update docs (`docs/*.md`, `README.md`) and `CHANGELOG.md`
6. Open the PR with a change description, how you tested it, and affected MC versions

<details>
<summary><strong>Key constraints (must follow)</strong></summary>

- **`common/` must not `import net.minecraft.*` or `import net.neoforged.*`** — version-sensitive code stays in platform modules; enforced by the `:common:checkCommonArchitecture` Gradle task wired into compilation
- Cross-platform changes start in the baseline module: new features in `v26_1_2`, legacy's sole module `v1_21_1` directly; pure new files via `scripts/mirror.sh new`, adapted files merged manually; **never overwrite `v26_2` / `forge_26_1_2` wholesale with `v26_1_2`** (it destroys platform adaptations)
- New `AnimRenderOps` render primitives must be added to **all three NeoForge platforms** or `scripts/check-animops-drift.sh` fails (CI-gated)
- `CONFIG` is `public static final` — no `null` guards
- Version bumps sync four places: `mod_version` in `gradle.properties`, per-platform `neoforge.mods.toml` (template-injected), `CHANGELOG.md`, `README.md`; guarded by `scripts/check-version.sh`
- **`premium_supply_box` / `ItemPremiumBox` is permanently removed (2026-08-19)**: the "Arms Dealer Premium Crate" and every trace of it (code / assets / config / docs) was fully deleted — **do not reintroduce it**; the `PlatformSmokeTest` guard in `forge_26_1_2` / `forge_26_2` will fail the build

</details>

### Reporting issues

Open them on [GitHub Issues](https://github.com/wikkd/CS2-Box/issues). Bug reports should include: MC version, NeoForge version, mod version, repro steps, expected/actual behavior, and relevant logs (`runs/client/logs/latest.log` or `.minecraft/logs/latest.log`). Feature requests should describe the use case and benefit.

## 📄 License

[MIT License](./LICENSE) — Copyright 2024 Reclizer

## 🧭 Project Status

- **Current release**: `2.0.1` (merging "compat batch A + crate config power + odds finishing" into one release, published in sync across all 6 platforms with a shared `mod_version`)
- **In development**: none (next version line to be planned)

<details>
<summary><strong>Recent progress</strong> (see <a href="./CHANGELOG.md">CHANGELOG.md</a>)</summary>

- ✅ **2.0.1**: odds in tooltips, arms-dealer cabin biome integration, `/csbox info` source-mod stats, official compat-packs, registry/config decoupling (multiplayer fix), in-tier weights / count ranges / `#tag` / loot tables / random enchants, terminal price table, per-crate icon, stock/restock/discount, opening constraints (per-player cap / cooldown / permission), `/csbox validate` dry-run, schema validation, boxgen/check-ids tooling, pity system, help & tutorial entry points
- ✅ **2.0.0**: bulk opening restored + UI polish (no confirmation screen, scrollable "show all" grid), terminal negotiation sessions (random wear + wear-point penalty for undamageable items), arms-dealer cabin world structure, JEI odds category, `blurRadius` background blur
- ✅ **1.0.6**: containerized layout, per-item visual baseline, 3-tier design tokens, dynamic box item, tutorial system, opening leaderboard, TACZ inspect viewport, v26_2 platform
- ✅ **AnimRenderOps facade**: all rendering from 6 screens + 3 helpers funneled through the per-platform `utils/AnimRenderOps.java` (13 public ops), zero raw draw calls left, signature consistency guarded by `scripts/check-animops-drift.sh`
- ✅ **forge_26_2 caught up**: migrated from `forge_26_1_2` as baseline, 5-platform `clean compileJava` all green, gates 7/7 PASS
- ✅ **forge_1_20_1 backport**: MC 1.20.1 release line (SRG remap, SimpleChannel networking, NBT storage, JEI/REI integration)

</details>

**Explicitly out of scope** (deferred): hard Cloth Config dependency (optional GUI remains), player-to-player trading (loot bind-on-open).

---

<div align="center">

**CS2-Box** · MIT License · multiloader for MC 1.20.1 – 26.2

[简体中文](./README.md) · English · [Online Box Editor](https://wikkd.github.io/CS2-Box/)

</div>
