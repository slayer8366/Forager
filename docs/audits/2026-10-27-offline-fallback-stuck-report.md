# Trail Map stays on the OpenStreetMap fallback after the connection returns: report

Dispatch `prompts/preserved/2026-10-27-09-offline-fallback-stuck.md` (branch `records-after-173`), RECORD -1738.
Report only: nothing was built, and no device was used. Written 2026-10-27 by a Forager app coder (Opus) for
Rowan Waymark.

**Base.** `origin/pre-main` at `6064fef86`, which is build 4094, the build the S22 check ran
(`docs/audits/2026-10-27-s22-check-v3-8d.md` on `s22-check-v3-8d`, `a5b7318f8`, "Builds" table). Every file:line
below is at `6064fef86` unless stated.

## Short answer

- **The cause is a recorded ruling, not a slip.** The fallback is a one-way flag for the life of the process. The
  KDoc at `ui/map/OwnStreetFallback.kt:21-22` says: "When our Street is tried again: at the next cold start. The
  flag lives for the process, so the map never flips back and forth within a session". Nothing in the code can
  clear the flag. `fellBackState` is set at `:48` and never written again. The process object at `:85` "resets
  only with the process".
- **Why it fires offline at all.** The trigger is a failed style load "online", and "online" means "not drawing
  from downloads" (`useOfflineTiles` false, `fallsBack` at `:74-79`). The network state is never read. So with no
  signal and no cached style, Trail Map's load fails, and the failure counts as the server failing.
- **The cheapest fix:** when the map's existing "no signal" state ends, the fallback is cleared and Trail Map is
  tried again, once, with one log line. That state is the app-wide one at `MainActivity.kt:104`, which ends after
  a validated network has held for 30 s. This needs one new function on `OwnStreetFallback` and one call in
  `noSignalState`. There are no new strings, settings or prompts. It does change the planner's -1513 ruling quoted
  above, so it needs that ruling revised first (see "Decided beyond scope").

## 1. The fallback path

### When it fires

| Step | Where | What |
|---|---|---|
| The style is requested | `MapStyleReload.kt:72-73` | `ownStreet = OWN_STREET_FALLBACK.ownStreet()` is read in composition and is a key of the style effect |
| The request is noted | `MapStyleReload.kt:121` | `state.lastStyleRequest = MapStyleRequest(source, useOfflineTiles)`, just before `setStyle` (`:127`) |
| MapLibre reports the failure | `MapListeners.kt:48-52` | `addOnDidFailLoadingMapListener` logs, then calls `OWN_STREET_FALLBACK.onStyleLoadFailed(state.lastStyleRequest, message)` |
| The fallback decides | `OwnStreetFallback.kt:46-50` | It falls back when the request is Trail Map's own style (`STREET_STYLE_URL`, or `StreetWithTopoOver` at Topographical night) with `useOfflineTiles` false, and it has not already fallen back |
| The flag flips | `OwnStreetFallback.kt:48` | `fellBackState.value = true`. This is Compose state, so the style effect relaunches with `ownStreet = false` and loads OpenStreetMap's raster |
| Other readers | `MapLayersSheet.kt:256`, `MapModeChips.kt:86`, `MapNavigationView.kt:48,61`, `SightingsMapLayout.kt:139` (caption), `NoSignalTrailMap.kt:92` | All of them read the same flag: the caption, the max zoom (19), the navigation zoom, the Layers sheet (which drops the "Official trails" row, as the S22 saw) and the "Map type" chips |

### Whether it is logged

Yes, twice, at warning level:

- `MapListeners.kt:49`: `"MapLibre failed to load the map style: $message"` (tag `SIGHTINGS_MAP_TAG`).
- `OwnStreetFallback.kt:49`: `"Own Street style failed to load ($message); drawing OpenStreetMap's Street until the app restarts."`
  This is tag `OwnStreetFallback` (`:87`), once per process.

The S22 report does not quote either line for card step 1. That the fallback fired there is shown by the caption and
the grey Trail chip, not by the log.

### Why nothing retries Trail Map when the connection returns

1. **The flag can only be set.** `OwnStreetFallback` has no function that clears `fellBackState`
   (`OwnStreetFallback.kt:26-66`, read in full). The only instance is the process-level `OWN_STREET_FALLBACK`
   (`:85`).
2. **This is deliberate.** The KDoc records it as one of the planner's rulings on the verify-first
   (`OwnStreetFallback.kt:11,21-22`). Commit `0ee02f0a2` says the same: "it lasts for the process, so the own Street
   is tried again at the next cold start".
3. **What the owner chose (-1513), word for word:** "Fall back automatically (Recommended)": "If our Street fails to
   load, the app quietly draws today's OpenStreetMap Street instead and logs it; built as a small PR before the
   switch flips." The owner's words say nothing about when Trail Map is tried again. "Until the next cold start" is
   the planner's ruling, not the owner's.
