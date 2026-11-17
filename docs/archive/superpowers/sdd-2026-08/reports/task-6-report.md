# Task 6 Report: 底行三件套（区域 11 名称 / 倒计时冒号变暗 / 圆形徽章）

**Status:** DONE
**Commit:** `6395f6f` feat(terminal): region-11 name, dim countdown colons, circular slot badge (branch `task/terminal-t6`, worktree `/Users/shuangyuexingxun/Desktop/.cs2box-wt/t6`)

## Changes

### 1. `common/.../terminal/TerminalPalette.java` (+2)
`COUNT_COLON = 0x8CCFD6DB` added next to `COUNT_EXPIRED` (pure data, no MC imports — CONSTRAINT-001 satisfied, architecture check passed in build).

### 2. `common/.../terminal/TerminalAssetsTest.java` (+1)
ASSETS array gains `{"terminal_badge.png", "72", "72"}`.

### 3. `scripts/gen-terminal-assets.py` (+15)
`make_badge()` added verbatim per brief (72x72 radial beige badge, #f0ece1 → #b3ac9b with t^0.9, inset ring 34≤d≤36 ×0.75), called in `main()` after `make_circle_glow()`.

Script output:
```
$ python3 scripts/gen-terminal-assets.py
.../common/src/main/resources/assets/csgobox/textures/gui/terminal
  exists, skip terminal_round_rect.png (--force to regenerate)
  ... (all pre-existing assets skipped as expected)
  wrote terminal_badge.png
  exists, skip terminal_avatar_wm.png ...
  ... done
$ file .../terminal_badge.png
PNG image data, 72 x 72, 8-bit/color RGBA, non-interlaced
```

### 4. `TerminalBottomRow.java` ×3 (v26_1_2 base → v26_2 / v1_21_1 point-merge)
Per-content edits (baseline bb0e353 WIP state), same net change on all platforms:
- **Region 11**: `csgobox.terminal.name` drawn at `(xpX, midY-4)` scale 1.2F in `TerminalPalette.TEXT`; `dotX = xpX + round(font.width(xpName)*1.2F) + 16` (dots pushed right of the name).
- **Region 9**: single-string `drawSpacedText` replaced with 4-token segmented draw (`text.substring` 0-2/3-5/6-8/9-11 — matches `countdownText`'s `dd:hh:mm:ss` format, verified in TerminalAnims:153-160). Colons drawn with `COUNT_COLON` (alpha 0x8C); `slide`/`color`/`expired` preserved. Dead `cw` local removed.
- **Region 10**: glow blit (`TEX_CIRCLE_GLOW`, 128x128) + 4× `0xFF39444C` border fills deleted; replaced by single `blitTextured(TEX_BADGE, ..., slotW, slotW, 72, 72)`. New constant `TEX_BADGE` (`Identifier.parse("csgobox:gui/terminal/terminal_badge")` on v26_*, `ResourceLocation.fromNamespaceAndPath` on v1_21_1). Swap scale-pop animation (`swapPop` scale + `renderItem2D`) untouched. `TEX_CIRCLE_GLOW` retained (still used by filled dots in region 11).
- v26_2 edited by point-merge (no file overwrite); diff between v26_1_2 and v26_2 after normalization = empty; v1_21_1 differs only in package/Identifier↔ResourceLocation/GuiGraphics↔GuiGraphicsExtractor.

## Verification evidence

| Check | Result |
|---|---|
| `./gradlew :common:test -Pactive_versions=26.1.2` | BUILD SUCCESSFUL (incl. architecture check on compile) |
| `TerminalAssetsTest` (--tests filter) | PASSED — "all terminal textures exist with valid PNG headers and sizes" |
| `:v26_1_2 clean compileJava -Pactive_versions=26.1.2` | BUILD SUCCESSFUL |
| `:v26_2 clean compileJava -Pactive_versions=26.2` | BUILD SUCCESSFUL |
| `:v1_21_1 clean compileJava -Pactive_versions=1.21.1` | BUILD SUCCESSFUL |

Note on v1_21_1: first clean build failed because the isolated worktree's `local-repo/` lacked the TACZ compileOnly dependency (gitignored, ~57MB). Ran `scripts/download-tacz.sh` (slow network ~100KB/s, resumed once, completed) which fetched the jar + wrote pom + extracted simplebedrockmodel; re-run of clean compile passed. This is an environment artifact, not a code issue — CI does this automatically.

## Self-review checklist
- [x] Palette: pure data only, no MC import
- [x] ASSETS row matches generated PNG (72x72)
- [x] Script deterministic, idempotent (skip existing), badge generated
- [x] Countdown tokens match `countdownText` format (4×2 digits, 3 colons)
- [x] Badge blit keeps slot geometry + swap pop animation; glow/border fill fully removed
- [x] Region-11 name offset used for dots
- [x] Mirror discipline: v26_1_2 base first, v26_2 point-merge (no overwrite), v1_21_1 own ResourceLocation/GuiGraphics variants
- [x] Commit file-scoped (7 files, no unrelated changes)
