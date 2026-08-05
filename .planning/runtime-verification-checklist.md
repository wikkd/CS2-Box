# Runtime Verification Checklist — v26_1_2 3D Animation & Rendering Audit

This checklist records the visual verification items for the M.1–M.3 fixes
applied after Stage 4 (commits I.1–L) and the purple-black items fix round
(`v26_1_2/src/main/resources/assets/csgobox/items/*.json` + `RenderPipelines`
`blit` migration). These items cannot be verified by code reading alone —
they need the client actually launched and the GUI screens inspected.

## Context

Stage 4A added 3 commits:

- **M.1** (`fix`): `CsboxProgressScreen.lastRenderWidth` now stores the raw
  tick value (`renderWidthAdd`) instead of the resize-scaled
  (`renderWidthNow`) value. The resize-scale (`*= this.width / startWidth`)
  is now applied ONLY inside the item-render loop.
- **M.2** (`chore`): `CsboxScreen.mouseDragged` detection uses percentage
  bounds (`width * 26 / 100` square region matching renderBg's `FrameWidth`)
  instead of hardcoded `200 × 176`.
- **M.3** (`chore`): Documentation-only — explains why the warning banner's
  `nextStratum()` pair is defensive but does no harm.

These are small, behaviorally equivalent in the happy-path. The items below
are the corner cases that can only be confirmed by running the client.

## Pre-launch

```bash
cd /Users/shuangyuexingxun/Desktop/CS2-Box
cp gradle.properties gradle.properties.bak
sed -i.bak 's/^active_versions=1.21.1$/active_versions=26.1.2/' gradle.properties
ls v26_1_2/build/neoForm/neoFormJoined26.1.2-1/raw.jar
ls v26_1_2/build/resources/main/assets/csgobox/textures/screens/csgo_background.png
```

## Launch

```bash
./gradlew :v26_1_2:runClient --no-daemon
```

## Visual Checks

### RV-1: Slot item icon scale
- [ ] Open `CsboxScreen` by holding a `csgo_box` and triggering its GUI.
- [ ] Observe the 2-row × 10-column item strip below the "ITEMS" label.
- [ ] **Expected**: Items appear at the scaled-up size (frame is ~26% of
  screen width, items fill ~60% of the frame → roughly 16% of screen width
  per icon).
- [ ] **If FAIL**: items render at fixed 16px regardless of slot width. The
  26.1.2 deferred item pipeline (`GuiItemRenderState`) is not honoring the
  2D `pose().scale(s, s)`. **Action**: requires writing a custom
  `PictureInPictureRenderer<MyIconRenderState>` per Stage 4 Deferred #1.

### RV-2: Scrolling reel item scale
- [ ] Click "Open" on `CsboxScreen`. Wait for `CsboxProgressScreen`.
- [ ] After the 5-tick wait, items scroll into view across the center gold
  line.
- [ ] **Expected**: Items in the scrolling strip match the size observed in
  RV-1 (proportional to the 18% × 25% frame dimensions).
- [ ] **If FAIL**: same root cause as RV-1.

### RV-3: Window resize during animation
- [ ] On `CsboxProgressScreen`, mid-animation, drag the game window to a
  different size.
- [ ] **Expected**: Item positions rescale smoothly without a visible
  single-frame snap or jump. Validate the cubic ease-out continues toward
  the winning item.
- [ ] **If FAIL**: visible jump or one-frame flicker at the resize
  boundary. M.1 fix didn't fully address it; investigate further (perhaps
  the resize multiplier needs to be applied symmetrically on both sides of
  the lerp, or the `init()` method needs to update `startWidth` more
  aggressively).

### RV-4: csgo_background.png overlay transparency
- [ ] On `CsboxProgressScreen`, after the items are visible, observe whether
  the scrolling items show through the center of the
  `csgo_background.png` overlay.
- [ ] **Expected**: Items are clearly visible behind/through a centered
  transparent region in the overlay texture. The overlay acts as a frame.
- [ ] **If FAIL**: Overlay covers items fully (texture is opaque). The
  render order in `CsboxProgressScreen.renderBg` (lines 102–132) needs to
  be inverted: blit must run BEFORE the item loop so items layer on top.

## Cleanup

```bash
mv gradle.properties.bak gradle.properties
```

## Non-Issues Confirmed in Audit