4. **The ruling's own limit.** It was written for a Worker that is down or moved: the "Fall back automatically"
   case with the phone online (`OwnStreetFallback.kt:12-16`). The phone having no signal was not one of its cases.
   `fallsBack` (`:74-79`) tests `useOfflineTiles`, which is the user's "Use offline tiles" choice or the no-signal
   switch to downloads (`NoSignalTrailMap.kt:59`). It never tests the network. A Trail Map user with no signal and
   no downloaded area is not switched to downloads. `drawsFromDownloads` is false for `FORAGER_TRAIL` while Trail
   Map draws (`NoSignalTrailMap.kt:59`), and false anyway with no area. So that user's style request is the online
   one, it fails, and the failure counts as Trail Map failing.
5. **A signal monitor already exists, and the fallback never hears it.** `noSignalState` (`NoSignalTrailMap.kt:106-118`)
   is built once per Activity at `MainActivity.kt:104`. It turns Android's validated-network callback
   (`net/AndroidNetworkSignal.kt:30-56`) into a "no signal" state with a delay in each direction: 5 s to go, 30 s
   to come back (`domain/NetworkSignal.kt:26,33,44-62`). It logs each change. Nothing passes those changes to
   `OwnStreetFallback`.

The S22 saw exactly this (`s22-check-v3-8d.md:134`): the fallback came on offline and stayed after aeroplane mode
was turned off. Trail Map came back "only after a force-stop and reopen online".

## 2. Outside cleared storage

| Case | Falls back and sticks? | Basis |
|---|---|---|
| **Clear storage, then offline** (the S22 case) | Yes | Seen on the S22. Clearing storage removes the ambient cache and every downloaded area, so nothing can serve Trail Map's style |
| **First install, offline** | Yes, by the same path (inferred) | A new install has the same state: no cached style and no downloaded area. The path in section 1 has no branch that tells the two apart. Not run |
| **Trail Map chosen, offline, cache warm** | No | S22 step 3: "Aeroplane mode on, force-stop, reopen. Trail Map loaded from cache" (`s22-check-v3-8d.md:136`) |
| **Style cache expired while offline** | No, by MapLibre's source (inferred, not run) | Explained below the table |
| **Cache evicted** (ambient cache full), then a style load offline | Yes, the same as cold (inferred) | The style is then simply not in the cache. Whether and when the ambient cache evicts the style document was not checked |
| **Topographical at night, zoomed out, offline, cold** | Yes (inferred) | `StreetWithTopoOver` always counts as a fallback case (`OwnStreetFallback.kt:77`), and its Street half is the same Worker URL |
| **Offline with a downloaded v3 area** | No for OpenStreetMap and Topographical users, who are switched to downloads (`NoSignalTrailMap.kt:59`). For a Trail Map user, inferred no | Areas are downloaded against `STREET_STYLE_URL` (`map/StreetStyle.kt:17-23`), so the offline store should answer the online request. Not run |
| **Offline with only v1 or v2 areas, cold cache, Trail Map chosen** | Yes (inferred) | The v3 URL is in neither cache. After the fallback, Amendment 3 switches the user to downloads (`NoSignalTrailMap.kt:32,59`), but when the signal returns they get OpenStreetMap's raster, not Trail Map, until a restart. This is the same stuck flag, seen one step later |
| **Short signal loss (under 5 s) during a style reload** | Yes, if the load fails in that window (inferred) | The flag sets with no "no signal" state ever starting. The fix below also leaves this case unretried; see "Could not determine" |

**Why an expired cache still loads offline.** In maplibre-native at tag `android-v13.5.0`, read on GitHub:

- `platform/default/src/mbgl/storage/main_resource_loader.cpp`: a cached response is passed to the requester when
  `response.isUsable()`, and only then goes to the network.
- `include/mbgl/storage/response.hpp:44`: `isUsable()` is `!mustRevalidate || (expires && *expires > now)`. An
  expired response is therefore usable unless the server sent `must-revalidate`.
- The Worker sends `"Cache-Control": "public, max-age=86400"` for every street style (`server/pmtiles-worker/src/index.ts:430-436`,
  used for v1 to v3 at `:483-492`). That has no `must-revalidate`.

So an expired style should still load offline, and the fallback should not fire. The pinned Android artefact being
built from that tag was not checked. -1515 listed "MapLibre with an expired cached style" as not verified, and this
is still source reading, not a run.

## 3. The cheapest fix

### What changes

1. **`OwnStreetFallback.onSignalReturned()`**, a new function. If the fallback has fired, it clears `fellBackState` and
   logs one line, for example "Connection back: trying Forager Trail Map again." If it has not fired, it does
   nothing and logs nothing. The tile-error counter is left alone.
