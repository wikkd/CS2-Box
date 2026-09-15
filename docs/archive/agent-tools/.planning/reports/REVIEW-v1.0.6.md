---
phase: v1.0.6
reviewed: 2026-07-02T00:00:00Z
depth: standard
files_reviewed: 33
files_reviewed_list:
  - v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/LoadError.java
  - v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/TutorialFetcher.java
  - v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/TutorialSources.java
  - v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/event/LoadErrorAnnouncer.java
  - v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/BoxDefaults.java
  - v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/BoxJsonLoader.java
  - v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/command/CsboxCommand.java
  - v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/config/CsboxConfig.java
  - v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/CsgoBox.java
  - v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/box/LoadError.java
  - v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/box/TutorialFetcher.java
  - v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/box/TutorialSources.java
  - v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/event/LoadErrorAnnouncer.java
  - v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/box/BoxDefaults.java
  - v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/box/BoxJsonLoader.java
  - v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/command/CsboxCommand.java
  - v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/config/CsboxConfig.java
  - v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/CsgoBox.java
  - v26_2/src/main/java/com/reclizer/csgobox/v26_2/box/LoadError.java
  - v26_2/src/main/java/com/reclizer/csgobox/v26_2/box/TutorialFetcher.java
  - v26_2/src/main/java/com/reclizer/csgobox/v26_2/box/TutorialSources.java
  - v26_2/src/main/java/com/reclizer/csgobox/v26_2/event/LoadErrorAnnouncer.java
  - v26_2/src/main/java/com/reclizer/csgobox/v26_2/box/BoxDefaults.java
  - v26_2/src/main/java/com/reclizer/csgobox/v26_2/box/BoxJsonLoader.java
  - v26_2/src/main/java/com/reclizer/csgobox/v26_2/command/CsboxCommand.java
  - v26_2/src/main/java/com/reclizer/csgobox/v26_2/config/CsboxConfig.java
  - v26_2/src/main/java/com/reclizer/csgobox/v26_2/CsgoBox.java
  - gradle.properties
  - v1_21_1/build.gradle
  - v26_1_2/build.gradle
  - v26_2/build.gradle
  - CHANGELOG.md
  - README.md
findings:
  critical: 4
  warning: 5
  info: 4
  total: 13
status: issues_found
---

# Phase v1.0.6: Code Review Report

**Reviewed:** 2026-07-02
**Depth:** standard
**Files Reviewed:** 33
**Status:** issues_found

## Summary

v1.0.6 introduces the tutorial system (network download + versioned files + cross-platform trash) and JSON load-error surfacing to players across three platform modules (`v1_21_1`, `v26_1_2`, `v26_2`). The three platform implementations are **byte-identical where they should be** (LoadError, TutorialSources, TutorialFetcher, BoxDefaults, CsgoBox diffs) and **intentionally divergent** only where MC/NeoForge APIs force it (permission check API, `ResourceLocation` → `Identifier` rename, optional PIP renderer event listener). One notable platform-asymmetry regression was found in `BoxJsonLoader.loadAll()`: v26_1_2 and v26_2 track `scannedFiles` / `skipped` counters, but the v1_21_1 implementation does not — this is a regression for the 1.21.1 platform. The tutorial URL fetcher has no URL scheme validation (SSRF risk if a player edits `_tutorial_sources.json`), and `LoadErrorAnnouncer` fires on every login with no once-per-server or once-per-player dedup. Overall the code is well-structured, defensively coded (try/catch around all tutorial flows), and the CHANGELOG/README/STATE.md documents are consistent. Several non-blocking improvements are flagged below.

## Platform Asymmetry Issues

### PA-01: `BoxJsonLoader.loadAll()` log output divergence (1.21.1 vs 26.x) — WARNING

