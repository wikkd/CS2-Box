# CS2 Box 1.0.4 Update Notes

Date: 2026-06-19

## Summary

Version 1.0.4 focuses on server-authoritative opening behavior, safer client networking, animation correctness, JSON documentation, and crash fixes found during manual testing.

The expected build artifact is:

```text
build/libs/csgobox-1.0.4.jar
```

## User-Facing Changes

- Opening results are now decided by the server and sent to the client together with the animation item strip.
- The animation now scrolls through a meaningful distance before slowing down and stopping on the winning item.
- Pressing ESC during the animation no longer causes the next opening attempt to wait forever.
- Empty-box warning text is drawn above the 3D model so it remains readable.
- The default auto-generated JSON now includes an English `_tutorial` section.

## JSON Tutorial

Minecraft JSON files do not support real comments. The generated default config uses a `_tutorial` object instead.

The loader ignores `_tutorial`, so it is safe to keep it in a config file.

The tutorial explains:

- How file names map to box ids.
- What `name`, `key`, `drop`, `random`, and `entity` mean.
- The rarity order from `grade1` to `grade5`.
- How to write item objects with `id` and optional `count`.
- How to use Minecraft 1.21.1 `components`.
- Why legacy `tag` strings are still accepted.
- How to copy the default file to create a new box.

Important behavior:

- Existing JSON files are not overwritten.
- The tutorial appears only when `config/csbox` has no `.json` files and the mod creates `weapon_supply_box.json`.

## Networking Changes

- `PacketBoxOpenResult` now includes:
  - Final item.
  - Final grade.
  - Winning animation index.
  - Server seed.
  - Client request id.
  - Server-generated animation items.
  - Server-generated animation grades.
- Preview sync and opening result packets use request id matching.
- Pending client packet queues are bounded to avoid unbounded growth.
- Invalid or rejected open requests return an empty matching result instead of leaving the client waiting.

## Animation Changes

- Winning items are placed in a late animation window instead of anywhere in the 50-item strip.
- This prevents the animation from stopping after only a small movement when the winning item would otherwise be index 0 or near the start.
- Frame interpolation now uses render partial ticks directly.
- Server cooldown is a short anti-double-click guard rather than the full animation duration.

## Crash Fixes

- Fixed a wrong-thread crash caused by opening a client GUI from an integrated server event path.
- Fixed a null font crash in custom text rendering.
- Fixed empty result and rejected request paths so the client screen closes safely.

## Config Compatibility

Supported item formats:

```json
{
  "id": "minecraft:diamond_sword",
  "count": 1
}
```

```json
{
  "id": "minecraft:diamond_sword",
  "count": 1,
  "components": {
    "minecraft:custom_name": "{\"text\":\"Example Sword\",\"italic\":false}"
  }
}
```

Legacy JSON-string item entries are still accepted for older files:

```json
"{\"id\":\"minecraft:diamond_sword\",\"count\":1}"
```

## Manual Test Checklist

- Start the game with an empty `config/csbox` folder and confirm `weapon_supply_box.json` is generated with `_tutorial`.
- Open a basic configured box and confirm the item shown at the end matches the reward received.
- Press ESC during the opening animation, then open another box.
- Test a box that uses `minecraft:air` as the key.
- Test a box that requires a different key.
- Test a box with legacy JSON-string item entries.
- Test empty or sparse grade lists.
- Test invalid weights and odd entity drop-rate arrays and check that the game does not crash.
- Confirm empty-box warning text is visible and not covered by the 3D model.

## Deployment Notes

When deploying to a manual test instance, remove older `csgobox-*.jar` files first. Loading multiple versions at once can produce misleading results.

Recommended deployment target:

```text
<minecraft instance>/mods/csgobox-1.0.4.jar
```
