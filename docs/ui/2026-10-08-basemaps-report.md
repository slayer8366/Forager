# Basemaps: Satellite removed, the basemap remembered across restarts

Dispatch 2026-09-28-708 (RECORD intent -708; preserved at `prompts/preserved/2026-10-08-01.md`). Branch `basemaps`, cut from
`origin/main` at `d223728b` (fetched before cutting; `origin/main` was still `d223728b` when this report was written). No PR.

**Status: written, not run.** The dispatch holds Gradle for the planner's go. Nothing on this branch has been compiled, no test
has run, and no revert check has been done. Every claim below about behaviour is what the code is written to do, not something
observed.

Paths are relative to `app/src/main/java/com/zynergylabs/forager/app/` unless they start with `app/` or `docs/`.

**Revisit:** Satellite is to be revisited once the forecast's habitat layers are on the map (the owner's "revisit later"). The
same line is in `ui/map/Basemap.kt`'s class doc and `ui/map/MapMode.kt`'s.

## Premises checked

| Premise in the dispatch | Found |
|---|---|
| `ui/map/Basemap.kt` `USGS_IMAGERY_ONLY`, its style, night exemption, attribution, Layers sheet and picker entries | Confirmed. Style and exemption in `ui/map/BasemapStyles.kt` (`basemapTakesNightPaint`, `effectiveNight`); the sheet (`ui/map/MapLayersSheet.kt`) and the picker (`ui/map/MapChrome.kt`, `MapModePicker`) both iterate `MapMode.entries`, so removing `MapMode.SATELLITE` removes both chips. |
| `domain/MapLayerPreferencesRepository.kt:27` says the basemap "stays session-only" | Confirmed, lines 27-28. |
| "Anyone with Satellite stored (the live map, an entry map's seeded basemap, an offline region's style if any)" | **Wrong: no basemap name was stored anywhere.** The live map's mode was `remember { mutableStateOf(MapMode.DEFAULT) }` in `ui/availability/AvailabilityScreen.kt` (reset every launch); the entry map's is `remember(entry.id) { mutableStateOf(initialMapMode) }` seeded from the live map, never stored (`ui/log/CartographyEntryReportScreen.kt:325`); `OfflineRegionEntity` has no style or basemap column (`data/local/OfflineRegionEntity.kt:30-37`), downloads use the one offline vector style, and the offline picker pins `Basemap.OPEN_TOPO_MAP` (`ui/availability/AvailabilityOfflineMapsUi.kt:178`); the journal backup is Room tables and photos (`data/backup/`, no preference or basemap read), and `android:allowBackup="false"` (`AndroidManifest.xml:134`). So no phone can hold Satellite today. The unknown-key path below is still built and tested (by seeding the key), because the new store is the first place a removed name could ever sit. |
| Stops: a stored basemap name in Room or a backup format needing a migration; any change to offline region downloads | Neither arises: nothing in Room or the backup holds a basemap, and the offline download code is untouched. |

## What was built

