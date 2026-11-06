# Sorrel's review of PR #418: step 8b, the Official trails switch (dormant on v2)

Sorrel Tally (Fable), 2026-10-26T21:33Z, a fresh session; read-only on code. For Rowan Waymark.

Reviewed: branch `step8b-official-trails-switch` at `cdd5c250e` (commits `82124db86` stubs, `0c180931d` build,
`24041cde9` entry-test fix, `cdd5c250e` one test line), base `410802b39` (pre-main, #417 merged). Against dispatch
`prompts/preserved/2026-10-26-15.md` with Amendments 1 and 2, RECORD -1633, -1678, -1680 to -1684
(`records-after-173`), and the step 8 report §5 and §8 (`later-steps-reports`). Every claim below is marked
**read** (from the named file or artifact), **computed** (by my own script over the cached artifacts) or
**inferred** (not verified against a source here).

## Baseline (read)

- `git merge-base origin/pre-main origin/step8b-official-trails-switch` = `410802b39` = pre-main's head at
  21:00Z, so the PR is a fast-forward on current pre-main; `gh pr view 418`: MERGEABLE, mergeStateStatus CLEAN;
  22 files, +892 −21. No file on 8c's list (MapTapHandler.kt, MapFeatureBubble.kt, MapBubbles.kt,
  domain/referencetrails/) or #415's list is touched.
- The ten runs are the runs named: 40396819864 (fail-first at `82124db86`), 40397928598 (`53b0b24cb`, stubs +
  the entry-test fix), the six reverts (branches `step8b-revert-r1-sheet-row-always`, `-r3-maps-tab-report`,
  `-r4-paint-no-presence`, `-r5-topo-drawn`, `-r6-blm-id`, `-r7-gate-off`, each one commit on `24041cde9`),
  40397982078 (PR at `24041cde9`, 1 failure), 40400284808 (PR at `cdd5c250e`, green). The revert commits are
  in the local object store; each is a one-line edit of the file it names (diffs below).
- The coder's worktree (`/mnt/work/forager-wt/step8b-official-trails-switch`) is clean at `cdd5c250e`; no
  report file exists there or in the branch. I could not find the document Rowan's "six beyond-scope calls"
  refers to; my reading of the phrase is in §3.12.

## Mechanism prediction (written after the dispatch and record, before the code)

Predicted: (a) the registry guard's filter to app-added layers would be the one re-pin that proves less;
(b) the entry map would need three lines, not "one or two"; (c) the drawn-state gate would need to act on
the switch's owner only, with followers through `stateOwnerId`; (d) the tap query would carry the band ids
on every style, harmless. Scored at the end.

## 1. The re-pinned existing tests: did any assertion weaken? (read, diff of base against head)

**MapLayerRegistryTest** (557 to 587 lines; 26 tests before and after, computed from the XML).
- `expectedOrder` (full registry order) gains the three ids between JOURNAL_ENTRY_PHOTOS and
  JOURNAL_ENTRY_REGIONS; exact list equality kept. Not weaker.
- "every current layer is in the registry exactly once": set + count 20 → 23. Not weaker.
- "the only change from the order before L0a": subtracts `referenceTrails` before comparing to
  `orderBeforeL0a − moved`, as it already subtracted the colour fields, halos and night border. The relative
  order of every app layer is still pinned. Not weaker.
- kind/renderer/source map, tapGroup map, owners map: each extended with three exact entries. Not weaker.
- credits: `colourFields → SYNTHETIC` becomes that plus `REFERENCE_FS_BAND → REFERENCE_TRAILS_CREDIT`; still
  an exact map. Toggleable list gains REFERENCE_FS_BAND at its registry position. Not weaker. The test's
  *name* ("only the colour fields … carry a credit") is now false of its own assertion (S4).
- J8 halo test: `JOURNAL_ENTRY_PHOTOS to (PHOTOS to REFERENCE_FS_BAND)` replaces `… to JOURNAL_ENTRY_REGIONS`;
  the "directly below" index pin stays exact. **It proves the same thing about the registry and less about the
  screen**: the band is drawn by the style beneath every layer the app adds (`OverlayLayerSetup.kt:79` skips
  STYLE_LAYER specs; app layers are `addLayer`ed on top), so on screen the photo ring still sits directly below
  JOURNAL_ENTRY_REGIONS, which this test no longer says. The guard (next) still pins that for app layers.