**File:** `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/BoxJsonLoader.java:64-86`
**Issue:** The v1_21_1 implementation logs `"Loaded {} box(es) from {}"` (old format). The v26_1_2 (line 94-96) and v26_2 (line 94-96) implementations log `"Scanned {} JSON file(s) in {}; loaded {}, skipped {}"` (new format with skipped counter). The `skipped`/`scannedFiles` tracking was not backported to 1.21.1.
**Impact:** Players on MC 1.21.1 will see different startup log lines than 26.x users, and operators debugging "why was my box not loaded?" have no visibility into how many files were skipped on 1.21.1.
**Fix:** Copy the v26_1_2 `loadAll()` body (lines 64-96 in `v26_1_2/.../BoxJsonLoader.java`) into the v1_21_1 version, adapting `Identifier` → `ResourceLocation` references (the v1_21_1 file currently has `ResourceLocation`, so no rename needed).

## Critical Issues

### CR-01: TutorialFetcher SSRF — no URL scheme validation

**File:** `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/TutorialFetcher.java:47-67` (and identically in v26_1_2 and v26_2)
**Issue:** The fetcher trusts whatever `baseUrl` the player puts in `_tutorial_sources.json` — no `https://` enforcement, no block on `file://`, `http://localhost`, `http://169.254.169.254`, or other SSRF targets. A malicious or careless entry can exfiltrate files or scan the player's LAN.
**Impact:** Critical in single-player contexts where Minecraft can read from localhost file services, FTP, etc. On a multi-user server, a malicious operator (or co-admin who edits the config) can coerce the server JVM into making arbitrary outbound requests with predictable timing.
**Fix:** In `tryOnce()` (line 47), before issuing the request, validate the scheme:
```java
String scheme = uri.getScheme();
if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
    CsgoBox.LOGGER.warn("Tutorial fetch from {} rejected: scheme '{}' not allowed", url, scheme);
    return null;
}
```
Also reject hosts in `127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, `::1`, `fc00::/7` if running on a public server.

### CR-02: LoadErrorAnnouncer has no per-player dedup — chat spam

**File:** `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/event/LoadErrorAnnouncer.java:27-44` (and identically v26_1_2 / v26_2)
**Issue:** Every time a player logs in while `LAST_LOAD_ERRORS` is non-empty, the announcer sends all error messages (including the yellow header) to that player. There is no per-player "already announced" flag. A player who relogs 10 times to test a broken JSON config will receive 10×N red error lines per login.
**Impact:** Real players on servers with bad config JSON will see a flood of red text on every world join — degrades UX badly, and could cause server log noise / chat-rate-limit issues.
**Fix:** Track announced UUIDs in a static `Set<UUID>` or just announce once per server lifetime using a `boolean announced` field on the class:
```java
private static boolean announced = false;

@SubscribeEvent
public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
    if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
    if (!BoxJsonLoader.hasLoadErrors()) return;
    if (announced) return; // already shown this session
    announced = true;
    // ... rest of method
}
```
Or, if the policy is "show on every join but suppress the full list after the first time", keep only the header line. The CHANGELOG entry on line 29 says "OPs receive messages" but doesn't specify cadence.

### CR-03: v1.21.1 BoxJsonLoader lost `scannedFiles`/`skipped` tracking (regression)

**File:** `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/BoxJsonLoader.java:64-86`
**Issue:** As described in PA-01 above, this is more than a log-message divergence. The v1_21.1 path no longer increments a `skipped` counter when `loadFromFile()` returns `Optional.empty()`. Operators running 1.21.1 cannot distinguish "no JSON files in the directory" from "5 JSON files all skipped due to bad syntax". This is a regression from a feature present in v26_1_2/v26_2 and an asymmetric platform behavior.
**Impact:** If a 1.21.1 player upgrades their mod and their box JSON silently fails to load (due to a schema change), they'll see nothing in the startup log indicating why; OPs running `/csbox errors` will see the LoadError entries but have no way to know which JSON files were scanned in the first place.
**Fix:** Same as PA-01 — backport the v26_1_2 implementation. This is the only critical asymmetry in this release.

### CR-04: TutorialSources baseUrl validated for trailing `/` but not for scheme/host

**File:** `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/TutorialSources.java:89-99` (and v26_1_2/v26_2)
**Issue:** `parseSource()` validates `baseUrl` is non-blank and ends with `/` (line 94), but does NOT validate scheme. A user who copies a wrong example (`ftp://...` or `file:///etc/passwd/`) gets a Source object that the fetcher will then try. The check is incomplete.
**Impact:** Same as CR-01 — pairs with the fetcher's missing scheme check to allow SSRF.
**Fix:** Add scheme check to `parseSource()` before creating the Source:
```java
URI parsed = URI.create(baseUrl);
String scheme = parsed.getScheme();
if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
    CsgoBox.LOGGER.warn("Skipping source '{}': baseUrl scheme '{}' must be http(s)", name, scheme);
    return null;
}
```

