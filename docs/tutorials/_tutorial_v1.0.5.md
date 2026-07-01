# CS2-Box Configuration Reference

> Auto-generated reference for the box JSON files in `config/csbox/`. The companion Simplified Chinese version is at [`_tutorial_zh_cn.md`](./_tutorial_zh_cn.md).

## Overview

Each `.json` file defines one box. The file name without `.json` becomes the box id under the `csgobox:` namespace (e.g. `my_custom_box.json` -> `csgobox:my_custom_box`).

Files starting with `_` are reserved for documentation and templates and are never loaded as boxes.

To create a new box:

1. Create `my_custom_box.json` in your `config/csbox/` directory.
2. Fill in the [top-level fields](#top-level-fields) and one or more `grade*` arrays using the [Item object](#item-object) format.
3. Run `/csbox reload` in-game, then `/csbox give @p csgobox:my_custom_box 1`.

## Top-level fields

| Field    | Type                  | Required | Default                | Description                                                                  |
|----------|-----------------------|----------|------------------------|------------------------------------------------------------------------------|
| `name`   | string                | yes      | the file name          | Display name shown on the box item tooltip and the GUI title.                |
| `key`    | resource_location     | yes      | `csgobox:csgo_key0`    | Item id the player must hold to open this box. Use `minecraft:air` for none. |
| `drop`   | float                 | no       | `0.12`                 | Default drop chance (0.0 to 1.0) for any mob in the `entity` list.           |
| `random` | array of 5 integers   | no       | `[625, 125, 25, 5, 2]` | Weights for `grade1..grade5`. Higher = more likely.                          |
| `entity` | array                 | no       | `[]`                   | Mob entity ids that drop this box. Two formats accepted (see below).         |
| `grade1` | array of item objects | no       | `[]`                   | Consumer-grade items (lowest rarity, light blue).                            |
| `grade2` | array of item objects | no       | `[]`                   | Industrial-grade items (light blue).                                         |
| `grade3` | array of item objects | no       | `[]`                   | Mil-spec grade items (blue).                                                 |
| `grade4` | array of item objects | no       | `[]`                   | Restricted-grade items (purple).                                             |
| `grade5` | array of item objects | no       | `[]`                   | Classified-grade items (highest rarity, pink).                               |

### Entity formats

The `entity` field accepts two formats.

**Plain list** — every entity uses the default `drop` rate:

```json
"entity": ["minecraft:zombie", "minecraft:skeleton", "minecraft:creeper"]
```

**Alternating pairs** — each entity has its own drop rate:

```json
"entity": ["minecraft:zombie", 0.25, "minecraft:skeleton", 0.10, "minecraft:creeper", 0.05]
```

Rates must be between 0.0 and 1.0. Values outside this range are accepted as-is and not clamped.

## Item object

Each entry inside a `grade*` array is an item object.

| Field        | Type              | Required | Default | Description                                                          |
|--------------|-------------------|----------|---------|----------------------------------------------------------------------|
| `id`         | resource_location | yes      | -       | Item id, e.g. `minecraft:diamond_sword`. Unknown ids are skipped.    |
| `count`      | integer           | no       | `1`     | Stack size. Most items accept 1-64.                                  |
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
| `grade1` | consumer    | `#4B69FF`   | 625            | 79.9%              |
| `grade2` | industrial  | `#4B69FF`   | 125            | 16.0%              |
| `grade3` | mil_spec    | `#4B69FF`   | 25             | 3.2%               |
| `grade4` | restricted  | `#8847FF`   | 5              | 0.64%              |
| `grade5` | classified  | `#D32CE6`   | 2              | 0.26%              |

## Keys

| Item id             | Material  |
|---------------------|-----------|
| `csgobox:csgo_key0` | iron      |
| `csgobox:csgo_key1` | gold      |
| `csgobox:csgo_key2` | diamond   |
| `csgobox:csgo_key3` | netherite |

Use `minecraft:air` as the `key` field for a box that requires no key. `csgobox:csgo_key3` is only obtainable via the smithing table by upgrading `csgobox:csgo_key2` with a netherite upgrade template.

## Validation rules

- If `grade1` through `grade5` are all empty or all unparseable, the file is skipped with a warning and the box is not registered.
- Negative or zero `random` weights fall back to the default weight for that grade. Weights above 10000 are clamped to 10000.
- Unknown item ids are skipped with a warning; the rest of the grade still loads.
- Item count must be a positive integer; non-positive values are treated as 1.
- The loader tolerates extra unknown top-level keys; they are ignored without warning.

## Troubleshooting

**Box does not appear in the game.**
Run `/csbox list` to see all registered boxes. If yours is missing, check `latest.log` for `Failed to load box JSON file` errors. Common causes: a JSON syntax error, a missing comma, or an item id that does not exist.

**Box appears but no items drop.**
All `grade1..grade5` arrays are empty or every item in them failed to parse. Check that every item id is a real Minecraft item (try it in `/give` first).

**Drop rate does not match expectations.**
Entity drop rates are taken from the per-entity rate if present, else the default `drop` field. The Looting enchantment adds +50% per level (capped at 100%).

**Want to remove a box.**
Delete the corresponding `.json` file and run `/csbox reload` (or restart the server).

**Want to share a box with a friend.**
Copy the `.json` file to their `config/csbox/` directory. Both servers will register the same box at the same `csgobox:` id.