The following were inspected and found to be correct (no visual check needed
unless behavior diverges from 1.21.1):

| Subsystem | Status | Reason |
|---|---|---|
| `RenderFontTool.drawString` 2D-matrix text scaling | OK | Deferred pipeline honors 2D pose for text. Verified visually in earlier rounds. |
| Matrix push/pop balance across all 3 screens | OK | Code-audited. |
| `guiGraphics.item(LivingEntity, ItemStack, int, int, int)` 5-arg call | OK | Correct 26.1.2 overload. Compiles. |
| `PacketSyncBoxItems` requestId + boxId matching | OK | Code-audited. |
| `OverlayColor` / `RenderFontTool` interaction | OK | Orthogonal utilities; no conflict. |
| `csgo_background.png` render order (after items) | INTENDED | Identical to 1.21.1; texture has transparent center by design. |
| 3D mouse rotation as no-op | INTENDED | Stage 4 architecture decision. Vanilla 26.1.2 precedent. |
| Warning banner `nextStratum()` defensive pairing | INTENDED | Defense-in-depth for future renderBg additions. M.3 documents the invariant. |

---

## Config Loading Pitfalls (Stage 4B)

### Symptom

`weapon_supply_box.json` (and any other `config/csbox/*.json`) silently fails
to load. `latest.log` shows:

```
WARN: Failed to parse item JSON: {"id":"minecraft:netherite_sword","count":1}
WARN: Failed to parse item JSON: {"id":"minecraft:netherite_axe","count":1}
...
WARN: Skipping box 'weapon_supply_box': all items failed to parse (missing mods?)
INFO: Loaded 0 box(es) from /.../csbox
```

### Root cause

`BoxJsonLoader.parseItem` originally used `new ItemStack(item, count)`. This
26.1.2 constructor routes through `Item.builtInRegistryHolder()` →
`Holder.Reference.components()`. The Reference's `components()` method:

```java
public DataComponentMap components() {
    return Objects.requireNonNull(this.components, "Components not bound yet");
}
```

throws NPE when `bindComponents(...)` was not yet applied during the
`FMLCommonSetupEvent` window. The exception was swallowed by a malformed
SLF4J call:

```java
// BEFORE — SLF4J drops the second extra arg when the format has only one {}
LOGGER.warn("Failed to parse item JSON: {}", elem, e.getMessage());
```

so the operator never saw the cause.

### Fix (committed)

1. **B.1**: change to `LOGGER.warn("...{}", elem, e)` — SLF4J detects the
   `Throwable` final argument and logs the full stack trace.
2. **C.1 (replaces B.2)**: move `BoxJsonLoader.loadAll()` invocation from
   `FMLCommonSetupEvent` to `ServerStartingEvent` in `CsgoBox.java`.
   Reason: at FMLCommonSetupEvent the intrusive-holder `components` field
   hasn't been bound yet (`bindComponents` runs during datapack reload,
   well after FMLCommonSetupEvent). Deferring lets `new ItemStack(item, count)`
   find a fully-initialised Holder.Reference, so the B.2 `Holder.Direct`
   workaround is no longer needed AND the resulting ItemStacks carry a
   registry ResourceKey that survives later `holderByNameCodec` serialization
   (e.g. into the player_data attachment).
3. **C.2**: revert B.2's `Holder.Direct` workaround; use
   `new ItemStack(item, count)` directly. The deferred load timing makes
   this safe.
4. **B.3**: `loadAll` and `writeDefaultIfEmpty` now log per-file outcome
   (scanned / loaded / skipped counts + file names) so a future silent
   failure has visible breadcrumbs.

### Verification

```bash
./gradlew :v26_1_2:runClient --no-daemon &
grep -E "Loaded [0-9]+ box|Loaded box from JSON|Skipping default|Skipping box" \
    v26_1_2/runs/client/logs/latest.log
```

Expected post-fix:
- `INFO Skipping default box write: <dir> already contains 1 JSON file(s) (weapon_supply_box.json)`
- `INFO Loaded box from JSON: weapon_supply_box.json -> csgobox:weapon_supply_box`
- `INFO Scanned 1 JSON file(s) in <dir>; loaded 1, skipped 0`

### Notes for future contributors

1. **There are three `runs/client/config/csbox/` directories** on disk (one per
   module + the repo root). `:v26_1_2:runClient` reads ONLY its own
   `v26_1_2/runs/client/config/csbox/`. Editing the wrong dir silently does
   nothing.