1. **Satellite removed.** `Basemap.USGS_IMAGERY_ONLY` and `MapMode.SATELLITE` deleted, with: `basemapTakesNightPaint` and
   `effectiveNight` (their only purpose was Satellite staying day; every basemap now takes the night paint and the requested
   basemap night is the toggle), `BasemapCoverage` and `Basemap.coverage` (see "Decided beyond scope"), and the Satellite
   asides in comments that described current behaviour (`SightingsMap.kt`, `MapSlot.kt`, `NavigationView.kt`,
   `theme/MapPalette.kt`, `MapLayersSheet.kt`, `AvailabilitySettingsUi.kt`). Comments that record history (the entry map's
   state leak, the owner's chip-centring quote) are left as they were. The night handover from night topo to Street is not
   touched: `styleJsonFor`'s Topographical night branch lost only a condition that was always true for Topographical.
2. **Basemap persisted** in DataStore, `map_preferences`, key `map.basemap`, beside the layer keys:
   - `domain/BasemapPreferenceRepository.kt` (new): `getBasemapKey(): Result<String?>`, `setBasemapKey(key)`. A sibling of
     `MapLayerPreferencesRepository` rather than two more methods on it, so its fakes do not grow. Plain strings, so the domain
     names no map type.
   - `DataStoreMapPreferencesRepository` implements it on the same instance (DataStore refuses a second one on the file);
     `AppContainer.basemapPreferenceRepository` hands that instance out; `MainActivity` passes it and wires
     `onMapModeSelected`.
   - `MapMode.storageKey` (`"street"`, `"topographic"`) is what is stored, not the enum name; `MapMode.forStoredKey` reads it,
     `null` for anything else. `MapMode.REPLACEMENT_FOR_UNKNOWN` is Topographical (the owner: "Topo (Recommended)").
   - `AvailabilityViewModel.loadBasemapPreference` at start: nothing stored opens on Topographical (as before); a stored
     unknown key (Satellite's, or anything else) opens on Topographical, logs one line, and stores Topographical so the line does
     not repeat; a failed read logs and opens on Topographical without writing. Every outcome sets `AvailabilityUiState.mapMode`,
     so it cannot hold the map blank. A pick made before the read lands wins. `onMapModeSelected` shows the pick and stores it; a
     failed write is logged.
3. **No first frame on the default.** `AvailabilityUiState.mapMode` is `null` until the read is in, and `AvailabilityScreen`
   now hands the map `nightModeLoaded = nightModeMapsLoaded && mapMode is known`. `SightingsMap` loads no style while that is
   false (`requestedMapStyle` returns `null`, unchanged), so a phone that left Street never gets a Topographical style first.
   The field keeps its name; `MapRenderMode.nightModeLoaded`'s doc says it now waits for both.
4. **Docs.** `MapLayerPreferencesRepository`'s comment and the journal-redesign note (`docs/plans/journal-redesign.md`, a
   dated superseding paragraph after the L0b sentence, which stays) quote the owner. `Basemap.kt` and `MapMode.kt` record the
   removal, the revisit, and the persistence.

## Decided beyond scope (for the planner to confirm or reverse)

**Planner's ruling, 2026-10-08: all five accepted as built** (coverage removed; USGS script kept as the revisit's record; `chosenMapMode` stays with its reason in the comment; the gate's field name stays with its comment updated; an unknown key writes Topographical back and a failed read writes nothing). No code changed for it: each was already in that state.

1. **`BasemapCoverage` and `Basemap.coverage` removed.** No production code ever read them (only `BasemapTest` and
   `MapModeTest`); `UNITED_STATES_ONLY` existed for USGS, and with it gone every value would be `WORLDWIDE` with a `null` note.
   I read that as code whose only purpose was Satellite. Reversible from git if the revisit wants it back.
2. **`scripts/verify-usgs-basemap.sh` kept.** It only checks USGS services, so it is Satellite-only by purpose, but it is a
   script rather than app code and `Basemap.kt` cites it as the record for whoever revisits Satellite. Say if it should go.
3. **The screen keeps a local `chosenMapMode` beside `uiState.mapMode`.** The map draws `chosenMapMode ?: uiState.mapMode ?:
   DEFAULT`. In the app the two agree from the first pick on. It is kept because the many test hosts that drive
   `AvailabilityScreen` without a ViewModel (`MapChromeTestScreen`, the settings-panel and icon-stack hosts) would otherwise
   get a picker that changes nothing; this keeps them as they were rather than wiring a callback into each. The alternative is
   one source of truth (the ViewModel) and wiring every host; I chose the smaller change.
4. **The gate reuses `nightModeLoaded`** rather than renaming it to something like "style inputs loaded" across
   `SightingsMap`, `requestedMapStyle`, `MapSlot` and their tests. Named as churn in its doc comment.
5. **Unknown key: write Topographical back; failed read: write nothing.** The first stops the log line firing every launch;
   the second avoids overwriting a value that may be fine.
6. **`effectiveNight` deleted rather than reduced to `nightMode`.** `AppliedMapStyle.night` is now the toggle directly.

## Tests

New:
- `app/src/test/.../ui/availability/AvailabilityScreenBasemapPersistenceTest.kt` (Robolectric, real screen, real ViewModel,
  real `map_preferences` file, real touches): the Layers sheet offers only Street and Topographical; a stored `"satellite"`
  opens on Topographical (every render mode released to the map is Topographical, the Layers row says "Topographical"), the
  log line is the expected one, and the file then holds `"topographic"` read with no repository in between; Street picked in
  the sheet survives a restart (the first repository released, a fresh repository and ViewModel over the same file), with the
  second launch's read held open so the test sees that no render mode is released before it lands, then that every released
  one is Street.
- `app/src/test/.../ui/availability/AvailabilityViewModelBasemapTest.kt` (plain JVM): nothing stored; stored Street; stored
  Satellite (logged, `"topographic"` written); failed read (logged, nothing written, gate released); a pick stored under its
  key; a pick before the read lands is not overwritten; a failed write logged and the pick holds.
- `MapModeTest`: the modes are Street and Topographical; each mode's stored key, pinned literally, reads back; unknown keys
  (`"satellite"`, `"SATELLITE"`, `""`) read as none and the replacement is Topographical.

Fixture change: `MapLayersUiFixtures.kt` gains `basemapPreferences` on `mapLayersViewModel` (default: nothing stored, which is
the old behaviour), wires `onMapModeSelected` in `MapLayersTestScreen`, and adds `InMemoryBasemapPreference`.

Night handover from night topo to Street: covered by the unchanged tests in `TopoNightStreetSwitchTest` ("night topo is the
Street layer under the topo layer, overlapping 9_5 to 9_7, V1 on both", "which layers the style draws on each side of the
crossfade", "Street's night style JSON is byte-identical to before", "day style JSON is byte-identical to before for every
basemap", the caption tests). Byte-identical pins mean the edit to `styleJsonFor` cannot have moved those documents unnoticed.

### Every changed assertion, with its reason

| File | Was | Now | Reason |
|---|---|---|---|
| `ui/map/MapModeTest.kt` | `assertEquals(USGS_IMAGERY_ONLY, SATELLITE.basemap)` | removed | Satellite removed. |
| `ui/map/MapModeTest.kt` | "only Satellite resolves to a USGS basemap", "only Satellite is US-only coverage" | replaced by "the modes are Street and Topographical, in that order" (+ the two key tests, new) | Subject removed; what remains to pin is the set offered. |
| `ui/map/BasemapTest.kt` | four coverage tests: one basemap works outside the US; OSM Standard unrestricted; a limited basemap carries a note; USGS iff US-only | replaced by "no basemap is a US-only USGS service" (no `USGS` credit, no `nationalmap.gov` template) | `BasemapCoverage` removed (beyond scope, item 1). |
| `ui/map/BasemapNightStyleTest.kt` | "Satellite's night style JSON equals its day style JSON"; "only Satellite opts out of the night paint" | replaced by "every basemap's own layer takes the V1 paint at night" | The opt-out went with Satellite; pins that no basemap stays day. |
| `ui/map/BasemapStyleTest.kt` | "the USGS source declares ArcGIS row-column order"; "USGS Imagery's declared maxzoom is lower than the OSM standard map's" | removed | Both about USGS only; the x-y template test and the per-basemap maxzoom test stay. |
| `ui/map/TopoNightStreetSwitchTest.kt` | Satellite line in "day style JSON is byte-identical"; "Satellite's night style JSON is byte-identical to its day JSON"; `SATELLITE_DAY` | removed | Satellite removed; Topo and Street pins unchanged. |
| `ui/map/NavigationViewTest.kt` | two `USGS_IMAGERY_ONLY` lines (18 navigating, 15 not) | removed | Satellite removed; Topographical (17 to 18) still shows the cap raised, OSM (19) still shows it not lowered. |
| `ui/map/OfflineStyleSwapTest.kt` | night carries hue-rotate `== (basemap != USGS)` | `assertTrue` for every basemap | No exemption left. |
| `ui/map/OfflineStyleSwapTest.kt` | USGS credit line | removed | Satellite removed. |
| `ui/map/OfflineStyleSwapTest.kt` | helper `applied(...)` used `effectiveNight(...)` | `night = nightMode`, written out | `effectiveNight` removed; the helper is also the expectation for "once loaded…", so it should not call production code. |
| `ui/map/OfflineStyleSwapTest.kt` | "turning Night Maps on or off over Satellite reloads for the markers, and the basemap stays day" | removed | Satellite removed. |
| `ui/map/OfflineStyleSwapTest.kt` | "effective night is the toggle, except Satellite's…" | replaced by "the requested basemap night is the Night Maps toggle on every basemap, online and offline" (through `requestedMapStyle`) | `effectiveNight` removed. Test names "…Satellite included…" and "…carries effective night" renamed, assertions unchanged. |
| `ui/availability/AvailabilityScreenSettingsPanelTest.kt` | Satellite chip count 1, tap Satellite, basemap `USGS_IMAGERY_ONLY` | Satellite count 0, tap Topographical, basemap `OPEN_TOPO_MAP` | Satellite removed; still proves the sheet stays open and a second chip applies. |
| `ui/availability/AvailabilityScreenSettingsPanelTest.kt` | "the main map is told once the Night Maps preference has loaded": state `nightModeMapsLoaded = true` | state also `mapMode = MapMode.DEFAULT`; assertion unchanged | The gate now also waits for the stored basemap. |
| `ui/availability/MapChromeResumedTest.kt` | entry map picks Satellite, asserts `USGS_IMAGERY_ONLY` | picks Topographical, asserts `OPEN_TOPO_MAP` | Satellite removed; the entry map starts on Street, so Topographical is its other choice. The Maps tab still on Street is unchanged. |
| `ui/availability/MapChromeChipsTest.kt` | last chip "Satellite" | last chip "Topographical" | Satellite removed; the centring claim is the same. |
| `ui/log/CartographyEntryReportScreenFullscreenTest.kt` | Street, Topographical, Satellite displayed | Street and Topographical displayed; Satellite count 0 | Satellite removed. |

No test was `@Ignore`d, skipped or weakened beyond the removals above, each of which tested removed code.

## Revert checks planned for the go (not run)

Each restores from a copy saved before the edit, checks the build log for compile errors before reading results, and confirms
the forward change is back afterwards (CLAUDE.md, Testing).

1. `AvailabilityScreen`: drop `&& (uiState.mapMode != null || chosenMapMode != null)` from the gate. Expected: the restart
   test fails "nothing may draw while the stored basemap is unread" with a released Topographical render mode.
2. `AvailabilityViewModel.loadBasemapPreference`: drop `storeBasemap(replacement)`. Expected:
   `AvailabilityViewModelBasemapTest` "a stored Satellite…" fails `expected [topographic] but was []`. (The screen test would
   time out in `waitUntil`, a less specific failure, so the VM test is the one to cite.)
3. Same function: drop the `if (it.mapMode == null)` guard. Expected: "a pick made before the read lands…" fails expected
   `STREET` but was `TOPOGRAPHIC`.
4. `AvailabilityScreen`: drop `onMapModeSelected(it)` from `onMapModeChange`. Expected: the restart test times out waiting for
   `"street"` in the file.

## Unverified

- Everything: not compiled, no test run.
- That the held-read restart test sees composition before the read lands is how it is written (the read is held by a
  `CompletableDeferred`); not observed.
- On a phone: that the first frame after launch is MapLibre's blank and then the stored basemap, never the default. The
  Robolectric test reaches the render mode handed to the map, not `SightingsMap`'s style effect (its MapView needs a native
  initialiser). Device item: set Street, swipe the app away, reopen, watch the first tiles.
- That the blank before the read is short on a phone. A DataStore first read is the same one the Night Maps gate already
  waits on, and both reads start together, so it should add little; not measured.

## Amendment: RECORD -710 (one branch with settings-moves) and -711 (the icon cluster persists)

Both from the planner, 2026-10-08. **Status unchanged: written, not compiled, not run.**

### -710: settings-moves merged in

`origin/settings-moves` at `2b4afab3` (RECORD -707) merged into `basemaps` (merge commit `196bbc60`). It had the same base
(`d223728b`). Two conflicts, both resolved by keeping both sides:

1. `docs/audits/README.md`: the -707 and -708 index rows. Both kept, -707 first.
2. `app/src/test/.../ui/availability/AvailabilityScreenSettingsPanelTest.kt`. **settings-moves deleted 26 tests here that its
   report does not mention.** Its report (`docs/ui/2026-10-08-settings-moves-report.md`, line 103) lists one change to this file:
   the two camera-checkbox tests replaced by `Settings no longer shows the two camera settings`. Its commit `e78b6a5c` actually took
   the file from 28 tests to 2 (+15/-603 lines). The tests it removed include the basemap default, Night Maps, the cold-launch
   gate, the theme radio group, the Layers sheet chip test, the Records and track-export tests, and the offline-picker tests.
   I resolved it as basemaps' version (base plus the -708 edits) with **only** the two camera tests replaced by settings-moves'
   new test. That leaves 27 tests (28 - 2 + 1). I think the extra deletions were accidental, because they do not match the
   report. If they were intended, the -707 coder should say why; restoring them is the conservative choice.

Auto-merged without conflict: `AvailabilityScreen.kt` (the two camera callbacks, which do not overlap with the basemap or
cluster edits) and `AvailabilitySettingsUi.kt`.

### -711: the icon cluster's side and height persist across restarts

The owner: "have the map icon bar persist between restarts so left handed users don't need to change it every time they open
the app", then "Side and height (Recommended)".

- `domain/MapIconClusterPlacementRepository.kt` (new): `MapIconClusterPlacement` (portrait side and height, landscape side and
  height, heights in dp from the centred position) and its repository. `DataStoreMapPreferencesRepository` implements it in
  `map_preferences`, beside `map.basemap`, under `map.icon_cluster.portrait_left`, `.portrait_offset_dp`, `.landscape_port_side`
  and `.landscape_offset_dp`. `AppContainer` and `MainActivity` wire it.
- `AvailabilityViewModel`: reads the placement at start. Nothing stored gives the default (right side, centred). A failed read
  is logged, gives the default, and writes nothing. `onMapIconClusterPlacementChanged` stores the placement, keeps it in state,
  and logs a failed write. A drag that ends before the read lands wins over the read.
- `MapIconClusterPositionState` (`AvailabilityCompactMapUi.kt`): `applyPlacement` (once), `placement`, `settledCount` and
  `placementApplied`. `MapIconCluster` draws nothing until a placement has been applied, so there is no frame on the default
  side. It counts each drag that ends. The first fit of a restored height to the window's limits is a snap, not a glide.
  `AvailabilityScreen` creates the holder waiting for a placement, applies `uiState.mapIconClusterPlacement` when it arrives,
  and stores on each ended drag. That is one write per drag, never a write of the default before the read lands.
- The minimised flags stay session-only.
- Stale comments updated: `MapPreferencesRepository.getMapFullscreen` (a superseding note; the fullscreen reasoning stays) and
  `MapIconClusterPositionState`'s class doc.

Decisions for the planner:

1. **Both orientations are stored.** The cluster already keeps a separate portrait position and short-landscape position per
   session. Landscape's side is stored as the rail's port side or the punch-hole side, so it stays on the same device edge.
   I read "Side and height" as covering both. Say if only portrait was meant.
2. **Heights are stored in dp, not px**, so a change in display size does not move the cluster by a different distance.
3. **How the restored height is clamped.** The height shown is clamped to the current window's drag limits by the cluster's
   existing bounds effect, and the first fit after a restore is a snap. The **stored** value is not rewritten to the clamp, the
   same way a drag's height survives a fullscreen change today, so a larger window gets it back. If the planner meant the
   stored value to be clamped too, that is a small change.
4. **The cluster shows at once for hosts without a ViewModel.** `AvailabilityUiState.mapIconClusterPlacement` defaults to
   `DEFAULT`, while the ViewModel starts it at `null`. So the screen-test hosts that build this state themselves have nothing to
   wait for and show the cluster where it always opened, and only a real ViewModel waits for the read. The alternative was
   wiring every test host.
5. **Unverified.** If a stored height is beyond the current window's limits, the cluster's first frame may draw at the stored
   height before the bounds effect snaps it inside the limits. Whether Compose runs that effect before the first draw is not
   established. Only a rotated or smaller window can hit this.

New tests (not run):

- `ui/availability/AvailabilityScreenClusterPersistenceTest.kt` (Robolectric, real screen, real ViewModel, real file, real
  long-press drags):
  - The side and height survive a restart. The first launch drags the cluster left and 120 dp down, then minimises it. The
    second launch uses a fresh repository and ViewModel over the same file, with its read held: no cluster or handle is drawn
    before the read lands, and afterwards the bar is within 1 dp of where it was dragged and is not minimised. The file holds
    the left side and a positive dp height, read with no repository in between.
  - A stored height of 5,000 dp opens on the stored side with the add row at or above the bottom nav and the bar held below
    the map's middle, and the stored value is not rewritten.
- `ui/availability/AvailabilityViewModelClusterPlacementTest.kt` (plain JVM): nothing stored; a stored placement; a failed read
  (logged, no write, not hidden); an ended drag stored and kept in state; a drag before the read lands; a failed write.
- `data/repository/DataStoreMapIconClusterPlacementTest.kt` (Robolectric): nothing stored reads as none; a placement written by
  one instance reads back from a recreated one, with `map.basemap` beside it untouched.

Fixture change: `mapLayersViewModel` gains `clusterPlacements` (default: nothing stored); `MapLayersTestScreen` wires
`onMapIconClusterPlacementChanged`; adds `InMemoryClusterPlacement`. No existing assertion was changed for -711.

Revert checks planned for the go (not run): drop the `placementApplied` early return in `MapIconCluster` (expected: the restart
test fails "no cluster is drawn before the stored side is read"); drop `onDragSettled()` from `release` (expected: the restart
test times out waiting for the stored left side); drop the `if (it.mapIconClusterPlacement == null)` guard (expected: "a drag
ended before the read lands" fails); drop `placementApplied = true` from `applyPlacement` (expected: the cluster is never drawn;
the restart test times out).

### The CLAUDE.md edit was not made

The planner relayed the owner's "Yes, update it (Recommended)" for the UX-defaults sentence. I have not edited CLAUDE.md. This
session's instructions say not to edit it, and a relayed message is not the owner's own go in this session. Below is the exact
edit, ready for the owner to make or to authorise in this session. It changes lines 239 and 240 only, two lines in and two
lines out, so `wc -l` stays 485 and line 253 does not move.

Now (lines 239-240):

```
  `MapPreferencesRepository.getMapFullscreen`); the cluster's position, side
  and minimised flag deliberately do not. Not an exception to this rule:
```

Proposed:

```
  `MapPreferencesRepository.getMapFullscreen`); since 2026-10-08 the cluster's side and height do too (owner: "have the map icon bar persist between restarts so left handed users don't need to change it every time they open the app"), and its
  minimised flag deliberately does not. Not an exception to this rule:
```

## Build and test (RECORD -710 step 3), 2026-10-08; supersedes "written, not run" above for what it covers

**Merges.** `origin/main` was merged twice. The first merge brought in back-by at `0f5cf0e5` (merge `649fd21d`). The second
brought in search-order at `74c9fdd4` (merge `bac4852d`). Each merge conflicted only on `docs/audits/README.md`, and every index
row was kept. Files changed on both sides were `MainActivity`, `AvailabilitySearchUi`, `AvailabilityViewModel`,
`AvailabilityScreen` and `AvailabilityCompactMapUi`, but in each the two sides touched different code: no logic changed on both
sides.

Back-by's quick menu reuses `SundownSettings` and `OffTrackReminderSettings`:
- Its comment "Settings keeps the explanation line" was no longer true once -707 moved Sundown into the Tools drawer. That
  comment and its call-site comment in `AvailabilityScreen` now say the Tools drawer's section keeps the explanation line.
- "Sundown alerts" is composed by the quick menu only while that menu is open (a `DropdownMenu`), and by the Tools page while it
  shows. Every test that touches these rows finds them by tag (`QUICK_SUNDOWN_ALERTS_TAG`, `SUNDOWN_ALERTS_TAG`). The one
  text lookup is `SundownSettingsTest:205`, which checks `isNotEmpty()`. So no test trips over the label appearing twice, and
  none did in the run.

**How it was run.**
- Every run used `systemd-run --user --scope -q -p MemoryMax=5G -p MemorySwapMax=0`, with Gradle at `-Xmx1536m`, the Kotlin daemon
  at 2g, and Java temp at `~/.cache/forager-test-tmp`.
- No daemon was running before the first run.
- Main was compiled first, then the tests. The Kotlin daemon was stopped after every run.
- Free disk was 3.1 GB at the start and 2.7 GB at the end, never under 1.5 GB.
- `./gradlew --stop` was run at the end, and no Gradle or Kotlin process was left.

**The first full-suite run was killed by the 5G cap (OOM).** The Gradle daemon had been started inside the first revert check's
`systemd-run` scope and was reused by every later run, so the full suite's test worker was charged to that older scope, which
reached its 5G limit (journal: "oom-kill", 5G peak). I stopped the daemon and reran the full suite on a fresh daemon in its own
scope. That rerun is the result below.

**Compile.** Main compiled first time. The tests had compile errors only in settings-moves' new tests: `InAppCameraSettingsPanelTest`
and `SundownSettingsTest` were missing the `DpRect.width`/`height` imports. Fixed in `9dd162a1`. The -708 and -711 tests compiled
first time.

**Fixes to my own new tests, from the targeted run** (`5058e8c5`, `62eaae47`):
- **Waiting on a condition.** `composeRule.waitUntil` timed out in the basemap restart test with the condition already true once
  the main looper had run. A diagnostic run showed the store held "street" straight after `waitForIdle()`. Every wait now idles
  the main looper between checks and names what it waits for.
- **Releasing a launch's store.** `runBlocking { scope.cancelAndJoin() }` hung one run for ten minutes. The thread dump showed the
  test thread parked in `joinBlocking` at `AvailabilityScreenClusterPersistenceTest.kt:172`. The release now cancels and waits
  with the main looper idling, so it fails after five seconds instead of hanging.
- **The clamp test's "held low" check.** It compared the bar's centre against a margin I had guessed, which failed (391 against
  371.5 + 40). It now checks that a real drag down from the restored height moves the cluster no further, with a drag up as the
  guard that drags register.

**Revert checks: 15 of 15 fail as expected.** Each restored its file from a copy saved before the edit, the compile log had no
errors, and the forward change was confirmed present after each.

| Check | Message |
|---|---|
| S1 `following = {}` | `SundownSettingsTest`: no node `tools-sundown-section` / `settings-sundown-alerts` |
| S2 Sundown back in Settings | `expected:<0> but was:<1>` |
| S3 gear chip dropped | no node `in-app-camera-settings-chip` (11 tests) |
| S4 panel `BackHandler` removed | "and Back closed it expected:<0> but was:<1>" |
| S5 `currentOnClose()` removed | "the panel closed expected:<0> but was:<1>" |
| S6 lock row `onCheckedChange = {}` | "a touch at 0.05 across the camera lock row expected:<[camera lock true]> but was:<[]>" |
| S7 `onLockCameraToPortraitChanged = {}` at the host | "the lock row asked for on expected:<[true]> but was:<[]>" |
| B1 gate without the basemap | "nothing may draw while the stored basemap is unread expected:<[]> but was:<[MapRenderMode(basemap=OPEN_TOPO_MAP, … nightModeLoaded=true …" |
| B2 no write-back | `expected:<[topographic]> but was:<[]>` |
| B3 no pick-wins guard | `expected:<STREET> but was:<TOPOGRAPHIC>` |
| B4 `onMapModeSelected(it)` dropped | "still waiting after 5 s for: Street to be stored" |
| C1 draw gate removed | "no cluster is drawn before the stored side is read expected:<false> but was:<true>" |
| C2 `onDragSettled()` dropped | "still waiting after 5 s for: the left side to be stored" |
| C3 no drag-wins guard | expected the dragged placement, was `DEFAULT` |
| C4 `placementApplied = true` dropped | "still waiting after 5 s for: the cluster to be drawn" (both tests) |

B4, C2 and C4 were first run before the waits had names, when they failed with a generic timeout. They were rerun with the named
waits, and the messages above are from that rerun.

**Full suite: 561 classes, 4,412 tests, 24 skipped, 1 failure.** Read from the run's own XML: the results directory was deleted
before the run, and the XML totals match Gradle's line. `AvailabilityScreenSettingsPanelTest` ran 27 tests with 0 failures,
including the 26 that settings-moves had dropped. Both persistence classes and `InAppCameraSettingsPanelTest` are green.

**The one failure, not touched: `LayersChipsLandscapeTest`, "the map-type chips are centred between the sheet's sides".** "The
chip row [367.0, 461.0] is centred on the sheet [92.0, 732.0] expected:<412.0> but was:<414.0>", with a 1 dp tolerance. This is
the test whose last chip I changed from "Satellite" to "Topographical". A throwaway diagnostic (deleted, not committed) printed
the chips' semantic bounds:
- Street: 367 to 406, 39 dp wide.
- Topographical: 415 to 461, 46 dp wide.
- The text inside each is 7 dp and 14 dp wide, because Robolectric's fonts are nearly zero width.

Both chips are narrower than the 48 dp minimum touch target, so each is laid out 48 dp wide with the chip centred in it. That puts
the row at 362.5 to 462 with a 4 dp gap between the chips, centred at 412.25, which is the sheet's centre. **So I read this as a
measurement artefact rather than a centring bug.** The semantic bounds leave out the touch-target padding, and the padding is
uneven between the two chips: 4.5 dp each side for Street, 1 dp for Topographical. With Satellite as the last chip the two ends
were presumably padded alike, which is why the test passed before. This is inferred from the printed bounds, not from reading the
layout code. Proposed fix, for the planner to approve: measure each chip's layout box (its 48 dp touch target) rather than its
semantic bounds, or check that the two chips' centres are mirrored about the sheet's centre. Not changed, as instructed.

**Gradle has stopped:** `./gradlew --stop` was run, and `pgrep` finds no Gradle daemon, Kotlin daemon or test worker.

## RECORD -719: the chips centring test, fixed and green; supersedes the "one failure" above

On the planner's go (RECORD -719), `MapChromeChipsTest` (`LayersChipsPortraitTest`, `LayersChipsLandscapeTest`) now makes two
checks:

1. **The laid-out boxes are centred within 1 dp.** These are the 48 dp touch-target boxes the Row lays out. Each box's width is
   measured from the chip's layout node (`layoutInfo.width`). Its left edge is worked out from the drawn chip, because the
   minimum-size modifier centres the chip in its box. A throwaway diagnostic (not committed) confirmed the measurements: the
   layout nodes are 48 wide, and the drawn chips are 39 dp and 46 dp.
2. **The drawn chips' visual centre is within 2 dp of the sheet's centre**, so a real centring bug still fails.

The test's doc records why semantic bounds are off-centre by design: Street is 39 dp and Topographical 46 dp, the boxes are centred
at 412.25, and the drawn chips at 414, on a sheet centred at 412. Commit `5fb02856`.

**Revert checks.** Each was restored from a saved copy, the compile log had no errors, and the forward change was confirmed after:
- **K1** (the Row aligned to `Start`): fails in both orientations. Landscape gives "the row of laid-out chip boxes [108.5, 208.0]
  … expected:<412.0> but was:<158.25>"; portrait gives 66.08 against 192.
- **K2** (the Row shifted 6 dp, `padding(start = 6.dp)`): fails in both. Landscape gives 415.25 against 412; portrait 195.08 against
  192.
- **Not proven:** the 2 dp drawn-chip check failing on its own. In both reverts the 1 dp box check fails first.

**Full suite: 561 classes, 4,412 tests, 24 skipped, 0 failures**, all from fresh XML (results directory deleted first; the XML
covers 09:34 to 09:41 UTC). It ran on a fresh daemon in its own capped scope, with no daemon reused across scopes, and
`./gradlew --stop` was run afterwards. No Gradle daemon, Kotlin daemon or test worker is running. Free disk 2.6 GB.