2. **`noSignalState`** (`NoSignalTrailMap.kt:106-118`) calls `fallback.onSignalReturned()` in its `onEach` when the
   state turns to "signal" (false). This is the same moment and the same hysteresis that already brings OpenStreetMap
   and Topographical users back from downloads, so a 30 s held connection means the same thing everywhere (-1556).
   `fallback` defaults to `OWN_STREET_FALLBACK`. A second overload takes a `NetworkSignal` instead of a `Context`, so
   a test can drive it without Android. The existing `Context` form calls the new one, and `MainActivity.kt:104` is
   unchanged.
3. **What follows on its own.** The flag is Compose state, read at `MapStyleReload.kt:72` as a key of the style
   effect, so clearing it relaunches the effect. `requested` then carries `ownStreet = true` (`BasemapStyles.kt:393-397`).
   `needsStyleReload` sees the change, and `setStyle` loads Trail Map. The caption, the zoom limits, the chips and
   the Layers sheet's "Official trails" row all follow the same flag. A map that is not on screen just reads the
   cleared flag when it is next composed.
4. **If Trail Map still fails** (the Worker really is down): the retry's load fails, and `onStyleLoadFailed` falls
   back again with its existing line. The cost is one reload per signal return, at most once every 30 s of held
   connection. It cannot flap faster than the existing no-signal switch already does.
5. **Text that changes.** The KDoc at `OwnStreetFallback.kt:21-22` and `:84`, and the log line at `:49`: "until the
   app restarts" would no longer be true. Suggested wording: "until the connection returns or the app restarts".

**Size:** about 15 lines in `OwnStreetFallback.kt` (88 lines now) and about 10 in `NoSignalTrailMap.kt` (140 lines
now), plus tests. No file comes near the 400 or 800 line limits. The design is a new function, not a branch threaded
into `onStyleLoadFailed`.

### As a step path (what the user sees)

The case is the one the S22 ran, a phone with no downloaded maps:

```
Phone offline, no maps downloaded, open Maps
  > shows a blank map credited "© OpenStreetMap contributors"; Trail chip grey     (same as today: nothing can load)
Turn the connection back on, wait (the connection must hold about 30 s)
  > shows Forager Trail Map reloading in place: Trail chip active, Trail Map's
    credit, Layers sheet has "Official trails" again                              (today: stays OpenStreetMap until a restart)
Back
  > what Back does on the map today; the reload adds no step and nothing to undo
```

If Trail Map's server is still failing when the connection returns, the map tries once, then goes back to
OpenStreetMap. There is no prompt and nothing for the user to do. Nothing in the path asks the user to notice,
decide or restart anything ("Don't make me think"). OpenStreetMap stays as the fallback whenever Trail Map can't load
("Every failure has a fallback", -519).

### The test: fails before, passes after

The user's action here is a change in the network. Its real entry point is the "no signal" state at
`MainActivity.kt:104`, through `noSignalState`. The test drives that state through the `NetworkSignal` interface
the project owns (`domain/NetworkSignal.kt:17-19`), with a fallback of its own, and uses virtual time as
`NetworkSignalTest` already does (`app/src/test/.../domain/NetworkSignalTest.kt:22`):

1. **`a fallback taken with no signal is retried when the signal holds again`**:
   - validated starts `false`, so the state is "no signal" at once.
   - `onStyleLoadFailed(MapStyleRequest(Uri(STREET_STYLE_URL), false), "…")` is called, and the test asserts
     `ownStreet()` is `false`.
   - validated turns `true`. At 29 s `ownStreet()` is still `false`. At 30 s it is `true`.
   - Exactly one retry line is logged.
   - **Before the fix** this fails at the 30 s assertion with "still fallen back", because nothing clears the flag.
2. **A Compose test** in `NoSignalTrailMapTest`, after the Amendment 3 test at `:80-104`. The pattern is a Trail
   user who has fallen back, with a downloaded area and no signal, drawing from downloads.
   - Today, once the signal returns, `rememberDrawsFromDownloads` is `false` and the user is on OpenStreetMap's
     raster (`:102-103`).
   - After the fix, the same run also asserts `fallback.ownStreet()` is `true`, which is Trail Map. This test needs
     the reset wired through the state that the test provides as `LocalNoSignal`. That depends on where the call
     sits. With the call in `noSignalState`, test 1 is the entry-point test, and this one only checks that every
     reader follows the cleared flag.
3. **A guard, not shown to bite on its own:** a fallback taken while the signal holds the whole time (the Worker
   down) stays in place. This passes before and after the fix by construction. It needs its own revert check, for
   example calling `onSignalReturned` on every emission, to show it bites.
