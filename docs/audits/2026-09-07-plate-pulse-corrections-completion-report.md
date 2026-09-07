# Completion report: the plate pulse's five corrections (items 2, 4, 5, 6, 7)

**Type:** completion report for a build dispatch. **Date:** 2026-09-07.
**Base:** `main` at `0ca2f55` (PR #73 merged). **Branch:** `claude/new-session-b7z9bg`, on top of the pulse report (`4eef46a`) and its rulings addendum (`72f89cf`). Code commit `0ccfd6a`; this report follows it.
**Dispatch:** the owner's rulings recorded in the addendum to `2026-09-07-cartography-plate-renderer-pulse.md`, built "as one dispatch — they're small, they share a subsystem, and four of them are corrections rather than features." Items 1 and 3 were ruled moot and are untouched.

---

## What was built, per item

### Item 2 — `updatedAtEpochMillis` is stamped on every save; the Entries feed sorts by day

- `SaveCartographyEntryUseCase` now takes a `now: () -> Long` (default `System::currentTimeMillis`, the same shape `CreateCartographyEntryUseCase`/`CommitCartographyEntryUseCase` already have), stamps `updatedAtEpochMillis = now()` and returns the **stamped** entry. Stamped at the one seam every save passes through rather than at each caller, for the same reason the network-fix rule lives at `RoomTrackRepository`'s read seam.
- `GetCartographyEntriesUseCase` sorts `compareByDescending { it.date }.thenByDescending { it.updatedAtEpochMillis }`. Its doc comment records that this **deliberately reverses** the previous "most recently updated first", which was the stated intent when written, and why (one field cannot be both a modification time and a sort key; an entry is a day). Sorted on the existing `date` column — no migration. `GetCartographyDraftEntriesUseCase` is unchanged, as the ruling was about the Entries feed; noted in the doc comment.
- `CartographyViewModel` keeps the returned (stamped) entry at its four list-updating call sites: `onStartEntry`, the `onOpenEntry` recompute write-back, `onSaveEntry`, `onSaveEntryAsDraft`. **One deliberate exception:** `persist` (a draft's per-keystroke autosave) does not write the stamped copy back over `editingEntry`, because that write completes asynchronously and a keystroke typed in between would be clobbered by the older text. The draft's in-memory stamp is stale until the next load; the row on disk is right. `onSaveEntry` replaces `editingEntry` only if it is still the entry the save started from, for the same race. Both are written down at the use case and at the call site.
- `AppContainer` is unchanged (the default clock).

### Item 6 — `isEmpty` fixed at the property, and empty polylines dropped at the use case. Both.

- `CartographyEntryMapData` gains `drawablePoints` (tracks flattened + finds + waypoints + photos, **no** region centres). `isEmpty` is now `drawablePoints.isEmpty()` — defined over points, so `[[]]` counts for nothing. `allPoints` is `drawablePoints + region centres`, unchanged in what it contains for the camera.
- `GetCartographyEntryMapDataUseCase` applies `?.points?.takeIf { it.isNotEmpty() }` before mapping, so a kept track that reads back with zero points (every stored point excluded at the read seam) contributes no polyline rather than an empty one. The doc comment says both fixes are deliberate and why neither alone was enough.

### Item 4 — a kept offline region alone is not georeferenced

- Falls out of item 6's definition: region circles are not in `drawablePoints`, so `isEmpty` is true for a region-only entry and `CartographyEntryReportScreen` renders no map section for it (the text still renders below, as for the photos-with-no-coordinates case). When anything else resolved, the circle still draws and its centre still pulls the frame.
- **One consequence the ruling implied but did not spell out, made here and recorded:** the report screen now asks `getCoveringOfflineRegion` about `drawablePoints`, not `allPoints`. With region centres in the list, any kept region trivially "covered" its entry through its own centre — the coverage check's own doc says coverage means the day's data sits on the region's tiles. The screen's doc comment records this under the item-4 paragraph.

### Item 5 — an explicit `showSearchCentre` flag on `MapRenderMode`

- `MapRenderMode.showSearchCentre: Boolean = true`. Every existing caller is unchanged by the default (`AvailabilityScreen`, `CentrePinLocationPicker`). `CartographyEntryReportScreen` passes `false`. Not inferred from `trackLiveLocation == false`, per the ruling; the field's doc comment says so and why the bundle is where it goes (the ten-parameter Compose compiler crash `MapOverlayContent`'s doc comment records).
- `SightingsMap` takes `showSearchCentre`, keys its data-refresh effect on it, and feeds the search-centre source `searchCentreOverlay(region, showSearchCentre)` — the existing feature collection when on, an empty collection when off. Source and layer stay in the style either way, so flipping the flag never rebuilds the style. `searchCentreOverlay` is `internal` so the decision is testable without a `Style`, the same boundary `searchCenterFeatureCollection` already draws.

### Item 7 — kept-only, stated as a rule at the use case

- `GetCartographyEntryMapDataUseCase`'s doc comment now states: an entry's map geometry comes from this use case and nowhere else; `WaypointDecision`/`OfflineRegionDecision` coordinates in the snapshot are not to be read straight off the entry by a renderer, because that renderer would then have to repeat the `kept` filter. Withheld items are unreachable by construction, not by convention. `CartographyEntryMapData`'s doc comment points back to the rule. This is a documented rule, not an enforced one — nothing in Kotlin's visibility stops a future composable reading `entry.waypointDecisions`; the rule is where a reviewer will find it.

---

## Tests

### Round 1: seven cases run red on the unchanged code, for the predicted reasons

Written against the pre-change API and run first, so each failure could be checked against what the fix was supposed to change (CLAUDE.md, "see the failure before writing the fix"):

| Test | Failure on `0ca2f55` | Predicted? |
|---|---|---|
| `CartographyEntryMapDataTest` · a polyline with no points is not content | `isEmpty` was `false` for `[[]]` | yes |
| `CartographyEntryMapDataTest` · a kept offline region alone is not georeferenced content | `isEmpty` was `false` for a region-only value | yes |
| `GetCartographyEntriesUseCaseTest` · entries are ordered by day, newest day first | `[older, newer]` — sorted on modification time | yes |
| `GetCartographyEntriesUseCaseTest` · a same-day tie-break never lifts an entry above a newer day | `[b, a, c]` | yes |
| `GetCartographyEntryMapDataUseCaseTest` · a kept track whose every stored point is excluded contributes no polyline | `expected:<[]> but was:<[[]]>` | yes |
| `CartographyEntryReportScreenMapTest` · the coverage check is asked about the day's own points | received the waypoint **and** the region centre | yes |
| `CartographyEntryReportScreenMapTest` · a kept offline region alone renders no map section | map node found | yes |

The third `GetCartographyEntriesUseCaseTest` case (same-day tie-break alone) passed on the old sort — expected, since the old sort *was* modification-time descending. It is kept because it pins the tie-break against a future change to the primary key.

### Round 2: the cases that needed the new API

`SaveCartographyEntryUseCaseTest` (3: stamped on the way to disk with nothing else changed, stamped copy returned, drafts stamped alike), `SightingsMapOverlayDataTest` · the search-centre source is empty when the caller turns the marker off, `CartographyEntryReportScreenMapTest` · an entry map asks for no search-centre marker (and asserts `trackLiveLocation` is still `false` alongside it, since the flag is its own), `CartographyEntryMapDataTest` · drawable points exclude region centres while `allPoints` still includes them, and three stamp assertions added to `CartographyViewModelTest`'s existing committed-entry save test (on disk, in the Entries list, on the open entry) with a separate `saveNow` clock so the stamp is distinguishable from `FIXED_NOW`.

One existing test changed **because the ruling changed the behaviour it asserted**, not to reach green: `CartographyEntryReportScreenMapTest` · "offline regions reach the map's overlay content" was region-only and passed because a region centre counted as content. It now includes a waypoint and is renamed "…when something else resolved alongside them"; the region-only case is the new "renders no map section" test beside it.

A `RecordingCartographyEntryRepository` test fake was added under `app/src/test/.../domain/` for the two pure use-case tests; its doc comment says it is not a substitute for the real Room repository where the claim is about persistence (those stay in `CartographyViewModelTest`).

### Reverted-variant check, per CLAUDE.md's protocol

Copies of the four files saved to the session scratchpad before editing (never restored from git). Four one-line reverts applied together, each aimed at a disjoint set of test names:

| Revert | Edit | Predicted failures | Observed |
|---|---|---|---|
| A | `searchCentreOverlay` always returns the marker | `SightingsMapOverlayDataTest` · source is empty when off | that one |
| B | report screen passes `showSearchCentre = true` | `…MapTest` · an entry map asks for no search-centre marker | that one (`expected:<false> but was:<true>`) |
| C | `SaveCartographyEntryUseCase` keeps the incoming timestamp | 3 × `SaveCartographyEntryUseCaseTest`; `CartographyViewModelTest` · committed-entry save | those four (`expected:<5000> but was:<1000>`; `expected:<15000> but was:<10000>`) |
| D | `drawablePoints` includes region centres | `CartographyEntryMapDataTest` · drawable points / region alone; `…MapTest` · coverage points / region alone | those four |

Build log checked first: **0 compile errors**. JUnit XML confirmed fresh (0 s old) before reading. **64 tests, 10 failed** — exactly the ten predicted, no extras, every message the one its revert produces. Files restored from the copies; `git diff --stat` against `0ccfd6a` empty; `grep REVERT` over the four files: 0.

### Full suite

`./gradlew testDebugUnitTest` on the restored tree at `0ccfd6a`, `BUILD SUCCESSFUL`:

| suites | tests | failures | errors | skipped |
|---|---|---|---|---|
| 158 | 1216 | 0 | 0 | 24 |

Up from 155 / 1201 / 24 on `0ca2f55` (the pulse's own run): three new classes, fifteen new cases (`CartographyEntryMapDataTest` 4, `GetCartographyEntriesUseCaseTest` 3, `SaveCartographyEntryUseCaseTest` 3, one each in `GetCartographyEntryMapDataUseCaseTest`, `SightingsMapOverlayDataTest`; `CartographyEntryReportScreenMapTest` +3 with one renamed). The 24 skips are the CI allowlist's identity set unchanged. `JournalTabTest`'s "From Album" case passed on this run too; not touched. `assembleDebug` is CI's other task and was not run separately here — the unit-test task compiles the same main sources, and nothing in this change touches resources, the manifest, or the APK-content guard.

---

## Not built, and why

- **Drafts feed order.** Unchanged ("most recently touched first"); the ruling was about the Entries feed and unfinished work plausibly wants the other order. Flagged in `GetCartographyEntriesUseCase`'s doc comment.
- **A per-day uniqueness check on `onStartEntry`.** The owner queued it separately; the tie-break exists because of it.
- **Enforcement of item 7.** A documented rule at the use case, not a visibility change. Making `WaypointDecision.lat/lng` unreadable from UI code would touch the edit screen, which displays them as text. Not asked for.
- **Anything on plates.** The owner's direction (328 dp, single column, 4:3, with tiles; Canvas-versus-snapshotter still open) comes after these five.

## What only hardware answers

Nothing here changes what a map draws under Robolectric, where MapLibre cannot be constructed at all. The search-centre dot's absence on the entry map is asserted at the `MapRenderMode` the screen builds and at the feature collection the source receives, not on pixels. Seeing it gone is a device check.

## Overlap with the timestamp filter (PR #73)

The zero-point-track case this dispatch handles exists *because* of the read seam. `onOpenEntry`'s recompute write-back now goes through the stamping save, so opening an entry whose track figures changed also refreshes its modification time — correct (the row changed), and it does not move the entry in the feed (day sort). Records row subtitles untouched.
