# CS2-Box Configuration Reference

> Auto-generated reference for the box JSON files in `config/csbox/`. The companion Simplified Chinese version is at [`_tutorial_v2.0.0-beta_zh_cn.md`](./_tutorial_v2.0.0-beta_zh_cn.md).

## Overview

Each `.json` file defines one box. The file name without `.json` becomes the box id under the `csgobox:` namespace (e.g. `my_custom_box.json` -> `csgobox:my_custom_box`).

Files starting with `_` are reserved for documentation and templates and are never loaded as boxes.

To create a new box:

1. Create `my_custom_box.json` in your `config/csbox/` directory.
2. Fill in the [top-level fields](#top-level-fields) and one or more `grade*` arrays using the [Item object](#item-object) format.
3. Run `/csbox reload` in-game, then `/give @p csgobox:csgo_box[csgobox:box_id="csgobox:my_custom_box"]`.

## Top-level fields

| Field    | Type                  | Required | Default                | Description                                                                                                                                              |
|----------|-----------------------|----------|------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`   | string                | yes      | the file name          | Display name shown on the box item tooltip and the GUI title. An optional `#RRGGBB ` prefix sets a custom color; see [Box name colors](#box-name-colors). |
| `key`    | resource_location     | yes      | `csgobox:csgo_key0`    | Item id the player must hold to open this box. Use `minecraft:air` for none.                                                                             |
| `drop`   | float                 | no       | `0.12`                 | Default drop chance (0.0 to 1.0) for any mob in the `entity` list.                                                                                       |
| `random` | array of 5 integers   | no       | `[625, 125, 25, 6, 4]` | Weights for `grade1..grade5`. Higher = more likely.                                                                                                      |
| `entity` | array                 | no       | `[]`                   | Mob entity ids that drop this box. Two formats accepted (see below).                                                                                     |
| `type`   | string                | no       | `csbox`                | Box type: `csbox` (regular box, default) / `terminal` (terminal machine).                                                                                |
| `grade1` | array of item objects | no       | `[]`                   | Consumer-grade items (lowest rarity, blue).                                                                                                              |
| `grade2` | array of item objects | no       | `[]`                   | Industrial-grade items (indigo).                                                                                                                         |
| `grade3` | array of item objects | no       | `[]`                   | Mil-spec grade items (magenta).                                                                                                                          |
| `grade4` | array of item objects | no       | `[]`                   | Restricted-grade items (red-orange).                                                                                                                     |
| `grade5` | array of item objects | no       | `[]`                   | Classified-grade items (highest rarity, gold).                                                                                                           |

### Entity formats

The `entity` field accepts two formats.

Plain list: every entity uses the default `drop` rate.

```json
"entity": ["minecraft:zombie", "minecraft:skeleton", "minecraft:creeper"]
```

Alternating pairs: each entity has its own drop rate.

```json
"entity": ["minecraft:zombie", 0.25, "minecraft:skeleton", 0.10, "minecraft:creeper", 0.05]
```

Rates must be between 0.0 and 1.0. Values outside this range are accepted as-is and not clamped.

### Box types

The `type` field selects which screen and rules the box uses:

- `csbox` (default) — the classic crate. Right-click opens the item-grid preview, a key is required, and opening plays the rolling animation.
- `terminal` — terminal machine loot. Used by the `csgobox:terminal` item: right-clicking a terminal opens the terminal UI instead of the crate screen. Terminal boxes need no key and their offers are priced in Armory Points — see [Terminal machine](#terminal-machine-200).

## Box name colors

A box can carry a colored display name by prefixing the `name` value with a hex color and a single ASCII space:

```json
{
  "name": "#FF5555 Crimson Crate",
  "key":  "csgobox:csgo_key0",
  "drop": 0.12,
  "random": [625, 125, 25, 6, 4],
  "entity": ["minecraft:zombie"],
  "grade5": [{"id": "minecraft:diamond_sword"}]
}
```

The prefix format is `#RRGGBB ` — a `#`, six hexadecimal digits (case-insensitive), and one space. The color is applied to:

- The box item's display name in the inventory, tooltip, and held-in-hand render.
- The centered title at the top of the open-box GUI.

When no prefix is present, the title falls back to the default `0xFFD3D3D3` light gray — the same color used before this feature was added, so existing boxes are visually unchanged. If the prefix is malformed (for example `#GG5555 Crate` or `#FFF Crate` — wrong number of hex digits), the whole string is used as the name without a color and a warning is logged to the server console; the box still loads.

The color is parsed at load time only; the mod never rewrites user JSON files under `config/csbox/`, so the original prefix is always preserved.

## Item object

Each entry inside a `grade*` array is an item object.

| Field        | Type              | Required | Default | Description                                                          |
|--------------|-------------------|----------|---------|----------------------------------------------------------------------|
| `id`         | resource_location | yes      | -       | Item id, e.g. `minecraft:diamond_sword`. Unknown ids are skipped.    |
| `count`      | integer           | no       | `1`     | Stack size. Most items accept 1-64.                                  |
| `price`      | integer           | no       | -       | The item's terminal purchase price in Armory Points (falls back to the grade-default price if unset). Must be a non-negative integer — see [Terminal machine](#terminal-machine-200). |
| `components` | object            | no       | -       | Minecraft 1.21+ data components (preferred over `tag`).              |
| `tag`        | string            | no       | -       | Legacy NBT tag string. Kept for backwards compatibility with 1.20.x. |

### Item examples

Custom name:

```json
{
  "id": "minecraft:netherite_sword",
  "count": 1,
  "components": {
    "minecraft:custom_name": "{\"text\":\"Excalibur\",\"italic\":false}"
  }
}
```

Enchanted:

```json
{
  "id": "minecraft:diamond_sword",
  "count": 1,
  "components": {
    "minecraft:enchantments": {
      "levels": {
        "minecraft:sharpness": 5,
        "minecraft:looting": 3,
        "minecraft:unbreaking": 3
      }
    }
  }
}
```

Player head:

```json
{
  "id": "minecraft:player_head",
  "count": 1,
  "components": {
    "minecraft:profile": {"name": "wikkd"}
  }
}
```

## Rarity grades

| Grade    | Internal id | Color (hex) | Default weight | Approximate chance |
|----------|-------------|-------------|----------------|--------------------|
| `grade1` | consumer    | `#4C70FF`   | 625            | 79.6%              |
| `grade2` | industrial  | `#8D5EFF`   | 125            | 15.9%              |
| `grade3` | mil_spec    | `#E54AF2`   | 25             | 3.2%               |
| `grade4` | restricted  | `#F86351`   | 6              | 0.8%               |
| `grade5` | classified  | `#FFDC1D`   | 4              | 0.5%               |

The hex colors are the item-frame and name colors shown in the box GUI, the
reveal screen, and the bulk-result screen.

## Keys

| Item id             | Material  |
|---------------------|-----------|
| `csgobox:csgo_key0` | iron      |
| `csgobox:csgo_key1` | gold      |
| `csgobox:csgo_key2` | diamond   |
| `csgobox:csgo_key3` | netherite |

Use `minecraft:air` as the `key` field for a box that requires no key. `csgobox:csgo_key3` is only obtainable via the smithing table by upgrading `csgobox:csgo_key2` with a netherite upgrade template.

Keys can also be obtained from the arms-dealer villager in exchange for Armory
Points (see [Armory economy](#armory-economy-200)), or crafted:

| Key               | Crafting recipe                  |
|-------------------|----------------------------------|
| `csgobox:csgo_key0` | 3 iron ingots                    |
| `csgobox:csgo_key1` | 3 gold ingots                    |
| `csgobox:csgo_key2` | 3 diamonds                       |

## Bulk opening (2.0.0)

Hold a `csgobox:csgo_box` and **Shift + right-click** to open the bulk overview
screen instead of the single-open preview. It shows how many boxes and keys you
have, and how many can be opened. Click the open button to start the batch
directly (there is no separate confirmation screen).

- Results stream in on a rising ticker; the server computes the batch
  asynchronously so the game thread is not blocked.
- Boxes and keys are consumed server-side. If you run out mid-batch, the
  remaining boxes stay in your inventory and can be opened in the next round.
- The batch size is capped by `bulkOpenCount` under `[advanced]` in
  `config/csgobox.toml` (`0` = no limit, the default). The cap is enforced on
  the server; the overview screen mirrors it.
- Terminal items always open their own screen and cannot be bulk-opened.

## Terminal machine (2.0.0)

The terminal (`csgobox:terminal`) is a box-type item with its own loot
pool: the `type: terminal` box definition in `config/csbox/terminal.json`.
Right-click the terminal to open the terminal UI instead of the crate screen.

> **Multiple terminals**: like regular boxes, **one JSON file registers one
> terminal** — any file declaring `"type": "terminal"` becomes a terminal with
> its own item id (e.g. `terminal2.json` → `csgobox:terminal2`) and its own
> negotiation loot pool. `csgobox:terminal` itself is statically registered
> (same as `csgobox:csgo_box`), so it always exists even without a
> `terminal.json` — it then opens as an **empty crate** (no loot bound);
> create `terminal.json` yourself to give it a negotiation pool. The
> arms-dealer villager always sells `csgobox:terminal`, extra terminals are
> obtained via `/give`.

- No key required: terminals have no `key` field at all, so opening one never consumes a key.
- Offers are priced in Armory Points. The price is the per-item `price`
  field in the box JSON if set, otherwise it falls back to the default
  grade-level price (grade1 = 6, grade2 = 10, grade3 = 16, grade4 = 22,
  grade5 = 30). Accepted items carry a **random wear value** (rolled like box
  openings, uniform 0..1; durability is reduced by the wear amount when
  `damageItemByWear` is on). Items **without a durability bar** take a wear
  penalty instead: every 5% of wear adds 1 Armory Point on top of the item
  price (up to +20 at Battle-Scarred), so the more worn the item, the more it
  costs.
- Each session runs a 5-round negotiation; every offer carries a 3-hour
  countdown, and the offered item is sampled from the terminal box's grade
  pools.
- Sources: creative tab, or the arms-dealer villager (level 4) for 12 Armory
  Points.

## Armory economy (2.0.0)

Armory Points (`csgobox:armory_point`) are the mod's currency. They drop from
boxes when you add the item to a grade pool, and are rewarded by the
arms-dealer villager.

- Armory recycler (`csgobox:armory_recycler`, crafted with iron ingots, a
  hopper, copper, and redstone): right-click it while holding an item that was
  opened from a box to recycle the whole stack. Only items stamped with a grade
  by the box-opening code are accepted, so raw loot cannot be recycled. Yield
  per grade: grade1 = 3, grade2 = 5, grade3 = 8, grade4 = 11, grade5 = 15
  points. Hoppers can also push graded items in for automatic recycling.
- Exchange recipe: a 3x3 grid filled with 64 Armory Points each crafts 1
  `csgobox:csgo_key0`.
- Arms-dealer villager (profession `arms_dealer`, work site: the armory
  recycler block) trades materials for points and points for items:

| Level | Trades |
|-------|--------|
| 1     | 1 iron ingot → 2 points; 1 emerald → 2 points |
| 2     | 1 gold ingot → 4 points; 8 points → `csgobox:csgo_box` |
| 3     | 1 diamond → 12 points; 9 points → `csgobox:csgo_key0` |
| 4     | 24 points → `csgobox:csgo_key1`; 12 points → `csgobox:terminal` |
| 5     | 45 points + 1 diamond → `csgobox:csgo_key2` |

## In-game commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/csbox` | OP (level 2) | Shows the help summary. |
| `/csbox info` | OP | Lists all registered boxes plus any load errors. |
| `/csbox info <box id>` | OP | Shows one box's weights, drop entities, and per-grade items. |
| `/csbox info error` | OP | Shows load errors only (green confirmation when there are none). |
| `/csbox reload` | OP | Re-reads every `config/csbox/*.json` file. |
| `/csbox reload tutorial` | OP | Also forces re-download of the tutorial markdown files. |
| `/csbox nbt hand` | any player | Prints the held item as serialized JSON, ready to paste into a box JSON grade array. |
| `/give @p csgobox:csgo_box[csgobox:box_id="csgobox:my_custom_box"]` | OP | Gives a specific dynamic box (vanilla command). |

## Validation rules

- If `grade1` through `grade5` are all empty or all unparseable, the file is skipped with a warning and the box is not registered.
- Negative or zero `random` weights fall back to the default weight for that grade. Weights above 10000 are clamped to 10000.
- Unknown item ids are skipped with a warning; the rest of the grade still loads.
- Item `count` defaults to 1; a count of 0 or less yields an empty stack, which is skipped from the grade pool (the item does not drop).
- An item `price` must be a non-negative integer; violations are reported as load errors.
- The loader tolerates extra unknown top-level keys; they are ignored without warning.
- A `name` that contains a malformed `#RRGGBB ` prefix is preserved verbatim and used as the plain name (no color). See [Box name colors](#box-name-colors).

## Troubleshooting

**Box does not appear in the game.**
Run `/csbox info` to see all registered boxes (`/csbox info error` shows load errors only). If yours is missing, check `latest.log` for `Failed to load box JSON file` errors. Common causes: a JSON syntax error, a missing comma, or an item id that does not exist.

**Box appears but no items drop.**
All `grade1..grade5` arrays are empty or every item in them failed to parse. Check that every item id is a real Minecraft item (try it in `/give` first).

**Drop rate does not match expectations.**
Entity drop rates are taken from the per-entity rate if present, else the default `drop` field. The Looting enchantment adds +50% per level (capped at 100%).

**Box name color does not show up.**
The prefix must be `#` followed by exactly six hex digits and one space, at the very start of the `name` string. Anything else (e.g. `#FFF Name`, `#GG5555 Name`, no trailing space) is treated as a plain name. Check `latest.log` for a `Box name has color prefix but empty text` warning if you expected a color.

**Want to remove a box.**
Delete the corresponding `.json` file and run `/csbox reload` (or restart the server).

**Want to share a box with a friend.**
Copy the `.json` file to their `config/csbox/` directory. Both servers will register the same box at the same `csgobox:` id.