4. **One existing assertion changes:** `OwnStreetFallbackTest.kt:85-88` checks the log line word for word, including
   "until the app restarts". If the wording changes, this expected string is updated with it. This is the test
   following a message the fix deliberately changes, not a weaker check. It is listed so it is not mistaken for
   one.

Revert check for test 1: delete the one `onSignalReturned()` call in `noSignalState`. Check the build log for zero
`e:` lines. Then confirm that only test 1 fails, at its 30 s assertion. Restore from a saved copy, not from git.

## Disclosures

### Confirmed vs inferred

- **Confirmed from source at `6064fef86`:**
  - every file:line in sections 1 and 3;
  - the flag can never be cleared;
  - "online" in `fallsBack` means "not drawing from downloads", not the network;
  - no "no signal" change reaches the fallback;
  - the two log lines and their tags;
  - the Worker's `Cache-Control` for the style;
  - the -1513 decision text, quoted from `RECORD.md` on `records-after-173`.
- **Confirmed from the S22 report** (`a5b7318f8`, line 134): the symptom, and its recovery only by a force-stop.
- **Confirmed from maplibre-native source on GitHub** (tag `android-v13.5.0`): how a cached response is used
  offline, and the `isUsable()` rule.
- **Inferred, not run:**
  - first install offline behaves as Clear storage does;
  - an expired cache does not trigger the fallback;
  - Topographical night, v1/v2-only and cache-eviction cases;
  - that the proposed reset reloads every reader as described. That last one follows from the Compose-state reads,
    but no test or device has run it.

### Could not determine

- Whether the pinned MapLibre Android 13.5.0 artefact is built from the `android-v13.5.0` native source read here.
- Whether MapLibre's ambient cache ever evicts the style document in practice. This decides the "cache evicted"
  row.
- Why the OpenStreetMap raster stayed blank after the connection returned until a tab round trip
  (`s22-check-v3-8d.md:134`). It looks like failed raster tiles not being retried by the live MapView. Not
  investigated. The fix removes this for Trail Map users, because the reload replaces the raster. An OpenStreetMap
  user offline with no downloaded area may see the same blank after reconnecting. That is outside this dispatch and
  is not covered by the fix.
- The short-loss window: a style load that fails during a signal loss shorter than 5 s sets the flag with no "no
  signal" state ever starting, so this fix never retries it. How often that happens is unknown. Resetting on the raw
  validated signal instead would cover it, at the cost of the 30 s guard against flapping (-1556).

### Premises that were wrong

- **The dispatch implies the stuck state is a defect in the path.** It is the stated, recorded behaviour: the
  planner's verify-first ruling at `OwnStreetFallback.kt:21-22`, carried into commit `0ee02f0a2` and -1515 ("for the
  rest of the process"). What is new is the case the ruling did not consider: no signal. The fix revises a ruling.
  It does not repair a slip.
- **"OwnStreetFallback.kt and its callers"**: there is one caller that can change the flag (`MapListeners.kt:51`,
  plus `:57` for tile errors, which only log). Everything else only reads it.
- The verify-first dispatch of 2026-10-21 ("PR A") quoted in the KDoc is not in `prompts/preserved/` on
  `records-after-173` (the 2026-10-21-01 to -08 files were searched). Its ruling is known only from the KDoc and
  from commit `0ee02f0a2`.

### Decided beyond scope

- **Stop-and-ask for the planner and the owner.** The fix replaces the ruling "tried again at the next cold start"
  with "tried again when the connection returns, or at the next cold start". The owner's "Report, then fix
  (Recommended)" reads as accepting a retry. It is still a change to a recorded ruling, so it needs the planner's
  word before the build. Two choices go with it:
  - **(a) When to retry.** Choice 1 is after the existing 30 s held connection (recommended: one rule app-wide, no
    flapping). Choice 2 is on the raw signal, at once (this also covers the short-loss window, but can reload at the
    edge of coverage).
  - **(b) Which failures to retry.** Choice 1 is any fallback (recommended, as above). Choice 2 is only a fallback
    taken while there was no signal, which keeps a Worker outage sticky. Choice 2 races the first network reading
    at a cold start (`stateIn` starts `false`, `NoSignalTrailMap.kt:118`), so it can miss the very S22 case it is
    for. In practice choice 1 already keeps a Worker outage sticky, because the reset needs the signal to drop and
    return first.
- MapLibre source was read on GitHub (the dispatch allows GitHub). No other network was used.
- A sparse worktree (`docs/audits` only) at `/mnt/work/forager-wt/offline-fallback-stuck-report`, cut from
  `origin/pre-main` at `6064fef86`.
- No row was added to `docs/audits/README.md`. It is a serialization point, left to Rowan, as recent reports have
  done. The RECORD is untouched.