- `lineLayers = referenceTrails + listOf(…)`: the ring test "every marker ring draws below every line" now
  asserts, in both the registry and `orderedLayers`, that each ring is below the three style layers. In the
  registry that is true; on screen it is false (the style's band is under the rings). Not weaker as an
  assertion; it is a stronger assertion about a model that no longer matches the screen for those three ids
  (S2).
- **Guard** `guard - the full draw order is the base's, layer for layer`: `drawOrderAtBase` unchanged (20
  ids), compared against `appAdded(orderedLayers(…))`, which drops STYLE_LAYER specs. For every layer the app
  adds the pin is layer-for-layer as before, in both branches. **What it no longer says**: where a STYLE_LAYER
  spec sits. A future STYLE_LAYER spec placed anywhere would pass this guard. That hole is closed by
  `ReferenceTrailLayersTest`: `MAP_LAYER_REGISTRY.filter { STYLE_LAYER }.map { id } == referenceIds` (an exact
  list, so no fourth style layer passes unseen) and "the reference layers sit below every app line, in the lines
  band" (first three of the LINES band). So the guard's claim is now carried by two tests instead of one; the
  comment on the guard says so. Acceptable; noted as the one re-pin whose meaning changed (N3).

**TapPrecedenceTest**: the exact `tappableLayerIds(order)` list gains REFERENCE_FS_BAND, REFERENCE_BLM_BAND
after the colour fields and before OFFLINE_REGION_OUTLINE. Exact list kept. Not weaker. 20 tests before and after.

**The three credit tests** (MapLayerStateTest "the real registry adds no credit while no colour field has
data"; MapLayerPreferencesStateTest "while a synthetic layer is visible the caption gains the synthetic credit
once…"; AvailabilityScreenMapLayersSheetTest "with the release build's store … no credit is added"): each
wraps the state in `withReferenceTrailsHiddenUnlessLoaded(…, loaded = false)` before `activeLayerCredits`,
with the expected lists unchanged (`[]`, `[OSM, Synthetic]`, `[]`). `registry` in the second is
`MAP_LAYER_REGISTRY` (`MapLayerPreferencesStateTest.kt:20`). Each assertion stays exact. What changed is the
premise: "the real registry adds no credit" became "the real registry, gated as `SightingsMap.kt:315-316`
gates it, adds no credit". That gate is the production path (dispatch Amendment 2 asked for exactly it), and
the registry-level credit set is pinned separately in MapLayerRegistryTest, so in combination nothing is lost.
Two things to know: (i) the Availability test applies the gate itself to the stand-in slot's `renderMode.layers`,
so it would still pass if `SightingsMap` dropped the gate; the same was already true of
`withUnavailableColourFieldsHidden`, and no test composes `SightingsMap` (MapLibre); (ii) the
MapLayerPreferencesStateTest re-pin (`cdd5c250e`) was made after the PR run showed it red with
`[OSM, Synthetic, Trails: …]`. I read that as the test catching the new credit and being brought onto the
production path, not as silencing: the expected value is unchanged and exact.

**Verdict on weakening: no assertion became weaker.** Two tests (halo, ring) now make an on-screen claim the
registry cannot back for three ids (S2); the guard's claim moved partly to ReferenceTrailLayersTest (N3).

## 2. The runs (computed from the downloaded JUnit XML, `art/<run>/`)

- **Fail-first 40396819864** (`82124db86`): 4 classes, 25 tests, 19 failures. The stubs commit is pure stubs
  (read: `has()` false, `applyReferenceTrailsOnStyleLoad` false, the gate returns `state`, `onShown` no-op, no
  registry entries; only the ids, `STYLE_LAYER` and the parameter defaults were real). Each failure names the
  stubbed thing: `Collection contains no element matching the predicate` (no registry spec), `expected:<true>
  but was:<false>` (load returns false), `performScrollTo() failed` (no row), the caption missing
  `· Trails: USDA Forest Service, CC BY 4.0 · BLM`. Six tests passed at stubs: the three that assert an
  absence (no row on v2 ×2 — one failed for a harness reason, see next — "on today's v2 style load nothing is
  written", "a flip on today's v2 map writes nothing"), the two id tests (the ids were already in the stub), and
  "at night each band takes Trail Map's plain V1 recolour" (N1).
- **CartographyEntryReferenceTrailsTest** failed at stubs with `the map section never resolved` (both tests):
  a harness fault (the entry had no kept waypoint). `24041cde9` fixes the test; **40397928598** re-ran it on
  stubs + fix (`53b0b24cb`): "no row" passes (absence), "with the band loaded the row shows" fails
  `performScrollTo() failed`. Right reason.
- **Reverts** (each a fresh CI checkout, so no stale XML is possible; XML present with timestamps, so each
  reverted build compiled; 71 tests over 6 classes each):
  - r1 `if (referenceTrails)` → `if (true)`: both "no row on today's style" tests fail `expected:<0> but
    was:<1>`. Specific.
  - r3 drop `onReferenceTrailsShown = onReferenceTrailsShown` in `availabilityMapRenderMode`: the four
    Maps-tab row tests fail `performScrollTo() failed`; the entry-map tests pass. Specific to the Maps-tab
    wiring.
  - r4 `applyStyleLayerPaint` without `has()`: "a flip on today's v2 map writes nothing" fails `wrote
    reference_fs_band, which this style does not have`. Specific.
  - r5 `drawn = complete` (Topo ignored): "under Topographical … hidden" fails `expected:<false> but
    was:<true>`. Specific.
  - r6 BLM id `reference_blm_bands`: 7 failures, all naming the misspelt id; one shows the partial path's
    output `[(reference_fs_band,false),(reference_fs_number,false)]`. Specific.
  - r7 gate off: caption test fails with the credit present; drawn-state test `reference_fs_band
    expected:<false> but was:<true>`. Specific.
  - **r2 is absent** from the series. The entry map's two wiring lines (`CartographyEntryReportScreen.kt:577,
    662`) have no single-line revert; they are covered by fail-first at stubs (where the row was absent for
    every reason at once) and by green (N5).
- **PR run 40397982078** (`24041cde9`): 5,680 tests, 1 failure (MapLayerPreferencesStateTest, above).
  **40400284808** (`cdd5c250e`): 5,680 tests, 0 failures, 24 skipped, 770 suites.
- **Count check**: pre-main's run 40395669153 at `410802b39` has 5,655 tests in 766 suites; the only per-suite
  differences are the four new classes (5 + 2 + 9 + 9 = 25). No existing suite changed its count.

## 3. The dispatch's questions, in order

**3.1 Nothing visible on v2 / Topo / OSM / v2 regions.** (read, plus the headless tests)
- *Row*: `SightingsMap.kt:313` starts `referenceTrailsLoaded = false`; `MapStyleReload.kt:146` sets it from
  `applyReferenceTrailsOnStyleLoad`, which returns false when no reference layer is present
  (`ReferenceTrailsVisibility.kt:72`); `SightingsMap.kt:320` reports it upward; both sheets show the row only
  on `true` (`MapLayersSheet.kt:309`). Tested through both real screens with a stand-in slot
  (ReferenceTrailsSwitchTest, CartographyEntryReferenceTrailsTest).
- *Credit*: the caption reads `activeLayerCredits(MAP_LAYER_REGISTRY, drawnLayersState)`
  (`SightingsMapLayout.kt:139`), and `drawnLayersState` hides the owner spec while not loaded
  (`SightingsMap.kt:315-316`, `withReferenceTrailsHiddenUnlessLoaded`). The caption test compares against the
  caption computed from the registry *without* the style-layer specs ("exactly what it was") and asserts no
  basemap carries the credit while unloaded. r7 covers the gate.
- *Tap targets*: `tappableLayerIds(order)` (`MapTapHandler.kt:93`) is **not** gated: it includes the two band
  ids on every style. No hit is possible on v2/OSM/Topo-day because the layers do not exist, and under
  Topo-night because they are set to `none` (3.6); so "no tap is aimed at the band" (PR body) is true in effect,
  not because the code stops aiming (N6). `layerDrawn` (fan membership) does read the gated state
  (`MapListeners.kt:96`).
- *Style writes*: on v2, `present.isEmpty()` returns before any write or log (`ReferenceTrailsVisibility.kt:72`);
  the flip path writes only to layers `has()` finds (`:89`). Tested: "on today's v2 style load nothing is found
  and nothing is written", "a flip on today's v2 map writes nothing and throws nothing" (the fixture throws on a
  write to an absent id; r4 proves it bites).
- *Logs*: none on v2 (the `Log.i` at `:79` is after the early return). On a style with the layers: one `Log.i`
  per load; a partial style adds one `Log.w`.
- *v2 regions offline*: `mapStyleSourceFor` gives `Uri(offlineStyleUrl)` (`BasemapStyles.kt:312-313`); a region
  downloaded against v2 has no reference layers, so the same as v2 online. (read; not tested with an offline
  fixture, and the v2 fixture is the served v2, which is what such a region holds.)

**3.2 The style's 0.35 never written by the app.** (read + tested) `referenceTrailLayer` sets
`baseOpacities = emptyList()` (`MapLayers.kt:356`), so `layerPaintFor` resolves no opacities;
`applyStyleLayerPaint` writes visibility only; `OverlayLayerSetup.kt:79` skips STYLE_LAYER specs before
`paintProperties`; `registryProblems` refuses any base opacity on a STYLE_LAYER spec because no
`OpacityProperty` names that renderer (tested: `… is a STYLE_LAYER layer but sets line-opacity`). `git grep`
of `0.35|0_35|35f` in `app/src/main` finds the figure only in comments. The one copy is
`forager-reference.mjs` `BAND.opacity`.

**3.3 Visibility after the night recolour and before `initializeOverlayLayers`.** (read only)
`MapStyleReload.kt:138-147`: `applyOfflineNightRecolour` (night, non-Json), `applyStreetNightImages`,
`hideStreetLabelsUnderTopo`, then the `applyReferenceTrailsOnStyleLoad` call, then `initializeOverlayLayers`.
`applyOfflineNightRecolour` walks `style.layers` without reading visibility
(`OfflineNightRecolourApply.kt:32`), so a band hidden at load is recoloured for a later switch-on, as the
kdoc claims. **Not tested, and cannot be under Robolectric**; the step 8 report §8 already lists both positions
for a phone check. The night test in ReferenceTrailsVisibilityTest pins only that `streetNightRecolourOf`
recolours `#7b5ea7` on `fs_trails`/`blm_trails` and that the offline recolour agrees; it passes at stubs and
at head (N1).

**3.4 A flip taking effect in-session via MapFan.** (read + headless re-implementation) The fade effect is
`LaunchedEffect(state.loadedStyle, drawnLayersState)` (`MapFan.kt:133`); a flip changes `drawnLayersState`,
the effect relaunches, `snapshotFlow { fanOut.isOpen }` emits the current value at once, and the walk calls
`applyStyleLayerPaint` for STYLE_LAYER specs, `applyLayerPaint` for the rest (`:142`). Both use
`currentStyle()` (`MapStyleReads.kt:25-30`, `isFullyLoaded`) before touching the style, which matters because
`Style.getLayer` calls `validateState` and throws on a superseded style (read from the 13.5.0 jar with javap);
`has()`/`setVisible()` sit inside that existing guard. The test `flip()` re-implements the walk's filter rather
than composing MapFan, so `MapFan.kt:142` itself is read-verified (N2). Same for the stale state during a
load: the callback applies from `chosenLayers.value` (the ungated choice, so a previous style's `false` cannot
hide the band on a new v3), and the effect re-applies once `loadedStyle` changes.

**3.5 Topo-over-Street hiding the band.** (read + tested) `drawnOnThisMap = source !is
MapStyleSource.StreetWithTopoOver` (`MapStyleReload.kt:146`); that source arises only for Topo at night
(`BasemapStyles.kt:269`); Topo by day is `Json` with no reference layers. With a v3 underlay the three are set
to `none` and the load returns false (test "under Topographical…", r5). Note this is a style write under Topo
at night, not "nothing": harmless, and the band's minzoom 12 is above the 9.5 crossfade anyway (read from
`forager-reference.mjs`; the 9.5 from CLAUDE.md).

**3.6 Partial layers treated as none.** (read) A style with some but not all three: `Log.w`, every present
layer set to `none`, return false, so no row, no credit (`ReferenceTrailsVisibility.kt:73-79`). For the user
that is "none"; for the style it is a write plus a warning on a style the app does not know, which CLAUDE.md's
no-silent-fallback rule wants. **No forward test covers this branch**; r6's failure output happens to show it
running (S3).

**3.7 The upward flag on both maps.** (read) Maps tab: `AvailabilityScreen.kt:456, 459, 476` →
`availabilityMapLayersControls`/`availabilityMapRenderMode` (`AvailabilityScreenMapInputs.kt`) →
`CompactMapPickers.kt:109` → `MapLayersSheet`. Entry map: `CartographyEntryReportScreen.kt:320` (remember),
`:577` (`onReferenceTrailsShown`), `:662` (`referenceTrails`): **three lines, 784 → 787**. Amendment 2 says
"one or two lines only, else stop". Three is the minimum for this design (a holder, its sink, its flag); the
coder neither stopped nor disclosed the overrun (the PR body is silent). Under 800 (S1). The holder
`ReferenceTrailsShown` is `@Stable` with one lambda for its life, so `MapRenderMode` equality holds across
recompositions; its `{}` default is a non-capturing lambda and so a single instance (inferred from Kotlin's
compilation of non-capturing lambdas; corroborated only by the green suite).

**3.8 The tap path querying two absent ids on every tap.** `MapLibreProbe.hitsAt/hitsInBox` issue one
`queryRenderedFeatures` per layer id (`FanOutLayers.kt:353-358`), so two more native calls per stage, up to
four per tap, on every map today. Java side (read, javap of `android-sdk-13.5.0-runtime.jar`):
`MapLibreMap.queryRenderedFeatures(PointF, String…)` passes straight to `NativeMap.queryRenderedFeatures`;
`NativeMapView` checks only that the map is not destroyed, then JNI. Native side (**inferred**, from my memory
of maplibre-native's `RenderOrchestrator::queryRenderedFeatures`, not verified against the pinned 13.5.0
sources): unknown layer ids are looked up and skipped, an empty layer set queries no source, no error. Cost: a
JNI round trip and a hash lookup each. No error path I can see; the same probe already queries every other
tappable id individually.

**3.9 Six beyond-scope calls.** Files touched outside Amendment 2's named list, with their edits:
`AvailabilityScreen.kt` (the `remember`, `referenceTrailsShown =`, `onReferenceTrailsShown =`: 3),
`MapRenderMode.kt` (the field: 1), `MapSlot.kt` (pass-through: 1), `CompactMapPickers.kt` (pass-through: 1) =
six one-line edits, if that is the count Rowan means. `AvailabilityScreenMapInputs.kt` I read as "the split's
input path", named. Each is a thread-through the flag needs between the map and the sheet; each has a default
(`false` / `{}`) so every other caller is unchanged; none is on 8c's or #415's lists. I could not read the
coder's own account of them.

**3.10 File sizes (computed, `wc -l` at base → head).** CartographyEntryReportScreen.kt 784 → 787;
SightingsMap.kt 759 → 770; AvailabilityScreen.kt 722 → 727; MapLayersSheet.kt 544 → 575; MapLayers.kt 473 →
536; MapStyleReload.kt 177 → 191; MapFan.kt 151 → 157; OverlayLayerSetup.kt 197 → 201; new
ReferenceTrailsVisibility.kt 119. None over 800; the new file is under 400. CartographyEntryReportScreen.kt is
13 lines from the limit.

**3.11 Clean merge.** Fast-forward on `410802b39`; MERGEABLE / CLEAN (read from `gh`); pre-main had not moved
at 21:00Z.

**3.12 Amendment 1 (tappable bands, ids in one place).** (read + tested) Two specs with `TapGroup.LINE`, the
number `NONE`; spec id = style id; `MapLayerIds` mirrored and held by `ReferenceTrailLayersTest`, which reads
`forager-reference.mjs` by regex and the served v3/v2 JSON (v3 carries exactly the three on source
`reference` in registry order; v2 none). Bands first in the LINES band, so last among lines in `tapWinner`.
The switch is owned by `reference_fs_band`, followers via `stateOwnerId`; a stored choice for a follower is
refused (tested). One switch over three specs did not fight the registry's shape.

## Verdict

**Blocking: none.**

**Should-fix** (none needs to hold the merge; Rowan's call):
- **S1** Entry map: three lines against Amendment 2's "one or two lines only, else stop", undisclosed in the
  PR. Needs a line in the record, or the amendment's bound amended; the file is at 787.
- **S2** MapLayerRegistryTest's `lineLayers` and the halo pin now say the marker rings draw below the three
  style layers and that the photo ring is "directly below" `reference_fs_band`. True of the registry, false of
  the screen (the style draws the band under everything the app adds). Exclude STYLE_LAYER specs from
  `lineLayers` and the neighbour pin, as the guard does, so the two tests keep their on-screen meaning.
- **S3** The partial-style branch (`Log.w`, hide, not offered) has no forward test; one fixture holding two of
  the three ids would pin it (r6's output shows the path runs).
- **S4** Stale names: `MapLayers.kt:176` cites `ReferenceTrailLayerIdsTest` (the class is
  `ReferenceTrailLayersTest`); the test "only the colour fields are reorderable, offer an opacity slider and
  carry a credit" now asserts a credit on the band too.

**Notes:**
- **N1** The night order (recolour → visibility → overlays) is read-verified only; the night test passes at
  stubs and at head because it pins 8a's style and existing recolour code, so it is not evidence for this PR's
  order. Phone check as §8 says.
- **N2** `MapFan.kt:142` and `SightingsMap.kt:315-316, 320` are read-verified; the headless tests re-implement
  the walk's filter and the gate's call rather than composing either.
- **N3** The registry's header ("SightingsMap adds one native layer per spec", "the draw order is this
  registry's") is now untrue for three specs; `ZGroup.LINES` for them models tap order, not draw order. The
  guard's placement claim for them lives in ReferenceTrailLayersTest's exact list.
- **N4** `reference_fs_number` is a symbol layer in the style but `LayerKind.LINE` in the registry; untappable,
  so no effect today.
- **N5** r2 is missing from the revert series; the entry map's wiring has no single-line revert.
- **N6** The tappable id list is not gated by the drawn state (3.1, 3.8); the PR body's "no tap is aimed at the
  band" is true in effect only.
- **N7** ReferenceTrailsSwitchTest's placement test pins "below Journal entries" only; "before the colour
  fields" is read-verified (`MapLayersSheet.kt:309-318`), since that test's store has no colour field.
- **N8** Under Topo at night with a v3 underlay the three layers are written `none` on each load (3.5): a
  write, not nothing; harmless.
- **N9** The DataStore key is `map.layer.reference_fs_band.visible` (`LAYER_KEY_PREFIX` + id +
  `VISIBLE_SUFFIX`, `DataStoreMapPreferencesRepository.kt:201-202`); the in-memory test store records
  `visible reference_fs_band false`, so the real key is read-verified, not tested here (as for every overlay).

**Prediction scored:** (a) half right: the guard proves less on its own, but the hole is closed elsewhere, and
the weakening of meaning I did not predict is in the ring and halo tests; (b) right, three lines; (c) right;
(d) right, with the Java side read and the native side still inferred.

**One line:** mergeable; no blocking item; S1 wants a record line before or with the merge; S2 to S4 can ride
the switch PR. Device-only remains: the band by day and by night once the app is on v3, and the order in 3.3.

Scratch: `/mnt/work/scratch/sorrel-418/` (the ten runs' JUnit XML under `art/`, `parse.py`, `suites.py`, the
diffs). Not committed.