2. **`loadDefaultBoxes = true`** must be set in
   `v26_1_2/runs/client/config/csgobox.toml` for the loader to even run.
3. The same root cause likely affects `v1_21_1` eventually when that target
   upgrades to 26.1.2 item-binding lifecycle. Mirror the fix there at that time.

---

## Achievement System Pitfalls (Stage 4C)

Two latent issues were exposed once the box config loader worked correctly.

### Issue 1: Background texture path doubled

`latest.log`:
```
WARN: Missing resource csgobox:textures/textures/gui/advancements/backgrounds/background_root.png.png
```

`DisplayInfo.background` in 26.1.2 wraps the value through
`ClientAsset.ResourceTexture`:

```java
public ResourceTexture(Identifier texture) {
    this(texture, texture.withPath(path -> "textures/" + path + ".png"));
}
```

so the JSON no longer needs the `textures/` prefix or `.png` suffix. The
1.21.1 format `csgobox:textures/gui/advancements/backgrounds/background_root.png`
gets transformed to `csgobox:textures/textures/gui/.../background_root.png.png`
which doesn't exist.

1.21.1's `DisplayInfo` accepts the raw `ResourceLocation` — full path
expected. 26.1.2 expects the asset-id style (no prefix/suffix). The two
formats are **incompatible**, so the file needs to be split per version.

**Fix**: `v26_1_2/src/main/resources/data/csgobox/advancement/root.json`
overrides the common one with the shortened form. `v26_1_2/build.gradle`
already orders `v26_1_2/src/main/resources` BEFORE `common/...resources`
and uses `duplicatesStrategy = EXCLUDE`, so this override automatically
wins.

```json
"background": "csgobox:gui/advancements/backgrounds/background_root"
```

(26.1.2 will prepend `textures/` and append `.png` automatically.)

### Issue 2: Player data attachment can't persist reward ItemStacks

`latest.log`:
```
ERROR: .neoforge:attachments.csgobox:player_data: Failed to encode value 'CsboxPlayerData[...,item=1 minecraft:stone_shovel, grade=1]' to field 'data': Unregistered holder in ResourceKey[minecraft:root / minecraft:item]: Direct{minecraft:stone_shovel}
```

The B.2 `Holder.Direct` workaround produced ItemStacks whose Holder had no
registered ResourceKey. When the server later serialized the player's
opened-box record (which carries the reward ItemStack), `Item.CODEC =
holderByNameCodec()` rejected `Direct{...}` because it has no name.

**Real root cause**: at FMLCommonSetupEvent, vanilla items' intrusive
Holder.Reference fields have `components == null`
(`DataComponentInitializers.run()` happens during datapack reload, much
later). `new ItemStack(item, count)` calls
`Item.builtInRegistryHolder().components()` which throws NPE for
`Objects.requireNonNull(this.components, "Components not bound yet")`.

**Fix (C.1 + C.2)**:
- Move `loadAll()` invocation from `FMLCommonSetupEvent` to
  `ServerStartingEvent`, where the registry is fully frozen and all
  `bindComponents` runs have completed.
- Revert the `Holder.Direct` workaround. Use `new ItemStack(item, count)`
  directly; the registry-backed Holder.Reference now serializes correctly.

### Verification

```bash
./gradlew :v26_1_2:runClient --no-daemon &
sleep 30 && grep -E "Loaded [0-9]+ box|Unregistered holder|Failed to encode|Skipping default|Missing resource.*textures" \
    v26_1_2/runs/client/logs/latest.log
```

Expected post-fix:
- `INFO Skipping default box write: <dir> already contains 1 JSON file(s) (weapon_supply_box.json)`
- `INFO Loaded box from JSON: weapon_supply_box.json -> csgobox:weapon_supply_box`
- `INFO Scanned 1 JSON file(s) in <dir>; loaded 1, skipped 0`
- `INFO CS2 Box server starting, registered box definitions`
- `INFO CS2 Box server started with <n> box definitions`
- **No** `Unregistered holder ... Direct{...}` errors after opening a box
- **No** `Missing resource csgobox:textures/textures/gui/...` warnings
- Open a box to trigger `OpenedBoxTrigger`; the achievements page should
  now show the CS:GO Box tab WITH the background tile and the "first box"
  entry marked completed.

