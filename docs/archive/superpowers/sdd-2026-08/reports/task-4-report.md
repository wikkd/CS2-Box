# Task 4 Report: 聊天区滚轮滚动 (TerminalChatRegion + TerminalScreen ×3)

**Status:** DONE

## Changes

### TerminalChatRegion.java (×3: v1_21_1 / v26_1_2 / v26_2)
- Added fields `private int scrollOffset; private int maxScroll;` in the constant block area (after `BUBBLE_SCALE`)
- Added public `scrolled(double scrollY)` — `scrollOffset += Math.round(scrollY * 20)` (scrollY>0 = 上滚/看更早)
- Replaced the "从底向上遇界 break" render loop with the brief's two-phase loop (verbatim): measure `totalH` over the ≤64-entry window → compute `maxScroll` → `pinned` (at bottom) auto-follows, else clamp → draw loop with `bottom = y1 - 4 - scrollOffset`
- Geometry unchanged at offset 0: old `bottom=y1-4`, break `bottom-h < bodyTop+2`; new `viewportH = y1-bodyTop-4`, same draw predicate

### TerminalScreen.java (×3)
- `extractRenderState` (v26_*) / `render` (v1_21_1): chat panel locals `lx0..ly1` now also stored to fields `chatX0,chatY0,chatX1,chatY1` (declared next to `closeX..` fields)
- New `mouseScrolled(double,double,double,double)` override, byte-identical across platforms: if pointer in chat rect → `chatRegion.scrolled(scrollY); return true;` else `super.mouseScrolled(...)`. Signature verified against pre-existing precedents: v26_1_2 CsboxScreen:366, v26_2 CsboxScreen:321, v1_21_1 CsboxScreen:334
- No new imports needed on any platform

## Compile evidence (all in worktree /Users/shuangyuexingxun/Desktop/.cs2box-wt/t4)
- `./gradlew :v26_1_2:clean :v26_1_2:compileJava -Pactive_versions=26.1.2` — BUILD SUCCESS (only pre-existing deprecation notes)
- `./gradlew :v26_2:clean :v26_2:compileJava -Pactive_versions=26.2` — BUILD SUCCESS
- `./gradlew :v1_21_1:clean :v1_21_1:compileJava -Pactive_versions=1.21.1` — BUILD SUCCESS (after populating local-repo TACZ; see concerns)
- Re-verification: all three `:x:compileJava` UP-TO-DATE on subsequent invocations
- `scripts/check-animops-drift.sh` — OK v1_21_1 / v26_1_2 / v26_2 (no AnimRenderOps changes)
- `./gradlew :common:test` — passed (no failures)

## Self-review
- Auto-follow semantics: at open, scrollOffset=maxScroll=0 → pinned=true → new entries keep view at bottom; once user scrolls up, pinned=false and offset clamps to [0,maxScroll] without snapping back while reading
- Direction consistency with existing precedent: CsboxScreen treats scrollY>0 as "previous/earlier"
- Diff parity: v26_1_2 ↔ v26_2 changes identical modulo platform (verified by sed-diff of git diffs); v1_21_1 has same 3 hunks (Screen API differs but mouseScrolled signature is identical)
- Mirror discipline: targeted edits only; v26_2 imports (HudVisibility) untouched
- Empty history safe: totalH=0 → maxScroll=0 → bottom=y1-4, loop no-ops

## Concerns
- TACZ compileOnly jar (~57MB) absent from worktree local-repo (it is gitignored in this repo, so this is expected): `scripts/download-tacz.sh` download from GitHub timed out (>5min, ~10MB partial); resolved by copying the already-downloaded files from the main workspace `/Users/shuangyuexingxun/Desktop/CS2-Box/local-repo/` (com/tacz + com/github/mcmodderanchor/simplebedrockmodel). Worktree local-repo is gitignored, so no commit impact. A fresh worktree without network luck may hit the same — CI/script handles it.
- No runtime/GameTest verification possible here; wheel UX (20px per notch, scrollbar-less) follows brief verbatim — visual QA belongs to the milestone runtime checklist.

---

## Critical review fix (commit b8111ce): scroll direction sign inversion

**Finding:** original brief algorithm had inverted signs — `bottom = y1 - 4 - scrollOffset` moved the newest window up and shrunk it (bottom edge blank, earlier entries never enter viewport); `pinned = scrollOffset >= maxScroll` was true only in the "window shifted up" state, so pinned grew offset with maxScroll and the visible window shrank to empty.

**Fix (3× TerminalChatRegion.render()):**
- `pinned = scrollOffset <= 0` (true bottom = offset 0) → pinned snaps offset to 0
- `bottom = y1 - 4 + scrollOffset` → content shifts down; earlier entries enter from the top
- New skip branch: fully-scrolled-out newest entries (`bottom - h >= y1 - 4`) are skipped (not drawn) but their height is still consumed
- `scrolled()` and TerminalScreen forwarding unchanged (scrollY>0 → offset↑ → 看更早, self-consistent)

**Semantics verified:** offset=0 reproduces old geometry exactly (pin → latest stays bottom); offset>0 keeps user's reading position on new messages (pinned false); negative offset from over-scroll snaps to 0; maxScroll = totalH - viewportH clamps so the earliest entry top reaches bodyTop+2 at max scroll.

**Compile evidence (worktree, all clean):**
- `:v26_1_2:clean :v26_1_2:compileJava -Pactive_versions=26.1.2` — SUCCESS (only pre-existing deprecation notes)
- `:v26_2:clean :v26_2:compileJava -Pactive_versions=26.2` — SUCCESS
- `:v1_21_1:clean :v1_21_1:compileJava -Pactive_versions=1.21.1` — SUCCESS (verified via follow-up `compileJava` UP-TO-DATE run; the piped `EXIT=1` was grep's empty-filter exit, not gradle)
- Content parity check: the three platform diffs are content-identical (verified by diffing filtered +/- lines with platform names normalized)

**Commit:** `b8111ce fix(terminal): correct chat scroll direction (content shifts down, pin at offset 0)` — exactly 3 paths (3× TerminalChatRegion.java)