## Warnings

### WR-01: TutorialFetcher treats all exceptions identically — no backoff/retry

**File:** `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/TutorialFetcher.java:63-66` (and v26_1_2/v26_2)
**Issue:** A single transient error (DNS hiccup, 502 from CDN) causes the source to be skipped forever. TutorialSources then moves to the next source (if any), and if all fail, no tutorial is written. A second startup a minute later will repeat the same dance.
**Impact:** Players on flaky connections get no tutorial at all instead of getting it on second launch. Not a bug, but a UX downgrade vs "retry next start".
**Fix:** Document explicitly that retry-on-next-start is the intended behavior (it already is — `tutorialFileNames()` only writes files that don't exist), and consider adding a 1-second delay between sources to avoid rate-limiting Gitee's free tier.

### WR-02: `recordLoadError` swallows exceptions when a Throwable is null

**File:** `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/BoxJsonLoader.java:97-102` (and v26_1_2/v26_2)
**Issue:** When `grades.isEmpty()` triggers a `LoadError` (line 167-168), `cause` is passed as `null`. The LoadError record's `toChatMessage()` doesn't print the cause, so the player sees "All items failed to parse (missing mods?)" with no diagnostic info. For other call sites, the cause is recorded but never used.
**Impact:** Players see a less helpful error message; can't tell which specific item parse failed. The cause Throwable is dead code — kept on the record but never displayed.
**Fix:** Either drop the `cause` field from `LoadError` (it's unused) or include `cause.getMessage()` in `toChatMessage()` when non-null. The first option is cleaner — the LoadError is already for display, not debugging.

### WR-03: CsgoBox.MODVERSION can be "unknown" when used before construction

**File:** `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/CsgoBox.java:44` (and v26_1_2/v26_2)
**Issue:** `MODVERSION = "unknown"` is the default, then the constructor sets it from `ModLoadingContext`. But `BoxDefaults.writeTutorialIfMissing()` (called from `loadAll()`) uses `CsgoBox.MODVERSION` to build the tutorial filename. In v1_21_1, `loadAll()` is called from `commonSetup` (line 102-104) — which runs **after** the constructor, so MODVERSION is set correctly. In v26_1_2/v26_2, `loadAll()` is called from `onServerStarting` (line 144-152) — also after the constructor. So the ordering is fine. **However**, if a future refactor moves the `loadAll()` call into an earlier event (or commonSetup in 26.x), MODVERSION will be "unknown" and the tutorial file will be named `_tutorial_vunknown.md`. The defensive `tutorialFileNames()` already handles this (line 110: `String v = CsgoBox.MODVERSION == null ? "unknown" : CsgoBox.MODVERSION;`).
**Impact:** Latent foot-gun — currently safe but fragile. The fact that both call sites run after the constructor should be made explicit in a code comment.
**Fix:** Add a comment at the MODVERSION field declaration (line 44) explaining the ordering invariant: "Set during construction; consumed by BoxDefaults.writeTutorialIfMissing via ServerStartingEvent (v26.x) or FMLCommonSetupEvent.enqueueWork (v1_21_1) — both fire AFTER the constructor runs."

### WR-04: README not updated to mention tutorial system or /csbox errors

**File:** `README.md` (lines 1-99)
**Issue:** The CHANGELOG.md (lines 19-32) describes the tutorial system and JSON-error push at length, but `README.md` only mentions `/csbox list`, `/csbox give`, `/csbox reload` (line 16). The new `/csbox errors` subcommand is undocumented in the README. The tutorial download behavior is also undocumented — a player reading the README won't know that `_tutorial_v1.0.6.md` will appear in `config/csbox/` on first launch.
**Impact:** Users have no way to discover the new `/csbox errors` command or the tutorial auto-download behavior from the README. They'll either find it by accident or via the CHANGELOG.
**Fix:** Add a "v1.0.6 features" section to README listing (1) auto-downloaded tutorials from Gitee, (2) `/csbox errors` OP command, (3) `csgobox.toml` → `advanced.jsonErrorAudience` setting.

### WR-05: TutorialFetcher tutorial content is untrusted — no length cap

**File:** `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/TutorialFetcher.java:55-60` (and v26_1_2/v26_2)
**Issue:** `HttpResponse.BodyHandlers.ofString()` reads the entire response body into memory with no upper bound. A malicious or buggy Gitee mirror (or DNS hijack) could serve a multi-GB response and OOM the server.
**Impact:** DoS vector on dedicated servers. A 1GB tutorial is unlikely from Gitee but trivial from a compromised mirror.
**Fix:** Use `BodyHandlers.ofString(Charset)` with a length check, or limit via `HttpRequest.Builder` + `BodyHandlers.fromLineStream()` with a manual cap. Simplest: check `resp.body().length()` before assigning, cap at ~10MB:
```java
if (resp.body().length() > 10 * 1024 * 1024) {
    CsgoBox.LOGGER.warn("Tutorial from {} exceeded 10MB; refusing", url);
    return null;
}
```

## Info

### IN-01: TutorialSources defaults() returns a hard-coded Gitee URL

**File:** `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/TutorialSources.java:101-107` (and v26_1_2/v26_2)
**Note:** `defaults()` points at the maintainer's personal Gitee repo. This is documented in CHANGELOG but creates a single point of failure: if `gitee.com/hou-xiangling/CS2-Box` is deleted/moved/suspended, every player loses access to tutorials. Consider adding a `github.com` mirror as a fallback source — Gitee has had regional outages.
**Suggested fix:** Add a second default source pointing at `github.com/<upstream>/CS2-Box/raw/main/docs/tutorials/` so a single-host outage doesn't kill tutorials.

### IN-02: TutorialFetcher HttpClient is recreated each `writeTutorialIfMissing` call

**File:** `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/BoxDefaults.java:79` (and v26_1_2/v26_2)
**Note:** `new TutorialFetcher()` is created inside `writeTutorialIfMissing()` (line 79). `loadAll()` calls `writeTutorialIfMissing()` once per server start, so the HttpClient is short-lived anyway. Not a bug, just an observation. If `loadAll()` ever moves to per-tick or hot-reload path, the HttpClient construction will become a hot-spot.
**Suggested fix:** None — current scope is fine.

### IN-03: `LoadError.recordLoadError` uses boxId derived from filename, not from JSON's "id" field

**File:** `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/box/BoxJsonLoader.java:97-102` (and v26_1_2/v26_2)
**Note:** When `loadFromFile()` records an error before parsing the JSON body, `boxId` is `fileName.substring(0, fileName.length() - 5)`. If a JSON file is named `my_lootbox.json` and the user's intent was `csgobox:my_lootbox`, this still works because the JSON body's `csgobox:<id>` is computed from the same substring (line 173). But if the file ever contains a path with underscores vs. hyphens that the user expected to be aliased, the error boxId won't match their mental model. Not a bug; current behavior is consistent.
**Suggested fix:** None.

### IN-04: CHANGELOG.md dates are correct but the "修复" section mentions a fix without a regression report

**File:** `CHANGELOG.md:39-43`
**Note:** Line 41 says "TutorialFetcher HttpClient 默认不跟随 302 重定向" was fixed by adding `.followRedirects(ALWAYS)`. This is documented as a fix, which is good. But the entry doesn't mention that Gitee raw serves 302s via the ADAS gateway — an operator who reads the fix won't know the root cause. A one-liner like "Gitee raw 走 ADAS 网关返回 302" would help.
**Suggested fix:** Optional — consider expanding line 41 with the ADAS gateway context.

---

**Reviewed:** 2026-07-02
**Reviewer:** Claude (gsd-code-reviewer)
**Depth:** standard